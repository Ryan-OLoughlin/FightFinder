package org.wit.fightfinder

import android.app.Application
import org.wit.fightfinder.models.GymListStore
import java.io.File

class FightFinderApplication : Application() {
    val gymStore by lazy { GymListStore(File(filesDir, "gyms.json")) }
}
