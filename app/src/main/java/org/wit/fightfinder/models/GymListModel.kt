package org.wit.fightfinder.models

import androidx.lifecycle.ViewModel

class GymListModel : ViewModel() {
    val gyms = mutableListOf<Gym>()
    private var nextId = 1L

    fun allocateId(): Long = nextId++
}
