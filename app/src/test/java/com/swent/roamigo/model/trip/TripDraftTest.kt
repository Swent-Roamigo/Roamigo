// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import com.swent.roamigo.model.TripLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDraftTest {

  private val lisbon = TripLocation(38.7223, -9.1393, "Lisbon")

  private val validDraft =
      TripDraft(
          name = "Lisbon weekend",
          destinations = listOf(lisbon),
          startDate = Instant.ofEpochSecond(1000),
          endDate = Instant.ofEpochSecond(2000),
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
  fun draftWithMaximumDestinationsIsValid() {
    val draft = validDraft.copy(destinations = List(10) { lisbon })

    assertEquals(emptyList<TripDraftError>(), draft.validate())
  }

  @Test
  fun draftWithTooManyDestinationsIsInvalid() {
    val draft = validDraft.copy(destinations = List(11) { lisbon })

    assertEquals(listOf(TripDraftError.TooManyDestinations), draft.validate())
  }

  @Test
  fun destinationWithBlankNameIsInvalid() {
    val draft = validDraft.copy(destinations = listOf(lisbon.copy(name = " ")))

    assertEquals(listOf(TripDraftError.BlankDestinationName), draft.validate())
  }

  @Test
  fun destinationsOnCoordinateBoundsAreValid() {
    val draft =
        validDraft.copy(
            destinations =
                listOf(
                    TripLocation(latitude = -90.0, longitude = -180.0, name = "South-west"),
                    TripLocation(latitude = 90.0, longitude = 180.0, name = "North-east"),
                )
        )

    assertEquals(emptyList<TripDraftError>(), draft.validate())
  }

  @Test
  fun latitudeBelowRangeIsInvalid() {
    assertCoordinatesAreInvalid(lisbon.copy(latitude = -90.1))
  }

  @Test
  fun latitudeAboveRangeIsInvalid() {
    assertCoordinatesAreInvalid(lisbon.copy(latitude = 90.1))
  }

  @Test
  fun longitudeBelowRangeIsInvalid() {
    assertCoordinatesAreInvalid(lisbon.copy(longitude = -180.1))
  }

  @Test
  fun longitudeAboveRangeIsInvalid() {
    assertCoordinatesAreInvalid(lisbon.copy(longitude = 180.1))
  }

  @Test
  fun coordinateThatIsNotANumberIsInvalid() {
    assertCoordinatesAreInvalid(lisbon.copy(latitude = Double.NaN))
  }

  @Test
  fun severalInvalidDestinationsAreReportedOnce() {
    val draft =
        validDraft.copy(
            destinations =
                listOf(
                    lisbon.copy(latitude = 91.0),
                    lisbon.copy(longitude = 181.0),
                    lisbon.copy(name = ""),
                    lisbon.copy(name = " "),
                )
        )

    assertEquals(
        listOf(
            TripDraftError.BlankDestinationName,
            TripDraftError.InvalidDestinationCoordinates,
        ),
        draft.validate(),
    )
  }

  @Test
  fun draftWithBlankNameIsInvalid() {
    val draft = validDraft.copy(name = "   ")

    assertEquals(listOf(TripDraftError.BlankName), draft.validate())
  }

  @Test
  fun draftEndingBeforeItStartsIsInvalid() {
    val draft = validDraft.copy(endDate = Instant.ofEpochSecond(999))

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
            startDate = Instant.ofEpochSecond(2000),
            endDate = Instant.ofEpochSecond(1000),
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

  private fun assertCoordinatesAreInvalid(destination: TripLocation) {
    val draft = validDraft.copy(destinations = listOf(destination))

    assertEquals(listOf(TripDraftError.InvalidDestinationCoordinates), draft.validate())
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
