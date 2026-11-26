package org.wit.fightfinder.models

import java.io.File
import java.security.MessageDigest

internal class AccountGymStores(
    private val filesDir: File,
    private val cloudForAccount: (String) -> GymCloud? = { null }
) {
    private var activeUid: String? = null
    private var activeStore: GymListStore? = null

    @Synchronized
    fun forAccount(uid: String): GymListStore {
        if (activeUid == uid) return checkNotNull(activeStore)

        val key = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        activeStore?.stopSync()
        val store = GymListStore(File(filesDir, "accounts/$key/gyms.json"), cloudForAccount(uid))
        activeUid = uid
        activeStore = store
        return store
    }

    @Synchronized
    fun clear() {
        activeStore?.stopSync()
        activeUid = null
        activeStore = null
    }
}
