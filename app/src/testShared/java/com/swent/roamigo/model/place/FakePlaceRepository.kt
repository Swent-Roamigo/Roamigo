// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.model.place

/** Configurable search/retrieve source; never performs a provider request. */
class FakePlaceRepository(
    var search: suspend (String, PlaceProximity?) -> List<PlaceCandidate> = { _, _ -> emptyList() },
    var resolve: suspend (PlaceCandidate) -> Place = { Place(it.name, it.address, 46.52, 6.63) },
) : PlaceRepository {
  val searchRequests = mutableListOf<Pair<String, PlaceProximity?>>()
  val resolveRequests = mutableListOf<PlaceCandidate>()
  override suspend fun searchPlaces(query: String, proximity: PlaceProximity?): List<PlaceCandidate> {
    searchRequests += query to proximity
    return search(query, proximity)
  }
  override suspend fun resolvePlace(candidate: PlaceCandidate): Place {
    resolveRequests += candidate
    return resolve(candidate)
  }
}
