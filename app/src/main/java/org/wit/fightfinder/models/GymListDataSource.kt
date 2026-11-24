package org.wit.fightfinder.models

import kotlinx.coroutines.flow.StateFlow

interface GymListDataSource {
    val gyms: StateFlow<List<Gym>>
    suspend fun load(): List<Gym>
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
