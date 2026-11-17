// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import java.time.Instant
import java.time.LocalDate

/** Shared test data for the activity form, matching the Figma UX-26 example. */
internal object ActivityFormFixtures {
  /** The trip ID used by every form under test. */
  const val TRIP_ID = "trip"

  /** A trip from 10 to 15 October 2026. */
  val trip =
      ActivityFormTrip("Lisbon → Porto", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 15))

  /** A day inside [trip]. */
  val day: LocalDate = LocalDate.of(2026, 10, 13)

  /** A valid place in Lisbon. */
  val place = TripLocation(38.7071, -9.1456, "Time Out Market")

  /** When [storedActivity] starts: 19:30 UTC on [day]. */
  val storedStart: Instant = Instant.parse("2026-10-13T19:30:00Z")

  /** An activity already stored in [trip], as Edit mode receives it. */
  fun storedActivity(end: Instant? = null, description: String? = "Bring cash") =
      Activity(
          uid = "activity-1",
          tripId = TRIP_ID,
          activityType = ActivityType.ACCEPTED,
          name = "Dinner at Time Out Market",
          description = description,
          location = place,
          startTime = storedStart.toTimestamp(),
          endTime = end?.toTimestamp(),
          // Placeholder author and creation time: the form never reads them.
          createdByUserId = "fake-user",
          creationTime = Timestamp(0, 0),
      )

  private fun Instant.toTimestamp() = Timestamp(epochSecond, nano)
}
