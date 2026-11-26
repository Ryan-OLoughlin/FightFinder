package org.wit.fightfinder.models

import kotlinx.coroutines.flow.Flow
import java.io.IOException

internal data class CloudGymChange(val key: String, val gym: Gym?)

internal interface GymCloud {
    val changes: Flow<Unit>
    suspend fun write(changes: List<CloudGymChange>)
    suspend fun list(): Map<String, Gym>
}

internal class GymCloudFailure(
    val status: GymListStore.SyncStatus,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
