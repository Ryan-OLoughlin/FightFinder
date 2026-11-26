package org.wit.fightfinder.cloud

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.wit.fightfinder.models.CloudGymChange
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymCloud
import org.wit.fightfinder.models.GymCloudFailure
import org.wit.fightfinder.models.GymListStore.SyncStatus
import org.wit.fightfinder.models.decodeGym
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class FirestoreGyms(private val database: FirebaseFirestore, userId: String) : GymCloud {
    private val gyms = database.collection("users").document(userId).collection("gyms")
    private val gson = Gson()
    private val fieldsType = object : TypeToken<Map<String, Any?>>() {}.type

    override val changes = callbackFlow {
        val listener = gyms.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(firestoreFailure("listen", error))
            } else if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                trySend(Unit)
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun write(changes: List<CloudGymChange>) {
        if (changes.isEmpty()) return
        val batch = database.batch()
        for ((key, gym) in changes) {
            val document = gyms.document(key)
            if (gym == null) {
                batch.delete(document)
            } else {
                val json = gson.toJsonTree(gym).asJsonObject.apply {
                    remove("id")
                    remove("cloudId")
                    remove("imageUri")
                }
                val fields: Map<String, Any?> = gson.fromJson(json, fieldsType)
                batch.set(document, fields)
            }
        }
        batch.commit().awaitResult("write")
    }

    override suspend fun list(): Map<String, Gym> {
        val snapshot = gyms.get(Source.SERVER).awaitResult("read")
        if (snapshot.metadata.hasPendingWrites()) {
            throw GymCloudFailure(SyncStatus.PENDING, "Gym changes are still pending")
        }
        try {
            return snapshot.documents.associate { document ->
                val json = gson.toJsonTree(document.data).asJsonObject.apply {
                    addProperty("id", 0L)
                    remove("imageUri")
                    remove("cloudId")
                }
                document.id to decodeGym(json)
            }
        } catch (error: RuntimeException) {
            Timber.w("A Firestore gym document could not be decoded (%s)", error.javaClass.simpleName)
            throw GymCloudFailure(SyncStatus.INVALID_RECORD, "Invalid cloud gym record", error)
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(operation: String): T = suspendCancellableCoroutine { result ->
    addOnCompleteListener { task ->
        if (!result.isActive) return@addOnCompleteListener
        if (task.isSuccessful) result.resume(task.result)
        else result.resumeWithException(firestoreFailure(operation, task.exception))
    }
}

private fun firestoreFailure(operation: String, error: Exception?): GymCloudFailure {
    val code = (error as? FirebaseFirestoreException)?.code
    val status = when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> SyncStatus.ACCESS_DENIED
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> SyncStatus.SIGN_IN_REQUIRED
        FirebaseFirestoreException.Code.NOT_FOUND,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        FirebaseFirestoreException.Code.UNIMPLEMENTED -> SyncStatus.DATABASE_SETUP
        FirebaseFirestoreException.Code.INVALID_ARGUMENT -> SyncStatus.INVALID_RECORD
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> SyncStatus.QUOTA_EXCEEDED
        else -> SyncStatus.UNAVAILABLE
    }
    Timber.w("Firestore gym %s failed (%s)", operation, code?.name ?: "UNKNOWN")
    return GymCloudFailure(status, "Firestore gym $operation failed", error)
}
