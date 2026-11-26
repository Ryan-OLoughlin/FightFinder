package org.wit.fightfinder.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class GymCloudSyncTest {
    @get:Rule val directory = TemporaryFolder()
    private val draft = Gym(
        id = 0,
        name = "Boxing Gym",
        latitude = 53.0,
        longitude = -6.0,
        imageUri = "content://phone-one/photos/1",
        addedFrom = Coordinates(53.1, -6.1)
    )

    @Test fun devicesSyncRecordsAndEachKeepsItsOwnPhoto() = runBlocking {
        val cloud = FakeCloud()
        val first = GymListStore(directory.root.resolve("first.json"), cloud)
        val second = GymListStore(directory.root.resolve("second.json"), cloud)
        try {
            first.load()
            second.load()
            val saved = first.create(draft)
            val synced = withTimeout(5_000) { second.gyms.first { it.isNotEmpty() }.single() }
            assertEquals(saved.cloudId, synced.cloudId)
            assertEquals(saved.addedFrom, synced.addedFrom)
            assertNull(synced.imageUri)
            assertNull(cloud.records.value.values.single().imageUri)

            second.update(synced.id) { gym ->
                gym.copy(reviews = gym.reviews + GymReview(5, "Good class"))
            }
            val reviewed = withTimeout(5_000) { first.gyms.first { it.singleOrNull()?.reviews?.isNotEmpty() == true }.single() }
            assertEquals(draft.imageUri, reviewed.imageUri)
            assertEquals(saved.id, reviewed.id)

            second.deleteAll()
            withTimeout(5_000) { first.gyms.first { it.isEmpty() } }
            assertTrue(cloud.records.value.isEmpty())
        } finally {
            first.stopSync()
            second.stopSync()
        }
    }

    @Test fun restartRetriesLocalOnlyChangesWithoutClobberingAnExistingCloudGym() = runBlocking {
        val file = directory.root.resolve("offline.json")
        val original = GymListStore(file).create(draft)
        val remoteId = "already-synced"
        val cloud = FakeCloud(mapOf(remoteId to draft.copy(name = "Other device", imageUri = null)))
        cloud.offline = true
        val offline = GymListStore(file, cloud)
        val linked = try {
            offline.load()
            val gym = offline.update(original.id) { it.copy(name = "Edited offline") }
            withTimeout(5_000) { offline.syncStatus.first { it == GymListStore.SyncStatus.UNAVAILABLE } }
            gym
        } finally {
            offline.stopSync()
        }
        assertNotEquals(remoteId, linked.cloudId)
        cloud.offline = false

        val reopened = GymListStore(file, cloud)
        try {
            reopened.load()
            withTimeout(5_000) { reopened.syncStatus.first { it == GymListStore.SyncStatus.SYNCED } }
            assertEquals("Edited offline", cloud.records.value[linked.cloudId]?.name)
            assertEquals("Other device", cloud.records.value[remoteId]?.name)
            assertEquals(draft.imageUri, reopened.gyms.value.single { it.id == original.id }.imageUri)
        } finally {
            reopened.stopSync()
        }
    }

    @Test fun editDuringUploadRemainsQueuedUntilTheNewVersionIsSynced() = runBlocking {
        val uploadStarted = CompletableDeferred<Unit>()
        val resumeUpload = CompletableDeferred<Unit>()
        val cloud = FakeCloud {
            uploadStarted.complete(Unit)
            resumeUpload.await()
        }
        val store = GymListStore(directory.root.resolve("edit-while-syncing.json"), cloud)
        try {
            val gym = store.create(draft)
            withTimeout(5_000) { uploadStarted.await() }
            store.update(gym.id) { it.copy(name = "Newer edit") }
            resumeUpload.complete(Unit)
            withTimeout(5_000) { store.syncStatus.first { it == GymListStore.SyncStatus.SYNCED } }
            assertEquals("Newer edit", cloud.records.value.values.single().name)
            assertEquals("Newer edit", store.gyms.value.single().name)
        } finally {
            store.stopSync()
        }
    }

    @Test fun deniedUploadKeepsTheGymInLocalJsonAndReportsTheReason() = runBlocking {
        val cloud = FakeCloud {
            throw GymCloudFailure(GymListStore.SyncStatus.ACCESS_DENIED, "Write rejected by rules")
        }
        val file = directory.root.resolve("denied.json")
        val store = GymListStore(file, cloud)
        try {
            val local = store.create(draft)
            withTimeout(5_000) {
                store.syncStatus.first { it == GymListStore.SyncStatus.ACCESS_DENIED }
            }
            assertTrue(cloud.records.value.isEmpty())
            assertEquals(local, GymListStore(file).load().single())
        } finally {
            store.stopSync()
        }
    }

    private class FakeCloud(
        initial: Map<String, Gym> = emptyMap(),
        private val beforeWrite: suspend () -> Unit = {}
    ) : GymCloud {
        val records = MutableStateFlow(initial)
        override val changes = records.map { Unit }
        @Volatile var offline = false

        override suspend fun write(changes: List<CloudGymChange>) {
            if (offline) throw IOException("Cloud unavailable")
            beforeWrite()
            records.update { current ->
                current.toMutableMap().apply {
                    for ((key, gym) in changes) {
                        if (gym == null) remove(key) else put(key, gym)
                    }
                }
            }
        }

        override suspend fun list(): Map<String, Gym> {
            if (offline) throw IOException("Cloud unavailable")
            return records.value
        }
    }
}
