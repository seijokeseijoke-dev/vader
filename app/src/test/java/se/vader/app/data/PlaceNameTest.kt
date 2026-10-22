package se.vader.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceNameTest {
    @Test
    fun billdalIsNotRenamedToGoteborg() {
        // Typiskt Geocoder-svar för en kommundel: locality = kommunen, ortnamnet i subLocality/featureName
        assertEquals("Billdal", pickPlaceName("billdal", "Billdal", "Billdal", "Göteborg", "Göteborgs kommun"))
        assertEquals("Billdal", pickPlaceName("Billdal", null, "Billdal", "Göteborg", null))
        assertEquals("Billdal", pickPlaceName("billdal", "Billdal", null, "Göteborg", null))
    }

    @Test
    fun ordinaryCityStillWorks() {
        assertEquals("Göteborg", pickPlaceName("göteborg", "Göteborg", null, "Göteborg", "Göteborgs kommun"))
        assertEquals("Kungälv", pickPlaceName("Kungälv", "1", null, "Kungälv", "Kungälvs kommun"))
    }

    @Test
    fun noMatchPrefersMostSpecificPlace() {
        assertEquals("Askim", pickPlaceName("askimm", "12", "Askim", "Göteborg", null))
    }
}
