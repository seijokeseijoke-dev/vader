package se.vader.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Sol upp/ned för ett datum. polarDay = midnattssol, polarNight = solen går inte upp. */
data class SunTimes(
    val rise: Instant?,
    val set: Instant?,
    val polarDay: Boolean = false,
    val polarNight: Boolean = false
) {
    val dayLength: Duration? get() = if (rise != null && set != null) Duration.between(rise, set) else null
}

/**
 * Soluppgångsekvationen (NOAA-förenklad), noggrann till ca ±1 minut på nordiska breddgrader.
 * Räknas lokalt – inget API behövs. lon positiv österut.
 */
object Sun {
    private const val RAD = Math.PI / 180

    fun times(date: LocalDate, lat: Double, lon: Double): SunTimes {
        val noonUtc = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val j = noonUtc / 86_400_000.0 + 2440587.5
        val n = ceil(j - 2451545.0 + 0.0008)
        val js = n - lon / 360
        val m = (357.5291 + 0.98560028 * js) % 360
        val c = 1.9148 * sin(m * RAD) + 0.02 * sin(2 * m * RAD) + 0.0003 * sin(3 * m * RAD)
        val l = (m + c + 180 + 102.9372) % 360
        val jTransit = 2451545.0 + js + 0.0053 * sin(m * RAD) - 0.0069 * sin(2 * l * RAD)
        val sinDecl = sin(l * RAD) * sin(23.4397 * RAD)
        val cosDecl = cos(asin(sinDecl))
        val cosW = (sin(-0.833 * RAD) - sin(lat * RAD) * sinDecl) / (cos(lat * RAD) * cosDecl)
        if (cosW < -1) return SunTimes(null, null, polarDay = true)
        if (cosW > 1) return SunTimes(null, null, polarNight = true)
        val w = acos(cosW) / RAD
        fun toInstant(jd: Double) = Instant.ofEpochMilli(((jd - 2440587.5) * 86_400_000).toLong())
        return SunTimes(toInstant(jTransit - w / 360), toInstant(jTransit + w / 360))
    }
}
