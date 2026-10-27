// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo

import coil.annotation.ExperimentalCoilApi
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoamigoApplicationTest {
  @Test
  fun destinationImageRequestsIdentifyTheApp() {
    val application = RuntimeEnvironment.getApplication() as RoamigoApplication
    val request = Request.Builder().url("https://images.example/city.jpg").build()
    val chain =
        mock(
            Interceptor.Chain::class.java,
            Answer { invocation ->
              when (invocation.method.name) {
                "request" -> request
                "proceed" ->
                    Response.Builder()
                        .request(invocation.getArgument(0))
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .build()
                else -> null
              }
            },
        )

    val response = application.destinationImageHttpClient().interceptors.single().intercept(chain)

    assertEquals(DESTINATION_USER_AGENT, response.request.header("User-Agent"))
  }

  @OptIn(ExperimentalCoilApi::class)
  @Test
  fun imageLoaderUsesBoundedDestinationCacheInsideAppCacheDirectory() {
    val application = RuntimeEnvironment.getApplication() as RoamigoApplication
    val imageLoader = application.newImageLoader()

    try {
      val diskCache = imageLoader.diskCache
      assertNotNull(diskCache)
      assertEquals(50L * 1024 * 1024, diskCache!!.maxSize)
      assertEquals(
          application.cacheDir.resolve("destination-images").absolutePath,
          diskCache.directory.toString(),
      )
    } finally {
      imageLoader.shutdown()
    }
  }
}
