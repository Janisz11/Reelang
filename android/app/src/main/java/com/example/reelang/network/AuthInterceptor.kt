package com.example.reelang.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response


class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return chain.proceed(chain.request())

        val idToken: String? = runBlocking {
            runCatching { user.getIdToken(/* forceRefresh = */ false).await()?.token }
                .getOrNull()
        }

        val request = chain.request().newBuilder()
            .header("X-User-Id", user.uid)
            .apply { if (idToken != null) header("Authorization", "Bearer $idToken") }
            .build()

        return chain.proceed(request)
    }
}
