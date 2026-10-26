package com.swent.roamigo.model.destination

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                            "Artist":{"value":"<a href='https://example.org'>Photographer</a>"},
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
    assertEquals("Photographer", photo?.creator)
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
    assertEquals("Paris", destinations.single().name)
    assertEquals("France", destinations.single().countryName)
  }
}
