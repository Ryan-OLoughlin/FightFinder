package org.wit.fightfinder.models

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountGymStoresTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun accountDataIsIsolatedAcrossSignOutAndRestart() = runBlocking {
        val legacy = directory.root.resolve("gyms.json")
        legacy.writeText("[]")
        val stores = AccountGymStores(directory.root)
        val first = stores.forAccount("first")
        val gym = first.create(Gym(0, "First gym", 53.0, -6.0))
        assertSame(first, stores.forAccount("first"))

        stores.clear()
        val second = stores.forAccount("second")
        assertTrue(second.load().isEmpty())
        second.create(Gym(0, "Second gym", 52.0, -7.0))
        assertEquals(listOf(gym), stores.forAccount("first").load())
        assertNotSame(first, stores.forAccount("first"))

        val restarted = AccountGymStores(directory.root)
        restarted.forAccount("second").deleteAll()
        assertEquals(listOf(gym), restarted.forAccount("first").load())
        assertEquals("[]", legacy.readText())
    }
}
