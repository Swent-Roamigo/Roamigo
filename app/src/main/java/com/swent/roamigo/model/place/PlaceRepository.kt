package com.swent.roamigo.model.place

/** Queries shorter than this are not worth a provider call. */
const val MIN_PLACE_QUERY_LENGTH = 2

/** Searches places by name or address. Separate from the destination search, which finds cities. */
interface PlaceRepository {
  /**
   * Candidates for [query], best first. A blank or too short query returns an empty list without
   * calling the provider, even when the provider is not configured.
   *
   * @throws PlaceSearchException when the provider cannot answer.
   */
  suspend fun searchPlaces(query: String, proximity: PlaceProximity? = null): List<PlaceCandidate>

  /**
   * Resolves [candidate], which must come from [searchPlaces], into a place with coordinates.
   *
   * @throws PlaceSearchException when the provider cannot answer or the place has no valid
   *   coordinates.
   */
  suspend fun resolvePlace(candidate: PlaceCandidate): Place
}

/** Why a place search failed, so that callers can tell a retry from a configuration problem. */
sealed class PlaceSearchException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  /** No provider token is configured in this build. */
  class Configuration : PlaceSearchException("Place search is not configured")

  /** The provider refused the token. */
  class InvalidToken : PlaceSearchException("The place search token was refused")

  /** The provider's rate limit or free quota is used up. */
  class QuotaExceeded : PlaceSearchException("The place search quota is exceeded")

  /** The place could not be used, for example because its id or coordinates are invalid. */
  class InvalidPlace : PlaceSearchException("The place cannot be used")

  /** A network failure or any other provider error. */
  class Unavailable(message: String, cause: Throwable? = null) :
      PlaceSearchException(message, cause)
}
