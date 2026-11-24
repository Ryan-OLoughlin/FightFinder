package org.wit.fightfinder.models

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.Dispatchers
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

class GymListStore(private val file: File) : GymListDataSource {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create()
    private val mutex = Mutex()
    private val records = MutableStateFlow(emptyList<Gym>())
    override val gyms = records.asStateFlow()
    private var loaded = false
    private var lastId = 0L

    override suspend fun load(): List<Gym> = withStore { records.value }

    suspend fun create(draft: Gym): Gym = withStore {
        if (lastId == Long.MAX_VALUE) throw IOException("No gym IDs available")
        val gym = draft.copy(id = lastId + 1)
        persist(records.value + gym, gym.id)
        gym
    }

    suspend fun update(id: Long, edit: (Gym) -> Gym): Gym = withStore {
        val next = records.value.toMutableList()
        val index = next.indexOfFirst { it.id == id }
        if (index < 0) throw IOException("Gym no longer exists")
        val updated = edit(next[index])
        require(updated.id == id)
        next[index] = updated
        persist(next, lastId)
        updated
    }

    override suspend fun delete(id: Long) = withStore {
        persist(records.value.filterNot { it.id == id }, lastId)
    }

    override suspend fun deleteAll() = withStore { persist(emptyList(), lastId) }

    private suspend fun <T> withStore(action: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                readFile()
                loaded = true
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
                lastId = counter
                records.value = restored
            }
        } catch (e: RuntimeException) {
            throw IOException("Invalid gym JSON", e)
        }
    }

    private fun decodeGym(json: JsonObject): Gym {
        fun optionalText(key: String) = json[key]?.takeUnless { it.isJsonNull }?.asString
        return Gym(
            id = json["id"].asLong,
            name = json["name"].asString,
            latitude = json["latitude"].asDouble,
            longitude = json["longitude"].asDouble,
            imageUri = optionalText("imageUri"),
            location = optionalText("location").orEmpty(),
            price = optionalText("price").orEmpty(),
            openingHours = optionalText("openingHours").orEmpty(),
            facilities = optionalText("facilities").orEmpty(),
            classes = optionalText("classes").orEmpty(),
            addedFrom = json["addedFrom"]?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
                Coordinates(it["latitude"].asDouble, it["longitude"].asDouble)
            },
            reviews = json.getAsJsonArray("reviews")?.map { element ->
                val review = element.asJsonObject
                GymReview(review["rating"].asInt, review["comment"].asString)
            }.orEmpty(),
            attendance = json.getAsJsonArray("attendance")?.map { element ->
                val entry = element.asJsonObject
                GymAttendance(AttendanceType.valueOf(entry["type"].asString), entry["date"].asString,
                    entry["className"]?.asString.orEmpty())
            }.orEmpty()
        )
    }

    private data class Snapshot(val lastId: Long, val gyms: List<Gym>)

    private fun persist(next: List<Gym>, counter: Long) {
        val destination = file.toPath().toAbsolutePath()
        val directory = Files.createDirectories(destination.parent)
        val temp = Files.createTempFile(directory, ".gyms-", ".tmp")
        try {
            Files.newBufferedWriter(temp).use { writer ->
                gson.newJsonWriter(writer).use { gson.getAdapter(Snapshot::class.java).write(it, Snapshot(counter, next)) }
            }
            Files.move(temp, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
        lastId = counter
        records.value = next
    }
}
