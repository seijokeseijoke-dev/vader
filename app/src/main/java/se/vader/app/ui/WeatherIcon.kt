package se.vader.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.vader.app.domain.Kind
import se.vader.app.domain.Symbols
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val SunColor = Color(0xFFF2B632)
private val DropColor = Color(0xFF2F86CF)
private val FlakeColor = Color(0xFF8FC3EC)

/** Väderikon ritad i kod (48×48-rutnät, samma motiv som webbversionen). */
@Composable
fun WeatherIcon(symbol: Int, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val info = Symbols.info(symbol)
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = Symbols.name(symbol) }
    ) {
        val u = this.size.width / 48f
        when (info.kind) {
            Kind.SUN -> sun(u, 24f, 24f)
            Kind.PARTLY -> { sun(u, 18f, 16f); cloud(u, 2f, 4f, Color(0xFFB3C3CF)) }
            Kind.CLOUD -> cloud(u, 0f, 0f, if (symbol == 6) Color(0xFF8A9CAA) else Color(0xFFA9BAC7))
            Kind.FOG -> {
                cloud(u, 0f, -4f, Color(0xFFB3C3CF))
                line(u, 10f, 38f, 38f, 38f, Color(0xFF9FB1BF)); line(u, 14f, 44f, 34f, 44f, Color(0xFF9FB1BF))
            }
            Kind.RAIN -> { cloud(u, 0f, -4f, Color(0xFF9FB1BF)); drops(u, max(1, info.intensity)) }
            Kind.SLEET -> {
                cloud(u, 0f, -4f, Color(0xFF9FB1BF)); drops(u, 1)
                drawCircle(FlakeColor, 2.2f * u, Offset(28f * u, 41f * u))
            }
            Kind.SNOW -> {
                cloud(u, 0f, -4f, Color(0xFF9FB1BF))
                listOf(16f, 24f, 32f).take(max(1, info.intensity)).forEach { drawCircle(FlakeColor, 2.2f * u, Offset(it * u, 41f * u)) }
            }
            Kind.THUNDER -> {
                cloud(u, 0f, -4f, Color(0xFF7F909E))
                val p = Path().apply {
                    moveTo(24f * u, 32f * u); lineTo(19f * u, 41f * u); lineTo(24f * u, 41f * u)
                    lineTo(21f * u, 48f * u); lineTo(30f * u, 37f * u); lineTo(25f * u, 37f * u); lineTo(28f * u, 32f * u); close()
                }
                drawPath(p, SunColor)
            }
        }
    }
}

private fun DrawScope.sun(u: Float, cx: Float, cy: Float) {
    drawCircle(SunColor, 7f * u, Offset(cx * u, cy * u))
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        drawLine(
            SunColor,
            Offset((cx + cos(a) * 10).toFloat() * u, (cy + sin(a) * 10).toFloat() * u),
            Offset((cx + cos(a) * 13).toFloat() * u, (cy + sin(a) * 13).toFloat() * u),
            strokeWidth = 2.2f * u, cap = StrokeCap.Round
        )
    }
}

/** Moln: tre cirklar + rundad bas, ungefär samma siluett som SVG-molnet. */
private fun DrawScope.cloud(u: Float, dx: Float, dy: Float, c: Color) {
    fun o(x: Float, y: Float) = Offset((x + dx) * u, (y + dy) * u)
    drawCircle(c, 7f * u, o(16f, 27f))
    drawCircle(c, 10f * u, o(25f, 22f))
    drawCircle(c, 7f * u, o(34f, 27f))
    drawRoundRect(c, topLeft = o(14f, 26f), size = Size(22f * u, 8f * u), cornerRadius = CornerRadius(4f * u))
}

private fun DrawScope.drops(u: Float, n: Int) {
    listOf(16f, 24f, 32f).take(n).forEach { x -> line(u, x, 38f, x - 2f, 44f, DropColor) }
}

private fun DrawScope.line(u: Float, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) {
    drawLine(c, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
}
