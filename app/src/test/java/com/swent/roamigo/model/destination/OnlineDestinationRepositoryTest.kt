// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.model.destination

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineDestinationRepositoryTest {
  @Test
  fun commonsAcceptsShareAlikePhotoWithAttribution() = runTest {
    val hosts = mutableListOf<String>()
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              hosts += chain.request().url.host
              val json =
                  when (chain.request().url.host) {
                    "query.wikidata.org" ->
                        """{"results":{"bindings":[{"image":{"value":"https://commons.wikimedia.org/wiki/Special:FilePath/Paris.jpg"}}]}}"""
                    "commons.wikimedia.org" ->
                        """
                        {"query":{"pages":{"1":{"imageinfo":[{
                          "thumburl":"https://upload.wikimedia.org/paris-small.jpg",
                          "descriptionurl":"https://commons.wikimedia.org/wiki/File:Paris.jpg",
                          "extmetadata":{
                            "LicenseShortName":{"value":"CC BY-SA 2.0"},
                            "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/2.0/"},
                            "Artist":{"value":"<a href='https://example.org'>Photographer &amp; Co</a>"},
                            "ObjectName":{"value":"Paris skyline"},
                            "Credit":{"value":"© Photographer"}
                          }
                        }]}}}}
                        """
                            .trimIndent()
                    else -> error("Unexpected API request")
                  }
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body(json.toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()
    val repository = OnlineDestinationRepository(geonamesUsername = "test-user", client = client)

    val photo =
        repository.findPhoto(
            destinationFixture("geonames:2988507", "Paris", "France", "FR", 2988507)
        )

    assertEquals(listOf("query.wikidata.org", "commons.wikimedia.org"), hosts)
    assertEquals("CC BY-SA 2.0", photo?.license)
    assertEquals("Photographer & Co", photo?.creator)
    assertEquals("Paris skyline", photo?.title)
    assertEquals("© Photographer", photo?.credit)
  }

  @Test
  fun openverseAcceptsShareAlikePhoto() = runTest {
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              val json =
                  when (chain.request().url.host) {
                    "query.wikidata.org" -> """{"results":{"bindings":[]}}"""
                    "api.openverse.org" -> {
                      assertEquals(
                          "cc0,pdm,by,by-sa",
                          chain.request().url.queryParameter("license"),
                      )
                      """
                      {"results":[{"url":"https://images.example.org/paris.jpg",
                      "foreign_landing_url":"https://example.org/photo", "title":"Paris",
                      "creator":"Photographer", "license":"by-sa", "license_version":"4.0",
                      "license_url":"https://creativecommons.org/licenses/by-sa/4.0/"}]}
                      """
                          .trimIndent()
                    }
                    else -> error("Unexpected API request")
                  }
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body(json.toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()
    val repository = OnlineDestinationRepository(geonamesUsername = "test-user", client = client)

    val photo =
        repository.findPhoto(
            destinationFixture("geonames:2988507", "Paris", "France", "FR", 2988507)
        )

    assertEquals("CC BY-SA 4.0", photo?.license)
    assertEquals("Paris", photo?.title)
  }

  @Test
  fun openverseUsesDirectImageInsteadOfThumbnailProxy() = runTest {
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              val json =
                  when (chain.request().url.host) {
                    "query.wikidata.org" -> """{"results":{"bindings":[]}}"""
                    "api.openverse.org" ->
                        """
                        {"results":[{
                          "url":"https://images.example.org/lisbon.jpg",
                          "thumbnail":"https://api.openverse.org/v1/images/example/thumb/",
                          "foreign_landing_url":"https://example.org/photo",
                          "creator":"Photographer",
                          "license":"by",
                          "license_url":"https://creativecommons.org/licenses/by/4.0/"
                        }]}
                        """
                            .trimIndent()
                    else -> error("Unexpected API request")
                  }
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body(json.toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()
    val repository = OnlineDestinationRepository(geonamesUsername = "test-user", client = client)

    val photo =
        repository.findPhoto(
            destinationFixture("geonames:2267057", "Lisbon", "Portugal", "PT", 2267057)
        )

    assertEquals("https://images.example.org/lisbon.jpg", photo?.imageUrl)
    assertEquals("https://example.org/photo", photo?.sourceUrl)
    assertEquals("Photographer", photo?.creator)
  }

  @Test
  fun citySearchMakesOneRequestWithCityFilter() = runTest {
    val requests = mutableListOf<HttpUrl>()
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              requests += chain.request().url
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body(
                      """
                      {"geonames":[{"geonameId":2988507,"name":"Paris",
                      "countryName":"France","countryCode":"FR","lat":"48.85","lng":"2.35"}]}
                      """
                          .trimIndent()
                          .toResponseBody("application/json".toMediaType())
                  )
                  .build()
            }
            .build()
    val repository = OnlineDestinationRepository(geonamesUsername = "test-user", client = client)

    val destinations = repository.searchDestinations("  Paris  ")

    assertEquals(1, requests.size)
    val url = requests.single()
    assertEquals("https", url.scheme)
    assertEquals("secure.geonames.org", url.host)
    assertEquals("P", url.queryParameter("featureClass"))
    assertNull(url.queryParameter("featureCode"))
    assertEquals("Paris", url.queryParameter("q"))
    assertEquals(DestinationKind.CITY, destinations.single().kind)
    assertEquals("geonames:2988507", destinations.single().stableId)
    assertEquals("Paris", destinations.single().name)
    assertEquals("France", destinations.single().countryName)
    assertEquals("FR", destinations.single().countryCode)
    assertEquals(48.85, destinations.single().latitude, 0.0)
    assertEquals(2.35, destinations.single().longitude, 0.0)
    assertEquals(2988507, destinations.single().geonameId)
  }

  @Test
  fun missingUsernameFailsAndShortQueryDoesNotCallTheProvider() = runTest {
    val repository = OnlineDestinationRepository(geonamesUsername = "")
    try {
      repository.searchDestinations("Paris")
      fail("Expected missing GeoNames username to be rejected")
    } catch (_: DestinationConfigurationException) {
      // Expected configuration failure.
    }

    val requests = mutableListOf<HttpUrl>()
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              requests += chain.request().url
              error("A query shorter than two characters must not reach GeoNames")
            }
            .build()
    assertEquals(
        emptyList<Destination>(),
        OnlineDestinationRepository("test-user", client).searchDestinations(" P "),
    )
    assertEquals(emptyList<HttpUrl>(), requests)
  }

  @Test
  fun citySearchRejectsBadRowsAndDeduplicatesByStableId() = runTest {
    val client = jsonClient { _ ->
      """
      {"geonames":[null,{"geonameId":0,"name":"Nowhere"},
      {"geonameId":1,"name":" "},
      {"geonameId":3,"name":"Missing"}, {"geonameId":4,"name":"Broken","lat":"bad","lng":"3"},
      {"geonameId":5,"name":"Out of range","lat":"91","lng":"3"},
      {"geonameId":2,"name":"Valid","lat":"48","lng":"2"},{"geonameId":2,"name":"Duplicate","lat":"48","lng":"2"}]}
      """
          .trimIndent()
    }

    val result = OnlineDestinationRepository("test-user", client).searchDestinations("Va")

    assertEquals(listOf("Valid"), result.map { it.name })
  }

  @Test
  fun citySearchReturnsEmptyForResponseWithoutResults() = runTest {
    val result =
        OnlineDestinationRepository("test-user", jsonClient { "{}" }).searchDestinations("Paris")

    assertTrue(result.isEmpty())
  }

  @Test
  fun citySearchReportsGeoNamesProviderErrors() = runTest {
    val client = jsonClient { """{"status":{"message":"daily limit exceeded"}}""" }

    val error =
        try {
          OnlineDestinationRepository("test-user", client).searchDestinations("Paris")
          fail("Expected GeoNames provider error")
          return@runTest
        } catch (expected: IOException) {
          expected
        }

    assertEquals("daily limit exceeded", error.message)
  }

  @Test
  fun photoLookupFallsBackToOpenverseWhenCommonsLicenseIsUnsupported() = runTest {
    val client = jsonClient { request ->
      when (request.url.host) {
        "query.wikidata.org" ->
            """{"results":{"bindings":[{"image":{"value":"https://commons.wikimedia.org/wiki/Special:FilePath/City.jpg"}}]}}"""
        "commons.wikimedia.org" ->
            """{"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://img.test/city.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:City.jpg","extmetadata":{"LicenseShortName":{"value":"All rights reserved"}}}]}}}}"""
        "api.openverse.org" ->
            """{"results":[{"url":"https://img.test/openverse.jpg","foreign_landing_url":"https://openverse.test/photo","license":"cc0","license_url":"https://creativecommons.org/publicdomain/zero/1.0/"}]}"""
        else -> error("Unexpected API request")
      }
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertEquals("https://img.test/openverse.jpg", photo?.imageUrl)
    assertEquals("CC0", photo?.license)
  }

  @Test
  fun commonsRequiresAttributionForCreativeCommonsByPhotos() = runTest {
    val client = jsonClient { request ->
      when (request.url.host) {
        "query.wikidata.org" ->
            """{"results":{"bindings":[{"image":{"value":"https://commons.wikimedia.org/wiki/Special:FilePath/City.jpg"}}]}}"""
        "commons.wikimedia.org" ->
            """{"query":{"pages":{"1":{"imageinfo":[{"url":"https://img.test/uncredited.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:City.jpg","extmetadata":{"LicenseShortName":{"value":"CC BY 4.0"}}}]}}}}"""
        "api.openverse.org" ->
            """{"results":[{"url":"https://img.test/credited.jpg","foreign_landing_url":"https://openverse.test/photo","creator":"Photographer","license":"by","license_url":"https://creativecommons.org/licenses/by/4.0/"}]}"""
        else -> error("Unexpected API request")
      }
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertEquals("https://img.test/credited.jpg", photo?.imageUrl)
    assertEquals("Photographer", photo?.creator)
  }

  @Test
  fun openverseSkipsUncreditedByImagesAndUsesEligiblePublicDomainPhoto() = runTest {
    val client = jsonClient { request ->
      if (request.url.host == "query.wikidata.org") {
        """{"results":{"bindings":[]}}"""
      } else {
        """{"results":[
          {"url":"https://img.test/uncredited.jpg","foreign_landing_url":"https://openverse.test/uncredited","license":"by","license_url":"https://creativecommons.org/licenses/by/4.0/"},
          {"url":"https://img.test/public.jpg","foreign_landing_url":"https://openverse.test/public","license":"pdm","license_url":"https://creativecommons.org/publicdomain/mark/1.0/"}
        ]}"""
      }
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertEquals("https://img.test/public.jpg", photo?.imageUrl)
    assertEquals("Public domain", photo?.license)
    assertNull(photo?.creator)
  }

  @Test
  fun commonsHttpFailureFallsBackToOpenverse() = runTest {
    val client =
        jsonClient(
            responseCode = { host -> if (host == "query.wikidata.org") 503 else 200 },
            body = { request ->
              if (request.url.host == "query.wikidata.org") "{}"
              else
                  """{"results":[{"url":"https://img.test/fallback.jpg","foreign_landing_url":"https://openverse.test/photo","license":"cc0","license_url":"https://creativecommons.org/publicdomain/zero/1.0/"}]}"""
            },
        )

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertEquals("https://img.test/fallback.jpg", photo?.imageUrl)
  }

  @Test
  fun photoLookupReturnsNullWhenNetworkConnectionsFail() = runTest {
    val hosts = mutableListOf<String>()
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              hosts += chain.request().url.host
              throw IOException("offline")
            }
            .build()

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertNull(photo)
    assertEquals(listOf("query.wikidata.org", "api.openverse.org"), hosts)
  }

  @Test
  fun cancellingPhotoLookupCancelsThePendingRequest() = runTest {
    val requestStarted = CountDownLatch(1)
    val releaseResponse = CountDownLatch(1)
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              requestStarted.countDown()
              check(releaseResponse.await(5, TimeUnit.SECONDS))
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body("{}".toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()
    val lookup =
        async(start = CoroutineStart.UNDISPATCHED) {
          OnlineDestinationRepository("test-user", client)
              .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))
        }

    try {
      assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
    } finally {
      lookup.cancel()
      releaseResponse.countDown()
      client.dispatcher.executorService.shutdown()
    }
    lookup.join()

    assertTrue(lookup.isCancelled)
  }

  @Test
  fun commonsUsesOriginalAndMetadataFallbacksForPublicDomainPhoto() = runTest {
    val client = jsonClient { request ->
      when (request.url.host) {
        "query.wikidata.org" ->
            """{"results":{"bindings":[{"image":{"value":"https://commons.wikimedia.org/wiki/Special:FilePath/City%20view.jpg"}}]}}"""
        "commons.wikimedia.org" ->
            """{"query":{"pages":{"1":{"imageinfo":[{"url":"https://img.test/original.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:City.jpg","extmetadata":{"LicenseShortName":{"value":"Public domain"},"Credit":{"value":"Archive"}}}]}}}}"""
        else -> error("Unexpected API request")
      }
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertNotNull(photo)
    assertEquals("https://img.test/original.jpg", photo?.imageUrl)
    assertEquals("City view", photo?.title)
    assertEquals("Archive", photo?.credit)
    assertNull(photo?.licenseUrl)
  }

  @Test
  fun photoLookupReturnsNullWhenProvidersFail() = runTest {
    val client = jsonClient { request ->
      if (request.url.host == "query.wikidata.org") "{}" else "not-json"
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertNull(photo)
  }

  @Test
  fun photoLookupReturnsNullWhenOpenverseHasNoResults() = runTest {
    val client = jsonClient { request ->
      if (request.url.host == "query.wikidata.org") "{}" else """{"results":[]}"""
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertNull(photo)
  }

  @Test
  fun commonsMissingMetadataFallsBackToEligibleOpenverseImage() = runTest {
    val commonsResponses =
        listOf(
            "{}",
            """{"query":{"pages":{}}}""",
            """{"query":{"pages":{"1":{}}}}""",
            """{"query":{"pages":{"1":{"imageinfo":[{}]}}}}""",
            """{"query":{"pages":{"1":{"imageinfo":[{"extmetadata":{}}]}}}}""",
        )

    commonsResponses.forEach { commonsResponse ->
      val client = jsonClient { request ->
        when (request.url.host) {
          "query.wikidata.org" ->
              """{"results":{"bindings":[{"image":{"value":"https://commons.wikimedia.org/wiki/Special:FilePath/City.jpg"}}]}}"""
          "commons.wikimedia.org" -> commonsResponse
          else ->
              """{"results":[{"url":"https://img.test/fallback.jpg","foreign_landing_url":"https://openverse.test/photo","creator":"Photographer","license":"by","license_url":"https://creativecommons.org/licenses/by/4.0/"}]}"""
        }
      }

      val photo =
          OnlineDestinationRepository("test-user", client)
              .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

      assertEquals("https://img.test/fallback.jpg", photo?.imageUrl)
    }
  }

  @Test
  fun commonsRejectsMissingImageLinksAndUsesAttributionBeforeCredit() = runTest {
    val incompleteResponses =
        listOf(
            """{"query":{"pages":{"1":{"imageinfo":[{"extmetadata":{"LicenseShortName":{"value":"CC0"}}}]}}}}""",
            """{"query":{"pages":{"1":{"imageinfo":[{"url":"https://img.test/city.jpg","extmetadata":{"LicenseShortName":{"value":"CC0"}}}]}}}}""",
        )
    incompleteResponses.forEach { commonsResponse ->
      val client = jsonClient { request ->
        if (request.url.host == "query.wikidata.org") wikidataImageResponse()
        else if (request.url.host == "commons.wikimedia.org") commonsResponse
        else """{"results":[]}"""
      }
      assertNull(
          OnlineDestinationRepository("test-user", client)
              .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))
      )
    }

    val client = jsonClient { request ->
      when (request.url.host) {
        "query.wikidata.org" -> wikidataImageResponse()
        "commons.wikimedia.org" ->
            """{"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://img.test/city.jpg","descriptionurl":"https://commons.test/file","extmetadata":{"LicenseShortName":{"value":"CC0"},"Attribution":{"value":"Preferred"},"Credit":{"value":"Fallback"}}}]}}}}"""
        else -> error("Unexpected API request")
      }
    }
    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))
    assertEquals("Preferred", photo?.credit)
  }

  @Test
  fun openverseSkipsMalformedAndUnlicensedRowsThenUsesImageAndSourceFallbacks() = runTest {
    val client = jsonClient { request ->
      if (request.url.host == "query.wikidata.org") {
        """{"results":{"bindings":[]}}"""
      } else {
        """{"results":[null,{"url":"https://img.test/rejected.jpg","license":"copyright"},{"thumbnail":"https://img.test/thumb.jpg","foreign_landing_url":"https://openverse.test/photo","license":"cc0","license_url":"https://creativecommons.org/publicdomain/zero/1.0/"},{"url":"https://img.test/usable.jpg","license":"pdm","license_url":"https://creativecommons.org/publicdomain/mark/1.0/"}]}"""
      }
    }

    val photo =
        OnlineDestinationRepository("test-user", client)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))

    assertEquals("https://img.test/thumb.jpg", photo?.imageUrl)
    assertEquals("https://openverse.test/photo", photo?.sourceUrl)
    assertEquals("CC0", photo?.license)

    val sourceFallbackClient = jsonClient { request ->
      if (request.url.host == "query.wikidata.org") """{"results":{"bindings":[]}}"""
      else
          """{"results":[{"url":"https://img.test/direct.jpg","license":"pdm","license_url":"https://creativecommons.org/publicdomain/mark/1.0/"}]}"""
    }
    val sourceFallback =
        OnlineDestinationRepository("test-user", sourceFallbackClient)
            .findPhoto(destinationFixture("geonames:1", "City", "Country", "CC", 1))
    assertEquals("https://img.test/direct.jpg", sourceFallback?.sourceUrl)
  }

  private fun wikidataImageResponse() =
      """{"results":{"bindings":[{"image":{"value":"https://commons.wikimedia.org/wiki/Special:FilePath/City.jpg"}}]}}"""

  private fun jsonClient(
      responseCode: (String) -> Int = { 200 },
      body: (okhttp3.Request) -> String,
  ): OkHttpClient =
      OkHttpClient.Builder()
          .addInterceptor { chain ->
            val request = chain.request()
            val json = body(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode(request.url.host))
                .message("OK")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
          }
          .build()
}
