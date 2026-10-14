package org.wit.fightfinder.models

data class GymAttendance(
    val type: AttendanceType,
    val date: String,
    val className: String = ""
)

enum class AttendanceType {
    VISIT,
    CLASS
}