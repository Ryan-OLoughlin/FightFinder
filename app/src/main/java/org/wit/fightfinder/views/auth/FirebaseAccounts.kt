package org.wit.fightfinder.views.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class FirebaseAccounts(private val auth: FirebaseAuth?) : AuthPresenter.Accounts {
    override val available get() = auth != null
    override val signedIn get() = auth?.currentUser != null

    override suspend fun submit(email: String, password: String, createAccount: Boolean): AuthPresenter.Error? =
        suspendCancellableCoroutine { result ->
            val firebase = checkNotNull(auth)
            val request = if (createAccount) {
                firebase.createUserWithEmailAndPassword(email, password)
            } else {
                firebase.signInWithEmailAndPassword(email, password)
            }

            request.addOnCompleteListener { task ->
                if (!result.isActive) return@addOnCompleteListener
                val error = if (task.isSuccessful) null else when (val failure = task.exception) {
                    is FirebaseAuthWeakPasswordException -> AuthPresenter.Error.WEAK_PASSWORD
                    is FirebaseAuthUserCollisionException -> AuthPresenter.Error.DUPLICATE_EMAIL
                    is FirebaseAuthInvalidCredentialsException -> {
                        if (failure.errorCode == "ERROR_INVALID_EMAIL") AuthPresenter.Error.EMAIL
                        else AuthPresenter.Error.CREDENTIALS
                    }
                    is FirebaseAuthInvalidUserException -> AuthPresenter.Error.CREDENTIALS
                    is FirebaseNetworkException -> AuthPresenter.Error.NETWORK
                    else -> AuthPresenter.Error.OTHER
                }
                result.resume(error)
            }
        }
}
