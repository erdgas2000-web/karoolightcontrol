package io.lightprofiles.karoo.data

/**
 * The order Karoo's own LightMode enum uses, from off through the steady
 * levels to the flashes and the manufacturer's custom slots.
 *
 * A light reports which of these it supports as an unordered set, so without
 * this the picker lists them in hash order — "Steady 3, Off, Custom 5, Steady
 * 1" reads like half of them are missing.
 */
object LightModes {
    val ORDER = listOf(
        "OFF",
        "STEADY1",
        "STEADY2",
        "STEADY3",
        "STEADY4",
        "STEADY5",
        "SLOW_FLASH",
        "FAST_FLASH",
        "RANDOM_FLASH",
        "AUTO",
        "SIGNAL_LEFT_AUTO",
        "SIGNAL_LEFT",
        "SIGNAL_RIGHT_AUTO",
        "SIGNAL_RIGHT",
        "HAZARD",
        "CUSTOM_MODE_1",
        "CUSTOM_MODE_2",
        "CUSTOM_MODE_3",
        "CUSTOM_MODE_4",
        "CUSTOM_MODE_5",
        "CUSTOM_MODE_6",
        "CUSTOM_MODE_7",
        "CUSTOM_MODE_8",
    )

    /** The modes a light reported, in a sensible order, with unknown ones last. */
    fun sorted(modes: Collection<String>): List<String> =
        modes.sortedBy { mode ->
            ORDER.indexOf(mode).let { if (it < 0) ORDER.size else it }
        }

    /**
     * Karoo's own wording for each mode, taken from its system UI, so a mode
     * reads the same here as in the Control Center. Worth knowing: the steady
     * levels count down from brightest, STEADY1 being the maximum.
     */
    private val NAMES = mapOf(
        "OFF" to "Off",
        "STEADY1" to "Solid Max",
        "STEADY2" to "Solid High",
        "STEADY3" to "Solid Med",
        "STEADY4" to "Solid Low",
        "STEADY5" to "Solid Min",
        "SLOW_FLASH" to "Slow Flash",
        "FAST_FLASH" to "Fast Flash",
        "RANDOM_FLASH" to "Random Flash",
        "AUTO" to "Auto",
        "SIGNAL_LEFT_AUTO" to "Left Signal (Auto)",
        "SIGNAL_LEFT" to "Left Signal",
        "SIGNAL_RIGHT_AUTO" to "Right Signal (Auto)",
        "SIGNAL_RIGHT" to "Right Signal",
        "HAZARD" to "Hazard",
        "CUSTOM_MODE_1" to "Custom 1",
        "CUSTOM_MODE_2" to "Custom 2",
        "CUSTOM_MODE_3" to "Custom 3",
        "CUSTOM_MODE_4" to "Custom 4",
        "CUSTOM_MODE_5" to "Custom 5",
        "CUSTOM_MODE_6" to "Custom 6",
        "CUSTOM_MODE_7" to "Custom 7",
        "CUSTOM_MODE_8" to "Custom 8",
    )

    fun nameOf(mode: String): String = when {
        mode.isEmpty() -> "—"
        else -> NAMES[mode] ?: mode.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** The same name cut down for a data field, which has one short line. */
    private val SHORT = mapOf(
        "OFF" to "off",
        "STEADY1" to "max",
        "STEADY2" to "high",
        "STEADY3" to "med",
        "STEADY4" to "low",
        "STEADY5" to "min",
        "SLOW_FLASH" to "flash",
        "FAST_FLASH" to "FLASH",
        "RANDOM_FLASH" to "rnd",
        "AUTO" to "auto",
        "HAZARD" to "haz",
    )

    fun shortNameOf(mode: String): String =
        SHORT[mode] ?: mode.removePrefix("CUSTOM_MODE_").let { "c$it" }
}
