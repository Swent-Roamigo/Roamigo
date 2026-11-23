// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.voting

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import java.time.Instant

internal fun votingTestTrip(id: String = "trip"): Trip {
  val start = Instant.parse("2026-10-12T00:00:00Z")
  val end = Instant.parse("2026-10-19T00:00:00Z")
  return Trip(
      id,
      "Lisbon & Porto",
      null,
      emptyList(),
      Timestamp(start.epochSecond, 0),
      Timestamp(end.epochSecond, 0),
      false,
      TravelMode.GROUP,
      3,
      "alex",
      listOf("alex"),
      TripStatus.PLANNED,
      Timestamp(start.epochSecond, 0),
  )
}
