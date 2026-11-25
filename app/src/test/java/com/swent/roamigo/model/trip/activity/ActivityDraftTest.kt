// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

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
    assertTrue(draft.validate(startRequired = true).isEmpty())
  }

  @Test
  fun draftWithoutEndTimeOrWithEqualTimesIsValid() {
    assertTrue(draft.copy(endTime = null).validate(startRequired = true).isEmpty())
    assertTrue(draft.copy(endTime = start).validate(startRequired = true).isEmpty())
  }

  @Test
  fun blankNameIsRejected() {
    listOf("", "   ").forEach { name ->
      assertEquals(
          listOf(ActivityDraftError.BlankName),
          draft.copy(name = name).validate(startRequired = true),
      )
    }
  }

  @Test
  fun nameAtTheMaximumLengthIsAcceptedAndOneMoreIsRejected() {
    val longest = "a".repeat(MAX_ACTIVITY_NAME_LENGTH)

    assertTrue(draft.copy(name = longest).validate(startRequired = true).isEmpty())
    assertEquals(
        listOf(ActivityDraftError.NameTooLong),
        draft.copy(name = longest + "a").validate(startRequired = true),
    )
  }

  @Test
  fun descriptionAtTheMaximumLengthIsAcceptedAndOneMoreIsRejected() {
    val longest = "a".repeat(MAX_ACTIVITY_DESCRIPTION_LENGTH)

    assertTrue(draft.copy(description = longest).validate(startRequired = true).isEmpty())
    assertEquals(
        listOf(ActivityDraftError.DescriptionTooLong),
        draft.copy(description = longest + "a").validate(startRequired = true),
    )
  }

  @Test
  fun blankLocationNameIsRejected() {
    val invalid = draft.copy(location = draft.location.copy(name = " "))

    assertEquals(
        listOf(ActivityDraftError.BlankLocationName),
        invalid.validate(startRequired = true),
    )
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
          draft.copy(location = location).validate(startRequired = true),
      )
    }
  }

  @Test
  fun coordinatesOnTheBoundsAreAccepted() {
    val corner = draft.location.copy(latitude = -90.0, longitude = 180.0)

    assertTrue(draft.copy(location = corner).validate(startRequired = true).isEmpty())
  }

  @Test
  fun endBeforeStartIsRejected() {
    val invalid = draft.copy(endTime = start.minusNanos(1))

    assertEquals(listOf(ActivityDraftError.EndBeforeStart), invalid.validate(startRequired = true))
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
        invalid.validate(startRequired = true),
    )
  }

  @Test
  fun missingStartIsRejectedOnlyWhenRequired() {
    val unscheduled = draft.copy(startTime = null, endTime = null)

    assertEquals(
        listOf(ActivityDraftError.MissingStartTime),
        unscheduled.validate(startRequired = true),
    )
    assertTrue(unscheduled.validate(startRequired = false).isEmpty())
  }

  @Test
  fun endWithoutStartIsRejectedWhateverTheRequirement() {
    val invalid = draft.copy(startTime = null)

    assertEquals(
        listOf(ActivityDraftError.EndWithoutStart),
        invalid.validate(startRequired = false),
    )
    assertEquals(
        listOf(ActivityDraftError.MissingStartTime, ActivityDraftError.EndWithoutStart),
        invalid.validate(startRequired = true),
    )
  }

  @Test
  fun optionalStartStillChecksTheOrderOfAScheduledDraft() {
    assertTrue(draft.validate(startRequired = false).isEmpty())
    assertEquals(
        listOf(ActivityDraftError.EndBeforeStart),
        draft.copy(endTime = start.minusNanos(1)).validate(startRequired = false),
    )
  }
}
