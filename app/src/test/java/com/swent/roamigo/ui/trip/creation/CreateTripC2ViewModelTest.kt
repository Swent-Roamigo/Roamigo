// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.trip.TravelMode
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateTripC2ViewModelTest {
  @Test
  fun initialDateRangeStartsTodayAndCoversThreeDays() {
    val today = LocalDate.now()
    val viewModel = CreateTripViewModel()

    assertEquals(today, viewModel.uiState.value.startDate)
    assertEquals(today.plusDays(2), viewModel.uiState.value.endDate)
    assertEquals(3L, viewModel.uiState.value.tripDays)
    assertEquals(2L, viewModel.uiState.value.tripNights)
    assertFalse(viewModel.uiState.value.isSelectingEndDate)
    assertFalse(viewModel.uiState.value.canCreateTrip)
  }

  @Test
  fun calendarNavigationAndRangeSelectionHandleMonthAndYearBoundaries() {
    val viewModel = CreateTripViewModel()
    val currentMonth = YearMonth.now()

    viewModel.showPreviousMonth()
    assertEquals(currentMonth, viewModel.uiState.value.visibleMonth)
    viewModel.showNextMonth()
    assertEquals(currentMonth.plusMonths(1), viewModel.uiState.value.visibleMonth)
    viewModel.showPreviousMonth()
    assertEquals(currentMonth, viewModel.uiState.value.visibleMonth)

    val start = LocalDate.of(YearMonth.now().year + 1, 12, 31)
    val end = start.plusDays(2)
    viewModel.selectDate(start)
    viewModel.selectDate(end)

    assertEquals(start, viewModel.uiState.value.startDate)
    assertEquals(end, viewModel.uiState.value.endDate)
    assertEquals(3L, viewModel.uiState.value.tripDays)
    assertEquals(2L, viewModel.uiState.value.tripNights)
  }

  @Test
  fun selectingAnEarlierDateStartsANewRangeAndFlexiblePreferenceStaysIndependent() {
    val viewModel = CreateTripViewModel()
    val firstStart = YearMonth.now().plusMonths(1).atDay(8)
    viewModel.selectDate(firstStart)
    viewModel.selectDate(firstStart.plusDays(4))
    viewModel.selectDate(firstStart.minusDays(2))
    viewModel.setFlexibleDates(true)

    assertEquals(firstStart.minusDays(2), viewModel.uiState.value.startDate)
    assertEquals(firstStart.minusDays(2), viewModel.uiState.value.endDate)
    assertTrue(viewModel.uiState.value.isSelectingEndDate)
    assertTrue(viewModel.uiState.value.flexibleDates)
    assertFalse(viewModel.uiState.value.canCreateTrip)
  }

  @Test
  fun travelerModeAndCountStayWithinBounds() {
    val viewModel = CreateTripViewModel()

    assertEquals(TravelMode.GROUP, viewModel.uiState.value.travelMode)
    assertEquals(3, viewModel.uiState.value.travelerCount)
    viewModel.selectTravelMode(TravelMode.SOLO)
    assertEquals(TravelMode.SOLO, viewModel.uiState.value.travelMode)
    assertEquals(1, viewModel.uiState.value.travelerCount)
    viewModel.decrementTravelers()
    assertEquals(1, viewModel.uiState.value.travelerCount)

    viewModel.selectTravelMode(TravelMode.GROUP)
    assertEquals(3, viewModel.uiState.value.travelerCount)
    repeat(12) { viewModel.incrementTravelers() }
    assertEquals(10, viewModel.uiState.value.travelerCount)
    repeat(12) { viewModel.decrementTravelers() }
    assertEquals(2, viewModel.uiState.value.travelerCount)
    viewModel.selectTravelMode(TravelMode.SOLO)
    assertEquals(1, viewModel.uiState.value.travelerCount)
    viewModel.selectTravelMode(TravelMode.GROUP)
    assertEquals(3, viewModel.uiState.value.travelerCount)
    assertEquals(TravelMode.GROUP, viewModel.uiState.value.travelMode)
  }

  @Test
  fun groupRequiresAtLeastTwoTravelersWhileSoloAllowsOne() {
    val groupWithOneTraveler =
        CreateTripUiState(
            selectedDestinations = listOf(sampleDestination("Lisbon")),
            travelMode = TravelMode.GROUP,
            travelerCount = 1,
        )
    val groupWithTwoTravelers = groupWithOneTraveler.copy(travelerCount = 2)
    val soloTraveler = groupWithOneTraveler.copy(travelMode = TravelMode.SOLO, travelerCount = 1)

    assertFalse(groupWithOneTraveler.canCreateTrip)
    assertTrue(groupWithTwoTravelers.canCreateTrip)
    assertTrue(soloTraveler.canCreateTrip)
  }

  @Test
  fun draftRemainsInvalidUntilBothDatesAreSelected() {
    val viewModel = CreateTripViewModel()
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.selectDate(LocalDate.now())

    assertTrue(viewModel.uiState.value.isSelectingEndDate)
    assertFalse(viewModel.uiState.value.canCreateTrip)
  }

  @Test
  fun pastDatesAreRejectedAndCannotMakeTheDraftCreatable() {
    val pastDate = LocalDate.now().minusDays(1)
    val viewModel = CreateTripViewModel()
    val originalState = viewModel.uiState.value

    viewModel.selectDate(pastDate)

    assertEquals(originalState, viewModel.uiState.value)
    val stateWithPastStart =
        originalState.copy(
            startDate = pastDate,
            endDate = LocalDate.now().plusDays(1),
            selectedDestinations = listOf(sampleDestination("Lisbon")),
        )
    assertFalse(stateWithPastStart.canCreateTrip)
  }

  @Test
  fun destinationToggleRejectsBlankNamesAndAcceptsStructuredDestinations() {
    val viewModel = CreateTripViewModel()

    viewModel.toggleDestination(sampleDestination("Lisbon").copy(name = "   "))
    assertTrue(viewModel.uiState.value.selectedDestinations.isEmpty())
    assertFalse(viewModel.uiState.value.canCreateTrip)

    viewModel.toggleDestination(sampleDestination("Lisbon"))
    assertEquals(listOf(sampleDestination("Lisbon")), viewModel.uiState.value.selectedDestinations)
  }
}
