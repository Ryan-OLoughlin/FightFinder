package org.wit.fightfinder.views.map

import org.wit.fightfinder.models.Coordinates
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListDataSource
import java.io.IOException

class GymMapListPresenter(private val store: GymListDataSource, private val view: View) {
    interface View {
        fun showPins(locations: Map<Coordinates, List<Gym>>)
        fun showSelection(gym: Gym?)
        fun chooseGymAtPin(gyms: List<Gym>)
        fun showLoadError()
    }

    var selectedId: Long? = null
        private set

    suspend fun loadAndObserve(restoredId: Long?) {
        selectedId = restoredId
        try {
            store.load()
        } catch (_: IOException) {
            view.showLoadError()
            return
        }

        store.gyms.collect { gyms ->
            view.showPins(gyms.groupBy { Coordinates(it.latitude, it.longitude) })
            val gym = gyms.find { it.id == selectedId }
            selectedId = gym?.id
            view.showSelection(gym)
        }
    }

    fun onPinSelected(gyms: List<Gym>) {
        if (gyms.size == 1) onGymSelected(gyms.first().id) else view.chooseGymAtPin(gyms)
    }

    fun onGymSelected(id: Long) {
        val gym = store.gyms.value.find { it.id == id } ?: return
        selectedId = gym.id
        view.showSelection(gym)
    }
}
