package org.wit.fightfinder.views.form

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.wit.fightfinder.models.AttendanceType
import org.wit.fightfinder.models.Coordinates
import org.wit.fightfinder.models.GymListStore
import org.wit.fightfinder.views.details.GymDetailsPresenter

class GymFormPresenterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val draft = GymFormPresenter.Draft(
        " Gym ", 53.0, -6.0, "content://photos/gym", "", "", "", "", ""
    )

    @Test fun deviceLocationDoesNotSupplyAMissingPin() = runBlocking {
        val store = GymListStore(temporaryFolder.newFolder().resolve("gyms.json"))
        val presenter = GymFormPresenter(store, null)
        presenter.addedFrom = Coordinates(52.0, -7.0)
        assertEquals(GymFormPresenter.InvalidField.LATITUDE, presenter.save(draft.copy(latitude = null)))
        assertEquals(GymFormPresenter.InvalidField.LONGITUDE, presenter.save(draft.copy(longitude = 181.0)))
        assertEquals(0, store.load().size)
        assertNull(presenter.save(draft))
        val saved = store.load().single()
        assertEquals(Coordinates(52.0, -7.0), saved.addedFrom)
        assertNotEquals(saved.addedFrom, Coordinates(saved.latitude, saved.longitude))
    }

    @Test fun editingPreservesTheLatestReviewsAttendanceAndOriginalDeviceLocation() = runBlocking {
        val file = temporaryFolder.newFolder().resolve("gyms.json")
        val store = GymListStore(file)
        val add = GymFormPresenter(store, null)
        add.addedFrom = Coordinates(52.0, -7.0)
        assertNull(add.save(draft))
        val saved = store.load().single()
        val edit = GymFormPresenter(store, saved.id)
        val details = GymDetailsPresenter(store, saved.id)
        assertNull(details.addReview(5, "   "))
        assertNull(details.recordAttendance(AttendanceType.CLASS, "24 Nov 2026", ""))
        details.addReview(5, " Great gym ")
        details.recordAttendance(AttendanceType.CLASS, "24 Nov 2026", " Boxing ")
        assertNull(edit.save(draft.copy(name = "Renamed", latitude = 54.0)))

        val reloaded = GymListStore(file).load().single()
        assertEquals(saved.id, reloaded.id)
        assertEquals("Renamed", reloaded.name)
        assertEquals(54.0, reloaded.latitude, 0.0)
        assertEquals(saved.addedFrom, reloaded.addedFrom)
        assertEquals("Great gym", reloaded.reviews.single().comment)
        assertEquals("Boxing", reloaded.attendance.single().className)
    }
}
