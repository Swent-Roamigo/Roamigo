package com.swent.roamigo.model.place

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapboxPlaceRepositoryTest {
  private val requests = mutableListOf<Request>()

  private fun repository(
      token: String = "pk.test",
      sessions: Iterator<String> = listOf("s1", "s2", "s3").iterator(),
      respond: (Request) -> Pair<Int, String>,
  ): MapboxPlaceRepository {
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              requests += chain.request()
              val (code, body) = respond(chain.request())
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(code)
                  .message("test")
                  .body(body.toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()
    return MapboxPlaceRepository(token, client) { sessions.next() }
  }

  private fun feature(coordinates: String = "[6.63, 46.52]", properties: String = PROPERTIES) =
      """{"features":[{"geometry":{"coordinates":$coordinates},"properties":$properties}]}"""

  private val candidate = PlaceCandidate("id-1", "Cafe Romand", "Lausanne")

  @Test
  fun suggestMapsResultsAndSendsQueryProximityAndSession() = runTest {
    val repository = repository {
      200 to
          """{"suggestions":[
            {"name":"Cafe Romand","mapbox_id":"id-1","full_address":"Place St-Francois, Lausanne"},
            {"name":"Cafe Romand","mapbox_id":"id-1"},
            {"name":"Pizzeria","mapbox_id":"id-2","place_formatted":"Geneva, Switzerland"},
            {"name":"","mapbox_id":"id-3"},
            {"name":"No id"}
          ]}"""
    }

    val result = repository.searchPlaces("  cafe ", PlaceProximity(46.5, 6.6))

    assertEquals(
        listOf(
            PlaceCandidate("id-1", "Cafe Romand", "Place St-Francois, Lausanne"),
            PlaceCandidate("id-2", "Pizzeria", "Geneva, Switzerland"),
        ),
        result,
    )
    val url = requests.single().url
    assertEquals("/search/searchbox/v1/suggest", url.encodedPath)
    assertEquals("cafe", url.queryParameter("q"))
    assertEquals("6.6,46.5", url.queryParameter("proximity"))
    assertEquals("pk.test", url.queryParameter("access_token"))
    assertEquals("s1", url.queryParameter("session_token"))
  }

  @Test
  fun suggestReturnsEmptyListWithoutCallingForShortQuery() = runTest {
    val repository = repository { error("must not be called") }

    assertTrue(repository.searchPlaces("   ").isEmpty())
    assertTrue(repository.searchPlaces("a").isEmpty())
    assertTrue(requests.isEmpty())
  }

  @Test
  fun suggestReturnsEmptyListWhenProviderFindsNothing() = runTest {
    assertTrue(repository { 200 to """{"suggestions":[]}""" }.searchPlaces("zzzz").isEmpty())
    assertTrue(repository { 200 to "{}" }.searchPlaces("zzzz").isEmpty())
  }

  @Test
  fun retrieveMapsGeoJsonCoordinatesAndCountry() = runTest {
    val repository = repository { 200 to feature() }

    val place = repository.resolvePlace(candidate)

    assertEquals(Place("Cafe Romand", "Lausanne, Switzerland", 46.52, 6.63, "CH"), place)
    assertEquals("CH", place.toTripLocation().countryCode)
    val url = requests.single().url
    assertEquals("/search/searchbox/v1/retrieve/id-1", url.encodedPath)
    assertEquals("s1", url.queryParameter("session_token"))
  }

  @Test
  fun retrieveFallsBackToSuggestionNameAndDropsMalformedCountry() = runTest {
    val repository = repository {
      200 to feature(properties = """{"context":{"country":{"country_code":"CHE"}}}""")
    }

    val place = repository.resolvePlace(candidate)

    assertEquals("Cafe Romand", place.name)
    assertNull(place.countryCode)
    assertNull(place.address)
  }

  @Test
  fun retrieveRejectsInvalidCoordinates() = runTest {
    val invalid = listOf("[6.63, 91]", "[181, 46.5]", "[6.63]", "[]", "[\"a\", \"b\"]", "null")
    for (coordinates in invalid) {
      val repository = repository { 200 to feature(coordinates) }
      try {
        repository.resolvePlace(candidate)
        fail("Expected invalid place for $coordinates")
      } catch (_: PlaceSearchException.InvalidPlace) {}
    }
    try {
      repository { 200 to """{"features":[]}""" }.resolvePlace(candidate)
      fail("Expected invalid place")
    } catch (_: PlaceSearchException.InvalidPlace) {}
  }

  @Test
  fun searchSessionIsSharedUntilRetrieveThenRenewed() = runTest {
    val repository = repository {
      if (it.url.encodedPath.endsWith("suggest")) 200 to """{"suggestions":[]}"""
      else 200 to feature()
    }

    repository.searchPlaces("caf")
    repository.searchPlaces("cafe")
    repository.resolvePlace(candidate)
    repository.searchPlaces("pizza")

    assertEquals(
        listOf("s1", "s1", "s1", "s2"),
        requests.map { it.url.queryParameter("session_token") },
    )
  }

  @Test
  fun failedRetrieveKeepsTheSession() = runTest {
    val repository = repository { 500 to "" }

    try {
      repository.resolvePlace(candidate)
      fail("Expected failure")
    } catch (_: PlaceSearchException.Unavailable) {}
    try {
      repository.searchPlaces("cafe")
    } catch (_: PlaceSearchException.Unavailable) {}

    assertEquals(
        listOf("s1", "s1"),
        requests.map { it.url.queryParameter("session_token") },
    )
  }

  @Test
  fun providerErrorsAreTyped() = runTest {
    suspend fun failure(code: Int) =
        try {
          repository { code to "{}" }.searchPlaces("cafe")
          null
        } catch (error: PlaceSearchException) {
          error
        }

    assertTrue(failure(401) is PlaceSearchException.InvalidToken)
    assertTrue(failure(403) is PlaceSearchException.InvalidToken)
    assertTrue(failure(429) is PlaceSearchException.QuotaExceeded)
    assertTrue(failure(500) is PlaceSearchException.Unavailable)
  }

  @Test
  fun networkAndParsingFailuresAreUnavailable() = runTest {
    val offline =
        MapboxPlaceRepository(
            "pk.test",
            OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build(),
        )
    try {
      offline.searchPlaces("cafe")
      fail("Expected failure")
    } catch (error: PlaceSearchException.Unavailable) {
      assertEquals("Place search is unreachable", error.message)
    }
    try {
      repository { 200 to "not json" }.searchPlaces("cafe")
      fail("Expected failure")
    } catch (_: PlaceSearchException.Unavailable) {}
  }

  @Test
  fun missingTokenIsAConfigurationError() = runTest {
    val repository = repository(token = " ") { error("must not be called") }

    try {
      repository.searchPlaces("cafe")
      fail("Expected configuration error")
    } catch (_: PlaceSearchException.Configuration) {}
    try {
      repository.resolvePlace(candidate)
      fail("Expected configuration error")
    } catch (_: PlaceSearchException.Configuration) {}
    assertTrue(requests.isEmpty())
  }

  @Test
  fun cancellingASuggestCancelsTheHttpCall() = runTest {
    val started = CountDownLatch(1)
    val cancelled = CountDownLatch(1)
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              started.countDown()
              // Waits for the cancellation, but gives up so a regression fails instead of hanging.
              val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
              while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(10)
              if (chain.call().isCanceled()) cancelled.countDown()
              throw IOException("Canceled")
            }
            .build()
    val repository = MapboxPlaceRepository("pk.test", client)

    val job = async(start = CoroutineStart.UNDISPATCHED) { repository.searchPlaces("cafe") }
    assertTrue(started.await(5, TimeUnit.SECONDS))
    job.cancel()

    assertTrue(cancelled.await(5, TimeUnit.SECONDS))
  }

  @Test
  fun shortQueryReturnsEmptyListEvenWithoutToken() = runTest {
    val repository = repository(token = "") { error("must not be called") }

    assertTrue(repository.searchPlaces("a").isEmpty())
    assertTrue(repository.searchPlaces("ca").isEmpty())
    assertTrue(repository.searchPlaces("  ").isEmpty())
    assertTrue(requests.isEmpty())
  }

  @Test
  fun resolvePlaceRejectsBlankIdWithoutCallingTheProvider() = runTest {
    val repository = repository { error("must not be called") }

    try {
      repository.resolvePlace(PlaceCandidate(" ", "Cafe Romand", null))
      fail("Expected invalid place")
    } catch (_: PlaceSearchException.InvalidPlace) {}
    assertTrue(requests.isEmpty())
  }

  @Test
  fun resolvePlaceRejectsCoordinatesThatAreNotNumbers() = runTest {
    val repository = repository { 200 to feature("[\"6.63\", \"46.52\"]") }

    try {
      repository.resolvePlace(candidate)
      fail("Expected invalid place")
    } catch (_: PlaceSearchException.InvalidPlace) {}
  }

  @Test
  fun candidateAndPlaceShareTheSameAddressFallback() = runTest {
    val repository = repository {
      if (it.url.encodedPath.endsWith("suggest"))
          200 to
              """{"suggestions":[{"name":"Park","mapbox_id":"p","place_formatted":"Lausanne"}]}"""
      else 200 to feature(properties = """{"name":"Park","place_formatted":"Lausanne"}""")
    }

    val found = repository.searchPlaces("park").single()
    val place = repository.resolvePlace(found)

    assertEquals("Lausanne", found.address)
    assertEquals(found.address, place.address)
  }

  @Test
  fun unusablePlaceStillEndsTheSession() = runTest {
    val answers =
        listOf(
            feature("[6.63, 91]"), // Out of range: Mapbox still completed the retrieve.
            """{"features":[]}""",
        )
    for (answer in answers) {
      requests.clear()
      val repository = repository { 200 to answer }

      try {
        repository.resolvePlace(candidate)
        fail("Expected invalid place")
      } catch (_: PlaceSearchException.InvalidPlace) {}
      try {
        repository.searchPlaces("cafe")
      } catch (_: PlaceSearchException) {}

      assertEquals(listOf("s1", "s2"), requests.map { it.url.queryParameter("session_token") })
    }
  }

  @Test
  fun refusedRequestStartsANewSession() = runTest {
    for (code in listOf(400, 422)) {
      requests.clear()
      val repository = repository { code to "{}" }

      for (call in 1..2) {
        try {
          repository.searchPlaces("cafe")
          fail("Expected failure")
        } catch (_: PlaceSearchException.Unavailable) {}
      }

      assertEquals(listOf("s1", "s2"), requests.map { it.url.queryParameter("session_token") })
    }
  }

  @Test
  fun refusedRetrieveStartsANewSession() = runTest {
    val repository = repository { 400 to "{}" }

    try {
      repository.resolvePlace(candidate)
      fail("Expected failure")
    } catch (_: PlaceSearchException.Unavailable) {}
    try {
      repository.searchPlaces("cafe")
    } catch (_: PlaceSearchException.Unavailable) {}

    assertEquals(listOf("s1", "s2"), requests.map { it.url.queryParameter("session_token") })
  }

  @Test
  fun countryCodeWithNonAsciiLettersIsDropped() = runTest {
    val repository = repository {
      200 to
          feature(
              properties = """{"name":"Cafe","context":{"country":{"country_code":"\u00e9a"}}}"""
          )
    }

    // A code that TripLocation would reject must not make the place fail.
    val place = repository.resolvePlace(candidate)

    assertNull(place.countryCode)
    place.toTripLocation()
  }

  private companion object {
    const val PROPERTIES =
        """{"name":"Cafe Romand","full_address":"Lausanne, Switzerland",
          "context":{"country":{"country_code":"ch"}}}"""
  }
}
