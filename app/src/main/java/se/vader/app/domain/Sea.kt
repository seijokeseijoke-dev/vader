package se.vader.app.domain

import se.vader.app.data.OcobsLatest
import se.vader.app.data.OcobsMeta
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot

data class SeaReading(val station: String, val distanceKm: Double, val temp: Double, val time: Instant) {
    val nearFreezing: Boolean get() = temp <= 1.0
}

/** Väljer närmaste fasta station med färskt ytvärde. Null = inget hav i närheten (t.ex. inlandsort). */
object Sea {
    const val MAX_KM = 40.0
    val MAX_AGE: Duration = Duration.ofHours(36)

    fun nearest(meta: OcobsMeta?, latest: OcobsLatest, lat: Double, lon: Double, now: Instant = Instant.now()): SeaReading? {
        val mobile = meta?.station.orEmpty().filter { it.mobile == true }.mapNotNull { it.key }.toSet()
        var best: SeaReading? = null
        for (st in latest.station.orEmpty()) {
            val name = st.name ?: continue
            if (st.key in mobile || name.contains("bottensystem", ignoreCase = true)) continue  // fartyg, bottengivare
            val sLat = st.latitude ?: continue
            val sLon = st.longitude ?: continue
            val v = st.value.orEmpty()
                .filter { it.value != null && it.date != null && (it.depth?.toDoubleOrNull() ?: 0.0) <= 2.0 && it.quality != "R" }
                .maxByOrNull { it.date!! } ?: continue
            val time = Instant.ofEpochMilli(v.date!!)
            if (Duration.between(time, now) > MAX_AGE) continue
            val km = distanceKm(lat, lon, sLat, sLon)
            if (km > MAX_KM) continue
            if (best == null || km < best.distanceKm) best = SeaReading(name, km, v.value!!, time)
        }
        return best
    }

    fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double =
        hypot((aLat - bLat) * 111.2, (aLon - bLon) * 111.2 * cos(Math.toRadians(aLat)))
}
