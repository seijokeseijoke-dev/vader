package se.vader.app.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import se.vader.app.domain.Favorites

/** Sparar favoriterna (med koordinater, så att ingen ny ortsökning behövs) i SharedPreferences som JSON. */
class FavoriteStore(private val prefs: SharedPreferences) {
    private val gson = Gson()
    private val type = object : TypeToken<List<Place>>() {}.type

    fun load(): List<Place> = runCatching {
        gson.fromJson<List<Place>>(prefs.getString(KEY, null) ?: return emptyList(), type).orEmpty()
            .filter { it.name.isNotBlank() }
            .take(Favorites.MAX)
    }.getOrDefault(emptyList())

    fun save(list: List<Place>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }

    private companion object { const val KEY = "favorites" }
}
