// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DateSummaryTestTags
import com.swent.roamigo.ui.trip.creation.components.TravelersCounterTestTags
import com.swent.roamigo.ui.trip.creation.components.TripCalendarTestTags
import com.swent.roamigo.ui.trip.creation.components.TripTypeSelectorTestTags
import java.time.LocalDate
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

  private val futureMonth = YearMonth.now().plusMonths(1)

  @Test
  fun validDraftShowsDateAndTravelerControlsAndEnablesCreation() {
    showScreen()

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
    showScreen(modifier = Modifier.size(width = 390.dp, height = 480.dp))

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
  fun monthNavigationForwardsActionsAndRendersUpdatedMonthsAcrossYearBoundary() {
    val currentMonth = YearMonth.now()
    val december = YearMonth.of(currentMonth.year + 1, 12)
    val january = december.plusMonths(1)
    var state by mutableStateOf(validDraft().copy(visibleMonth = currentMonth))
    var previousRequests = 0
    var nextRequests = 0
    showScreen(
        state = { state },
        onPreviousMonth = { previousRequests++ },
        onNextMonth = { nextRequests++ },
    )

    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(currentMonth.atDay(1))).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.PREVIOUS_MONTH).assertIsNotEnabled()
    composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).performClick()
    composeRule.runOnIdle {
      assertEquals(1, nextRequests)
      state = state.copy(visibleMonth = december)
    }
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(december.atDay(1))).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).performClick()
    composeRule.runOnIdle {
      assertEquals(2, nextRequests)
      state = state.copy(visibleMonth = january)
    }
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(january.atDay(1))).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.PREVIOUS_MONTH).performClick()
    composeRule.runOnIdle {
      assertEquals(1, previousRequests)
      state = state.copy(visibleMonth = december)
    }
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(december.atDay(1))).assertExists()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(january.atDay(1))).assertDoesNotExist()
  }

  @Test
  fun selectedDatesAcrossMonthsShowDateRangeAndInclusiveDuration() {
    val start = futureMonth.atEndOfMonth()
    val end = futureMonth.plusMonths(1).atDay(2)
    val state = validDraft().copy(startDate = start, endDate = end)
    showScreen(state = { state })

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
  fun dateAndFlexiblePreferenceSelectionsAreForwardedWhileRangeIsIncomplete() {
    val selectedDates = mutableListOf<LocalDate>()
    val flexiblePreferences = mutableListOf<Boolean>()
    val start = futureMonth.atDay(8)
    val state = validDraft().copy(startDate = start, endDate = start, isSelectingEndDate = true)
    showScreen(
        state = { state },
        onSelectDate = selectedDates::add,
        onFlexibleDatesChange = flexiblePreferences::add,
    )

    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(start)).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(futureMonth.atDay(12))).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(futureMonth.atDay(4))).performClick()
    composeRule.onNodeWithTag(DateSummaryTestTags.FLEXIBLE_TOGGLE).performClick()

    composeRule.runOnIdle {
      assertEquals(listOf(start, futureMonth.atDay(12), futureMonth.atDay(4)), selectedDates)
      assertEquals(listOf(true), flexiblePreferences)
    }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun travelerAndModeActionsAreForwardedAndControlsReflectBounds() {
    var state by mutableStateOf(validDraft())
    val selectedModes = mutableListOf<TravelMode>()
    var incrementRequests = 0
    var decrementRequests = 0
    showScreen(
        state = { state },
        onTravelModeChange = selectedModes::add,
        onIncrementTravelers = { incrementRequests++ },
        onDecrementTravelers = { decrementRequests++ },
    )

    composeRule.onNodeWithTag(TravelersCounterTestTags.INCREMENT).performClick()
    composeRule.onNodeWithTag(TravelersCounterTestTags.DECREMENT).performClick()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.SOLO).performClick()
    composeRule.runOnIdle {
      assertEquals(1, incrementRequests)
      assertEquals(1, decrementRequests)
      assertEquals(listOf(TravelMode.SOLO), selectedModes)
      state = state.copy(travelMode = TravelMode.SOLO, travelerCount = 1)
    }
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertDoesNotExist()
    composeRule.onNodeWithTag(TravelersCounterTestTags.DECREMENT).assertDoesNotExist()
    composeRule.onNodeWithTag(TravelersCounterTestTags.INCREMENT).assertDoesNotExist()

    composeRule.onNodeWithTag(TripTypeSelectorTestTags.GROUP).performClick()
    composeRule.runOnIdle {
      assertEquals(listOf(TravelMode.SOLO, TravelMode.GROUP), selectedModes)
      state = state.copy(travelMode = TravelMode.GROUP, travelerCount = 10)
    }
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("10")
    composeRule
        .onNodeWithTag(TravelersCounterTestTags.INCREMENT)
        .assertIsNotEnabled()
        .performClick()
    composeRule.runOnIdle {
      assertEquals(1, incrementRequests)
      state = state.copy(travelerCount = 2)
    }
    composeRule.onNodeWithTag(TravelersCounterTestTags.COUNT).assertTextEquals("2")
    composeRule
        .onNodeWithTag(TravelersCounterTestTags.DECREMENT)
        .assertIsNotEnabled()
        .performClick()
    composeRule.runOnIdle { assertEquals(1, decrementRequests) }
  }

  @Test
  fun pastCalendarDaysAreDisabledAndDoNotEmitSelection() {
    val pastMonth = YearMonth.now().minusMonths(1)
    val selectedDates = mutableListOf<LocalDate>()
    showScreen(
        state = { validDraft().copy(visibleMonth = pastMonth) },
        onSelectDate = selectedDates::add,
    )

    composeRule
        .onNodeWithTag(TripCalendarTestTags.dayFor(pastMonth.atDay(1)))
        .assertIsNotEnabled()
        .performClick()

    composeRule.runOnIdle { assertTrue(selectedDates.isEmpty()) }
  }

  @Test
  fun clickingCreateTripEmitsCallbackAndKeepsTheActionEnabled() {
    var createRequests = 0
    showScreen(onCreateTrip = { createRequests++ })

    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertIsEnabled()
        .performClick()

    composeRule.runOnIdle { assertEquals(1, createRequests) }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsEnabled()
  }

  private fun showScreen(
      state: () -> CreateTripUiState = { validDraft() },
      modifier: Modifier = Modifier,
      onCreateTrip: () -> Unit = {},
      onPreviousMonth: () -> Unit = {},
      onNextMonth: () -> Unit = {},
      onSelectDate: (LocalDate) -> Unit = {},
      onFlexibleDatesChange: (Boolean) -> Unit = {},
      onTravelModeChange: (TravelMode) -> Unit = {},
      onDecrementTravelers: () -> Unit = {},
      onIncrementTravelers: () -> Unit = {},
  ) {
    composeRule.setContent {
      Box(modifier) {
        RoamigoTheme {
          DatesTravelersScreen(
              uiState = state(),
              onBack = {},
              onClose = {},
              onCreateTrip = onCreateTrip,
              onPreviousMonth = onPreviousMonth,
              onNextMonth = onNextMonth,
              onSelectDate = onSelectDate,
              onFlexibleDatesChange = onFlexibleDatesChange,
              onTravelModeChange = onTravelModeChange,
              onDecrementTravelers = onDecrementTravelers,
              onIncrementTravelers = onIncrementTravelers,
          )
        }
      }
    }
  }

  private fun validDraft() =
      CreateTripUiState(
          selectedDestinations = listOf(sampleDestination("Lisbon")),
          visibleMonth = futureMonth,
          startDate = futureMonth.atDay(5),
          endDate = futureMonth.atDay(8),
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
      )
}
