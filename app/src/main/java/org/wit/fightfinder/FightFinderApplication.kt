package org.wit.fightfinder

import android.app.Application
import org.osmdroid.config.Configuration
import org.wit.fightfinder.models.GymListStore
import java.io.File

class FightFinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }

    val gymStore by lazy { GymListStore(File(filesDir, "gyms.json")) }
}
