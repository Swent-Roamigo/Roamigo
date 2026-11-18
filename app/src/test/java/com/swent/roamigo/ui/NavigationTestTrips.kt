// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import java.time.Instant

private const val HOUR_SECONDS = 3_600L
private const val DAY_SECONDS = 86_400L

/**
 * A trip for navigation tests, owned by "owner". It started an hour ago and ends tomorrow, or, when
 * [ended], it ended yesterday, so that it opens read-only.
 */
internal fun navigationTestTrip(
    id: String,
    name: String,
    memberIds: List<String> = listOf("owner", "member"),
    ended: Boolean = false,
): Trip {
  val now = Instant.now().epochSecond
  return Trip(
      id,
      name,
      null,
      listOf(TripLocation(38.7, -9.1, "Lisbon")),
      Timestamp(if (ended) now - 2 * DAY_SECONDS else now - HOUR_SECONDS, 0),
      Timestamp(if (ended) now - DAY_SECONDS else now + DAY_SECONDS, 0),
      false,
      TravelMode.GROUP,
      2,
      "owner",
      memberIds,
      if (ended) TripStatus.COMPLETED else TripStatus.ACTIVE,
      Timestamp(now - 2 * HOUR_SECONDS, 0),
  )
}
