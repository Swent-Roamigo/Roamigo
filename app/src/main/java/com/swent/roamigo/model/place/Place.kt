package com.swent.roamigo.model.place

import com.swent.roamigo.model.TripLocation

/**
 * A possible match for a search query that is not yet resolved: the provider only gives its
 * coordinates when the candidate is resolved into a [Place]. [attribution] is the provider notice
 * to display alongside its results.
 */
data class PlaceCandidate(
    val id: String,
    val name: String,
    val address: String?,
    val attribution: String? = null,
)

/** A place with validated coordinates, as returned by a [PlaceRepository]. */
data class Place(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String? = null,
    val attribution: String? = null,
) {
  fun toTripLocation(): TripLocation =
      TripLocation(
          latitude = latitude,
          longitude = longitude,
          name = name,
          countryCode = countryCode,
      )
}

/** A point used to rank candidates near the user or the trip. */
data class PlaceProximity(val latitude: Double, val longitude: Double)

/**
 * Where a place search for a trip should look: results near [proximity] rank first, and only places
 * in [countryCodes] are returned, when it is not empty.
 */
data class PlaceSearchArea(
    val proximity: PlaceProximity,
    val countryCodes: Set<String> = emptySet(),
)

/**
 * The area to search for a place of a trip with these [destinations], or null when it has none.
 *
 * The first destination ranks results, because a search can only be centred on one point. The
 * countries are only used when every destination has one: a destination without a country code
 * could be anywhere, so restricting the search would hide places the user is looking for.
 */
fun placeSearchAreaOf(destinations: List<TripLocation>): PlaceSearchArea? {
  val first = destinations.firstOrNull() ?: return null
  val countries = destinations.map { it.countryCode }
  return PlaceSearchArea(
      proximity = PlaceProximity(first.latitude, first.longitude),
      countryCodes =
          if (countries.all { it != null }) countries.filterNotNull().toSet() else emptySet(),
  )
}
