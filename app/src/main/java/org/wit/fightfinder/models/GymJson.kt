package org.wit.fightfinder.models

import com.google.gson.JsonObject

internal fun decodeGym(json: JsonObject): Gym {
    fun optionalText(key: String) = json[key]?.takeUnless { it.isJsonNull }?.asString
    return Gym(
        id = json["id"].asLong,
        name = json["name"].asString,
        latitude = json["latitude"].asDouble,
        longitude = json["longitude"].asDouble,
        imageUri = optionalText("imageUri"),
        cloudId = optionalText("cloudId"),
        location = optionalText("location").orEmpty(),
        price = optionalText("price").orEmpty(),
        openingHours = optionalText("openingHours").orEmpty(),
        facilities = optionalText("facilities").orEmpty(),
        classes = optionalText("classes").orEmpty(),
        addedFrom = json["addedFrom"]?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
            Coordinates(it["latitude"].asDouble, it["longitude"].asDouble)
        },
        reviews = json.getAsJsonArray("reviews")?.map { element ->
            val review = element.asJsonObject
            GymReview(review["rating"].asInt, review["comment"].asString)
        }.orEmpty(),
        attendance = json.getAsJsonArray("attendance")?.map { element ->
            val entry = element.asJsonObject
            GymAttendance(AttendanceType.valueOf(entry["type"].asString), entry["date"].asString,
                entry["className"]?.asString.orEmpty())
        }.orEmpty()
    )
}
