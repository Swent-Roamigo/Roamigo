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
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    val viewModel = CreateTripViewModel()
    viewModel.toggleDestination("Lisbon")
    showScreen(viewModel)

    composeRule.onNodeWithTag(C.Tag.c2_screen).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_month_previous).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_month_next).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_flexible_toggle).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_trip_type_solo).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_trip_type_group).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertTextEquals("3")
    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsEnabled()
  }

  @Test
  fun shortScreenCanScrollToTravelerControlsWhileKeepingCreateActionVisible() {
    val viewModel = CreateTripViewModel()
    viewModel.toggleDestination("Lisbon")
    composeRule.setContent {
      Box(Modifier.size(width = 390.dp, height = 480.dp)) {
        RoamigoTheme {
          DatesTravelersScreen(
              createTripViewModel = viewModel,
              onBack = {},
              onClose = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsDisplayed().assertIsEnabled()
    composeRule
        .onNodeWithTag(C.Tag.c2_screen)
        .performScrollToNode(hasTestTag(C.Tag.c2_traveler_count))
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsDisplayed()
  }

  @Test
  fun monthNavigationMovesAcrossYearBoundaryAndRestoresTheOriginalMonth() {
    val viewModel = CreateTripViewModel()
    showScreen(viewModel)
    val currentMonth = YearMonth.now()

    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + currentMonth.atDay(1)).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_month_previous).performClick()
    composeRule
        .onNodeWithTag(C.Tag.c2_calendar_day_prefix + currentMonth.minusMonths(1).atDay(1))
        .assertExists()
    composeRule
        .onNodeWithTag(C.Tag.c2_calendar_day_prefix + currentMonth.atDay(1))
        .assertDoesNotExist()

    composeRule.onNodeWithTag(C.Tag.c2_month_next).performClick()
    val monthsToNextJanuary = 13 - currentMonth.monthValue
    repeat(monthsToNextJanuary) { composeRule.onNodeWithTag(C.Tag.c2_month_next).performClick() }
    val nextJanuary = currentMonth.plusMonths(monthsToNextJanuary.toLong())
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + nextJanuary.atDay(1)).assertExists()
    repeat(monthsToNextJanuary) {
      composeRule.onNodeWithTag(C.Tag.c2_month_previous).performClick()
    }
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + currentMonth.atDay(1)).assertExists()
  }

  @Test
  fun selectedDatesCanSpanMonthsAndProduceInclusiveDuration() {
    val viewModel = CreateTripViewModel()
    showScreen(viewModel)
    val start = YearMonth.now().atEndOfMonth()
    val end = YearMonth.from(start).plusMonths(1).atDay(2)

    viewModel.toggleDestination("Lisbon")
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + start).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_month_next).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + end).performClick()

    composeRule.runOnIdle {
      assertEquals(start, viewModel.uiState.value.startDate)
      assertEquals(end, viewModel.uiState.value.endDate)
      assertEquals(3L, viewModel.uiState.value.tripDays)
      assertEquals(2L, viewModel.uiState.value.tripNights)
    }
    val context = ApplicationProvider.getApplicationContext<Context>()
    val locale = context.resources.configuration.locales[0]
    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
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
    composeRule.onNodeWithTag(C.Tag.c2_date_range).assertTextEquals(expectedDateRange)
    composeRule.onNodeWithTag(C.Tag.c2_duration_summary).assertTextEquals(expectedDuration)
    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsEnabled()
  }

  @Test
  fun selectingEarlierDateRestartsRangeAndFlexiblePreferenceIsRetained() {
    val viewModel = CreateTripViewModel()
    viewModel.toggleDestination("Lisbon")
    showScreen(viewModel)
    val month = YearMonth.now()

    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + month.atDay(8)).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + month.atDay(12)).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + month.atDay(4)).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_flexible_toggle).performClick()

    composeRule.runOnIdle {
      assertEquals(month.atDay(4), viewModel.uiState.value.startDate)
      assertEquals(month.atDay(4), viewModel.uiState.value.endDate)
      assertTrue(viewModel.uiState.value.isSelectingEndDate)
      assertTrue(viewModel.uiState.value.flexibleDates)
    }
    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsNotEnabled()
  }

  @Test
  fun soloAndGroupSelectionRespectTravelerBounds() {
    val viewModel = CreateTripViewModel()
    showScreen(viewModel)

    composeRule.onNodeWithTag(C.Tag.c2_trip_type_solo).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.c2_travelers_decrement).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.c2_travelers_increment).assertDoesNotExist()

    composeRule.onNodeWithTag(C.Tag.c2_trip_type_group).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertExists()
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertTextEquals("3")
    repeat(10) { composeRule.onNodeWithTag(C.Tag.c2_travelers_increment).performClick() }
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertTextEquals("10")
    composeRule.onNodeWithTag(C.Tag.c2_travelers_increment).assertIsNotEnabled()
    repeat(10) { composeRule.onNodeWithTag(C.Tag.c2_travelers_decrement).performClick() }
    composeRule.onNodeWithTag(C.Tag.c2_traveler_count).assertTextEquals("2")
    composeRule.onNodeWithTag(C.Tag.c2_travelers_decrement).assertIsNotEnabled()

    composeRule.runOnIdle {
      assertEquals(TripType.GROUP, viewModel.uiState.value.tripType)
      assertEquals(2, viewModel.uiState.value.travelerCount)
    }
  }

  @Test
  fun clickingCreateTripLeavesTheDraftUnchanged() {
    val viewModel = CreateTripViewModel()
    showScreen(viewModel)
    selectTwoDatesInVisibleMonth(viewModel)
    val draftBeforeClick = viewModel.uiState.value

    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsEnabled().performClick()

    composeRule.runOnIdle { assertEquals(draftBeforeClick, viewModel.uiState.value) }
    composeRule.onNodeWithTag(C.Tag.c2_create_trip).assertIsEnabled()
  }

  private fun showScreen(viewModel: CreateTripViewModel) {
    composeRule.setContent {
      RoamigoTheme {
        DatesTravelersScreen(
            createTripViewModel = viewModel,
            onBack = {},
            onClose = {},
        )
      }
    }
  }

  private fun selectTwoDatesInVisibleMonth(
      viewModel: CreateTripViewModel,
  ) {
    val month = viewModel.uiState.value.visibleMonth
    val start = month.atDay(5)
    val end = month.atDay(8)
    viewModel.toggleDestination("Lisbon")
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + start).performClick()
    composeRule.onNodeWithTag(C.Tag.c2_calendar_day_prefix + end).performClick()
  }
}
