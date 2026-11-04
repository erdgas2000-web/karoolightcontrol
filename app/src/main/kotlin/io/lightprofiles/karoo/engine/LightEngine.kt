package io.lightprofiles.karoo.engine

import io.lightprofiles.karoo.data.AppSettings
import io.lightprofiles.karoo.data.AutoActivate
import io.lightprofiles.karoo.data.Band
import io.lightprofiles.karoo.data.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Decides which profile is in force and what the lights should be doing.
 *
 * Everything the lights need is in [Output], handed out in one piece, so no
 * part of the app can set a mode behind another part's back.
 */
class LightEngine(
    private val bands: BandController = BandController(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val SUN_CHECK_INTERVAL_MS = 60_000L
    }

    data class Output(
        val profile: Profile?,
        val lightsOn: Boolean,
        val band: Band = Band.NORMAL,
        val radarActive: Boolean = false,
    )

    var onApply: ((Output) -> Unit)? = null

    /** Called when the active profile changes, for an in-ride notice. */
    var onProfileChanged: ((Profile, String) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var radarHoldJob: Job? = null
    private var sunCheckJob: Job? = null
    private var bandJob: Job? = null

    private val _output = MutableStateFlow(Output(null, false))
    val output: StateFlow<Output> = _output

    private val _radarActive = MutableStateFlow(false)
    val radarActive: StateFlow<Boolean> = _radarActive

    val band: StateFlow<Band> = bands.band

    private var riding = false

    /** Set once the rider picks a profile by hand; auto switching then waits for the next ride. */
    private var manualChoice = false

    private var latitude: Double? = null
    private var longitude: Double? = null

    @Volatile
    var settings: AppSettings = AppSettings()
        set(value) {
            field = value
            bands.hysteresis = value.bandHysteresis.toDouble()
            bands.minHoldMillis = value.bandHoldSeconds * 1000L
            value.activeProfile?.let { bands.configure(it) }
            apply()
        }

    val activeProfile: Profile? get() = settings.activeProfile

    fun onLocation(lat: Double, lng: Double) {
        latitude = lat
        longitude = lng
    }

    fun onRideStart() {
        if (riding) return
        riding = true
        manualChoice = false
        Timber.d("LightEngine: ride started")
        if (settings.autoSwitchBySun) selectBySun("ride start")
        startBandObserving()
        startSunChecking()
        apply()
    }

    /**
     * Auto-pause fires at every traffic light, so a pause changes nothing: the
     * lights keep doing what they were doing, and the bands keep following.
     */
    fun onRidePause() {
        Timber.d("LightEngine: ride paused")
    }

    fun onRideStop() {
        if (!riding) return
        riding = false
        Timber.d("LightEngine: ride stopped")
        stopBandObserving()
        stopSunChecking()
        stopRadarHold()
        bands.reset()
        _radarActive.value = false
        // Without auto-off we simply stop steering and leave the lights as they
        // are, rather than reaching in once more to switch them off.
        if (settings.autoOffWithRide) apply()
    }

    /** True while the lights are being driven at all. */
    private fun lightsOn(): Boolean = riding && settings.autoOnWithRide

    fun onSample(value: Double) {
        val profile = activeProfile ?: return
        if (!profile.usesBands) return
        bands.onSample(value)
    }

    /**
     * Radar threat, with a hold so a column of cars does not strobe the light.
     */
    fun onRadarThreat(threatDetected: Boolean) {
        if (threatDetected) {
            stopRadarHold()
            if (!_radarActive.value) {
                _radarActive.value = true
                Timber.d("LightEngine: vehicle detected")
                apply()
            }
            return
        }
        if (!_radarActive.value || radarHoldJob != null) return
        radarHoldJob = scope.launch {
            delay(settings.radarHoldSeconds * 1000L)
            _radarActive.value = false
            radarHoldJob = null
            Timber.d("LightEngine: radar clear")
            apply()
        }
    }

    private fun stopRadarHold() {
        radarHoldJob?.cancel()
        radarHoldJob = null
    }

    /** Step to the next profile in the list; used by the handlebar button. */
    fun nextProfile(): Profile? {
        val profiles = settings.profiles
        if (profiles.isEmpty()) return null
        val index = profiles.indexOfFirst { it.id == settings.activeProfileId }
        val next = profiles[(index + 1).mod(profiles.size)]
        selectProfile(next.id, "button")
        return next
    }

    fun selectProfile(profileId: String, reason: String = "manual") {
        if (profileId == settings.activeProfileId) return
        val profile = settings.profiles.find { it.id == profileId } ?: return
        manualChoice = reason != "sun"
        settings = settings.copy(activeProfileId = profileId)
        bands.reset()
        Timber.d("LightEngine: profile '${profile.name}' ($reason)")
        onProfileChanged?.invoke(profile, reason)
    }

    private fun selectBySun(reason: String) {
        if (manualChoice) return
        val lat = latitude ?: return
        val lng = longitude ?: return
        val elevation = SunPosition.elevationDegrees(now(), lat, lng)
        val wanted = if (elevation < settings.nightElevationDegrees) {
            AutoActivate.NIGHT
        } else {
            AutoActivate.DAY
        }
        val profile = settings.profileFor(wanted) ?: return
        if (profile.id == settings.activeProfileId) return
        Timber.d("LightEngine: sun at %.1f° selects '%s' (%s)", elevation, profile.name, reason)
        selectProfile(profile.id, "sun")
    }

    private fun startSunChecking() {
        if (sunCheckJob != null) return
        sunCheckJob = scope.launch {
            while (true) {
                delay(SUN_CHECK_INTERVAL_MS)
                if (settings.autoSwitchBySun) selectBySun("sun moved")
            }
        }
    }

    private fun stopSunChecking() {
        sunCheckJob?.cancel()
        sunCheckJob = null
    }

    private fun startBandObserving() {
        if (bandJob != null) return
        bandJob = scope.launch {
            bands.band.collectLatest { apply() }
        }
    }

    private fun stopBandObserving() {
        bandJob?.cancel()
        bandJob = null
    }

    /** Hand the full picture to whoever talks to the lights. */
    fun apply() {
        val out = Output(
            profile = activeProfile,
            lightsOn = lightsOn(),
            band = bands.band.value,
            radarActive = _radarActive.value,
        )
        _output.value = out
        onApply?.invoke(out)
    }

    fun destroy() {
        stopBandObserving()
        stopSunChecking()
        stopRadarHold()
        scope.cancel()
    }
}
