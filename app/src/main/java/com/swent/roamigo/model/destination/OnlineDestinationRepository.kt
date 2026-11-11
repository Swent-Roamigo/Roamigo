// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.model.destination

import android.net.Uri
import androidx.core.text.HtmlCompat
import com.swent.roamigo.BuildConfig
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

  /**
   * Puts the cities named like [query] first, the biggest first: those named exactly like it, then
   * those whose name starts with it. Every other result keeps GeoNames' order after them, because
   * ranking those by size alone would bring unrelated big cities up (Warsaw for "paris").
   */
  private fun rankByImportance(
      query: String,
      cities: List<Pair<Destination, Long>>,
  ): List<Destination> {
    val wanted = query.foldedForMatching()

    fun tier(city: Destination): Int {
      val name = city.name.foldedForMatching()
      return when {
        name == wanted -> 0
        name.startsWith(wanted) -> 1
        else -> 2
      }
    }
    // sortedWith is stable, so tier 2 stays in GeoNames' order.
    return cities
        .sortedWith(
            compareBy<Pair<Destination, Long>> { tier(it.first) }
                .thenByDescending { if (tier(it.first) < 2) it.second else 0L }
        )
        .map { it.first }
  }

  private fun String.foldedForMatching(): String =
      Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

  private suspend fun searchCities(query: String): List<Destination> {
    val url =
        "https://secure.geonames.org/searchJSON"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("featureClass", "P")
            .addQueryParameter("maxRows", SEARCH_CANDIDATES.toString())
            .addQueryParameter("orderby", "relevance")
            .addQueryParameter("username", geonamesUsername)
            .build()
    val root = getJson(url)
    if (root.has("status")) throw IOException(root.getJSONObject("status").optString("message"))
    val cities =
        root.optJSONArray("geonames")?.let { results ->
          (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("geonameId", 0)
            if (id <= 0) return@mapNotNull null
            val name = item.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val countryCode = item.optString("countryCode")
            val latitude = item.optDouble("lat")
            val longitude = item.optDouble("lng")
            if (
                !latitude.isFinite() ||
                    latitude !in -90.0..90.0 ||
                    !longitude.isFinite() ||
                    longitude !in -180.0..180.0
            )
                return@mapNotNull null
            Destination(
                stableId = "geonames:$id",
                kind = DestinationKind.CITY,
                name = name,
                countryName = item.optString("countryName"),
                countryCode = countryCode,
                latitude = latitude,
                longitude = longitude,
                geonameId = id,
            ) to item.optLong("population", 0L)
          }
        } ?: emptyList()
    return rankByImportance(query, cities)
  }

  override suspend fun findPhoto(destination: Destination): DestinationPhoto? =
      // Cheapest and most reliable first: Wikidata's image of the city, then the image of its
      // Wikipedia article, both checked on Commons. Openverse is slow, so it comes last.
      optionally { wikidataPhoto(destination)?.let { commonsPhoto(it) } }
          ?: optionally { wikipediaPhoto(destination) }
          ?: optionally { openversePhoto(destination) }

  /** Runs [lookup], treating any failure as "no photo" except being cancelled. */
  private suspend fun optionally(lookup: suspend () -> DestinationPhoto?): DestinationPhoto? =
      try {
        lookup()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }

  /**
   * The photo of the city's English Wikipedia article. The article is found by title and accepted
   * only when its coordinates are near the city, since several places share a name (Sion, Paris).
   * Only images hosted on Commons are used, because their licence can be checked there.
   */
  private suspend fun wikipediaPhoto(destination: Destination): DestinationPhoto? {
    val titles = listOf(destination.name, "${destination.name}, ${destination.countryName}")
    for (title in titles) {
      val file = wikipediaImageFile(title, destination) ?: continue
      commonsPhoto(file)?.let {
        return it
      }
    }
    return null
  }

  private suspend fun wikipediaImageFile(title: String, destination: Destination): String? {
    val url =
        "https://en.wikipedia.org/api/rest_v1/page/summary"
            .toHttpUrl()
            .newBuilder()
            .addPathSegment(title.replace(' ', '_'))
            .build()
    val page =
        try {
          getJson(url, WIKIPEDIA_TIMEOUT_SECONDS)
        } catch (error: HttpStatusException) {
          // A title that does not exist is a 404 and a normal miss. Any other failure (a timeout,
          // a 5xx) ends the Wikipedia step, so that Openverse is tried at once.
          if (error.code == HTTP_NOT_FOUND) return null else throw error
        }
    if (page.optString("type") != "standard") return null
    val coordinates = page.optJSONObject("coordinates") ?: return null
    // optDouble gives NaN for a missing or malformed value, and any comparison with NaN is false,
    // so such an article would otherwise pass as "near".
    val latitude = coordinates.optDouble("lat")
    val longitude = coordinates.optDouble("lon")
    if (
        !latitude.isFinite() ||
            !longitude.isFinite() ||
            abs(latitude - destination.latitude) > ARTICLE_MAX_DISTANCE_DEGREES ||
            abs(longitude - destination.longitude) > ARTICLE_MAX_DISTANCE_DEGREES
    )
        return null
    val image =
        page.optJSONObject("originalimage")?.optString("source")?.takeIf(String::isNotBlank)
            ?: page.optJSONObject("thumbnail")?.optString("source")?.takeIf(String::isNotBlank)
            ?: return null
    return commonsFileName(image)
  }

  /**
   * The name of the Commons file behind a Wikimedia image link, or null for other hosts' files,
   * such as non-free images kept on Wikipedia itself. Resized links end in the size, so the name is
   * the segment before it.
   */
  private fun commonsFileName(image: String): String? {
    val segments = image.substringBefore('?').split('/')
    if ("commons" !in segments) return null
    return if ("thumb" in segments) segments.getOrNull(segments.size - 2) else segments.lastOrNull()
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
            licenseUrl = metadata.plain("LicenseUrl"),
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
    val results = getJson(url, OPENVERSE_TIMEOUT_SECONDS).optJSONArray("results") ?: return null
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

  private suspend fun getJson(url: HttpUrl, callTimeoutSeconds: Long? = null): JSONObject =
      withContext(Dispatchers.IO) {
        val request =
            Request.Builder().url(url).header("User-Agent", DESTINATION_USER_AGENT).build()
        val http =
            callTimeoutSeconds?.let {
              client.newBuilder().callTimeout(it, TimeUnit.SECONDS).build()
            } ?: client
        http.newCall(request).await().use { response ->
          if (!response.isSuccessful) throw HttpStatusException(response.code)
          JSONObject(response.body?.string() ?: throw IOException("Empty response"))
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
          ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
          ?.trim()
          ?.takeIf(String::isNotBlank)

  companion object {
    private const val PUBLIC_DOMAIN_LABEL = "Public domain"
    private const val COMMONS_THUMBNAIL_WIDTH_PX = 640

    // More than shown, so that ranking by size can pick the important ones among them.
    private const val SEARCH_CANDIDATES = 50
    private const val OPENVERSE_PAGE_SIZE = 5
    // Openverse often takes longer than the other providers, so it must not hold up a photo.
    private const val OPENVERSE_TIMEOUT_SECONDS = 4L
    private const val WIKIPEDIA_TIMEOUT_SECONDS = 4L
    private const val HTTP_NOT_FOUND = 404
    private const val ARTICLE_MAX_DISTANCE_DEGREES = 0.3
    private const val REQUEST_TIMEOUT_SECONDS = 12L
    private val defaultClient =
        OkHttpClient.Builder().callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
  }
}

internal const val DESTINATION_USER_AGENT = "Roamigo/1.0 (https://github.com/Swent-Roamigo/Roamigo)"

/** A response whose status is not a success, so callers can tell a 404 from a server failure. */
private class HttpStatusException(val code: Int) : IOException("HTTP $code")
