package se.vader.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import se.vader.app.domain.Day
import se.vader.app.domain.Point
import se.vader.app.domain.STOCKHOLM
import se.vader.app.domain.SunTimes
import se.vader.app.domain.Week
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val SV = Locale("sv", "SE")
private val HM = DateTimeFormatter.ofPattern("HH:mm", SV).withZone(STOCKHOLM)
private val DM = DateTimeFormatter.ofPattern("d MMM", SV)

val ColdColor = Color(0xFF2F86CF)
val WarmColor = Color(0xFFC8641E)
val RainColor = Color(0xFF2A73B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(vm: WeatherViewModel) {
    val s = vm.state
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { keyboard?.hide(); vm.search() }

    Scaffold(topBar = { TopAppBar(title = { Text("Väder 6 dagar") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = s.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = s.query,
                            onValueChange = vm::onQueryChange,
                            label = { Text("Ort") },
                            placeholder = { Text("t.ex. Kungälv") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(onSearch = { submit() })
                        )
                        Button(onClick = ::submit, enabled = s.query.isNotBlank()) { Text("Visa") }
                    }
                }

                if (s.alternatives.isNotEmpty()) item { Alternatives(s.alternatives.map { it to "${it.name}${it.detail.substringBefore(',').let { d -> if (d.isBlank()) "" else ", $d" }}" }, vm) }

                when {
                    s.loading -> item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            s.status?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    s.error != null && s.week == null -> item {
                        ErrorBox(s.error, onRetry = vm::search)
                    }
                }

                val week = s.week
                val place = s.place
                if (week != null && place != null && week.days.isNotEmpty()) {
                    item {
                        Column {
                            Text(place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(place.detail.ifBlank { null }, week.created?.let { "uppdaterad ${HM.format(it)}" })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            s.sea?.let { sea ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Havet ${String.format(SV, "%.1f", sea.temp)}°" + if (sea.nearFreezing) " · nära fryspunkten" else "",
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${sea.station}, ${sea.distanceKm.roundToInt()} km, mätt ${HM.format(sea.time)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (s.error != null) item { ErrorBox(s.error, onRetry = vm::refresh) }
                    item { SummaryCard(week) }
                    item { DaysGrid(week) }
                    item {
                        Text(
                            "Tryck på en dag för timprognos. Högsta och lägsta temperatur under dygnet. ↑↓ = sol upp och ned. " +
                                "Dag 5–6 är mindre säkra.\nData: SMHI (CC BY 4.0), prognosmodell SNOW1g. Havstemperatur: närmaste SMHI-mätstation inom 40 km.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Alternatives(items: List<Pair<se.vader.app.data.Place, String>>, vm: WeatherViewModel) {
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text("Menade du:", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
        items.forEach { (p, label) -> TextButton(onClick = { vm.choose(p) }) { Text(label) } }
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Försök igen") }
        }
    }
}

@Composable
private fun SummaryCard(week: Week) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            week.summary.forEachIndexed { i, line ->
                Text(line, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** 6 dagar i rutnät: 3 i bredd (2 rader). Vid smal skärm eller stor text: 2 i bredd (3 rader). */
@Composable
private fun DaysGrid(week: Week) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (maxWidth >= 300.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                week.days.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d ->
                            DayTile(
                                d,
                                selected = selected == d.date.toString(),
                                onClick = { selected = if (selected == d.date.toString()) null else d.date.toString() },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        week.days.firstOrNull { it.date.toString() == selected }?.let { Hours(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DayTile(d: Day, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.alpha(if (d.uncertain) 0.8f else 1f),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                DM.format(d.date).trimEnd('.') + if (d.partial) " · resten" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            WeatherIcon(d.sym, 32.dp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${d.tMax.roundToInt()}°", color = WarmColor, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall)
                Text("${d.tMin.roundToInt()}°", color = ColdColor, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 1.dp))
            }
            d.sun?.let {
                Text(sunShort(it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val flags = d.flags
            if (flags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) { flags.forEach { FlagChip(it.text, it.warning) } }
            }
        }
    }
}

@Composable
private fun FlagChip(text: String, warning: Boolean) {
    val bg = if (warning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text.uppercase(SV),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hours(d: Day) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${d.title} ${DM.format(d.date).trimEnd('.')}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            d.sun?.let {
                Text(sunLong(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (d.coarse) {
                Text("Längre fram ges prognosen i 6- eller 12-timmarssteg.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                d.points.forEach { HourItem(it) }
            }
        }
    }
}

@Composable
private fun HourItem(p: Point) {
    Row(Modifier.width(92.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(HM.format(p.time), style = MaterialTheme.typography.bodyMedium)
        WeatherIcon(p.sym, 24.dp, Modifier.padding(horizontal = 4.dp))
        Text("${p.temp.roundToInt()}°", fontWeight = FontWeight.SemiBold)
    }
}

private fun sunShort(s: SunTimes): String = when {
    s.polarDay -> "Midnattssol"
    s.polarNight -> "Polarnatt"
    else -> "↑${HM.format(s.rise)} ↓${HM.format(s.set)}"
}

private fun sunLong(s: SunTimes): String {
    if (s.polarDay) return "Midnattssol – solen går inte ner."
    if (s.polarNight) return "Polarnatt – solen går inte upp."
    val mins = s.dayLength?.toMinutes() ?: 0
    return "Sol upp ${HM.format(s.rise)} · ned ${HM.format(s.set)} · dagslängd ${mins / 60} h ${mins % 60} min"
}
