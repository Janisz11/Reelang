package com.example.reelang.auth.model

import com.google.firebase.auth.FirebaseAuth

object UserSession {
    val userId: String
        get() = FirebaseAuth.getInstance()
            .currentUser?.uid ?: "default_user"

    val displayName: String
        get() = FirebaseAuth.getInstance()
            .currentUser?.displayName ?: "User"

    val email: String
        get() = FirebaseAuth.getInstance()
            .currentUser?.email ?: ""

    fun initials(): String {
        val name = displayName
        val parts = name.trim().split(" ")
        return if (parts.size >= 2) {
            "${parts[0].first()}${parts[1].first()}".uppercase()
        } else {
            name.take(2).uppercase()
        }
    }
}