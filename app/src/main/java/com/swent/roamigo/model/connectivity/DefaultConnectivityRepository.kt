// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ConnectivityRepository] following the device's default network through [ConnectivityManager].
 */
class DefaultConnectivityRepository(
    context: Context,
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java),
) : ConnectivityRepository {

  override val isOnline: Flow<Boolean> = callbackFlow {
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              capabilities: NetworkCapabilities,
          ) {
            trySend(capabilities.isOnline())
          }

          override fun onLost(network: Network) {
            trySend(false)
          }
        }
    trySend(isCurrentlyOnline())
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
      .conflate()
      .distinctUntilChanged()

  private fun isCurrentlyOnline(): Boolean =
      connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.isOnline() ==
          true

  // A network can be connected without reaching the internet, e.g. behind a captive portal. Only a
  // validated one is reported as online.
  private fun NetworkCapabilities.isOnline(): Boolean =
      hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
