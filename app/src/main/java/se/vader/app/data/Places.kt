package se.vader.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot

data class Place(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double
)

/**
 * Ort -> koordinater med Androids inbyggda Geocoder (gratis, ingen nyckel).
 * Kräver att telefonen har en geokodningstjänst (Google Play-tjänster på de flesta telefoner).
 */
class PlaceRepository(context: Context) {

    private val geocoder = Geocoder(context.applicationContext, Locale("sv", "SE"))

    suspend fun search(query: String): List<Place> {
        if (!Geocoder.isPresent()) {
            throw ForecastException("Telefonen saknar tjänst för ortsökning.")
        }
        val raw = try {
            lookup(query.trim(), 8)
        } catch (e: IOException) {
            throw ForecastException("Ortsökningen svarade inte. Kontrollera internet och försök igen.")
        }
        // SMHI täcker Norden – sortera nordiska träffar först, behåll övriga sist.
        val nordic = setOf("SE", "NO", "DK", "FI")
        val places = raw.sortedBy { if (it.countryCode in nordic) 0 else 1 }.map { it.toPlace(query) }
        return dedupe(places)
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(q: String, max: Int): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(q, max, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) { geocoder.getFromLocationName(q, max).orEmpty() }
        }

    private fun Address.toPlace(query: String): Place {
        val name = locality ?: subLocality ?: featureName ?: query.trim()
        val detail = listOfNotNull(subAdminArea, adminArea).distinct()
            .filter { it != name }.joinToString(", ")
        return Place(name, detail, latitude, longitude)
    }

    /** Ta bort träffar som ligger inom 5 km från en tidigare träff (samma ort i olika former). */
    private fun dedupe(list: List<Place>): List<Place> {
        val out = mutableListOf<Place>()
        for (p in list) if (out.none { it.distanceKm(p) < 5.0 }) out += p
        return out
    }

    private fun Place.distanceKm(o: Place): Double =
        hypot((lat - o.lat) * 111.0, (lon - o.lon) * 111.0 * cos(Math.toRadians(lat)))
}
