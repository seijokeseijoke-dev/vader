package se.vader.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

    Scaffold(topBar = { TopAppBar(title = { Text("Väder 7 dagar") }) }) { padding ->
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
                        }
                    }
                    if (s.error != null) item { ErrorBox(s.error, onRetry = vm::refresh) }
                    item { SummaryCard(week) }
                    item { DaysCard(week) }
                    item {
                        Text(
                            "Tryck på en dag för timprognos. Stapeln visar dagens lägsta–högsta på veckans skala. " +
                                "Dag 5–7 är mindre säkra.\nData: SMHI (CC BY 4.0), prognosmodell SNOW1g.",
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

@Composable
private fun DaysCard(week: Week) {
    val lo = week.days.minOf { it.tMin }
    val hi = week.days.maxOf { it.tMax }
    val span = max(1.0, hi - lo)
    Card(Modifier.fillMaxWidth()) {
        Column {
            week.days.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider()
                DayRow(d, lo, span)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayRow(d: Day, lo: Double, span: Double) {
    var open by rememberSaveable(d.date.toString()) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .animateContentSize()
            .alpha(if (d.uncertain) 0.8f else 1f)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    DM.format(d.date).trimEnd('.') + if (d.partial) " · resten" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            WeatherIcon(d.sym, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${d.tMin.roundToInt()}°", color = ColdColor, fontWeight = FontWeight.SemiBold)
                    Text("${d.tMax.roundToInt()}°", color = WarmColor, fontWeight = FontWeight.SemiBold)
                }
                TempBar(((d.tMin - lo) / span).toFloat(), ((d.tMax - d.tMin) / span).toFloat())
                val flags = d.flags
                if (flags.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        flags.forEach { FlagChip(it.text, it.warning) }
                    }
                }
            }
        }
        if (open) Hours(d)
    }
}

@Composable
private fun TempBar(start: Float, width: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(8.dp)
            .background(track, RoundedCornerShape(4.dp))
    ) {
        Row(Modifier.fillMaxSize()) {
            if (start > 0f) Spacer(Modifier.weight(start.coerceIn(0.001f, 1f)))
            Box(
                Modifier
                    .weight(width.coerceIn(0.03f, 1f))
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(ColdColor, WarmColor)), RoundedCornerShape(4.dp))
            )
            val rest = 1f - start - width.coerceAtLeast(0.03f)
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
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

@Composable
private fun Hours(d: Day) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (d.coarse) {
            Text("Längre fram ges prognosen i 6- eller 12-timmarssteg.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.points.forEach { HourRow(it) }
    }
}

@Composable
private fun HourRow(p: Point) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(HM.format(p.time), Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
        WeatherIcon(p.sym, 26.dp)
        Text("${p.temp.roundToInt()}°", Modifier.width(48.dp).padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
    }
}
