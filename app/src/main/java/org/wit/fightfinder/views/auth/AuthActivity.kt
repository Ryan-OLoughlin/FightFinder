package org.wit.fightfinder.views.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.wit.fightfinder.FightFinderApplication
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.ActivityAuthBinding
import org.wit.fightfinder.views.list.MainActivity

class AuthActivity : AppCompatActivity() {
    private val app get() = application as FightFinderApplication
    private lateinit var binding: ActivityAuthBinding
    private val presenter by lazy { AuthPresenter(FirebaseAccounts(app.auth), view) }
    private val sessionListener = FirebaseAuth.AuthStateListener { presenter.onSessionChanged() }
    private var routing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        if (app.signedInUid != null) {
            presenter.onSessionChanged()
            return
        }
        setContentView(binding.root)
        binding.signIn.setOnClickListener { submit(createAccount = false) }
        binding.createAccount.setOnClickListener { submit(createAccount = true) }
        presenter.onSessionChanged()
    }

    override fun onStart() {
        super.onStart()
        app.auth?.addAuthStateListener(sessionListener)
    }

    override fun onStop() {
        app.auth?.removeAuthStateListener(sessionListener)
        super.onStop()
    }

    private fun submit(createAccount: Boolean) {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.authStatus.text = ""
        val email = binding.email.text.toString()
        val password = binding.password.text.toString()
        lifecycleScope.launch { presenter.submit(email, password, createAccount) }
    }

    private val view = object : AuthPresenter.View {
        override fun showUnavailable() {
            binding.authStatus.setText(R.string.auth_not_configured)
            showBusy(false)
        }

        override fun openGymList() {
            if (routing) return
            routing = true
            startActivity(Intent(this@AuthActivity, MainActivity::class.java).clearTask())
            finish()
        }

        override fun showBusy(busy: Boolean) {
            val canSubmit = !busy && app.auth != null
            binding.signIn.isEnabled = canSubmit
            binding.createAccount.isEnabled = canSubmit
            binding.email.isEnabled = canSubmit
            binding.password.isEnabled = canSubmit
            binding.progress.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
        }

        override fun showError(error: AuthPresenter.Error) {
            when (error) {
                AuthPresenter.Error.EMAIL -> binding.emailLayout.error = getString(R.string.auth_invalid_email)
                AuthPresenter.Error.PASSWORD -> binding.passwordLayout.error = getString(R.string.auth_password_required)
                AuthPresenter.Error.WEAK_PASSWORD -> binding.passwordLayout.error = getString(R.string.auth_weak_password)
                AuthPresenter.Error.DUPLICATE_EMAIL -> binding.emailLayout.error = getString(R.string.auth_duplicate_email)
                AuthPresenter.Error.CREDENTIALS -> binding.authStatus.setText(R.string.auth_invalid_credentials)
                AuthPresenter.Error.NETWORK -> binding.authStatus.setText(R.string.auth_network_error)
                AuthPresenter.Error.OTHER -> binding.authStatus.setText(R.string.auth_error)
            }
        }
    }
}

internal fun Activity.openAuth() {
    startActivity(Intent(this, AuthActivity::class.java).clearTask())
    finish()
}

private fun Intent.clearTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
