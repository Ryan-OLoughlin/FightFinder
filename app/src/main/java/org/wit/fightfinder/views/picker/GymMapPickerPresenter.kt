package org.wit.fightfinder.views.picker

import org.wit.fightfinder.models.Coordinates
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListDataSource

internal class GymMapPickerPresenter(private val store: GymListDataSource) {
    var selected: Coordinates? = null
        private set

    suspend fun savedGyms() = store.load()

    fun initialCenter(gyms: List<Gym>): Coordinates? =
        selected ?: gyms.firstOrNull()?.let { Coordinates(it.latitude, it.longitude) }

    fun select(point: Coordinates) {
        selected = point
    }
}
