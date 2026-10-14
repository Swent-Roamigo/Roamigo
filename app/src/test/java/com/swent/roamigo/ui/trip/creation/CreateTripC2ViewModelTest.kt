// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateTripC2ViewModelTest {
  @Test
  fun initialDateRangeStartsTodayAndEndsThreeDaysLater() {
    val today = LocalDate.now()
    val viewModel = CreateTripViewModel()

    assertEquals(today, viewModel.uiState.value.startDate)
    assertEquals(today.plusDays(3), viewModel.uiState.value.endDate)
    assertFalse(viewModel.uiState.value.isSelectingEndDate)
    assertFalse(viewModel.uiState.value.canCreateTrip)
  }

  @Test
  fun calendarNavigationAndRangeSelectionHandleMonthAndYearBoundaries() {
    val viewModel = CreateTripViewModel()
    val currentMonth = YearMonth.now()

    viewModel.showPreviousMonth()
    assertEquals(currentMonth.minusMonths(1), viewModel.uiState.value.visibleMonth)
    viewModel.showNextMonth()
    assertEquals(currentMonth, viewModel.uiState.value.visibleMonth)

    val start = LocalDate.of(2026, 12, 31)
    val end = LocalDate.of(2027, 1, 2)
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
    val firstStart = LocalDate.of(2026, 7, 8)
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

    assertEquals(TripType.GROUP, viewModel.uiState.value.tripType)
    assertEquals(3, viewModel.uiState.value.travelerCount)
    viewModel.selectTripType(TripType.SOLO)
    assertEquals(TripType.SOLO, viewModel.uiState.value.tripType)
    assertEquals(1, viewModel.uiState.value.travelerCount)
    viewModel.decrementTravelers()
    assertEquals(1, viewModel.uiState.value.travelerCount)

    viewModel.selectTripType(TripType.GROUP)
    assertEquals(3, viewModel.uiState.value.travelerCount)
    repeat(12) { viewModel.incrementTravelers() }
    assertEquals(10, viewModel.uiState.value.travelerCount)
    repeat(12) { viewModel.decrementTravelers() }
    assertEquals(2, viewModel.uiState.value.travelerCount)
    viewModel.selectTripType(TripType.GROUP)
    assertEquals(2, viewModel.uiState.value.travelerCount)
    assertEquals(TripType.GROUP, viewModel.uiState.value.tripType)
  }

  @Test
  fun groupRequiresAtLeastTwoTravelersWhileSoloAllowsOne() {
    val groupWithOneTraveler =
        CreateTripUiState(
            selectedDestinations = listOf("Lisbon"),
            tripType = TripType.GROUP,
            travelerCount = 1,
        )
    val groupWithTwoTravelers = groupWithOneTraveler.copy(travelerCount = 2)
    val soloTraveler = groupWithOneTraveler.copy(tripType = TripType.SOLO, travelerCount = 1)

    assertFalse(groupWithOneTraveler.canCreateTrip)
    assertTrue(groupWithTwoTravelers.canCreateTrip)
    assertTrue(soloTraveler.canCreateTrip)
  }

  @Test
  fun draftRemainsInvalidUntilBothDatesAreSelected() {
    val viewModel = CreateTripViewModel()
    viewModel.toggleDestination("Lisbon")
    viewModel.selectDate(LocalDate.now())

    assertTrue(viewModel.uiState.value.isSelectingEndDate)
    assertFalse(viewModel.uiState.value.canCreateTrip)
  }

  @Test
  fun destinationToggleIgnoresBlankInputAndTrimsValidInput() {
    val viewModel = CreateTripViewModel()

    viewModel.toggleDestination("   ")
    assertTrue(viewModel.uiState.value.selectedDestinations.isEmpty())
    assertFalse(viewModel.uiState.value.canCreateTrip)

    viewModel.toggleDestination(" Lisbon ")
    assertEquals(listOf("Lisbon"), viewModel.uiState.value.selectedDestinations)
  }
}
