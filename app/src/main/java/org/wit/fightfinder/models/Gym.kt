package org.wit.fightfinder.models

data class Gym(
    val name: String,
    val location: String = "",
    val price: String = "",
    val openingHours: String = "",
    val facilities: String = "",
    val classes: String = ""
)