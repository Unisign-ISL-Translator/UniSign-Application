package com.unisign.unisign.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentUserNameOrGuest(): String {
        val user = auth.currentUser ?: return "Guest"
        return user.email ?: if (user.isAnonymous) "Guest" else "User"
    }

    fun isGuest(): Boolean = auth.currentUser?.isAnonymous == true

    fun signInAnonymously(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInAnonymously()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Guest sign-in failed") }
    }

    fun signInWithEmailPassword(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanedEmail = email.trim()
        if (cleanedEmail.isEmpty() || password.isEmpty()) {
            onError("Email and password must not be empty")
            return
        }

        auth.signInWithEmailAndPassword(cleanedEmail, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Sign-in failed") }
    }

    fun registerWithEmailPassword(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanedEmail = email.trim()
        if (cleanedEmail.isEmpty() || password.isEmpty()) {
            onError("Email and password must not be empty")
            return
        }
        if (password.length < 6) {
            onError("Password must be at least 6 characters")
            return
        }

        auth.createUserWithEmailAndPassword(cleanedEmail, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Registration failed") }
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (idToken.isBlank()) {
            onError("Google sign-in failed: missing token")
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Google sign-in failed") }
    }

    fun signOut() {
        auth.signOut()
    }
}

