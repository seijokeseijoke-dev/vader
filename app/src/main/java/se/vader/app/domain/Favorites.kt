package se.vader.app.domain

import se.vader.app.data.Place
import kotlin.math.cos
import kotlin.math.hypot

/** Favoritorter: max 3. Samma ort = inom 1 km (Geocoder kan ge något olika punkter för samma namn). */
object Favorites {
    const val MAX = 3

    fun same(a: Place, b: Place): Boolean =
        hypot((a.lat - b.lat) * 111.0, (a.lon - b.lon) * 111.0 * cos(Math.toRadians(a.lat))) < 1.0

    fun contains(list: List<Place>, p: Place?): Boolean = p != null && list.any { same(it, p) }

    sealed interface Toggle {
        data class Ok(val list: List<Place>) : Toggle
        data object Full : Toggle
    }

    fun toggle(list: List<Place>, p: Place): Toggle = when {
        contains(list, p) -> Toggle.Ok(list.filterNot { same(it, p) })
        list.size >= MAX -> Toggle.Full
        else -> Toggle.Ok(list + p)
    }
}
