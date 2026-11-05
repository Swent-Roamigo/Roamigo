// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import com.swent.roamigo.model.destination.DestinationConfigurationException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeoNamesCountryCodeLookupTest {

  @Test
  fun queriesCountryCodeWithExpectedCoordinatesAndCredentials() = runTest {
    var request: Request? = null

    val lookup =
        GeoNamesCountryCodeLookup(
            "geonames-test-user",
            stubClient(
                """{"countryCode":"PT","countryName":"Portugal"}""",
                onRequest = { request = it },
            ),
        )

    assertEquals("PT", lookup.lookup(38.7223, -9.1393))

    val url = requireNotNull(request).url

    assertEquals("https", url.scheme)
    assertEquals("secure.geonames.org", url.host)
    assertEquals("/countryCode", url.encodedPath)
    assertEquals("38.7223", url.queryParameter("lat"))
    assertEquals("-9.1393", url.queryParameter("lng"))
    assertEquals("JSON", url.queryParameter("type"))
    assertEquals("geonames-test-user", url.queryParameter("username"))
    assertEquals(DESTINATION_USER_AGENT, requireNotNull(request).header("User-Agent"))
  }

  @Test
  fun missingCountryOrInvalidValueReturnNull() = runTest {
    listOf(
            "{}",
            """{"countryCode":""}""",
            """{"countryCode":"PRT"}""",
            """{"countryCode":"pt"}""",
            """{"countryCode":42}""",
            """{"status":{"value":15,"message":"no country found"}}""",
        )
        .forEach { json ->
          val lookup = GeoNamesCountryCodeLookup("test", stubClient(json))
          assertNull(lookup.lookup(0.0, -20.0))
        }
  }

  @Test
  fun providerLimitAndHttpErrorAreReported() = runTest {
    val providerError =
        GeoNamesCountryCodeLookup(
            "test",
            stubClient("""{"status":{"value":18,"message":"daily limit exceeded"}}"""),
        )

    assertIoFailure(providerError)

    val serverError = GeoNamesCountryCodeLookup("test", stubClient("{}", 503))

    assertIoFailure(serverError)
  }

  @Test
  fun invalidCoordinatesDoNotSendARequest() = runTest {
    var requests = 0

    val lookup =
        GeoNamesCountryCodeLookup(
            "test",
            stubClient("{}", onRequest = { requests++ }),
        )

    assertNull(lookup.lookup(Double.NaN, 6.0))
    assertNull(lookup.lookup(91.0, 6.0))
    assertNull(lookup.lookup(46.0, Double.NEGATIVE_INFINITY))
    assertEquals(0, requests)
  }

  @Test
  fun networkFailureIsReportedForRetryByCaller() = runTest {
    val client = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()

    val lookup = GeoNamesCountryCodeLookup("test", client)

    assertIoFailure(lookup)
  }

  @Test
  fun missingUsernameDoesNotSendARequest() = runTest {
    var requests = 0

    val lookup =
        GeoNamesCountryCodeLookup(
            "",
            stubClient("{}", onRequest = { requests++ }),
        )

    try {
      lookup.lookup(38.7, -9.1)
      fail("Expected missing GeoNames credentials to be rejected")
    } catch (_: DestinationConfigurationException) {
      assertEquals(0, requests)
    }
  }

  private suspend fun assertIoFailure(lookup: GeoNamesCountryCodeLookup) {
    try {
      lookup.lookup(38.7, -9.1)
      fail("Expected a GeoNames failure")
    } catch (_: IOException) {
      // Higher-level resolver will skip this destination and retry in a future refresh.
    }
  }

  private fun stubClient(
      json: String,
      statusCode: Int = 200,
      onRequest: (Request) -> Unit = {},
  ): OkHttpClient =
      OkHttpClient.Builder()
          .addInterceptor { chain ->
            onRequest(chain.request())

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("Test response")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
          }
          .build()
}
