package org.wit.fightfinder

import android.app.Application
import org.osmdroid.config.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.wit.fightfinder.models.AccountGymStores
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

    private val gymStores by lazy { AccountGymStores(filesDir) }
    val auth by lazy {
        FirebaseApp.getApps(this).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?.let(FirebaseAuth::getInstance)
    }

    val signedInUid get() = auth?.currentUser?.uid
    val gymStore get() = gymStores.forAccount(checkNotNull(signedInUid))

    fun signOut() {
        auth?.signOut()
        gymStores.clear()
    }
}
