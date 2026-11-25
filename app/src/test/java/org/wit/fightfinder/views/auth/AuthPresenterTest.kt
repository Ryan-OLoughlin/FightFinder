package org.wit.fightfinder.views.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPresenterTest {
    private val accounts = FakeAccounts()
    private val view = RecordingView()
    private val presenter = AuthPresenter(accounts, view)

    @Test fun missingConfigurationAndRestoredSessionRouteDifferently() {
        accounts.available = false
        presenter.onSessionChanged()
        assertTrue(view.unavailable)
        assertFalse(view.gymListOpened)

        accounts.available = true
        accounts.signedIn = true
        presenter.onSessionChanged()
        assertTrue(view.gymListOpened)
    }

    @Test fun registrationPassesCredentialsAndRoutesOnSuccess() = runBlocking {
        presenter.submit("  gym@example.com  ", " Demo password ", true)
        assertEquals(Triple("gym@example.com", " Demo password ", true), accounts.submission)
        assertEquals(listOf(true, false), view.busyChanges)
        assertTrue(view.gymListOpened)
    }

    @Test fun loginErrorsStayOnSignInAndBlankFieldsNeverSubmit() = runBlocking {
        presenter.submit("", "password", false)
        assertEquals(AuthPresenter.Error.EMAIL, view.error)
        presenter.submit("gym@example.com", "", false)
        assertEquals(AuthPresenter.Error.PASSWORD, view.error)
        assertNull(accounts.submission)

        accounts.failure = AuthPresenter.Error.CREDENTIALS
        presenter.submit("gym@example.com", "password", false)
        assertEquals(AuthPresenter.Error.CREDENTIALS, view.error)
        assertFalse(view.gymListOpened)
    }

    private class FakeAccounts : AuthPresenter.Accounts {
        override var available = true
        override var signedIn = false
        var failure: AuthPresenter.Error? = null
        var submission: Triple<String, String, Boolean>? = null

        override suspend fun submit(email: String, password: String, createAccount: Boolean): AuthPresenter.Error? {
            submission = Triple(email, password, createAccount)
            signedIn = failure == null
            return failure
        }
    }

    private class RecordingView : AuthPresenter.View {
        var unavailable = false
        var gymListOpened = false
        val busyChanges = mutableListOf<Boolean>()
        var error: AuthPresenter.Error? = null

        override fun showUnavailable() { unavailable = true }
        override fun openGymList() { gymListOpened = true }
        override fun showBusy(busy: Boolean) { busyChanges.add(busy) }
        override fun showError(error: AuthPresenter.Error) { this.error = error }
    }
}
