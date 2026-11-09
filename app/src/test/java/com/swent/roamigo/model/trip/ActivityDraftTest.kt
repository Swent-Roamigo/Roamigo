// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip

import com.swent.roamigo.model.TripLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDraftTest {

  private val start = Instant.ofEpochSecond(1_900_000_000)

  private val draft =
      ActivityDraft(
          name = "Visit EPFL",
          description = null,
          location = TripLocation(46.5197, 6.5668, "EPFL"),
          startTime = start,
          endTime = start.plusSeconds(3600),
      )

  @Test
  fun validDraftHasNoErrors() {
    assertTrue(draft.validate().isEmpty())
  }

  @Test
  fun draftWithoutEndTimeOrWithEqualTimesIsValid() {
    assertTrue(draft.copy(endTime = null).validate().isEmpty())
    assertTrue(draft.copy(endTime = start).validate().isEmpty())
  }

  @Test
  fun blankNameIsRejected() {
    listOf("", "   ").forEach { name ->
      assertEquals(listOf(ActivityDraftError.BlankName), draft.copy(name = name).validate())
    }
  }

  @Test
  fun blankLocationNameIsRejected() {
    val invalid = draft.copy(location = draft.location.copy(name = " "))

    assertEquals(listOf(ActivityDraftError.BlankLocationName), invalid.validate())
  }

  @Test
  fun outOfRangeOrNonNumericCoordinatesAreRejected() {
    val locations =
        listOf(
            draft.location.copy(latitude = 90.1),
            draft.location.copy(latitude = -90.1),
            draft.location.copy(longitude = 180.1),
            draft.location.copy(longitude = -180.1),
            draft.location.copy(latitude = Double.NaN),
            draft.location.copy(longitude = Double.POSITIVE_INFINITY),
        )

    locations.forEach { location ->
      assertEquals(
          listOf(ActivityDraftError.InvalidLocationCoordinates),
          draft.copy(location = location).validate(),
      )
    }
  }

  @Test
  fun coordinatesOnTheBoundsAreAccepted() {
    val corner = draft.location.copy(latitude = -90.0, longitude = 180.0)

    assertTrue(draft.copy(location = corner).validate().isEmpty())
  }

  @Test
  fun endBeforeStartIsRejected() {
    val invalid = draft.copy(endTime = start.minusNanos(1))

    assertEquals(listOf(ActivityDraftError.EndBeforeStart), invalid.validate())
  }

  @Test
  fun everyProblemIsReportedAtOnce() {
    val invalid =
        draft.copy(
            name = "",
            location = draft.location.copy(name = "", latitude = 200.0),
            endTime = start.minusSeconds(1),
        )

    assertEquals(
        listOf(
            ActivityDraftError.BlankName,
            ActivityDraftError.BlankLocationName,
            ActivityDraftError.InvalidLocationCoordinates,
            ActivityDraftError.EndBeforeStart,
        ),
        invalid.validate(),
    )
  }
}
