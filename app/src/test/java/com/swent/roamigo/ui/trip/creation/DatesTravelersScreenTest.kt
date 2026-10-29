// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DateSummaryTestTags
import com.swent.roamigo.ui.trip.creation.components.TravelersCounterTestTags
import com.swent.roamigo.ui.trip.creation.components.TripCalendarTestTags
import com.swent.roamigo.ui.trip.creation.components.TripTypeSelectorTestTags
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatesTravelersScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun initialScreenShowsC2ControlsAndEnablesCreationForValidDefaultDraft() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    showScreen(viewModel)

    composeRule.onNodeWithTag(DatesTravelersScreenTestTags.SCREEN).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.PREVIOUS_MONTH).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).assertExists()
    composeRule.onNodeWithTag(DateSummaryTestTags.FLEXIBLE_TOGGLE).assertExists()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.SOLO).assertExists()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.GROUP).assertExists()
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("3")
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsEnabled()
  }

  @Test
  fun shortScreenCanScrollToTravelerControlsWhileKeepingCreateActionVisible() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    composeRule.setContent {
      Box(Modifier.size(width = 390.dp, height = 480.dp)) {
        RoamigoTheme {
          DatesTravelersScreen(
              createTripViewModel = viewModel,
              onBack = {},
              onClose = {},
              onCreateTrip = {},
          )
        }
      }
    }

    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertIsDisplayed()
        .assertIsEnabled()
    composeRule
        .onNodeWithTag(DatesTravelersScreenTestTags.SCREEN)
        .performScrollToNode(hasTestTag(TravelersCounterTestTags.COUNT))
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertIsDisplayed()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsDisplayed()
  }

  @Test
  fun monthNavigationMovesAcrossYearBoundaryAndRestoresTheOriginalMonth() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    showScreen(viewModel)
    val currentMonth = YearMonth.now()

    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(currentMonth.atDay(1))).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.PREVIOUS_MONTH).assertIsNotEnabled()
    composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).performClick()
    composeRule
        .onNodeWithTag(TripCalendarTestTags.dayFor(currentMonth.plusMonths(1).atDay(1)))
        .assertExists()
    composeRule
        .onNodeWithTag(TripCalendarTestTags.dayFor(currentMonth.atDay(1)))
        .assertDoesNotExist()

    val monthsToNextJanuary = 13 - currentMonth.monthValue
    repeat(monthsToNextJanuary - 1) {
      composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).performClick()
    }
    val nextJanuary = currentMonth.plusMonths(monthsToNextJanuary.toLong())
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(nextJanuary.atDay(1))).assertExists()
    repeat(monthsToNextJanuary) {
      composeRule.onNodeWithTag(TripCalendarTestTags.PREVIOUS_MONTH).performClick()
    }
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(currentMonth.atDay(1))).assertExists()
  }

  @Test
  fun selectedDatesCanSpanMonthsAndProduceInclusiveDuration() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    showScreen(viewModel)
    val start = YearMonth.now().atEndOfMonth()
    val end = YearMonth.from(start).plusMonths(1).atDay(2)

    viewModel.toggleDestination(sampleDestination("Lisbon"))
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(start)).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(end)).performClick()

    composeRule.runOnIdle {
      assertEquals(start, viewModel.uiState.value.startDate)
      assertEquals(end, viewModel.uiState.value.endDate)
      assertEquals(3L, viewModel.uiState.value.tripDays)
      assertEquals(2L, viewModel.uiState.value.tripNights)
    }
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dateFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH)
    val expectedDateRange =
        context.getString(
            R.string.c2_date_range,
            start.format(dateFormatter),
            end.format(dateFormatter),
        )
    val expectedDuration =
        context.getString(
            R.string.c2_duration_summary,
            context.resources.getQuantityString(R.plurals.c2_trip_days, 3, 3),
            context.resources.getQuantityString(R.plurals.c2_trip_nights, 2, 2),
        )
    composeRule.onNodeWithTag(DateSummaryTestTags.DATE_RANGE).assertTextEquals(expectedDateRange)
    composeRule
        .onNodeWithTag(DateSummaryTestTags.DURATION_SUMMARY)
        .assertTextEquals(expectedDuration)
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsEnabled()
  }

  @Test
  fun selectingEarlierDateRestartsRangeAndFlexiblePreferenceIsRetained() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.showNextMonth()
    showScreen(viewModel)
    val month = YearMonth.now().plusMonths(1)
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(month.atDay(8))).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(month.atDay(12))).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(month.atDay(4))).performClick()
    composeRule.onNodeWithTag(DateSummaryTestTags.FLEXIBLE_TOGGLE).performClick()

    composeRule.runOnIdle {
      assertEquals(month.atDay(4), viewModel.uiState.value.startDate)
      assertEquals(month.atDay(4), viewModel.uiState.value.endDate)
      assertTrue(viewModel.uiState.value.isSelectingEndDate)
      assertTrue(viewModel.uiState.value.flexibleDates)
    }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun soloAndGroupSelectionRespectTravelerBounds() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    showScreen(viewModel)

    composeRule.onNodeWithTag(TripTypeSelectorTestTags.SOLO).performClick()
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertDoesNotExist()
    composeRule.onNodeWithTag(TravelersCounterTestTags.DECREMENT).assertDoesNotExist()
    composeRule.onNodeWithTag(TravelersCounterTestTags.INCREMENT).assertDoesNotExist()

    composeRule.onNodeWithTag(TripTypeSelectorTestTags.GROUP).performClick()
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertExists()
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("3")
    repeat(10) { composeRule.onNodeWithTag(TravelersCounterTestTags.INCREMENT).performClick() }
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("10")
    composeRule.onNodeWithTag(TravelersCounterTestTags.INCREMENT).assertIsNotEnabled()
    repeat(10) { composeRule.onNodeWithTag(TravelersCounterTestTags.DECREMENT).performClick() }
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("2")
    composeRule.onNodeWithTag(TravelersCounterTestTags.DECREMENT).assertIsNotEnabled()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.SOLO).performClick()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.GROUP).performClick()
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("3")

    composeRule.runOnIdle {
      assertEquals(TravelMode.GROUP, viewModel.uiState.value.travelMode)
      assertEquals(3, viewModel.uiState.value.travelerCount)
    }
  }

  @Test
  fun pastCalendarDaysAreDisabledAndCannotChangeTheDraft() {
    val pastMonth = YearMonth.now().minusMonths(1)
    val initialState = CreateTripUiState(visibleMonth = pastMonth)
    composeRule.setContent {
      RoamigoTheme {
        DatesTravelersScreenContent(
            uiState = initialState,
            onBack = {},
            onClose = {},
            onCreateTrip = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onSelectDate = {},
            onFlexibleDatesChange = {},
            onTravelModeChange = {},
            onDecrementTravelers = {},
            onIncrementTravelers = {},
        )
      }
    }

    composeRule
        .onNodeWithTag(TripCalendarTestTags.dayFor(pastMonth.atDay(1)))
        .assertIsNotEnabled()
        .performClick()
  }

  @Test
  fun clickingCreateTripInvokesCallbackWithoutMutatingDraft() {
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = FakeDestinationRepository(),
        )
    var createTripCalled = false
    showScreen(viewModel) { createTripCalled = true }
    selectTwoDatesInVisibleMonth(viewModel)
    val draftBeforeClick = viewModel.uiState.value

    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertIsEnabled()
        .performClick()

    composeRule.runOnIdle {
      assertEquals(draftBeforeClick, viewModel.uiState.value)
      assertTrue(createTripCalled)
    }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsEnabled()
  }

  private fun showScreen(viewModel: CreateTripViewModel, onCreateTrip: () -> Unit = {}) {
    composeRule.setContent {
      RoamigoTheme {
        DatesTravelersScreen(
            createTripViewModel = viewModel,
            onBack = {},
            onClose = {},
            onCreateTrip = onCreateTrip,
        )
      }
    }
  }

  private fun selectTwoDatesInVisibleMonth(
      viewModel: CreateTripViewModel,
  ) {
    val month = viewModel.uiState.value.visibleMonth.plusMonths(1)
    viewModel.showNextMonth()
    val start = month.atDay(5)
    val end = month.atDay(8)
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(start)).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(end)).performClick()
  }
}
