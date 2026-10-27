// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.model.destination

/** In-memory destination source for tests; it never performs network or image-byte requests. */
class FakeDestinationRepository(
    private val search: suspend (String) -> List<Destination> = { emptyList() },
    private val photo: suspend (Destination) -> DestinationPhoto? = { null },
) : DestinationRepository {
  val searchQueries = mutableListOf<String>()
  val photoRequests = mutableListOf<Destination>()

  override suspend fun searchDestinations(query: String): List<Destination> {
    searchQueries += query
    return search(query)
  }

  override suspend fun findPhoto(destination: Destination): DestinationPhoto? {
    photoRequests += destination
    return photo(destination)
  }
}

fun destinationFixture(
    stableId: String,
    name: String = "Sample City",
    countryName: String = "Sample Country",
    countryCode: String = "SC",
    geonameId: Int = 1,
) =
    Destination(
        stableId = stableId,
        kind = DestinationKind.CITY,
        name = name,
        countryName = countryName,
        countryCode = countryCode,
        latitude = 10.0,
        longitude = 20.0,
        geonameId = geonameId,
    )

/** Deterministic online-search results used by creation and navigation tests. */
val sampleDestinations = listOf(
    Destination("geonames:2267057", DestinationKind.CITY, "Lisbon", "Portugal", "PT", 38.7223, -9.1393, 2267057),
    Destination("geonames:2735941", DestinationKind.CITY, "Porto", "Portugal", "PT", 41.1579, -8.6291, 2735941),
    Destination("geonames:3128760", DestinationKind.CITY, "Barcelona", "Spain", "ES", 41.3874, 2.1686, 3128760),
    Destination("geonames:3067696", DestinationKind.CITY, "Prague", "Czechia", "CZ", 50.0755, 14.4378, 3067696),
)

fun sampleDestination(name: String): Destination = sampleDestinations.first { it.name == name }

fun sampleDestinationRepository() = FakeDestinationRepository(search = { query ->
    if (query == "all") sampleDestinations
    else sampleDestinations.filter { it.name.contains(query, ignoreCase = true) }
})
