package org.wit.fightfinder.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class GymListStoreTest {
    @get:Rule val directory = TemporaryFolder()
    private val file get() = File(directory.root, "gyms.json")
    private val draft = Gym(0, "Dublin", 53.3498, -6.2603, "content://photos/1",
        "Main St", "€10", "9–5", "Mats", "Judo", addedFrom = Coordinates(52.1, -7.2))

    @Test fun reloadsFullProfilesAndAllMutationsById() = runBlocking {
        val store = GymListStore(file)
        assertTrue(store.load().isEmpty())
        val first = store.create(draft)
        val second = store.create(draft.copy(imageUri = null, addedFrom = null))
        assertNotEquals(first.id, second.id)
        val restarted = GymListStore(file)
        assertEquals(listOf(first, second), restarted.load())
        val review = GymReview(4, "Good class")
        val visit = GymAttendance(AttendanceType.VISIT, "18 Nov 2026")
        val lesson = GymAttendance(AttendanceType.CLASS, "19 Nov 2026", "Judo")
        restarted.update(first.id) { it.copy(reviews = it.reviews + review) }
        restarted.update(first.id) { it.copy(attendance = it.attendance + visit + lesson) }
        val edited = restarted.update(first.id) { it.copy(latitude = 53.2, name = "New name", imageUri = "content://photos/2") }
        assertEquals(first.copy(name = "New name", latitude = 53.2, imageUri = "content://photos/2",
            reviews = listOf(review), attendance = listOf(visit, lesson)), edited)
        assertEquals(listOf(edited, second), GymListStore(file).load())
        restarted.delete(second.id)
        assertEquals(listOf(edited), GymListStore(file).load())
        restarted.deleteAll()
        val empty = GymListStore(file)
        assertTrue(empty.load().isEmpty())
        assertTrue(empty.create(draft).id > second.id)
    }

    @Test fun opensLabArrayAndDefaultsOptionalFields() = runBlocking {
        file.writeText("""[{"id":20,"name":"Old gym","latitude":0,"longitude":0}]""")
        val store = GymListStore(file)
        assertEquals(listOf(Gym(20, "Old gym", 0.0, 0.0)), store.load())
        assertEquals(21L, store.create(draft).id)
        assertEquals(store.gyms.value, GymListStore(file).load())
    }

    @Test fun failedLoadPreservesTheOriginalFileAndCanBeRetried() {
        val store = GymListStore(file)
        for (bad in listOf("", "{", "null", """[{"id":1,"name":"Oops","latitude":0}]""")) {
            file.writeText(bad)
            assertThrows(IOException::class.java) { runBlocking { store.create(draft) } }
            assertEquals(bad, file.readText())
        }
        file.writeText("[]")
        assertEquals(1L, runBlocking { store.create(draft).id })
    }

    @Test fun failedReplacementKeepsTheLastCommittedListAndId() = runBlocking {
        val store = GymListStore(file)
        val gym = store.create(draft)
        val backup = File(directory.root, "original.json")
        assertTrue(file.renameTo(backup))
        assertTrue(file.mkdir())
        assertThrows(IOException::class.java) { runBlocking { store.create(draft) } }
        assertEquals(listOf(gym), store.gyms.value)
        assertTrue(file.delete())
        assertTrue(backup.renameTo(file))
        assertEquals(listOf(gym), GymListStore(file).load())
        assertEquals(gym.id + 1, store.create(draft).id)
    }

    @Test fun concurrentUpdatesKeepEveryReview() = runBlocking {
        val store = GymListStore(file)
        val gym = store.create(draft)
        coroutineScope {
            (1..12).map { n ->
                async(Dispatchers.Default) {
                    store.update(gym.id) { it.copy(reviews = it.reviews + GymReview(5, n.toString())) }
                }
            }.awaitAll()
        }
        val reviews = GymListStore(file).load().single().reviews
        assertEquals((1..12).map { it.toString() }.toSet(), reviews.map { it.comment }.toSet())
    }
}
