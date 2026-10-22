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
import se.vader.app.data.FavoriteStore
import se.vader.app.domain.Favorites
import se.vader.app.domain.ForecastBuilder
import se.vader.app.domain.SeaReading
import se.vader.app.domain.Week
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException

data class WeatherState(
    val query: String = "",
    val place: Place? = null,
    val alternatives: List<Place> = emptyList(),
    val week: Week? = null,
    val sea: SeaReading? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val status: String? = null,   // "Söker Kungälv…"
    val error: String? = null,
    val favorites: List<Place> = emptyList(),
    val favMessage: String? = null
) {
    val isFavorite: Boolean get() = Favorites.contains(favorites, place)
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val places = PlaceRepository(app)
    private val smhi = SmhiRepository()
    private val prefs = app.getSharedPreferences("vader", Context.MODE_PRIVATE)
    private val favStore = FavoriteStore(prefs)
    private var job: Job? = null

    var state by mutableStateOf(
        WeatherState(query = prefs.getString(KEY_ORT, DEFAULT_ORT) ?: DEFAULT_ORT, favorites = favStore.load())
    )
        private set

    /** Stjärnan vid ortnamnet: lägg till / ta bort aktuell ort. */
    fun toggleFavorite() {
        val p = state.place ?: return
        state = when (val r = Favorites.toggle(state.favorites, p)) {
            is Favorites.Toggle.Ok -> { favStore.save(r.list); state.copy(favorites = r.list, favMessage = null) }
            Favorites.Toggle.Full -> state.copy(favMessage = "Max ${Favorites.MAX} favoriter – ta bort en med × först.")
        }
    }

    fun removeFavorite(p: Place) {
        val list = state.favorites.filterNot { Favorites.same(it, p) }
        favStore.save(list)
        state = state.copy(favorites = list, favMessage = null)
    }

    /** Tryck på en favorit: visas som sökt ort, utan ny ortsökning (koordinaterna är sparade). */
    fun openFavorite(p: Place) {
        job?.cancel()
        prefs.edit().putString(KEY_ORT, p.name).apply()
        state = state.copy(query = p.name, alternatives = emptyList(), favMessage = null)
        job = viewModelScope.launch { load(p) }
    }

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
            state = state.copy(loading = true, status = "Söker $q…", error = null, alternatives = emptyList(), favMessage = null)
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
            week = if (keepContent) state.week else null, place = place,
            sea = if (keepContent) state.sea else null
        )
        state = try {
            val (json, sea) = coroutineScope {
                val f = async { smhi.forecast(place.lat, place.lon) }
                val s = async { smhi.seaTemperature(place.lat, place.lon) }
                f.await() to s.await()
            }
            val week = ForecastBuilder.build(json)
            state.copy(week = week, sea = sea, loading = false, refreshing = false, status = null,
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
