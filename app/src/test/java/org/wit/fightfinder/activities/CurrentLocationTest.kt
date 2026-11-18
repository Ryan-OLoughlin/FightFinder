package org.wit.fightfinder.activities

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.fightfinder.models.Coordinates

class CurrentLocationTest {
    @Test fun unavailableProviderDoesNotHideAnotherProvidersFix() = runBlocking {
        val point = Coordinates(53.3, -6.2)
        val unavailable = CompletableDeferred<Unit>()
        val sources = listOf<suspend () -> Coordinates?>(
            { unavailable.complete(Unit); null },
            { unavailable.await(); point }
        )
        assertEquals(point, firstAvailableFix(sources, 1000))
    }

    @Test fun successfulFixCancelsOutstandingRequests() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val sources = listOf<suspend () -> Coordinates?>(
            {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            },
            { started.await(); Coordinates(0.0, 0.0) }
        )
        assertEquals(Coordinates(0.0, 0.0), firstAvailableFix(sources, 1000))
        assertTrue(cancelled.isCompleted)
    }

    @Test fun noProviderOrTimeoutAllowsManualFallback() = runBlocking {
        assertNull(firstAvailableFix(emptyList(), 1000))
        assertNull(firstAvailableFix(listOf({ null }), 1000))
        assertNull(firstAvailableFix(listOf({ awaitCancellation() }), 50))
    }
}
