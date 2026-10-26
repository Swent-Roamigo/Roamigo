// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DefaultConnectivityRepositoryTest {

  private lateinit var connectivityManager: ConnectivityManager

  private lateinit var repository: DefaultConnectivityRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    repository = DefaultConnectivityRepository(context, connectivityManager)
  }

  private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
      ShadowNetworkCapabilities.newInstance().also { networkCapabilities ->
        capabilities.forEach { shadowOf(networkCapabilities).addCapability(it) }
      }

  private fun setActiveNetworkCapabilities(capabilities: NetworkCapabilities) {
    shadowOf(connectivityManager)
        .setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)
  }

  @Test
  fun isOnline_withValidatedActiveNetwork_startsOnline() = runTest {
    setActiveNetworkCapabilities(capabilities(INTERNET, VALIDATED))

    assertTrue(repository.isOnline.first())
  }

  @Test
  fun isOnline_withUnvalidatedActiveNetwork_startsOffline() = runTest {
    // Connected, but the internet is not reachable, e.g. behind a captive portal.
    setActiveNetworkCapabilities(capabilities(INTERNET))

    assertFalse(repository.isOnline.first())
  }

  @Test
  fun isOnline_followsTheDefaultNetwork() = runTest {
    setActiveNetworkCapabilities(capabilities(INTERNET, VALIDATED))
    val values = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.isOnline.toList(values)
    }
    val callback = shadowOf(connectivityManager).networkCallbacks.single()
    val network = ShadowNetwork.newInstance(1)

    callback.onLost(network)
    callback.onCapabilitiesChanged(network, capabilities(INTERNET))
    callback.onCapabilitiesChanged(network, capabilities(INTERNET, VALIDATED))
    callback.onCapabilitiesChanged(network, capabilities(INTERNET, VALIDATED))

    assertEquals(listOf(true, false, true), values)
  }

  @Test
  fun isOnline_whenCollectionStops_unregistersTheCallback() = runTest {
    val collection =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.isOnline.collect {}
        }
    assertEquals(1, shadowOf(connectivityManager).networkCallbacks.size)

    collection.cancel()

    assertTrue(shadowOf(connectivityManager).networkCallbacks.isEmpty())
  }

  private companion object {
    const val INTERNET = NetworkCapabilities.NET_CAPABILITY_INTERNET
    const val VALIDATED = NetworkCapabilities.NET_CAPABILITY_VALIDATED
  }
}
