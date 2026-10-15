package tw.stan.homequiet

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tw.stan.homequiet.ui.HomeQuietTheme
import tw.stan.homequiet.ui.QuietRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        setContent {
            HomeQuietTheme {
                QuietRoot()
            }
        }
    }

    /**
     * Ask for the display mode with the highest refresh rate at the current resolution
     * (e.g. 120 Hz). Some OEMs (Samsung, Xiaomi, OnePlus) otherwise keep "low-motion" apps at 60 Hz.
     */
    private fun requestHighestRefreshRate() {
        val display: Display = (if (Build.VERSION.SDK_INT >= 30) display else null)
            ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
            ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val attrs = window.attributes
        attrs.preferredDisplayModeId = best.modeId
        attrs.preferredRefreshRate = best.refreshRate
        window.attributes = attrs
    }
}
