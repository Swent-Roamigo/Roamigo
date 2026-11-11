package com.swent.roamigo.model.place

import com.swent.roamigo.model.TripLocation

/**
 * A search result that is not yet resolved: the provider only gives its coordinates on retrieve.
 */
data class PlaceSuggestion(val id: String, val name: String, val address: String?)

/** A place with validated coordinates, as returned by a [PlaceRepository]. */
data class Place(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String? = null,
) {
  fun toTripLocation(): TripLocation =
      TripLocation(
          latitude = latitude,
          longitude = longitude,
          name = name,
          countryCode = countryCode,
      )
}

/** A point used to rank suggestions near the user or the trip. */
data class PlaceProximity(val latitude: Double, val longitude: Double)
