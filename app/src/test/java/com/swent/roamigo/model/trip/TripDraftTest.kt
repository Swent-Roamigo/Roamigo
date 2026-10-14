// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDraftTest {

  private val validDraft =
      TripDraft(
          name = "Lisbon weekend",
          destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon")),
          startDate = Timestamp(1000, 0),
          endDate = Timestamp(2000, 0),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
      )

  @Test
  fun validDraftHasNoErrors() {
    assertEquals(emptyList<TripDraftError>(), validDraft.validate())
  }

  @Test
  fun singleDayTripIsValid() {
    val draft = validDraft.copy(endDate = validDraft.startDate)

    assertEquals(emptyList<TripDraftError>(), draft.validate())
  }

  @Test
  fun soloTripWithOneTravelerIsValid() {
    val draft = validDraft.copy(travelMode = TravelMode.SOLO, travelerCount = 1)

    assertEquals(emptyList<TripDraftError>(), draft.validate())
  }

  @Test
  fun draftWithoutDestinationsIsInvalid() {
    val draft = validDraft.copy(destinations = emptyList())

    assertEquals(listOf(TripDraftError.NoDestinations), draft.validate())
  }

  @Test
  fun draftWithBlankNameIsInvalid() {
    val draft = validDraft.copy(name = "   ")

    assertEquals(listOf(TripDraftError.BlankName), draft.validate())
  }

  @Test
  fun draftEndingBeforeItStartsIsInvalid() {
    val draft = validDraft.copy(endDate = Timestamp(999, 0))

    assertEquals(listOf(TripDraftError.EndBeforeStart), draft.validate())
  }

  @Test
  fun draftWithoutTravelersIsInvalid() {
    val draft = validDraft.copy(travelerCount = 0)

    assertEquals(listOf(TripDraftError.InvalidTravelerCount), draft.validate())
  }

  @Test
  fun soloTripWithMultipleTravelersIsInvalid() {
    val draft = validDraft.copy(travelMode = TravelMode.SOLO, travelerCount = 2)

    assertEquals(listOf(TripDraftError.SoloWithMultipleTravelers), draft.validate())
  }

  @Test
  fun soloTripWithoutTravelersReportsOnlyTheInvalidCount() {
    val draft = validDraft.copy(travelMode = TravelMode.SOLO, travelerCount = 0)

    assertEquals(listOf(TripDraftError.InvalidTravelerCount), draft.validate())
  }

  @Test
  fun everyProblemIsReported() {
    val draft =
        TripDraft(
            name = "",
            destinations = emptyList(),
            startDate = Timestamp(2000, 0),
            endDate = Timestamp(1000, 0),
            flexibleDates = true,
            travelMode = TravelMode.SOLO,
            travelerCount = 4,
        )

    assertEquals(
        listOf(
            TripDraftError.NoDestinations,
            TripDraftError.BlankName,
            TripDraftError.EndBeforeStart,
            TripDraftError.SoloWithMultipleTravelers,
        ),
        draft.validate(),
    )
  }
}
