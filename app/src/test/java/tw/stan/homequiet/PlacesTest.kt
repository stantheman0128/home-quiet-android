package tw.stan.homequiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.stan.homequiet.data.Place
import tw.stan.homequiet.data.QuietSnapshot
import tw.stan.homequiet.data.bearingDeg
import tw.stan.homequiet.data.compassLabel

class PlacesTest {
    private val now = System.currentTimeMillis()
    private val work = Place("w", "公司", 25.03, 121.56, 150)

    @Test fun awayEverywhere() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, fixTime = now, places = listOf(work))
        assertNull(s.activePlace); assertFalse(s.atHome)
    }

    @Test fun insideWorkByFence() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, fixTime = now, places = listOf(work.copy(fenceInside = true)))
        assertEquals("公司", s.activePlace); assertTrue(s.atHome); assertFalse(s.atHomeOnly)
    }

    @Test fun staleFixDoesNotCountButFenceDoes() {
        val old = now - QuietSnapshot.FIX_TTL_MS - 1
        assertFalse(QuietSnapshot(fixTime = old, places = listOf(work.copy(fixInside = true))).atHome)
        assertTrue(QuietSnapshot(fixTime = old, places = listOf(work.copy(fenceInside = true))).atHome)
    }

    @Test fun disabledPlaceIgnored() {
        assertFalse(QuietSnapshot(fixTime = now, places = listOf(work.copy(enabled = false, fenceInside = true))).atHome)
    }

    @Test fun placeWifi() {
        val s = QuietSnapshot(currentSsid = "\"Office\"", places = listOf(work.copy(ssids = setOf("Office"))))
        assertEquals("公司", s.activePlace)
    }

    @Test fun homeWins() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, fixTime = now, geofenceInside = true, places = listOf(work.copy(fenceInside = true)))
        assertEquals(QuietSnapshot.HOME_NAME, s.activePlace)
    }

    @Test fun codecRoundTrip() {
        val l = listOf(work.copy(ssids = setOf("A", "B"), fenceInside = true))
        assertEquals(l, Place.decode(Place.encode(l)))
    }

    @Test fun bearing() {
        assertEquals("北", compassLabel(bearingDeg(25.0, 121.5, 25.1, 121.5)))
        assertEquals("東", compassLabel(bearingDeg(25.0, 121.5, 25.0, 121.6)))
    }
}
