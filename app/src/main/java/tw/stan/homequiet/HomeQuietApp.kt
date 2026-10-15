package tw.stan.homequiet

import android.app.Application
import tw.stan.homequiet.data.QuietStore

class HomeQuietApp : Application() {
    override fun onCreate() {
        super.onCreate()
        QuietStore.get(this)
    }
}
