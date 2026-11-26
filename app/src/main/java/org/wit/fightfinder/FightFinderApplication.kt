package org.wit.fightfinder

import android.app.Application
import org.osmdroid.config.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.wit.fightfinder.cloud.FirestoreGyms
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

    private val firebaseApp by lazy {
        FirebaseApp.getApps(this).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
    }
    val auth by lazy { firebaseApp?.let(FirebaseAuth::getInstance) }
    private val firestore by lazy {
        firebaseApp?.let { app ->
            FirebaseFirestore.getInstance(app).apply {
                // The account's JSON file already provides the durable offline queue.
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }
    private val gymStores by lazy {
        AccountGymStores(filesDir) { uid -> firestore?.let { FirestoreGyms(it, uid) } }
    }

    val signedInUid get() = auth?.currentUser?.uid
    val gymStore get() = gymStores.forAccount(checkNotNull(signedInUid))

    fun signOut() {
        gymStores.clear()
        auth?.signOut()
    }
}
