package org.wit.fightfinder.models

import java.io.File
import java.security.MessageDigest

internal class AccountGymStores(private val filesDir: File) {
    private var activeUid: String? = null
    private var activeStore: GymListStore? = null

    @Synchronized
    fun forAccount(uid: String): GymListStore {
        if (activeUid == uid) return checkNotNull(activeStore)

        val key = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val store = GymListStore(File(filesDir, "accounts/$key/gyms.json"))
        activeUid = uid
        activeStore = store
        return store
    }

    @Synchronized
    fun clear() {
        activeUid = null
        activeStore = null
    }
}
