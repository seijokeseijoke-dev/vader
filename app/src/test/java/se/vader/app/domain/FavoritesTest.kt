package se.vader.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.vader.app.data.Place

class FavoritesTest {
    private val gbg = Place("Göteborg", "Västra Götaland", 57.7089, 11.9746)
    private val gbg2 = Place("Göteborg", "", 57.7095, 11.9750)   // samma ort, något annan punkt
    private val kungalv = Place("Kungälv", "", 57.8706, 11.9805)
    private val alingsas = Place("Alingsås", "", 57.9300, 12.5334)
    private val boras = Place("Borås", "", 57.7210, 12.9401)

    @Test
    fun addAndRemoveSamePlace() {
        val added = (Favorites.toggle(emptyList(), gbg) as Favorites.Toggle.Ok).list
        assertEquals(1, added.size)
        assertTrue(Favorites.contains(added, gbg2))
        val removed = (Favorites.toggle(added, gbg2) as Favorites.Toggle.Ok).list
        assertTrue(removed.isEmpty())
    }

    @Test
    fun maxThree() {
        val three = listOf(gbg, kungalv, alingsas)
        assertEquals(Favorites.Toggle.Full, Favorites.toggle(three, boras))
        // men man kan fortfarande ta bort en av de tre
        assertEquals(2, (Favorites.toggle(three, kungalv) as Favorites.Toggle.Ok).list.size)
    }
}
