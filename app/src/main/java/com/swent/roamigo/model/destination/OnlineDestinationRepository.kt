package com.swent.roamigo.model.destination

import android.net.Uri
import com.swent.roamigo.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/** Online GeoNames search and Commons/Openverse photo lookup. */
class OnlineDestinationRepository(
    private val geonamesUsername: String = BuildConfig.GEONAMES_USERNAME,
    private val client: OkHttpClient = defaultClient,
) : DestinationRepository {

  override suspend fun searchDestinations(query: String): List<Destination> {
    if (geonamesUsername.isBlank()) throw DestinationConfigurationException()
    val normalized = query.trim()
    if (normalized.length < MIN_DESTINATION_QUERY_LENGTH) return emptyList()
    return searchCities(normalized).distinctBy { it.stableId }.take(MAX_DESTINATION_RESULTS)
  }

  private suspend fun searchCities(query: String): List<Destination> {
    val url =
        "https://secure.geonames.org/searchJSON"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("featureClass", "P")
            .addQueryParameter("maxRows", MAX_DESTINATION_RESULTS.toString())
            .addQueryParameter("orderby", "relevance")
            .addQueryParameter("username", geonamesUsername)
            .build()
    val root = getJson(url)
    if (root.has("status")) throw IOException(root.getJSONObject("status").optString("message"))
    return root.optJSONArray("geonames")?.let { results ->
      (0 until results.length()).mapNotNull { index ->
        val item = results.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optInt("geonameId", 0)
        if (id <= 0) return@mapNotNull null
        val name = item.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val countryCode = item.optString("countryCode")
        Destination(
            stableId = "geonames:$id",
            kind = DestinationKind.CITY,
            name = name,
            countryName = item.optString("countryName"),
            countryCode = countryCode,
            latitude = item.optDouble("lat"),
            longitude = item.optDouble("lng"),
            geonameId = id,
            wikidataId = null,
        )
      }
    } ?: emptyList()
  }

  override suspend fun findPhoto(destination: Destination): DestinationPhoto? {
    return try {
      val commons =
          try {
            wikidataPhoto(destination)?.let { commonsPhoto(it) }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            null
          }
      commons ?: openversePhoto(destination)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      null
    }
  }

  private suspend fun wikidataPhoto(destination: Destination): String? {
    val query =
        "SELECT ?image WHERE { ?item wdt:P1566 \"${destination.geonameId}\"; wdt:P18 ?image. } LIMIT 1"
    val url =
        "https://query.wikidata.org/sparql"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("format", "json")
            .build()
    return getJson(url)
        .optJSONObject("results")
        ?.optJSONArray("bindings")
        ?.optJSONObject(0)
        ?.optJSONObject("image")
        ?.optString("value")
  }

  private suspend fun commonsPhoto(image: String): DestinationPhoto? {
    val filename = Uri.decode(image.substringAfterLast('/').substringBefore('?')).replace(' ', '_')
    val url =
        "https://commons.wikimedia.org/w/api.php"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("titles", "File:$filename")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|extmetadata")
            .addQueryParameter("iiurlwidth", COMMONS_THUMBNAIL_WIDTH_PX.toString())
            .addQueryParameter("format", "json")
            .build()
    val pages = getJson(url).optJSONObject("query")?.optJSONObject("pages") ?: return null
    val page =
        pages.keys().asSequence().mapNotNull(pages::optJSONObject).firstOrNull() ?: return null
    val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
    val metadata = info.optJSONObject("extmetadata") ?: return null
    val license = metadata.plain("LicenseShortName") ?: return null
    val normalizedLicense = license.lowercase()
    if (
        normalizedLicense !in
            setOf(
                "cc0",
                "public domain",
                "cc by",
                "cc by 4.0",
                "cc by 3.0",
                "cc by 2.0",
                "cc by-sa 4.0",
                "cc by-sa 3.0",
                "cc by-sa 2.5",
                "cc by-sa 2.0",
            )
    )
        return null
    val creator = metadata.plain("Artist")
    if (normalizedLicense.startsWith("cc by") && creator == null) return null
    return DestinationPhoto(
            imageUrl = info.optString("thumburl").ifBlank { info.optString("url") },
            creator = creator,
            sourceUrl = info.optString("descriptionurl"),
            license = license,
            licenseUrl = metadata.plain("LicenseUrl") ?: "https://creativecommons.org/",
            title =
                metadata.plain("ObjectName") ?: filename.substringBeforeLast('.').replace('_', ' '),
            credit = metadata.plain("Attribution") ?: metadata.plain("Credit"),
        )
        .takeIf { it.imageUrl.isNotBlank() && it.sourceUrl.isNotBlank() }
  }

  private suspend fun openversePhoto(destination: Destination): DestinationPhoto? {
    val url =
        "https://api.openverse.org/v1/images/"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", "${destination.name} ${destination.countryName}".trim())
            .addQueryParameter("license", "cc0,pdm,by,by-sa")
            .addQueryParameter("page_size", OPENVERSE_PAGE_SIZE.toString())
            .build()
    val results = getJson(url).optJSONArray("results") ?: return null
    for (index in 0 until results.length()) {
      val item = results.optJSONObject(index) ?: continue
      val license = item.optString("license").lowercase()
      if (license !in setOf("cc0", "pdm", "by", "by-sa")) continue
      // Avoid the Openverse thumbnail proxy: it can time out while the source image is available.
      val image = item.optString("url").ifBlank { item.optString("thumbnail") }
      val source = item.optString("foreign_landing_url").ifBlank { item.optString("url") }
      val licenseUrl = item.optString("license_url")
      val creator = item.optString("creator")
      if (
          image.isNotBlank() &&
              source.isNotBlank() &&
              licenseUrl.isNotBlank() &&
              (!license.startsWith("by") || creator.isNotBlank())
      ) {
        return DestinationPhoto(
            imageUrl = image,
            creator = creator.ifBlank { null },
            sourceUrl = source,
            license =
                when (license) {
                  "pdm" -> PUBLIC_DOMAIN_LABEL
                  "cc0" -> "CC0"
                  else -> "CC ${license.uppercase()} ${item.optString("license_version")}".trim()
                },
            licenseUrl = licenseUrl,
            title = item.optString("title").ifBlank { null },
        )
      }
    }
    return null
  }

  private suspend fun getJson(url: HttpUrl): JSONObject {
    val request = Request.Builder().url(url).header("User-Agent", DESTINATION_USER_AGENT).build()
    client.newCall(request).await().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      return JSONObject(response.body?.string() ?: throw IOException("Empty response"))
    }
  }

  private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
          }

          override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
          }
        }
    )
  }

  private fun JSONObject.plain(key: String): String? =
      optJSONObject(key)
          ?.optString("value")
          ?.replace(Regex("<[^>]*>"), "")
          ?.trim()
          ?.takeIf(String::isNotBlank)

  companion object {
    private const val PUBLIC_DOMAIN_LABEL = "Public domain"
    private const val COMMONS_THUMBNAIL_WIDTH_PX = 640
    private const val OPENVERSE_PAGE_SIZE = 5
    private const val REQUEST_TIMEOUT_SECONDS = 12L
    private val defaultClient =
        OkHttpClient.Builder().callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
  }
}

internal const val DESTINATION_USER_AGENT = "Roamigo/1.0 (https://github.com/Swent-Roamigo/Roamigo)"
