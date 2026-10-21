package se.vader.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SunTest {
    private val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(STOCKHOLM)

    private fun assertNear(expected: String, actual: java.time.Instant?) {
        val (eh, em) = expected.split(":").map { it.toInt() }
        val (ah, am) = hm.format(actual).split(":").map { it.toInt() }
        val diff = kotlin.math.abs((eh * 60 + em) - (ah * 60 + am))
        assertTrue("förväntade $expected, fick ${hm.format(actual)}", diff <= 2)
    }

    @Test
    fun stockholmMidsummer() {
        val s = Sun.times(LocalDate.of(2026, 6, 21), 59.3293, 18.0686)
        assertNear("03:31", s.rise); assertNear("22:08", s.set)
    }

    @Test
    fun malmoMidwinter() {
        val s = Sun.times(LocalDate.of(2026, 12, 21), 55.605, 13.0038)
        assertNear("08:35", s.rise); assertNear("15:37", s.set)
    }

    @Test
    fun kirunaMidnightSunAndPolarNight() {
        assertTrue(Sun.times(LocalDate.of(2026, 6, 21), 67.8558, 20.2253).polarDay)
        assertTrue(Sun.times(LocalDate.of(2026, 12, 21), 67.8558, 20.2253).polarNight)
    }

    @Test
    fun dayLengthGoteborgOctober() {
        val s = Sun.times(LocalDate.of(2026, 10, 21), 57.7089, 11.9746)
        assertEquals(9L, s.dayLength!!.toHours())
    }
}
