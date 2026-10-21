package se.vader.app.domain

import se.vader.app.data.SmhiForecast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val SV = Locale("sv", "SE")
val STOCKHOLM: ZoneId = ZoneId.of("Europe/Stockholm")

/* ---------------- Wsymb2 (1–27) ---------------- */

enum class Kind { SUN, PARTLY, CLOUD, FOG, RAIN, SLEET, SNOW, THUNDER }

data class SymInfo(val kind: Kind, val intensity: Int)

object Symbols {
    val names = mapOf(
        1 to "Klart", 2 to "Nästan klart", 3 to "Växlande molnighet", 4 to "Halvklart", 5 to "Molnigt",
        6 to "Mulet", 7 to "Dimma", 8 to "Lätta regnskurar", 9 to "Regnskurar", 10 to "Kraftiga regnskurar",
        11 to "Åskskurar", 12 to "Lätta byar av snöblandat regn", 13 to "Byar av snöblandat regn",
        14 to "Kraftiga byar av snöblandat regn", 15 to "Lätta snöbyar", 16 to "Snöbyar", 17 to "Kraftiga snöbyar",
        18 to "Lätt regn", 19 to "Regn", 20 to "Kraftigt regn", 21 to "Åska", 22 to "Lätt snöblandat regn",
        23 to "Snöblandat regn", 24 to "Kraftigt snöblandat regn", 25 to "Lätt snöfall", 26 to "Snöfall",
        27 to "Kraftigt snöfall"
    )

    fun name(s: Int) = names[s] ?: "Väder"

    fun info(s: Int): SymInfo = when (s) {
        in 1..2 -> SymInfo(Kind.SUN, 0)
        in 3..4 -> SymInfo(Kind.PARTLY, 0)
        in 5..6 -> SymInfo(Kind.CLOUD, 0)
        7 -> SymInfo(Kind.FOG, 0)
        11, 21 -> SymInfo(Kind.THUNDER, 3)
        in 8..10 -> SymInfo(Kind.RAIN, s - 7)
        in 12..14 -> SymInfo(Kind.SLEET, s - 11)
        in 15..17 -> SymInfo(Kind.SNOW, s - 14)
        in 18..20 -> SymInfo(Kind.RAIN, s - 17)
        in 22..24 -> SymInfo(Kind.SLEET, s - 21)
        in 25..27 -> SymInfo(Kind.SNOW, s - 24)
        else -> SymInfo(Kind.CLOUD, 0)
    }

    fun isWet(s: Int) = s >= 8
}

/* ---------------- modeller ---------------- */

data class Point(
    val time: Instant,
    val start: Instant,
    val hours: Int,
    val localHour: Int,
    val temp: Double,
    val wind: Double,
    val gust: Double,
    val precip: Double,      // mm under intervallet start -> time
    val pop: Double,
    val thunder: Double,
    val sym: Int
)

data class Flag(val text: String, val warning: Boolean)

data class Day(
    val date: LocalDate,
    val index: Int,
    val points: List<Point>,
    val tMin: Double,
    val tMax: Double,
    val precip: Double,
    val windMax: Double,
    val gustMax: Double,
    val pop: Double,
    val thunder: Double,
    val sym: Int,
    val partial: Boolean,    // idag, när en del av dygnet redan passerat
    val coarse: Boolean,     // 6/12-timmarssteg
    val uncertain: Boolean   // dag 5 och framåt
) {
    val snowy: Boolean get() = Symbols.info(sym).kind in setOf(Kind.SNOW, Kind.SLEET)
    private val weekday get() = date.dayOfWeek
    /** "Idag", "Imorgon", "Fre" */
    val title: String
        get() = when (index) {
            0 -> "Idag"; 1 -> "Imorgon"
            else -> weekday.getDisplayName(TextStyle.SHORT, SV).trimEnd('.').replaceFirstChar { it.titlecase(SV) }
        }
    /** "idag", "imorgon", "fre" – i löptext */
    val short: String
        get() = when (index) {
            0 -> "idag"; 1 -> "imorgon"
            else -> weekday.getDisplayName(TextStyle.SHORT, SV).trimEnd('.')
        }
    /** "idag", "imorgon", "fredag" */
    val long: String
        get() = when (index) {
            0 -> "idag"; 1 -> "imorgon"
            else -> weekday.getDisplayName(TextStyle.FULL, SV)
        }

    val flags: List<Flag>
        get() = buildList {
            if (tMin <= 0) add(Flag("Frost", true))
            if (gustMax >= 15 || windMax >= 10) add(Flag("Blåsigt", true))
            if (precip >= 10) add(Flag("Mycket nederbörd", true))
            if (thunder >= 30 || sym == 11 || sym == 21) add(Flag("Åska", true))
            if (uncertain) add(Flag("Osäker", false))
        }
}

data class Week(val days: List<Day>, val summary: List<String>, val created: Instant?)

/* ---------------- aggregering ---------------- */

object ForecastBuilder {

    fun build(json: SmhiForecast, now: Instant = Instant.now(), nDays: Int = 7): Week {
        val points = json.timeSeries.orEmpty().mapNotNull { step ->
            val d = step.data ?: return@mapNotNull null
            val time = step.time?.let(::parse) ?: return@mapNotNull null
            val start = step.intervalParametersStartTime?.let(::parse) ?: time.minusSeconds(3600)
            val temp = d.airTemperature ?: return@mapNotNull null
            val wind = d.windSpeed ?: 0.0
            Point(
                time = time,
                start = start,
                hours = max(1, Duration.between(start, time).toHours().toInt()),
                localHour = time.atZone(STOCKHOLM).hour,
                temp = temp,
                wind = wind,
                gust = d.windGust ?: wind,
                precip = max(0.0, d.precipitationMean ?: 0.0),
                pop = d.precipitationProbability ?: 0.0,
                thunder = d.thunderProbability ?: 0.0,
                sym = d.symbolCode?.roundToInt() ?: 6
            )
        }

        val byDay = points.groupBy { it.time.atZone(STOCKHOLM).toLocalDate() }
        // Nederbörd till det dygn där intervallets mittpunkt ligger (12h-steg korsar midnatt).
        val precipByDay = HashMap<LocalDate, Double>()
        for (p in points) {
            val mid = p.start.plus(Duration.between(p.start, p.time).dividedBy(2))
            val key = mid.atZone(STOCKHOLM).toLocalDate()
            precipByDay[key] = (precipByDay[key] ?: 0.0) + p.precip
        }

        val today = now.atZone(STOCKHOLM).toLocalDate()
        val days = byDay.keys.filter { !it.isBefore(today) }.sorted().take(nDays).mapIndexed { i, date ->
            val pts = byDay.getValue(date)
            val precip = ((precipByDay[date] ?: 0.0) * 10).roundToInt() / 10.0
            Day(
                date = date,
                index = i,
                points = pts,
                tMin = pts.minOf { it.temp },
                tMax = pts.maxOf { it.temp },
                precip = precip,
                windMax = pts.maxOf { it.wind },
                gustMax = pts.maxOf { it.gust },
                pop = pts.maxOf { it.pop },
                thunder = pts.maxOf { it.thunder },
                sym = daySymbol(pts, precip),
                partial = date == today && pts.first().localHour > 9,
                coarse = pts.size <= 4,
                uncertain = i >= 4
            )
        }
        return Week(days, summarize(days), json.createdTime?.let(::parse))
    }

    private fun parse(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()

    /** Dagtid 07–20. Torr dag -> typisk molnighet (median). Våt dag -> vanligaste nederbördssymbolen, lika -> kraftigast. */
    fun daySymbol(all: List<Point>, precip: Double): Int {
        val pts = all.filter { it.localHour in 7..20 }.ifEmpty { all }
        if (precip < 0.5) {
            val dry = pts.map { if (Symbols.isWet(it.sym)) 6 else it.sym }.sorted()
            return dry[dry.size / 2]
        }
        val tally = HashMap<Int, Int>()
        for (p in pts) if (Symbols.isWet(p.sym)) tally[p.sym] = (tally[p.sym] ?: 0) + p.hours
        if (tally.isEmpty()) return 18 // nederbörd nattetid, torrt på dagen
        return tally.entries.sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { Symbols.info(it.key).intensity }
        ).first().key
    }

    /* ---------------- sammanfattning i klartext ---------------- */

    fun summarize(days: List<Day>): List<String> {
        if (days.isEmpty()) return emptyList()
        val out = mutableListOf<String>()

        val wet = days.map { it.precip >= 1 }
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < days.size) {
            if (wet[i]) { var j = i; while (j + 1 < days.size && wet[j + 1]) j++; ranges += i..j; i = j }
            i++
        }
        fun label(r: IntRange) = if (r.first == r.last) days[r.first].short else "${days[r.first].short}–${days[r.last].short}"
        fun what(r: IntRange): String {
            val sub = days.subList(r.first, r.last + 1)
            return when {
                sub.all { it.snowy } -> "snö"
                sub.any { it.snowy } -> "regn eller snö"
                else -> "regn"
            }
        }

        if (ranges.isEmpty()) {
            out += "Torrt hela perioden."
        } else {
            val parts = ranges.map { "${what(it)} ${label(it)}" }
            val first = ranges.first().first
            var s = when {
                ranges.size == 1 && first == 0 && ranges[0].last == days.lastIndex -> "Nederbörd alla dagar"
                first == 0 -> parts.joinToString(", ").cap()
                first == 1 -> "Uppehåll idag, sedan ${parts.joinToString(", ")}"
                else -> "Uppehåll till ${days[first].long}, sedan ${parts.joinToString(", ")}"
            }
            val wettest = days.maxBy { it.precip }
            val total = days.sumOf { it.precip }.roundToInt()
            s += ", mest ${wettest.short} (${mm(wettest.precip)} mm). Totalt ca $total mm."
            out += s
        }

        val frost = days.filter { it.tMin <= 0 }
        if (frost.isNotEmpty()) out += "Risk för minusgrader ${listDays(frost)} (lägst ${temp(frost.minOf { it.tMin })}°)."


        return out
    }

    private fun listDays(a: List<Day>): String {
        val n = a.map { it.short }
        return if (n.size <= 2) n.joinToString(" och ") else n.dropLast(1).joinToString(", ") + " och " + n.last()
    }

    private fun String.cap() = replaceFirstChar { it.titlecase(SV) }
}

/** mm med decimalkomma: 3,7 respektive 21 */
fun mm(x: Double): String =
    if (x < 10) String.format(SV, "%.1f", x) else x.roundToInt().toString()

/** Temperatur nära noll med en decimal så att −0,4 inte blir "0". */
fun temp(x: Double): String =
    (if (abs(x) < 1) String.format(SV, "%.1f", x) else x.roundToInt().toString()).replace("-", "−")
