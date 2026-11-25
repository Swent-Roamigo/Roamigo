// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.swent.roamigo.BuildConfig
import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import com.swent.roamigo.model.destination.DestinationConfigurationException
import com.swent.roamigo.model.network.await
import com.swent.roamigo.model.validCountryCodeOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Reverse-geocodes a point with the GeoNames countryCode JSON service. */
class GeoNamesCountryCodeLookup(
    private val geonamesUsername: String = BuildConfig.GEONAMES_USERNAME,
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CountryCodeLookup {

  override suspend fun lookup(latitude: Double, longitude: Double): String? {
    if (latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
      return null
    }

    if (geonamesUsername.isBlank()) {
      throw DestinationConfigurationException()
    }

    val url =
        "https://secure.geonames.org/countryCode"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("lat", latitude.toString())
            .addQueryParameter("lng", longitude.toString())
            .addQueryParameter("type", "JSON")
            .addQueryParameter("username", geonamesUsername)
            .build()

    val json =
        withContext(ioDispatcher) {
          val request =
              Request.Builder().url(url).header("User-Agent", DESTINATION_USER_AGENT).build()

          client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
              throw IOException("GeoNames HTTP ${response.code}")
            }

            JSONObject(response.body?.string() ?: throw IOException("Empty GeoNames response"))
          }
        }

    if (json.has("status")) {
      val status = json.optJSONObject("status")

      if (status?.optInt("value") == NO_RESULT_CODE) {
        return null
      }

      throw IOException(status?.optString("message").orEmpty().ifBlank { "GeoNames error" })
    }

    // Missing or malformed provider values remain unresolved; never cache them.
    return (json.opt("countryCode") as? String).validCountryCodeOrNull()
  }

  private companion object {
    const val NO_RESULT_CODE = 15
    const val TIMEOUT_SECONDS = 12L

    val DEFAULT_CLIENT =
        OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
  }
}
