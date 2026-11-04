package io.lightprofiles.karoo.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Decides whether it is dark around, from the Karoo's light sensor.
 *
 * Tuned for tunnels and bridges rather than for dusk: it turns on fast,
 * because a bridge is over in seconds, and lets go slowly, because blinking
 * the lights at every shadow is worse than leaving them on a while longer.
 *
 *  - dark after [triggerMillis] below the threshold, about a second and a half
 *  - bright again only after [holdMillis] above it, with a margin on top so a
 *    tunnel's own lighting does not release it early
 */
class DarkDetector(
    private val now: () -> Long = System::currentTimeMillis,
) {
    var thresholdLux: Float = 30f
    var holdMillis: Long = 20_000L
    var triggerMillis: Long = 1_500L

    /** How far above the threshold it must get before counting as bright. */
    private val releaseMargin = 1.5f

    private val _dark = MutableStateFlow(false)
    val dark: StateFlow<Boolean> = _dark

    private val _lux = MutableStateFlow(0f)
    val lux: StateFlow<Float> = _lux

    private var darkSince: Long? = null
    private var brightSince: Long? = null

    fun onLux(value: Float) {
        _lux.value = value
        val time = now()

        if (value < thresholdLux) {
            brightSince = null
            val since = darkSince ?: time.also { darkSince = it }
            if (!_dark.value && time - since >= triggerMillis) {
                _dark.value = true
                Timber.d("DarkDetector: dark at %.0f lux", value)
            }
        } else if (value > thresholdLux * releaseMargin) {
            darkSince = null
            val since = brightSince ?: time.also { brightSince = it }
            if (_dark.value && time - since >= holdMillis) {
                _dark.value = false
                Timber.d("DarkDetector: bright again at %.0f lux", value)
            }
        }
    }

    fun reset() {
        darkSince = null
        brightSince = null
        _dark.value = false
    }
}
