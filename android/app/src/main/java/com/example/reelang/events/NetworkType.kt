package com.example.reelang.events

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

const val NETWORK_WIFI = "wifi"
const val NETWORK_CELLULAR = "cellular"
const val NETWORK_UNKNOWN = "unknown"

fun currentNetworkType(context: Context): String {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NETWORK_UNKNOWN

    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
                ?: return@runCatching NETWORK_UNKNOWN
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NETWORK_CELLULAR
                else -> NETWORK_UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            when (manager.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> NETWORK_WIFI
                ConnectivityManager.TYPE_MOBILE -> NETWORK_CELLULAR
                else -> NETWORK_UNKNOWN
            }
        }
    }.getOrDefault(NETWORK_UNKNOWN)
}
