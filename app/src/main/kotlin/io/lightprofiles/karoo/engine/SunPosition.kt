package io.lightprofiles.karoo.engine

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Solar elevation for a moment and a place.
 *
 * Uses the low-precision formulas from the Astronomical Almanac, good to roughly
 * 0.01 degrees — far beyond what switching a bike light needs. Elevation is used
 * instead of sunrise/sunset times because the interesting zone boundary ("sun is
 * low but still up") is an elevation, and because it stays correct across
 * midnight without recalculating anything.
 */
object SunPosition {

    /** Sun elevation above the horizon in degrees; negative when below. */
    fun elevationDegrees(epochMillis: Long, latitude: Double, longitude: Double): Double {
        // Days since J2000.0 (2000-01-01 12:00 UTC)
        val n = epochMillis / 86_400_000.0 + 2_440_587.5 - 2_451_545.0

        val meanLongitude = normalizeDegrees(280.460 + 0.9856474 * n)
        val meanAnomaly = Math.toRadians(normalizeDegrees(357.528 + 0.9856003 * n))
        val eclipticLongitude = Math.toRadians(
            meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly),
        )
        val obliquity = Math.toRadians(23.439 - 0.0000004 * n)

        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))

        val greenwichSiderealHours = normalizeHours(18.697374558 + 24.06570982441908 * n)
        val localSiderealDegrees = greenwichSiderealHours * 15.0 + longitude
        val hourAngle = Math.toRadians(
            normalizeDegrees(localSiderealDegrees - Math.toDegrees(rightAscension)),
        )

        val lat = Math.toRadians(latitude)
        return Math.toDegrees(
            asin(sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)),
        )
    }

    private fun normalizeDegrees(degrees: Double): Double = ((degrees % 360) + 360) % 360

    private fun normalizeHours(hours: Double): Double = ((hours % 24) + 24) % 24
}
