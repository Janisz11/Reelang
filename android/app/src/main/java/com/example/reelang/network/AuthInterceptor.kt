package com.example.reelang.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that stamps every request with the Firebase user's identity:
 *   X-User-Id: <uid>
 *   Authorization: Bearer <idToken>
 *
 * The ID token is fetched synchronously via [runBlocking] — safe here because
 * OkHttp dispatches interceptors on its own background thread pool.
 * Firebase caches the token locally and only hits the network when it has expired.
 *
 * If no user is signed in, the headers are omitted and the request proceeds normally.
 */
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
