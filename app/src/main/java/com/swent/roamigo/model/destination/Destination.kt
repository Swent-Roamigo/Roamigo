// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.model.destination

/**
 * A city returned by GeoNames.
 *
 * @property stableId Provider-qualified identifier used for selection and caching.
 * @property kind Category of destination.
 * @property name City name.
 * @property countryName Display name of the country.
 * @property countryCode ISO country code supplied by GeoNames.
 * @property latitude Latitude in degrees.
 * @property longitude Longitude in degrees.
 * @property geonameId GeoNames identifier used to find related photos.
 */
data class Destination(
    val stableId: String,
    val kind: DestinationKind,
    val name: String,
    val countryName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val geonameId: Int,
)

enum class DestinationKind {
  CITY,
}

data class DestinationPhoto(
    val imageUrl: String,
    val creator: String?,
    val sourceUrl: String,
    val license: String,
    val licenseUrl: String?,
    val title: String? = null,
    val credit: String? = null,
)
