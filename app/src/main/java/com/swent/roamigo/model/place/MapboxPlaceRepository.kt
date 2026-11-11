package com.swent.roamigo.model.place

import com.swent.roamigo.BuildConfig
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
import org.json.JSONException
import org.json.JSONObject

/**
 * Place search through the Mapbox Search Box REST API.
 *
 * Mapbox bills a search as one session: the "suggest" calls typed so far plus the "retrieve" that
 * ends it must share a session token. The token is therefore kept here and renewed after each
 * successful [resolvePlace]. There is one session for the whole repository, so two searches that
 * overlap in time would share it. Mapbox limits Search Box results to temporary use; the team
 * accepted storing the chosen place anyway because the app is a course project that is not
 * distributed.
 */
class MapboxPlaceRepository(
    private val accessToken: String = BuildConfig.MAPBOX_ACCESS_TOKEN,
    private val client: OkHttpClient = defaultClient,
    private val newSessionToken: () -> String = { UUID.randomUUID().toString() },
) : PlaceRepository {
  private val sessionLock = Any()
  private var sessionToken: String = newSessionToken()

  override suspend fun searchPlaces(
      query: String,
      proximity: PlaceProximity?,
  ): List<PlaceCandidate> {
    val normalized = query.trim()
    // Checked before the configuration, as documented: such a query never needs the provider.
    if (normalized.length < MIN_PLACE_QUERY_LENGTH) return emptyList()
    requireConfigured()
    val url =
        baseUrl()
            .addPathSegments("suggest")
            .addQueryParameter("q", normalized)
            .addQueryParameter("limit", MAX_SUGGESTIONS.toString())
            .apply {
              if (proximity != null) {
                addQueryParameter("proximity", "${proximity.longitude},${proximity.latitude}")
              }
            }
            .build()
    val suggestions = getJson(url).optJSONArray("suggestions") ?: return emptyList()
    return (0 until suggestions.length())
        .mapNotNull { index -> suggestions.optJSONObject(index)?.toCandidate() }
        .distinctBy { it.id }
  }

  override suspend fun resolvePlace(candidate: PlaceCandidate): Place {
    requireConfigured()
    // A blank id would request the retrieve root and fail as if the provider were down.
    if (candidate.id.isBlank()) throw PlaceSearchException.InvalidPlace()
    val url = baseUrl().addPathSegments("retrieve").addPathSegment(candidate.id).build()
    val feature =
        getJson(url).optJSONArray("features")?.optJSONObject(0)
            ?: throw PlaceSearchException.InvalidPlace()
    val place = feature.toPlace(fallbackName = candidate.name)
    // The session ends with the retrieve, so the next search must start a new one.
    synchronized(sessionLock) { sessionToken = newSessionToken() }
    return place
  }

  private fun requireConfigured() {
    if (accessToken.isBlank()) throw PlaceSearchException.Configuration()
  }

  private fun baseUrl(): HttpUrl.Builder =
      SEARCH_BOX_URL.toHttpUrl()
          .newBuilder()
          .addQueryParameter("access_token", accessToken)
          .addQueryParameter("session_token", synchronized(sessionLock) { sessionToken })

  private fun JSONObject.toCandidate(): PlaceCandidate? {
    val id = optString("mapbox_id").takeIf(String::isNotBlank) ?: return null
    val name = optString("name").takeIf(String::isNotBlank) ?: return null
    return PlaceCandidate(id = id, name = name, address = address())
  }

  /** The full address, or the locality when the provider has no street address. */
  private fun JSONObject.address(): String? =
      optString("full_address").ifBlank { optString("place_formatted") }.takeIf(String::isNotBlank)

  private fun JSONObject.toPlace(fallbackName: String): Place {
    val properties = optJSONObject("properties") ?: throw PlaceSearchException.InvalidPlace()
    // GeoJSON order is [longitude, latitude].
    val coordinates = optJSONObject("geometry")?.optJSONArray("coordinates")
    // optDouble would also accept numeric strings, which the provider does not promise.
    val longitude = (coordinates?.opt(0) as? Number)?.toDouble() ?: Double.NaN
    val latitude = (coordinates?.opt(1) as? Number)?.toDouble() ?: Double.NaN
    if (
        !latitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            !longitude.isFinite() ||
            longitude !in -180.0..180.0
    )
        throw PlaceSearchException.InvalidPlace()
    val name = properties.optString("name").ifBlank { fallbackName }.takeIf(String::isNotBlank)
    if (name == null) throw PlaceSearchException.InvalidPlace()
    // TripLocation rejects anything but two uppercase letters, so normalize or drop it.
    val countryCode =
        properties
            .optJSONObject("context")
            ?.optJSONObject("country")
            ?.optString("country_code")
            ?.uppercase()
            ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
    return Place(name, properties.address(), latitude, longitude, countryCode)
  }

  private suspend fun getJson(url: HttpUrl): JSONObject =
      withContext(Dispatchers.IO) {
        val request =
            Request.Builder().url(url).header("User-Agent", DESTINATION_USER_AGENT).build()
        try {
          client.newCall(request).await().use { response ->
            when {
              response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN ->
                  throw PlaceSearchException.InvalidToken()
              response.code == HTTP_TOO_MANY_REQUESTS -> throw PlaceSearchException.QuotaExceeded()
              !response.isSuccessful ->
                  throw PlaceSearchException.Unavailable(
                      "Place search failed: HTTP ${response.code}"
                  )
            }
            JSONObject(response.body?.string() ?: throw IOException("Empty response"))
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: PlaceSearchException) {
          throw error
        } catch (error: IOException) {
          throw PlaceSearchException.Unavailable("Place search is unreachable", error)
        } catch (error: JSONException) {
          throw PlaceSearchException.Unavailable("Place search sent an unreadable answer", error)
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

  companion object {
    private const val SEARCH_BOX_URL = "https://api.mapbox.com/search/searchbox/v1/"
    private const val MAX_SUGGESTIONS = 8
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val REQUEST_TIMEOUT_SECONDS = 12L
    private val defaultClient =
        OkHttpClient.Builder().callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
  }
}
