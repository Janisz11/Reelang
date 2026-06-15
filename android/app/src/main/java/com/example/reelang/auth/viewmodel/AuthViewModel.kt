package com.example.reelang.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─── Auth state ───────────────────────────────────────────────────────────────

sealed class AuthState {
    data object Idle    : AuthState()
    data object Loading : AuthState()
    data object Success : AuthState()
    data class  Error(val message: String) : AuthState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()


    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()


    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Listener is registered once and updates _currentUser on every auth change
    // (sign-in, sign-out, token refresh, etc.).
    private val authStateListener = FirebaseAuth.AuthStateListener { fa ->
        _currentUser.value = fa.currentUser
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun signInWithEmail(email: String, password: String) {
        if (!validate(email, password)) return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            runCatching {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
            }.onSuccess {
                _authState.value = AuthState.Success
            }.onFailure { e ->
                _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun createAccount(email: String, password: String) {
        if (!validate(email, password)) return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            runCatching {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            }.onSuccess {
                _authState.value = AuthState.Success
            }.onFailure { e ->
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    /** Called after receiving the Google ID token from the sign-in intent result. */
    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
            }.onSuccess {
                _authState.value = AuthState.Success
            }.onFailure { e ->
                _authState.value = AuthState.Error(e.message ?: "Google sign-in failed")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Idle
    }

    fun reportError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun validate(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Email and password are required")
            return false
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters")
            return false
        }
        return true
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
    }
}
