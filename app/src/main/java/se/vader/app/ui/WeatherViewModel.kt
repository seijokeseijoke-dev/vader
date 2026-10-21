package se.vader.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import se.vader.app.data.ForecastException
import se.vader.app.data.Place
import se.vader.app.data.PlaceRepository
import se.vader.app.data.SmhiRepository
import se.vader.app.domain.ForecastBuilder
import se.vader.app.domain.Week
import java.io.IOException

data class WeatherState(
    val query: String = "",
    val place: Place? = null,
    val alternatives: List<Place> = emptyList(),
    val week: Week? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val status: String? = null,   // "Söker Kungälv…"
    val error: String? = null
)

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val places = PlaceRepository(app)
    private val smhi = SmhiRepository()
    private val prefs = app.getSharedPreferences("vader", Context.MODE_PRIVATE)
    private var job: Job? = null

    var state by mutableStateOf(WeatherState(query = prefs.getString(KEY_ORT, DEFAULT_ORT) ?: DEFAULT_ORT))
        private set

    init {
        search()
    }

    fun onQueryChange(q: String) {
        state = state.copy(query = q)
    }

    /** Sök ort och hämta prognos för bästa träffen. Övriga träffar visas som "Menade du". */
    fun search() {
        val q = state.query.trim()
        if (q.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            state = state.copy(loading = true, status = "Söker $q…", error = null, alternatives = emptyList())
            try {
                val hits = places.search(q)
                if (hits.isEmpty()) {
                    state = state.copy(loading = false, status = null, week = null, place = null,
                        error = "Hittade ingen ort som heter \"$q\". Kontrollera stavningen.")
                    return@launch
                }
                prefs.edit().putString(KEY_ORT, q).apply()
                state = state.copy(alternatives = hits.drop(1).take(4))
                load(hits.first())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(loading = false, status = null, error = e.toMessage())
            }
        }
    }

    fun choose(place: Place) {
        job?.cancel()
        job = viewModelScope.launch {
            state = state.copy(alternatives = state.alternatives.filter { it != place } +
                listOfNotNull(state.place))
            load(place)
        }
    }

    fun refresh() {
        val p = state.place ?: return search()
        job?.cancel()
        job = viewModelScope.launch {
            state = state.copy(refreshing = true)
            load(p, keepContent = true)
        }
    }

    private suspend fun load(place: Place, keepContent: Boolean = false) {
        state = state.copy(
            loading = !keepContent, status = "Hämtar prognos för ${place.name}…", error = null,
            week = if (keepContent) state.week else null, place = place
        )
        state = try {
            val week = ForecastBuilder.build(smhi.forecast(place.lat, place.lon))
            state.copy(week = week, loading = false, refreshing = false, status = null,
                error = if (week.days.isEmpty()) "Prognosen saknar data för kommande dagar." else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.copy(loading = false, refreshing = false, status = null, error = e.toMessage())
        }
    }

    private fun Exception.toMessage(): String = when (this) {
        is ForecastException -> message ?: "Något gick fel."
        is IOException -> "Ingen anslutning. Kontrollera internet och försök igen."
        else -> "Kunde inte hämta prognosen."
    }

    companion object {
        private const val KEY_ORT = "ort"
        private const val DEFAULT_ORT = "Göteborg"
    }
}
