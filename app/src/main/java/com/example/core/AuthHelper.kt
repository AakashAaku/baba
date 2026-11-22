package com.example.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "BabaAuth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.unwrapActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.unwrapActivity()
    else -> null
}

@Suppress("DEPRECATION")
fun getAppSigningSha1(context: Context): String? = runCatching {
    val pm = context.packageManager
    val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val pkg = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val info = pkg.signingInfo
        val sig = info?.apkContentsSigners?.firstOrNull() ?: info?.signingCertificateHistory?.firstOrNull()
        sig?.toByteArray()
    } else {
        val pkg = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        pkg.signatures?.firstOrNull()?.toByteArray()
    } ?: return null
    val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
    digest.joinToString(":") { "%02x".format(it) }
}.getOrNull()

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    if (clientId.isBlank()) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val hostContext = context.unwrapActivity() ?: context
            val result = credentialManager.getCredential(hostContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
        return
    }
    if (clientId.isBlank()) {
        onAuthError("Google Sign-In configuration is blank. Check app/google-services.json.")
        return
    }

    val activityContext = context.unwrapActivity()
    if (activityContext == null) {
        onAuthError("Unable to launch Google Sign-In without an active screen.")
        return
    }

    fun formatCredentialDiagnostic(error: Throwable, baseReason: String): String {
        val rawMsg = (error as? GetCredentialException)?.errorMessage?.toString()
            ?: error.localizedMessage
            ?: error::class.java.simpleName
        val shaHint = if (BuildConfig.DEBUG) {
            val sha1 = getAppSigningSha1(activityContext)
            if (sha1 != null) {
                "\nDebug SHA-1: $sha1\nPackage: ${activityContext.packageName}\nVerify this SHA-1 is added in Firebase Console -> Project Settings -> Your Android App."
            } else ""
        } else ""
        return "$baseReason ($rawMsg)$shaHint"
    }

    suspend fun exchangeGoogleCredential(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(authCredential).await()
            onAuthSuccess()
        } else {
            onAuthError("Unexpected credential type returned by Google Sign-In.")
        }
    }

    scope.launch {
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val startedAt = System.currentTimeMillis()
        try {
            val result = credentialManager.getCredential(activityContext, primaryRequest)
            exchangeGoogleCredential(result.credential)
        } catch (primaryErr: GetCredentialCancellationException) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            val errDetail = primaryErr.errorMessage?.toString().orEmpty()
            Log.w(AUTH_TAG, "Primary Google Sign-In cancelled in ${elapsedMs}ms: $errDetail", primaryErr)

            // If Play Services immediately rejected/aborted the bottom sheet (< 500ms) or reported a block/error,
            // fall back once to GetGoogleIdOption and surface diagnostics rather than silently doing nothing.
            if (elapsedMs >= 700L && !errDetail.contains("block", ignoreCase = true)) {
                onAuthCancelled()
                return@launch
            }

            try {
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(activityContext, fallbackRequest)
                exchangeGoogleCredential(fallbackResult.credential)
            } catch (_: GetCredentialCancellationException) {
                onAuthError(
                    formatCredentialDiagnostic(
                        primaryErr,
                        "Google Sign-In did not open or was blocked by Google Play Services"
                    )
                )
            } catch (fallbackErr: Exception) {
                Log.e(AUTH_TAG, "Fallback Google Sign-In failed", fallbackErr)
                onAuthError(
                    formatCredentialDiagnostic(
                        fallbackErr,
                        "Google Sign-In failed. Check Google account on device & Firebase SHA-1"
                    )
                )
            }
        } catch (noCredErr: NoCredentialException) {
            Log.w(AUTH_TAG, "NoCredentialException on primary Sign-In, trying GetGoogleIdOption fallback", noCredErr)
            try {
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(activityContext, fallbackRequest)
                exchangeGoogleCredential(fallbackResult.credential)
            } catch (fallbackErr: Exception) {
                Log.e(AUTH_TAG, "No Google credential available", fallbackErr)
                onAuthError(
                    formatCredentialDiagnostic(
                        fallbackErr,
                        "No Google account or Credential Provider available on this device"
                    )
                )
            }
        } catch (configErr: GetCredentialProviderConfigurationException) {
            Log.e(AUTH_TAG, "Credential provider configuration error", configErr)
            onAuthError(
                formatCredentialDiagnostic(
                    configErr,
                    "Google Play Services Credential Manager is missing or outdated on this device"
                )
            )
        } catch (unsupportedErr: GetCredentialUnsupportedException) {
            Log.e(AUTH_TAG, "Credential Manager unsupported", unsupportedErr)
            onAuthError(
                formatCredentialDiagnostic(
                    unsupportedErr,
                    "Credential Manager is disabled or unsupported in device settings"
                )
            )
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(
                formatCredentialDiagnostic(
                    e,
                    "Google Sign-In failed"
                )
            )
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
