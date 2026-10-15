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
  fun soloTripWithoutTravelersIsInvalid() {
    assertTravelerCountIsInvalid(TravelMode.SOLO, 0)
  }

  @Test
  fun soloTripWithOneTravelerIsValid() {
    assertTravelerCountIsValid(TravelMode.SOLO, 1)
  }

  @Test
  fun soloTripWithTwoTravelersIsInvalid() {
    assertTravelerCountIsInvalid(TravelMode.SOLO, 2)
  }

  @Test
  fun groupTripWithOneTravelerIsInvalid() {
    assertTravelerCountIsInvalid(TravelMode.GROUP, 1)
  }

  @Test
  fun groupTripWithTwoTravelersIsValid() {
    assertTravelerCountIsValid(TravelMode.GROUP, 2)
  }

  @Test
  fun groupTripWithTenTravelersIsValid() {
    assertTravelerCountIsValid(TravelMode.GROUP, 10)
  }

  @Test
  fun groupTripWithElevenTravelersIsInvalid() {
    assertTravelerCountIsInvalid(TravelMode.GROUP, 11)
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
            TripDraftError.InvalidTravelerCount,
        ),
        draft.validate(),
    )
  }

  private fun assertTravelerCountIsValid(travelMode: TravelMode, travelerCount: Int) {
    val draft = validDraft.copy(travelMode = travelMode, travelerCount = travelerCount)

    assertEquals(emptyList<TripDraftError>(), draft.validate())
  }

  private fun assertTravelerCountIsInvalid(travelMode: TravelMode, travelerCount: Int) {
    val draft = validDraft.copy(travelMode = travelMode, travelerCount = travelerCount)

    assertEquals(listOf(TripDraftError.InvalidTravelerCount), draft.validate())
  }
}
