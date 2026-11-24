package org.wit.fightfinder.views.form

import org.wit.fightfinder.models.Coordinates
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListStore

internal class GymFormPresenter(private val store: GymListStore, private val gymId: Long?) {
    enum class InvalidField { NAME, LATITUDE, LONGITUDE }

    data class Draft(
        val name: String,
        val latitude: Double?,
        val longitude: Double?,
        val imageUri: String?,
        val location: String,
        val price: String,
        val openingHours: String,
        val facilities: String,
        val classes: String
    )

    val editing get() = gymId != null
    var addedFrom: Coordinates? = null

    fun existingGym(): Gym? = store.gyms.value.find { it.id == gymId }

    fun pin(latitude: Double?, longitude: Double?): Coordinates? {
        if (latitude == null || latitude !in Gym.LATITUDE_RANGE) return null
        if (longitude == null || longitude !in Gym.LONGITUDE_RANGE) return null
        return Coordinates(latitude, longitude)
    }

    suspend fun save(draft: Draft): InvalidField? {
        val name = draft.name.trim()
        if (name.isBlank()) return InvalidField.NAME
        val latitude = draft.latitude?.takeIf { it in Gym.LATITUDE_RANGE } ?: return InvalidField.LATITUDE
        val longitude = draft.longitude?.takeIf { it in Gym.LONGITUDE_RANGE } ?: return InvalidField.LONGITUDE

        val gym = Gym(
            id = gymId ?: 0,
            name = name,
            latitude = latitude,
            longitude = longitude,
            imageUri = draft.imageUri,
            addedFrom = addedFrom,
            location = draft.location.trim(),
            price = draft.price.trim(),
            openingHours = draft.openingHours.trim(),
            facilities = draft.facilities.trim(),
            classes = draft.classes.trim()
        )
        if (gymId == null) {
            store.create(gym)
        } else {
            store.update(gymId) { current ->
                gym.copy(addedFrom = current.addedFrom, reviews = current.reviews, attendance = current.attendance)
            }
        }
        return null
    }
}
