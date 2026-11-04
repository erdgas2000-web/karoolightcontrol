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
}
