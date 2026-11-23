package com.jupiter.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

interface NetworkConnectivityMonitor {
    val isConnected: Flow<Boolean>
    fun isCurrentlyConnected(): Boolean
}

object AlwaysConnectedNetworkMonitor : NetworkConnectivityMonitor {
    override val isConnected: Flow<Boolean> = flowOf(true)
    override fun isCurrentlyConnected(): Boolean = true
}

class AndroidNetworkConnectivityMonitor(
    context: Context,
) : NetworkConnectivityMonitor {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val isConnected: Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        val activeNetworks = LinkedHashSet<Network>()
        cm.activeNetwork?.let { active ->
            if (isUsableNetwork(cm.getNetworkCapabilities(active))) {
                activeNetworks.add(active)
            }
        }
        trySend(activeNetworks.isNotEmpty() || isCurrentlyConnected())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isUsableNetwork(cm.getNetworkCapabilities(network))) {
                    activeNetworks.add(network)
                    trySend(true)
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (isUsableNetwork(networkCapabilities)) {
                    activeNetworks.add(network)
                } else {
                    activeNetworks.remove(network)
                }
                trySend(activeNetworks.isNotEmpty())
            }

            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                trySend(activeNetworks.isNotEmpty() || isCurrentlyConnected())
            }

            override fun onUnavailable() {
                trySend(activeNetworks.isNotEmpty() || isCurrentlyConnected())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val registered = runCatching {
            cm.registerNetworkCallback(request, callback)
            true
        }.getOrDefault(false)

        awaitClose {
            if (registered) {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }.distinctUntilChanged()

    override fun isCurrentlyConnected(): Boolean {
        val cm = connectivityManager ?: return true
        return runCatching {
            val active = cm.activeNetwork ?: return@runCatching false
            isUsableNetwork(cm.getNetworkCapabilities(active))
        }.getOrDefault(true)
    }

    private fun isUsableNetwork(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
