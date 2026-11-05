// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.swent.roamigo.model.TripLocation

/** Resolves the country of a trip destination, or null when it cannot be identified. */
fun interface CountryCodeResolver {
  suspend fun resolve(location: TripLocation): String?
}

/** Looks up the country containing a geographical point. */
fun interface CountryCodeLookup {
  suspend fun lookup(latitude: Double, longitude: Double): String?
}

/** Persists geographical point-to-country results independently from trip documents. */
interface CountryCodeCache {
  suspend fun get(latitude: Double, longitude: Double): String?

  suspend fun put(latitude: Double, longitude: Double, countryCode: String)
}
