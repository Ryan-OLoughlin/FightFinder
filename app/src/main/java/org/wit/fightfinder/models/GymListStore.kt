package org.wit.fightfinder.models

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.Strictness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

class GymListStore internal constructor(
    private val file: File,
    private val cloud: GymCloud?
) : GymListDataSource {
    constructor(file: File) : this(file, null)

    enum class SyncStatus {
        DISABLED, PENDING, SYNCED, UNAVAILABLE, ACCESS_DENIED, SIGN_IN_REQUIRED,
        DATABASE_SETUP, INVALID_RECORD, QUOTA_EXCEEDED
    }

    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create()
    private val mutex = Mutex()
    private val records = MutableStateFlow(emptyList<Gym>())
    override val gyms = records.asStateFlow()
    private var loaded = false
    private var lastId = 0L
    private var pending = emptyMap<String, String>()
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val syncScope = cloud?.let { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private val status = MutableStateFlow(if (cloud == null) SyncStatus.DISABLED else SyncStatus.PENDING)
    val syncStatus = status.asStateFlow()

    internal fun stopSync() {
        syncScope?.cancel()
    }

    override suspend fun load(): List<Gym> = withStore { records.value }

    suspend fun create(draft: Gym): Gym = withStore {
        if (lastId == Long.MAX_VALUE) throw IOException("No gym IDs available")
        val gym = draft.copy(id = lastId + 1, cloudId = if (cloud == null) null else newCloudId())
        saveChange(records.value + gym, gym.id, listOf(gym))
        gym
    }

    suspend fun update(id: Long, edit: (Gym) -> Gym): Gym = withStore {
        val next = records.value.toMutableList()
        val index = next.indexOfFirst { it.id == id }
        if (index < 0) throw IOException("Gym no longer exists")
        val current = next[index]
        val updated = edit(current).copy(cloudId = current.cloudId)
        require(updated.id == id)
        next[index] = updated
        if (updated != current) {
            val changed = if (updated.copy(imageUri = null) != current.copy(imageUri = null)) listOf(updated) else emptyList()
            saveChange(next, lastId, changed)
        }
        updated
    }

    override suspend fun delete(id: Long) = withStore {
        val gym = records.value.find { it.id == id } ?: return@withStore
        saveChange(records.value.filterNot { it.id == id }, lastId, listOf(gym))
    }

    override suspend fun deleteAll() = withStore { saveChange(emptyList(), lastId, records.value) }

    private suspend fun <T> withStore(action: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                readFile()
                prepareCloudRecords()
                loaded = true
                startSync()
            }
            action()
        }
    }

    private fun readFile() {
        val reader = try {
            Files.newBufferedReader(file.toPath())
        } catch (_: NoSuchFileException) {
            return
        }
        try {
            reader.use {
                val json = gson.fromJson(it, JsonElement::class.java)
                // The lab's original format is an array. New files retain the ID counter after deletions.
                val entries = if (json.isJsonArray) json.asJsonArray else json.asJsonObject.getAsJsonArray("gyms")
                val restored = entries.map { element -> decodeGym(element.asJsonObject) }
                require(restored.all { gym -> gym.id > 0 } && restored.distinctBy { gym -> gym.id }.size == restored.size)
                val maxId = restored.maxOfOrNull { gym -> gym.id } ?: 0L
                val counter = if (json.isJsonArray) maxId else json.asJsonObject["lastId"].asLong
                require(counter >= maxId)
                val changes = if (json.isJsonArray) null else json.asJsonObject.getAsJsonObject("pending")
                pending = changes?.entrySet()?.associate { it.key to it.value.asString }.orEmpty()
                lastId = counter
                records.value = restored
            }
        } catch (e: RuntimeException) {
            throw IOException("Invalid gym JSON", e)
        }
    }


    private fun prepareCloudRecords() {
        if (cloud == null) return
        val unlinked = records.value.filter { it.cloudId == null }
        if (unlinked.isEmpty()) return

        val linked = unlinked.associate { it.id to it.copy(cloudId = newCloudId()) }
        val next = records.value.map { linked[it.id] ?: it }
        saveChange(next, lastId, linked.values)
    }

    private fun saveChange(next: List<Gym>, counter: Long, changed: Collection<Gym>) {
        if (cloud == null) {
            persist(next, counter, pending)
            return
        }
        val queued = changed.mapNotNull { gym -> gym.cloudId?.let { it to UUID.randomUUID().toString() } }
        persist(next, counter, pending + queued)
        if (queued.isNotEmpty()) {
            status.update { if (it == SyncStatus.SYNCED) SyncStatus.PENDING else it }
            syncRequests.trySend(Unit)
        }
    }

    private fun newCloudId() = UUID.randomUUID().toString()

    private fun startSync() {
        val remote = cloud ?: return
        val scope = checkNotNull(syncScope)
        scope.launch {
            remote.changes.retryWhen { error, _ ->
                if (error !is IOException) return@retryWhen false
                showSyncFailure(error)
                delay(15_000)
                true
            }.collect { syncRequests.trySend(Unit) }
        }

        syncRequests.trySend(Unit)
        scope.launch {
            for (request in syncRequests) {
                try {
                    synchronize(remote)
                } catch (error: IOException) {
                    showSyncFailure(error)
                    delay(15_000)
                    syncRequests.trySend(Unit)
                }
            }
        }
    }

    private fun showSyncFailure(error: IOException) {
        status.value = (error as? GymCloudFailure)?.status ?: SyncStatus.UNAVAILABLE
    }

    private suspend fun synchronize(remote: GymCloud) {
        val (versions, outgoing) = withStore {
            val versions = pending.entries.take(100).associate { it.key to it.value }
            val gymsByKey = records.value.associateBy { it.cloudId }
            versions to versions.keys.map { key -> CloudGymChange(key, gymsByKey[key]?.copy(imageUri = null)) }
        }
        if (outgoing.isNotEmpty()) {
            remote.write(outgoing)
            withStore {
                val remaining = pending.filter { (key, version) -> versions[key] != version }
                persist(records.value, lastId, remaining)
            }
        }
        val incoming = remote.list()
        withStore {
            mergeCloudRecords(incoming)
            if (pending.isEmpty()) {
                status.value = SyncStatus.SYNCED
            } else {
                status.value = SyncStatus.PENDING
                syncRequests.trySend(Unit)
            }
        }
    }

    private fun mergeCloudRecords(incoming: Map<String, Gym>) {
        val local = records.value
        var counter = lastId
        val next = local.mapNotNull { gym ->
            if (gym.cloudId in pending) gym
            else incoming[gym.cloudId]?.copy(id = gym.id, cloudId = gym.cloudId, imageUri = gym.imageUri)
        }.toMutableList()
        val knownKeys = local.mapTo(mutableSetOf()) { it.cloudId }
        for ((key, gym) in incoming) {
            if (key in knownKeys || key in pending) continue
            if (counter == Long.MAX_VALUE) throw IOException("No gym IDs available")
            next.add(gym.copy(id = ++counter, cloudId = key, imageUri = null))
        }
        if (next != local) persist(next, counter, pending)
    }

    private data class Snapshot(val lastId: Long, val gyms: List<Gym>, val pending: Map<String, String>)

    private fun persist(next: List<Gym>, counter: Long, changes: Map<String, String>) {
        val destination = file.toPath().toAbsolutePath()
        val directory = Files.createDirectories(destination.parent)
        val temp = Files.createTempFile(directory, ".gyms-", ".tmp")
        try {
            Files.newBufferedWriter(temp).use { writer ->
                gson.newJsonWriter(writer).use { gson.getAdapter(Snapshot::class.java).write(it, Snapshot(counter, next, changes)) }
            }
            Files.move(temp, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
        pending = changes
        lastId = counter
        records.value = next
    }
}
