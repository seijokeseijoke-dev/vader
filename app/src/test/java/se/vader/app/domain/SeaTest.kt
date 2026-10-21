package se.vader.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.vader.app.data.OcobsLatest
import se.vader.app.data.OcobsMeta
import se.vader.app.data.OcobsMetaStation
import se.vader.app.data.OcobsStation
import se.vader.app.data.OcobsValue
import java.time.Instant

class SeaTest {
    private val now = Instant.parse("2026-10-21T14:00:00Z")
    private val h = 3_600_000L
    private val t = now.toEpochMilli()

    private val meta = OcobsMeta(listOf(
        OcobsMetaStation("1", "Onsala", false), OcobsMetaStation("2", "Stena Danica", true),
        OcobsMetaStation("3", "Göteborg-Tångudden", false), OcobsMetaStation("4", "Vinga", false)
    ))
    private val latest = OcobsLatest(listOf(
        OcobsStation("1", "Onsala", 57.3919, 11.9189, listOf(OcobsValue(t - h, 11.46, "G", "0"))),
        OcobsStation("2", "Stena Danica", 57.70, 11.94, listOf(OcobsValue(t - h, 12.0, "G", "0"))),
        OcobsStation("3", "Göteborg-Tångudden", 57.6821, 11.8722, listOf(OcobsValue(t - 50 * h, 10.0, "G", "0"))),
        OcobsStation("4", "Vinga", 57.6317, 11.6088, listOf(OcobsValue(t - 2 * h, 12.3, "G", "0"), OcobsValue(t - h, 12.1, "G", "0")))
    ))

    @Test
    fun picksNearestFreshFixedStation() {
        val r = Sea.nearest(meta, latest, 57.7089, 11.9746, now)!!
        assertEquals("Vinga", r.station)      // fartyget och 50 h gammalt värde sorteras bort
        assertEquals(12.1, r.temp, 0.001)     // senaste värdet
    }

    @Test
    fun inlandGivesNull() {
        assertNull(Sea.nearest(meta, latest, 57.721, 12.94, now))   // Borås
    }
}
