package com.example.sync

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String? = null
)

interface CloudAuthProvider {
    val currentUser: AuthUser?
    val isAvailable: Boolean
    val authStateFlow: StateFlow<AuthUser?>

    fun isUserSignedIn(): Boolean = currentUser != null

    suspend fun signInWithEmailAndPassword(email: String, pass: String): Result<AuthUser>
    suspend fun signUpWithEmailAndPassword(email: String, pass: String): Result<AuthUser>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun sendSignInLink(email: String): Result<Unit>
    suspend fun completeSignInWithEmailLink(email: String, emailLink: String): Result<AuthUser>
    fun isSignInWithEmailLink(link: String): Boolean
    suspend fun verifyPasswordResetCode(code: String): Result<Unit>
    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser>
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
}
