package com.swent.roamigo.model.destination

data class Destination(
    val stableId: String,
    val kind: DestinationKind,
    val name: String,
    val countryName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val geonameId: Int,
    val wikidataId: String?,
)

enum class DestinationKind {
  CITY,
}

data class DestinationPhoto(
    val imageUrl: String,
    val creator: String?,
    val sourceUrl: String,
    val license: String,
    val licenseUrl: String,
    val title: String? = null,
    val credit: String? = null,
)
