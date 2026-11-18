package org.wit.fightfinder.models

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in Gym.LATITUDE_RANGE && longitude in Gym.LONGITUDE_RANGE)
    }
}
