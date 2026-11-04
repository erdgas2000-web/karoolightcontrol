package io.lightprofiles.karoo.engine

import io.lightprofiles.karoo.data.Band
import io.lightprofiles.karoo.data.BandSource
import io.lightprofiles.karoo.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Turns one signal — speed or grade — into one of three bands.
 *
 * Grade is inverted on the way in: riding up a 6% ramp is the same band as
 * crawling along at 8 km/h, because both are when a headlight needs least.
 * Everything downstream therefore only ever sees SLOW, NORMAL or FAST.
 *
 * Two guards keep this from spamming the light with mode commands, which an
 * ANT+ light answers slowly and which costs battery on both ends: a band is
 * only left once the signal is [hysteresis] past the edge it came in through,
 * and a change is ignored while the previous one is younger than
 * [minHoldMillis].
 */
class BandController(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var source: BandSource = BandSource.SPEED
    private var slowEdge: Double = 12.0
    private var fastEdge: Double = 28.0

    var hysteresis: Double = 3.0
    var minHoldMillis: Long = 15_000L

    private val _band = MutableStateFlow(Band.NORMAL)
    val band: StateFlow<Band> = _band

    private var lastChangeAt: Long = 0L

    /** Which data stream this profile wants the bands to follow. */
    val activeSource: BandSource get() = source

    fun configure(profile: Profile) {
        source = profile.bandSource
        when (source) {
            BandSource.SPEED -> {
                slowEdge = profile.speedSlowKmh.toDouble()
                fastEdge = profile.speedFastKmh.toDouble()
            }
            BandSource.GRADE -> {
                // Negated so that "more of the signal" always means "more light":
                // a -4% descent becomes +4, a +4% climb becomes -4.
                slowEdge = -profile.gradeUphillPct.toDouble()
                fastEdge = -profile.gradeDownhillPct.toDouble()
            }
        }
    }

    /** Feed a sample: km/h for [BandSource.SPEED], percent for [BandSource.GRADE]. */
    fun onSample(value: Double) {
        val signal = if (source == BandSource.GRADE) -value else value
        val target = bandFor(signal)
        if (target == _band.value) return

        if (lastChangeAt != 0L && now() - lastChangeAt < minHoldMillis) return

        lastChangeAt = now()
        val previous = _band.value
        _band.value = target
        Timber.d("BandController: %s → %s (%s signal %.1f)", previous, target, source, signal)
    }

    private fun bandFor(signal: Double): Band {
        val plain = when {
            signal >= fastEdge -> Band.FAST
            signal >= slowEdge -> Band.NORMAL
            else -> Band.SLOW
        }
        return when {
            _band.value == Band.FAST && signal > fastEdge - hysteresis -> Band.FAST
            _band.value == Band.NORMAL && plain == Band.SLOW &&
                signal > slowEdge - hysteresis -> Band.NORMAL
            else -> plain
        }
    }

    fun reset() {
        lastChangeAt = 0L
        _band.value = Band.NORMAL
    }
}
