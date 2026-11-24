package org.wit.fightfinder.views.details

import org.wit.fightfinder.models.AttendanceType
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymAttendance
import org.wit.fightfinder.models.GymListStore
import org.wit.fightfinder.models.GymReview

internal class GymDetailsPresenter(private val store: GymListStore, private val gymId: Long) {
    fun currentGym(): Gym? = store.gyms.value.find { it.id == gymId }

    suspend fun addReview(rating: Int, input: String): Gym? {
        val comment = input.trim()
        if (comment.isBlank()) return null
        return store.update(gymId) { gym ->
            gym.copy(reviews = gym.reviews + GymReview(rating, comment))
        }
    }

    suspend fun recordAttendance(type: AttendanceType, date: String, input: String): Gym? {
        val className = input.trim()
        if (type == AttendanceType.CLASS && className.isBlank()) return null
        val attendance = GymAttendance(type, date, className)
        return store.update(gymId) { gym ->
            gym.copy(attendance = gym.attendance + attendance)
        }
    }
}
