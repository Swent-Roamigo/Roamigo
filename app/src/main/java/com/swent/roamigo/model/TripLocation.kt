// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model

private val COUNTRY_CODE_REGEX = Regex("[A-Z]{2}")

/**
 * Returns a valid two-letter uppercase country code, or null if absent or malformed. This checks
 * the format, not membership in the ISO 3166-1 code list.
 */
internal fun String?.validCountryCodeOrNull(): String? =
    this?.takeIf { COUNTRY_CODE_REGEX.matches(it) }

/** A trip destination. Country codes are optional for backward compatibility. */
data class TripLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val countryCode: String? = null,
) {
  init {
    require(countryCode == null || countryCode.validCountryCodeOrNull() != null) {
      "Invalid countryCode: expected two uppercase letters"
    }
  }
}

// firestore.rules cannot import these values, so its destination check repeats them.

/** The smallest valid latitude, in degrees. */
const val MIN_LATITUDE = -90.0

/** The largest valid latitude, in degrees. */
const val MAX_LATITUDE = 90.0

/** The smallest valid longitude, in degrees. */
const val MIN_LONGITUDE = -180.0

/** The largest valid longitude, in degrees. */
const val MAX_LONGITUDE = 180.0
