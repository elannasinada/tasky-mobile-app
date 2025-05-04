package io.tasky.taskyapp.sign_in.data.auth_client

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.BeginSignInRequest.GoogleIdTokenRequestOptions
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.tasky.taskyapp.R
import io.tasky.taskyapp.sign_in.domain.model.SignInResult
import io.tasky.taskyapp.sign_in.domain.model.UserData
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.util.concurrent.CancellationException

class GoogleAuthClientImpl(
    private val auth: FirebaseAuth,
    private val context: Context,
    private val oneTapClient: SignInClient
) : GoogleAuthClient {
    override suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): SignInResult {
        val result = try {
            auth.createUserWithEmailAndPassword(
                email,
                password
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        return SignInResult(
            data = result?.user?.run {
                UserData(
                    userId = uid,
                    userName = displayName,
                    profilePictureUrl = photoUrl?.toString(),
                    email = email
                )
            },
        )
    }

    override suspend fun loginWithEmailAndPassword(
        email: String,
        password: String
    ): SignInResult {
        val result = try {
            auth.signInWithEmailAndPassword(
                email,
                password
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        return SignInResult(
            data = result?.user?.run {
                UserData(
                    userId = uid,
                    userName = displayName,
                    profilePictureUrl = photoUrl?.toString(),
                    email = email
                )
            },
        )
    }

    override suspend fun signIn(): IntentSender? {
        val result = try {
            oneTapClient.beginSignIn(
                buildSignInRequest()
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        return result?.pendingIntent?.intentSender
    }

    override suspend fun getSignInResultFromIntent(intent: Intent): SignInResult {
        val credential = oneTapClient.getSignInCredentialFromIntent(intent)
        val googleIdToken = credential.googleIdToken
        val googleCredentials = GoogleAuthProvider.getCredential(googleIdToken, null)
        return try {
            val user = auth.signInWithCredential(googleCredentials).await().user
            SignInResult(
                data = user?.run {
                    UserData(
                        userId = uid,
                        userName = displayName,
                        profilePictureUrl = photoUrl?.toString(),
                        email = email
                    )
                },
            )
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
            SignInResult(
                data = null,
                errorMessage = e.message
            )
        }
    }

    override fun getSignedInUser(): UserData? = auth.currentUser?.run {
        UserData(
            userId = uid,
            userName = displayName,
            profilePictureUrl = photoUrl?.toString(),
            email = email
        )
    }

    override suspend fun logout() {
        try {
            oneTapClient.signOut().await()
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
        }
    }

    private fun buildSignInRequest(): BeginSignInRequest {
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()
        return signInRequest
    }
}