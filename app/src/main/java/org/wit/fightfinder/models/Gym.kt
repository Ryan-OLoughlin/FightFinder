package org.wit.fightfinder.models

data class Gym(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val imageUri: String? = null,
    val location: String = "",
    val price: String = "",
    val openingHours: String = "",
    val facilities: String = "",
    val classes: String = "",
    val reviews: List<GymReview> = emptyList(),
    val attendance: List<GymAttendance> = emptyList(),
    val addedFrom: Coordinates? = null,
    val cloudId: String? = null
) {
    init {
        require(name.isNotBlank())
        require(latitude in LATITUDE_RANGE)
        require(longitude in LONGITUDE_RANGE)
    }

    companion object {
        val LATITUDE_RANGE = -90.0..90.0
        val LONGITUDE_RANGE = -180.0..180.0
    }
}
