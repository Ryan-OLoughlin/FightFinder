package org.wit.fightfinder.models

import org.junit.Assert.assertThrows
import org.junit.Test

class GymValidationTest {
    private val gym = Gym(id = 1, name = "Dublin Gym", latitude = 53.3498, longitude = -6.2603)

    @Test fun acceptsCoordinateBoundaries() {
        gym.copy(latitude = -90.0, longitude = -180.0)
        gym.copy(latitude = 90.0, longitude = 180.0)
    }

    @Test fun rejectsWhitespaceOnlyName() {
        assertThrows(IllegalArgumentException::class.java) { gym.copy(name = "   ") }
    }

    @Test fun rejectsInvalidLatitudes() {
        for (value in listOf(-91.0, 91.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { gym.copy(latitude = value) }
        }
    }

    @Test fun rejectsInvalidLongitudes() {
        for (value in listOf(-181.0, 181.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { gym.copy(longitude = value) }
        }
    }
}
