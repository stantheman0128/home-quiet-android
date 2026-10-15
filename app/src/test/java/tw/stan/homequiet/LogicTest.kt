package tw.stan.homequiet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.stan.homequiet.data.QuietSnapshot
import tw.stan.homequiet.data.Updater

class LogicTest {
    @Test fun versionCompare() {
        assertTrue(Updater.newer("0.3.6", "0.3.5"))
        assertTrue(Updater.newer("0.4.0", "0.3.10"))
        assertTrue(Updater.newer("0.3.10", "0.3.9"))
        assertFalse(Updater.newer("0.3.5", "0.3.5"))
        assertFalse(Updater.newer("0.3.4", "0.3.5"))
    }

    @Test fun anyOfSeveralHomeWifisCounts() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, wifiAlsoCounts = true, homeSsids = setOf("A", "B", "C"))
        assertTrue(s.copy(currentSsid = "B").atHome)
        assertFalse(s.copy(currentSsid = null).atHome) // disconnected must clear Wi-Fi home
        assertFalse(s.copy(currentSsid = "Cafe").atHome)
        assertFalse(s.copy(currentSsid = "B", wifiAlsoCounts = false).atHome)
    }

    @Test fun playFenceInsideNeedsNoFreshFix() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, geofenceInside = true, fixTime = 1L)
        assertTrue(s.copy(playFence = true).atHome)
        assertFalse(s.copy(playFence = false).atHome) // fallback fix is stale
    }
}
