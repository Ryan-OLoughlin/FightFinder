package org.wit.fightfinder.presenters

import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListDataSource
import java.io.IOException

class GymListPresenter(private val store: GymListDataSource, private val view: View) {
    enum class EmptyState { EMPTY_LIST, NO_RESULTS }

    interface View {
        fun showLoading()
        fun showLoadError()
        fun showGyms(gyms: List<Gym>, emptyState: EmptyState?, canDeleteAll: Boolean)
        fun confirmDelete(gym: Gym)
        fun confirmDeleteAll()
        fun clearSearch()
        fun showDeleteError()
    }

    private var query = ""
    private var loaded = false

    suspend fun loadAndObserve() {
        view.showLoading()
        try {
            store.load()
        } catch (_: IOException) {
            view.showLoadError()
            return
        }
        loaded = true
        store.gyms.collect { render(it) }
    }

    fun onSearchChanged(search: String) {
        query = search.trim()
        if (loaded) render(store.gyms.value)
    }

    fun onDeleteRequested(gymId: Long) {
        if (loaded) store.gyms.value.find { it.id == gymId }?.let(view::confirmDelete)
    }

    fun onDeleteAllRequested() {
        if (loaded && store.gyms.value.isNotEmpty()) view.confirmDeleteAll()
    }

    suspend fun onDeleteConfirmed(gymId: Long) {
        try {
            store.delete(gymId)
        } catch (_: IOException) {
            view.showDeleteError()
        }
    }

    suspend fun onDeleteAllConfirmed() {
        try {
            store.deleteAll()
            query = ""
            view.clearSearch()
        } catch (_: IOException) {
            view.showDeleteError()
        }
    }

    private fun render(gyms: List<Gym>) {
        val matches = if (query.isEmpty()) gyms else gyms.filter { gym ->
            gym.name.contains(query, ignoreCase = true) ||
                gym.location.contains(query, ignoreCase = true) ||
                gym.classes.contains(query, ignoreCase = true) ||
                gym.facilities.contains(query, ignoreCase = true)
        }
        val emptyState = when {
            gyms.isEmpty() -> EmptyState.EMPTY_LIST
            matches.isEmpty() -> EmptyState.NO_RESULTS
            else -> null
        }
        view.showGyms(matches, emptyState, gyms.isNotEmpty())
    }
}
