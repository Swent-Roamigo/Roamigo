// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.model.destination

const val MIN_DESTINATION_QUERY_LENGTH = 2
const val MAX_DESTINATION_RESULTS = 20

interface DestinationRepository {
  suspend fun searchDestinations(query: String): List<Destination>

  suspend fun findPhoto(destination: Destination): DestinationPhoto?
}

class DestinationConfigurationException : IllegalStateException()
