package com.applab.docsnap.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google sign-in for Drive using Play services' AuthorizationClient.
 * No client id or secret lives in the app: Google matches the app by package name + signing key,
 * registered once in the Google Cloud project.
 */
object DriveAuth {

    sealed interface Outcome {
        data class Token(val value: String) : Outcome
        /** First time (or consent removed): the user must pick an account / allow Drive. */
        data class NeedsUser(val intent: PendingIntent) : Outcome
        data class Failed(val message: String) : Outcome
    }

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DriveApi.SCOPE)))
        .build()

    suspend fun authorize(context: Context): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            val r: AuthorizationResult = Tasks.await(Identity.getAuthorizationClient(context).authorize(request()))
            when {
                r.hasResolution() && r.pendingIntent != null -> Outcome.NeedsUser(r.pendingIntent!!)
                !r.accessToken.isNullOrBlank() -> Outcome.Token(r.accessToken!!)
                else -> Outcome.Failed("No access token")
            }
        }.getOrElse { Outcome.Failed(it.cause?.message ?: it.message ?: "Sign-in failed") }
    }

    /** Token after the user finished the Google screen. */
    fun tokenFromIntent(context: Context, data: Intent?): String? = runCatching {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
    }.getOrNull()

    /** For background work: a token only if no screen is needed. */
    suspend fun silentToken(context: Context): String? = (authorize(context) as? Outcome.Token)?.value
}
