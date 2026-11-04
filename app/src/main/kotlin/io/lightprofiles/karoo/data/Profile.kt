package io.lightprofiles.karoo.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class LightRole { FRONT, REAR }

/** What the radar does for one light in one profile. */
@Serializable
enum class RadarBehaviour {
    /** The radar changes nothing for this light. */
    IGNORE,

    /** The light stays dark and only lights up while a vehicle is behind. */
    ONLY_ON_VEHICLE,

    /** The light runs its normal mode and switches to the warning while a vehicle is behind. */
    WARN_ON_TOP,
}

/** What the three brightness bands follow. */
@Serializable
enum class BandSource { SPEED, GRADE }

/**
 * The three bands, named after how the riding feels rather than after the raw
 * number: uphill and slow are the same band, downhill and fast are the other.
 * That keeps SLOW meaning "needs less light" whichever source drives it.
 */
@Serializable
enum class Band { SLOW, NORMAL, FAST }

/** When a profile may select itself, if automatic switching is on. */
@Serializable
enum class AutoActivate { NEVER, DAY, NIGHT }

/** Mode id meaning "not set, use the profile's normal mode". */
const val MODE_INHERIT = ""

@Serializable
data class LightSetup(
    val deviceId: String,
    val deviceName: String = "",
    val role: LightRole = LightRole.FRONT,
    /** What this light does normally in this profile. */
    val mode: String = "OFF",
    val bandsEnabled: Boolean = false,
    val slowMode: String = MODE_INHERIT,
    val fastMode: String = MODE_INHERIT,
    val radar: RadarBehaviour = RadarBehaviour.IGNORE,
    val radarMode: String = "FAST_FLASH",
) {
    fun modeForBand(band: Band): String = when (band) {
        Band.SLOW -> slowMode
        Band.NORMAL -> MODE_INHERIT
        Band.FAST -> fastMode
    }
}

@Serializable
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Profile",
    val lights: List<LightSetup> = emptyList(),
    val bandSource: BandSource = BandSource.SPEED,
    val speedSlowKmh: Int = 12,
    val speedFastKmh: Int = 28,
    /** Grade at or above which the ride counts as slow, in percent. */
    val gradeUphillPct: Int = 3,
    /** Grade at or below which the ride counts as fast, in percent. */
    val gradeDownhillPct: Int = -3,
    val autoActivate: AutoActivate = AutoActivate.NEVER,
) {
    fun setupFor(deviceId: String): LightSetup? = lights.find { it.deviceId == deviceId }

    /** True when any light in this profile reacts to the bands. */
    val usesBands: Boolean
        get() = lights.any { setup ->
            setup.bandsEnabled &&
                (setup.slowMode.isNotEmpty() || setup.fastMode.isNotEmpty())
        }

    /** True when any light in this profile reacts to the radar. */
    val usesRadar: Boolean
        get() = lights.any { it.radar != RadarBehaviour.IGNORE }
}

@Serializable
data class AppSettings(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
    /** Let profiles select themselves from the sun's position. */
    val autoSwitchBySun: Boolean = false,
    /** Below this sun elevation the night profile applies, in degrees. */
    val nightElevationDegrees: Int = -6,
    /** How far past a band edge the ride must go before the band changes. */
    val bandHysteresis: Int = 3,
    val bandHoldSeconds: Int = 15,
    val radarHoldSeconds: Int = 15,
    val autoOnWithRide: Boolean = true,
    val autoOffWithRide: Boolean = true,
) {
    val activeProfile: Profile?
        get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

    fun profileFor(autoActivate: AutoActivate): Profile? =
        profiles.find { it.autoActivate == autoActivate }
}

/**
 * Decides the mode one light should be in. The whole control logic is these few
 * lines, so the settings screen can show the same table the code follows:
 *
 *   lights off        → OFF, nothing may switch a light on
 *   radar, only-on-vehicle → warning while a vehicle is there, otherwise OFF
 *   radar, warn-on-top     → warning while a vehicle is there
 *   bands on          → the band's mode, where that band has one
 *   otherwise         → the profile's normal mode
 */
object ModeResolver {
    fun resolve(
        setup: LightSetup,
        band: Band = Band.NORMAL,
        radarActive: Boolean = false,
        lightsOn: Boolean = true,
    ): String {
        if (!lightsOn) return "OFF"

        when (setup.radar) {
            RadarBehaviour.ONLY_ON_VEHICLE ->
                return if (radarActive) setup.radarMode else "OFF"
            RadarBehaviour.WARN_ON_TOP ->
                if (radarActive) return setup.radarMode
            RadarBehaviour.IGNORE -> Unit
        }

        if (setup.bandsEnabled) {
            val bandMode = setup.modeForBand(band)
            if (bandMode.isNotEmpty()) return bandMode
        }
        return setup.mode
    }
}
