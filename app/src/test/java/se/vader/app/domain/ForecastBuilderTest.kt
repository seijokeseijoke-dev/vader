package se.vader.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.vader.app.data.SmhiData
import se.vader.app.data.SmhiForecast
import se.vader.app.data.SmhiStep
import java.time.Instant
import java.time.temporal.ChronoUnit

class ForecastBuilderTest {

    /** Syntetisk serie i SMHI:s form: 41 h timvis, 4 × 2 h, sedan 6 h och 12 h till 10 dygn. */
    private fun series(now: Instant): SmhiForecast {
        val steps = mutableListOf<SmhiStep>()
        var t = now
        fun add(h: Long) {
            val end = t.plus(h, ChronoUnit.HOURS)
            val dayN = ChronoUnit.HOURS.between(now, end) / 24
            val wet = dayN == 2L || dayN == 3L
            val gusty = dayN == 2L
            steps += SmhiStep(
                time = end.toString(),
                intervalParametersStartTime = t.toString(),
                data = SmhiData(
                    airTemperature = 10.0 - dayN * 1.6,
                    windSpeed = if (gusty) 11.0 else 4.0,
                    windGust = if (gusty) 17.0 else 7.0,
                    precipitationMean = if (wet) 0.6 * h else 0.0,
                    precipitationProbability = if (wet) 80.0 else 5.0,
                    thunderProbability = 1.0,
                    symbolCode = if (wet) 19.0 else 2.0,
                    cloudOktas = if (wet) 8.0 else 2.0
                )
            )
            t = end
        }
        repeat(41) { add(1) }
        repeat(4) { add(2) }
        while (ChronoUnit.HOURS.between(now, t) < 240) add(if (ChronoUnit.HOURS.between(now, t) < 120) 6 else 12)
        return SmhiForecast("2026-10-21T07:46:08Z", "2026-10-21T07:30:00Z", steps)
    }

    private val now = Instant.parse("2026-10-21T06:00:00Z")

    @Test
    fun sixDaysStartingToday() {
        val week = ForecastBuilder.build(series(now), now)
        assertEquals(6, week.days.size)
        assertEquals("Idag", week.days[0].title)
        assertEquals("Imorgon", week.days[1].title)
        assertTrue(week.days[4].uncertain)
    }

    @Test
    fun precipitationIsSummedPerDayNotAveraged() {
        val week = ForecastBuilder.build(series(now), now)
        val wetDays = week.days.filter { it.precip >= 1 }
        assertTrue("förväntade regndagar", wetDays.isNotEmpty())
        // 0,6 mm/h under ett helt dygn ≈ 14 mm
        assertTrue(wetDays.maxOf { it.precip } in 10.0..16.0)
        assertEquals(19, wetDays.first().sym)
    }

    @Test
    fun summaryIsOnlyPrecipitation() {
        val summary = ForecastBuilder.build(series(now), now).summary
        val text = summary.joinToString("\n")
        assertTrue(text, summary[0].contains("regn"))
        // Borttaget på begäran: temperaturtrend, blåst och bästa dag. Endast nederbörd (+ ev. frost) får finnas.
        assertTrue(text, listOf("Blåsigt", "Bäst", "Jämn temperatur", "Kallare", "Varmare").none { text.contains(it) })
        assertTrue(text, summary.drop(1).all { it.startsWith("Risk för minusgrader") })
    }

    @Test
    fun formatting() {
        assertEquals("3,7", mm(3.7))
        assertEquals("21", mm(21.2))
        assertEquals("−0,4", temp(-0.4))
        assertEquals("−3", temp(-3.2))
    }
}
