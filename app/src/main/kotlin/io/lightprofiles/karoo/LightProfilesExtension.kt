package io.lightprofiles.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState
import io.lightprofiles.karoo.data.AppSettings
import io.lightprofiles.karoo.data.BandSource
import io.lightprofiles.karoo.data.LightModes
import io.lightprofiles.karoo.data.ModeResolver
import io.lightprofiles.karoo.data.SettingsStore
import io.lightprofiles.karoo.datatypes.ProfileStatusDataType
import io.lightprofiles.karoo.engine.LightEngine
import io.lightprofiles.karoo.karoo.KarooLightControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/** A bike light Karoo knows about, as the settings screen needs it. */
data class KnownLight(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    /** What SensorService reports: CONNECTED, SEARCHING, NOT_AVAILABLE … or null when it has said nothing yet. */
    val state: String? = null,
    val supportedModes: List<String> = emptyList(),
    /** True when the list is what the light said last time, not right now. */
    val modesAreRemembered: Boolean = false,
)

class LightProfilesExtension : KarooExtension("light-profiles", BuildConfig.VERSION_NAME) {

    companion object {
        private const val TAG = "LightProfiles"
        private const val BIKE_LIGHT_DATA_TYPE = "TYPE_BIKE_LIGHT_ID"
        private const val DEVICE_TYPE_BIKE_LIGHT = 35

        @Volatile
        private var instance: LightProfilesExtension? = null
        fun getInstance(): LightProfilesExtension? = instance
    }

    init {
        instance = this
    }

    internal lateinit var karooSystem: KarooSystemService
    internal lateinit var lightControl: KarooLightControl
    internal lateinit var engine: LightEngine
    internal lateinit var store: SettingsStore

    private val _lights = MutableStateFlow<List<KnownLight>>(emptyList())
    val lights: StateFlow<List<KnownLight>> = _lights

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var radarConsumerId: String? = null
    private var bandConsumerId: String? = null
    private var bandSourceInUse: BandSource? = null

    /** Last mode sent per light, so an unchanged mode is not sent again. */
    private val lastSent = mutableMapOf<String, String>()

    override val types by lazy { listOf(ProfileStatusDataType(engine)) }

    override fun onCreate() {
        super.onCreate()
        Timber.d("$TAG: extension starting")

        karooSystem = KarooSystemService(applicationContext)
        store = SettingsStore(applicationContext)
        lightControl = KarooLightControl(applicationContext)
        engine = LightEngine()

        engine.onApply = { out ->
            // Every known light, not just the ones the profile names: a light
            // left out of a profile is off in that profile. Steering only the
            // listed ones left the headlight burning from the previous profile,
            // which reads as the setting being ignored.
            for (light in knownDevices) {
                send(
                    light.id,
                    ModeResolver.resolveFor(
                        profile = out.profile,
                        deviceId = light.id,
                        band = out.band,
                        radarActive = out.radarActive,
                        lightsOn = out.lightsOn,
                    ),
                )
            }
            updateStreams()
        }

        engine.onProfileChanged = { profile, reason ->
            karooSystem.dispatch(
                InRideAlert(
                    id = "profile-changed",
                    icon = R.drawable.ic_light,
                    title = profile.name,
                    detail = if (reason == "sun") "Switched by sun" else null,
                    autoDismissMs = 4000,
                    backgroundColor = android.R.color.black,
                    textColor = android.R.color.white,
                ),
            )
        }

        lightControl.onServiceReady = {
            lastSent.clear()
            engine.apply()
        }

        karooSystem.connect { connected ->
            Timber.d("$TAG: Karoo connected=$connected")
            if (connected) {
                observeSettings()
                karooSystem.addConsumer<RideState> { handleRideState(it) }
                karooSystem.addConsumer<OnLocationChanged> { engine.onLocation(it.lat, it.lng) }
                karooSystem.addConsumer<SavedDevices> { handleSavedDevices(it) }
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            store.flow.collect { settings ->
                engine.settings = settings
            }
        }
        scope.launch {
            lightControl.supportedModes.collect { live ->
                refreshLights()
                rememberModes(live)
            }
        }
        scope.launch {
            lightControl.connectionStates.collect { refreshLights() }
        }
    }

    private fun handleRideState(state: RideState) {
        when (state) {
            is RideState.Recording -> {
                lightControl.bind()
                engine.onRideStart()
            }
            is RideState.Paused -> engine.onRidePause()
            is RideState.Idle -> {
                engine.onRideStop()
                if (!uiActive) lightControl.unbind()
            }
        }
    }

    @Volatile
    private var uiActive = false

    /** The settings screen needs the light session for mode previews. */
    fun setUiActive(active: Boolean) {
        uiActive = active
        if (active) lightControl.bind() else engine.apply()
    }

    private var knownDevices: List<KnownLight> = emptyList()

    private fun handleSavedDevices(savedDevices: SavedDevices) {
        val lights = savedDevices.devices
            .filter { it.supportedDataTypes.contains(BIKE_LIGHT_DATA_TYPE) && it.enabled }
            .filter { device ->
                val parts = device.id.split("-")
                parts.size >= 3 && parts[1].toIntOrNull() == DEVICE_TYPE_BIKE_LIGHT
            }
            .map { KnownLight(it.id, it.name, it.details?.manufacturer) }

        knownDevices = lights
        Timber.d("$TAG: ${lights.size} light(s): ${lights.joinToString { it.name }}")

        for (light in lights) {
            lightControl.registerConnectionState(light.id)
            lightControl.registerForLightParameters(light.id)
        }
        refreshLights()
    }

    /**
     * Keep what a light told us about itself. It only reports while awake, and
     * many lights sleep as soon as they are switched off — without this the
     * editor forgets the mode list on every restart and offers a generic one.
     */
    private suspend fun rememberModes(live: Map<String, Set<String>>) {
        val stored = engine.settings.knownModes
        val merged = stored.toMutableMap()
        var changed = false
        for ((id, modes) in live) {
            if (modes.isEmpty()) continue
            val sorted = LightModes.sorted(modes)
            if (stored[id] != sorted) {
                merged[id] = sorted
                changed = true
            }
        }
        if (changed) {
            Timber.d("$TAG: remembering modes ${merged.mapValues { it.value.size }}")
            store.update(engine.settings.copy(knownModes = merged))
        }
    }

    private fun refreshLights() {
        _lights.value = knownDevices.map { light ->
            val live = lightControl.supportedModes.value[light.id].orEmpty()
            val remembered = engine.settings.knownModes[light.id].orEmpty()
            light.copy(
                state = lightControl.connectionStates.value[light.id],
                supportedModes = if (live.isNotEmpty()) LightModes.sorted(live) else remembered,
                modesAreRemembered = live.isEmpty() && remembered.isNotEmpty(),
            )
        }
    }

    private fun send(deviceId: String, mode: String) {
        if (lastSent[deviceId] == mode) return
        lastSent[deviceId] = mode
        Timber.d("$TAG: $deviceId → $mode")
        lightControl.setLightMode(deviceId, mode)
    }

    /** Preview a mode from the settings screen, bypassing all the logic. */
    fun previewMode(deviceId: String, mode: String) {
        lastSent.remove(deviceId)
        lightControl.setLightMode(deviceId, mode)
    }

    /** Subscribe to exactly the streams the active profile needs, and no others. */
    private fun updateStreams() {
        val profile = engine.activeProfile
        val wantRadar = profile?.usesRadar == true && engine.output.value.lightsOn
        if (wantRadar) startRadar() else stopRadar()

        val wantBands = profile?.usesBands == true && engine.output.value.lightsOn
        val source = profile?.bandSource
        if (!wantBands || source == null) {
            stopBands()
        } else if (source != bandSourceInUse) {
            stopBands()
            startBands(source)
        }
    }

    private fun startRadar() {
        if (radarConsumerId != null) return
        radarConsumerId = karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.RADAR),
        ) { event: OnStreamState ->
            val state = event.state
            if (state is StreamState.Streaming) {
                val threat = state.dataPoint.values[DataType.Field.RADAR_THREAT_LEVEL]?.toInt() ?: 0
                engine.onRadarThreat(threat > 0)
            }
        }
        Timber.d("$TAG: radar stream on")
    }

    private fun stopRadar() {
        radarConsumerId?.let { karooSystem.removeConsumer(it) }
        radarConsumerId = null
    }

    private fun startBands(source: BandSource) {
        val type = when (source) {
            BandSource.SPEED -> DataType.Type.SMOOTHED_10S_AVERAGE_SPEED
            BandSource.GRADE -> DataType.Type.ELEVATION_GRADE
        }
        bandConsumerId = karooSystem.addConsumer(
            OnStreamState.StartStreaming(type),
        ) { event: OnStreamState ->
            val state = event.state
            if (state is StreamState.Streaming) {
                val values = state.dataPoint.values
                when (source) {
                    // Karoo streams speed in m/s.
                    BandSource.SPEED -> values[DataType.Field.SPEED]?.let { engine.onSample(it * 3.6) }
                    BandSource.GRADE -> values[DataType.Field.ELEVATION_GRADE]?.let { engine.onSample(it) }
                }
            }
        }
        bandSourceInUse = source
        Timber.d("$TAG: band stream on ($source)")
    }

    private fun stopBands() {
        bandConsumerId?.let { karooSystem.removeConsumer(it) }
        bandConsumerId = null
        bandSourceInUse = null
    }

    override fun onBonusAction(actionId: String) {
        when (actionId) {
            "next-profile" -> {
                val profile = engine.nextProfile()
                if (profile != null) {
                    scope.launch { store.update(engine.settings) }
                }
            }
            "toggle-lights" -> {
                // Off means off: send OFF to every light of the active profile.
                val profile = engine.activeProfile ?: return
                val anyOn = profile.lights.any { lastSent[it.deviceId] != "OFF" }
                if (anyOn) {
                    profile.lights.forEach { send(it.deviceId, "OFF") }
                } else {
                    lastSent.clear()
                    engine.apply()
                }
            }
        }
    }

    /** Persist a settings change made from the UI. */
    fun save(settings: AppSettings) {
        scope.launch { store.update(settings) }
    }

    override fun onDestroy() {
        Timber.d("$TAG: extension stopping")
        instance = null
        stopRadar()
        stopBands()
        engine.destroy()
        lightControl.unbind()
        karooSystem.disconnect()
        scope.cancel()
        super.onDestroy()
    }
}
