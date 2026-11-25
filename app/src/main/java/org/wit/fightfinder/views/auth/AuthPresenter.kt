package org.wit.fightfinder.views.auth

internal class AuthPresenter(private val accounts: Accounts, private val view: View) {
    interface Accounts {
        val available: Boolean
        val signedIn: Boolean
        suspend fun submit(email: String, password: String, createAccount: Boolean): Error?
    }

    interface View {
        fun showUnavailable()
        fun openGymList()
        fun showBusy(busy: Boolean)
        fun showError(error: Error)
    }

    enum class Error { EMAIL, PASSWORD, WEAK_PASSWORD, DUPLICATE_EMAIL, CREDENTIALS, NETWORK, OTHER }

    fun onSessionChanged() {
        if (!accounts.available) view.showUnavailable()
        else if (accounts.signedIn) view.openGymList()
    }

    suspend fun submit(email: String, password: String, createAccount: Boolean) {
        val address = email.trim()
        if (address.isEmpty()) {
            view.showError(Error.EMAIL)
            return
        }
        if (password.isEmpty()) {
            view.showError(Error.PASSWORD)
            return
        }
        view.showBusy(true)
        try {
            val error = accounts.submit(address, password, createAccount)
            if (error == null) onSessionChanged() else view.showError(error)
        } finally {
            view.showBusy(false)
        }
    }
}
