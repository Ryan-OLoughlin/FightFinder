package org.wit.fightfinder.presenters

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListDataSource
import org.wit.fightfinder.presenters.GymListPresenter.EmptyState
import java.io.IOException

class GymListPresenterTest {
    private val first = Gym(1, "Dublin Gym", 53.0, -6.0, classes = "Boxing")
    private val second = Gym(2, "Cork Gym", 52.0, -8.0, facilities = "Weights")
    private val store = FakeStore(listOf(first, second))
    private val view = RecordingView()
    private val presenter = GymListPresenter(store, view)

    @Test fun searchMatchesProfileFieldsAndDistinguishesNoResults() = runBlocking {
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { presenter.loadAndObserve() }
        try {
            assertEquals(listOf(first, second), view.visibleGyms)
            presenter.onSearchChanged("  bOxInG ")
            assertEquals(listOf(first), view.visibleGyms)
            presenter.onSearchChanged("weights")
            assertEquals(listOf(second), view.visibleGyms)
            presenter.onSearchChanged("no match")
            assertEquals(emptyList<Gym>(), view.visibleGyms)
            assertEquals(EmptyState.NO_RESULTS, view.emptyState)
            assertTrue(view.deleteAllEnabled)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test fun requestingDeletionWaitsForConfirmationAndDeletesById() = runBlocking {
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { presenter.loadAndObserve() }
        try {
            presenter.onDeleteRequested(second.id)
            assertEquals(second, view.deletePrompt)
            assertEquals(listOf(first, second), store.gyms.value)
            presenter.onDeleteConfirmed(second.id)
            yield()
            assertEquals(listOf(first), view.visibleGyms)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test fun deleteAllWhileFilteredClearsTheEntireStoreAndSearch() = runBlocking {
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { presenter.loadAndObserve() }
        try {
            presenter.onSearchChanged("boxing")
            presenter.onDeleteAllRequested()
            assertTrue(view.deleteAllPrompt)
            assertEquals(listOf(first, second), store.gyms.value)
            presenter.onDeleteAllConfirmed()
            yield()
            assertEquals(emptyList<Gym>(), store.gyms.value)
            assertEquals(EmptyState.EMPTY_LIST, view.emptyState)
            assertFalse(view.deleteAllEnabled)
            assertTrue(view.searchCleared)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test fun loadAndDeletionErrorsAreReportedAndFailedDeleteKeepsSearch() = runBlocking {
        store.failLoad = true
        presenter.loadAndObserve()
        assertTrue(view.loadError)
        store.failLoad = false
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { presenter.loadAndObserve() }
        try {
            presenter.onSearchChanged("boxing")
            store.failDeleteAll = true
            presenter.onDeleteAllConfirmed()
            assertTrue(view.deleteError)
            assertFalse(view.searchCleared)
            assertEquals(listOf(first), view.visibleGyms)
            assertEquals(listOf(first, second), store.gyms.value)
        } finally {
            observer.cancelAndJoin()
        }
    }

    private class FakeStore(initial: List<Gym>) : GymListDataSource {
        override val gyms = MutableStateFlow(initial)
        var failLoad = false
        var failDeleteAll = false

        override suspend fun load(): List<Gym> {
            if (failLoad) throw IOException("Test load failure")
            return gyms.value
        }

        override suspend fun delete(id: Long) {
            gyms.value = gyms.value.filterNot { it.id == id }
        }

        override suspend fun deleteAll() {
            if (failDeleteAll) throw IOException("Test save failure")
            gyms.value = emptyList()
        }
    }

    private class RecordingView : GymListPresenter.View {
        var visibleGyms = emptyList<Gym>()
        var emptyState: EmptyState? = null
        var deleteAllEnabled = false
        var deletePrompt: Gym? = null
        var deleteAllPrompt = false
        var searchCleared = false
        var loadError = false
        var deleteError = false

        override fun showLoading() = Unit

        override fun showLoadError() {
            loadError = true
        }

        override fun showGyms(gyms: List<Gym>, emptyState: EmptyState?, canDeleteAll: Boolean) {
            visibleGyms = gyms
            this.emptyState = emptyState
            deleteAllEnabled = canDeleteAll
        }

        override fun confirmDelete(gym: Gym) {
            deletePrompt = gym
        }

        override fun confirmDeleteAll() {
            deleteAllPrompt = true
        }

        override fun clearSearch() {
            searchCleared = true
        }

        override fun showDeleteError() {
            deleteError = true
        }
    }
}
