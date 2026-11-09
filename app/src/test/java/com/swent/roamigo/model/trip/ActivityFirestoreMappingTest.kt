// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ActivityFirestoreMappingTest {

  private val activity =
      Activity(
          uid = "activity-1",
          tripId = "trip-1",
          activityType = ActivityType.ACCEPTED,
          name = "Visit EPFL",
          description = "Rolex Learning Center",
          location = TripLocation(46.5197, 6.5668, "EPFL", "CH"),
          startTime = Timestamp(1200, 0),
          endTime = Timestamp(1300, 0),
          createdByUserId = "user-1",
          creationTime = Timestamp(900, 0),
      )

  @Test
  fun activityIsReadBackFromItsStoredFields() {
    val data = activity.toFirestoreData()

    assertEquals(activity, activityFromFirestore(activity.uid, activity.tripId, data))
  }

  @Test
  fun optionalFieldsRoundTripAsNull() {
    val minimal = activity.copy(description = null, endTime = null)

    val data = minimal.toFirestoreData()

    assertEquals(minimal, activityFromFirestore(minimal.uid, minimal.tripId, data))
  }

  @Test
  fun identityComesFromThePathAndIsNotStored() {
    val data = activity.toFirestoreData()

    assertFalse("uid" in data)
    assertFalse("tripId" in data)
  }

  @Test
  fun storedFieldsMatchTheOnesRequiredByTheRules() {
    assertEquals(
        setOf(
            "activityType",
            "name",
            "description",
            "location",
            "startTime",
            "endTime",
            "createdByUserId",
            "creationTime",
        ),
        activity.toFirestoreData().keys,
    )
  }

  @Test
  fun locationWithoutCountryCodeOmitsIt() {
    val noCode = activity.copy(location = activity.location.copy(countryCode = null))

    val location = noCode.toFirestoreData()["location"] as Map<*, *>

    assertFalse("countryCode" in location)
  }

  @Test
  fun malformedStoredActivityIsRejected() {
    val data = activity.toFirestoreData()

    assertThrows(IllegalArgumentException::class.java) {
      activityFromFirestore("a", "t", data + ("activityType" to "UNKNOWN"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      activityFromFirestore("a", "t", data - "startTime")
    }
  }
}
