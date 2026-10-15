package tw.stan.homequiet

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.stan.homequiet.data.HomeSignals
import tw.stan.homequiet.data.PackageMatch
import tw.stan.homequiet.data.QuietSnapshot
import tw.stan.homequiet.data.Updater

class LogicTest {
    @Test fun versionCompare() {
        assertTrue(Updater.newer("0.3.7", "0.3.6"))
        assertTrue(Updater.newer("0.3.6", "0.3.5"))
        assertTrue(Updater.newer("0.4.0", "0.3.10"))
        assertTrue(Updater.newer("0.3.10", "0.3.9"))
        assertFalse(Updater.newer("0.3.5", "0.3.5"))
        assertFalse(Updater.newer("0.3.4", "0.3.5"))
    }

    @Test fun updateErrorsAreSpecific() {
        assertEquals("GitHub 拒絕更新檢查（HTTP 403）", Updater.explain(IOException("HTTP 403 missing User-Agent")))
        assertEquals("沒有網路，請連線後再檢查更新", Updater.explain(java.net.UnknownHostException("api.github.com")))
        assertEquals("檢查更新逾時，請稍後再試", Updater.explain(java.net.SocketTimeoutException("timeout")))
    }

    @Test fun anyOfSeveralHomeWifisCounts() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, wifiAlsoCounts = true, homeSsids = setOf("A", "B", "C"))
        assertTrue(s.copy(currentSsid = "B").atHome)
        assertFalse(s.copy(currentSsid = null).atHome) // disconnected must clear Wi-Fi home
        assertFalse(s.copy(currentSsid = "Cafe").atHome)
        assertFalse(s.copy(currentSsid = "B", wifiAlsoCounts = false).atHome)
        assertTrue(s.copy(currentSsid = "B", homeSsids = setOf("\"B\"")).atHome)
    }

    @Test fun playFenceInsideNeedsNoFreshFix() {
        val s = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, geofenceInside = true, fixTime = 1L)
        assertTrue(s.copy(playFence = true).atHome)
        assertFalse(s.copy(playFence = false).atHome) // fallback fix is stale
    }

    @Test fun geofenceStaysHomeWhenGpsLatchIsOutside() {
        val s = QuietSnapshot(
            homeLat = 25.0,
            homeLon = 121.5,
            playFence = true,
            fenceInside = true,
            geofenceInside = false,
            fixTime = 1L,
        )
        assertTrue(s.atHome)
    }

    @Test fun freshFixInsideCountsWithoutFence() {
        val s = QuietSnapshot(
            homeLat = 25.0,
            homeLon = 121.5,
            playFence = false,
            geofenceInside = true,
            fixTime = System.currentTimeMillis(),
        )
        assertTrue(s.atHome)
    }

    @Test fun anyHomeSignalIsEnough() {
        val base = QuietSnapshot(homeLat = 25.0, homeLon = 121.5, paused = false)
        val wifi = base.copy(wifiAlsoCounts = true, homeSsids = setOf("home"), currentSsid = "home")
        assertTrue(wifi.atHome)
        assertTrue(
            PackageMatch.shouldClear(
                paused = false,
                atHome = wifi.atHome,
                wasHome = false,
                postedPackage = PackageMatch.BOT,
                selected = setOf(PackageMatch.LEGACY),
                aliasLegacy = true,
            ),
        )
        assertFalse(
            PackageMatch.shouldClear(
                paused = true,
                atHome = true,
                wasHome = true,
                postedPackage = PackageMatch.BOT,
                selected = setOf(PackageMatch.BOT),
                aliasLegacy = false,
            ),
        )
    }

    @Test fun legacyPinBlocksRealGrokBot() {
        assertTrue(PackageMatch.blocks(PackageMatch.BOT, setOf(PackageMatch.LEGACY), aliasLegacy = true))
        assertFalse(PackageMatch.blocks(PackageMatch.BOT, setOf(PackageMatch.LEGACY), aliasLegacy = false))
        assertTrue(PackageMatch.blocks(PackageMatch.BOT, setOf(PackageMatch.BOT), aliasLegacy = false))
        assertFalse(PackageMatch.blocks("com.example.mail", setOf(PackageMatch.LEGACY), aliasLegacy = true))
        assertTrue(
            PackageMatch.blocks(
                "com.example.agent",
                setOf(PackageMatch.LEGACY),
                aliasLegacy = true,
                label = "Grok Bot",
            ),
        )
        assertTrue(PackageMatch.isGrokBot("ai.x.grok.bot"))
        assertFalse(PackageMatch.isGrokBot(PackageMatch.LEGACY, "Grok"))
    }

    @Test fun locationHysteresisIgnoresOneCoarseMiss() {
        assertNull(HomeSignals.locationInside(wasInside = true, distanceMeters = 800.0, accuracyMeters = 400f, radiusMeters = 120))
        assertEquals(true, HomeSignals.locationInside(wasInside = false, distanceMeters = 40.0, accuracyMeters = 400f, radiusMeters = 120))
        assertEquals(true, HomeSignals.locationInside(wasInside = true, distanceMeters = 150.0, accuracyMeters = 10f, radiusMeters = 120))
        assertEquals(false, HomeSignals.locationInside(wasInside = true, distanceMeters = 250.0, accuracyMeters = 10f, radiusMeters = 120))
        assertTrue(HomeSignals.hardExit(distanceMeters = 2_500.0, accuracyMeters = 20f))
        assertFalse(HomeSignals.hardExit(distanceMeters = 250.0, accuracyMeters = 10f))
    }
}
