// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.details

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.weather.DailyForecast
import com.swent.roamigo.model.weather.WeatherCondition
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripForecastCardTest {
  @get:Rule val composeRule = createComposeRule()
  private var retries = 0
  private val fetchedAt = Instant.parse("2026-11-23T08:00:00Z")
  private val now = Instant.parse("2026-11-23T10:00:00Z")

  private val days =
      listOf(
          DailyForecast(LocalDate.of(2026, 11, 28), WeatherCondition.SUNNY, 16, 10, 5),
          DailyForecast(LocalDate.of(2026, 11, 29), WeatherCondition.PARTLY_CLOUDY, 15, 9, 20),
          DailyForecast(LocalDate.of(2026, 11, 30), WeatherCondition.RAIN, 13, 9, 80),
          DailyForecast(LocalDate.of(2026, 12, 1), WeatherCondition.RAIN, 14, 10, 65),
          DailyForecast(LocalDate.of(2026, 12, 2), WeatherCondition.CLOUDY, 12, 8, null),
          DailyForecast(LocalDate.of(2026, 12, 3), WeatherCondition.SNOW, -1, -6, 70),
      )

  private fun show(state: ForecastUiState) {
    composeRule.setContent {
      RoamigoTheme { TripForecastCard(state, city = "Lisbon", onRetry = { retries++ }, now = now) }
    }
  }

  private fun ready(fromCache: Boolean = false) = ForecastUiState.Ready(days, fetchedAt, fromCache)

  @Test
  fun showsEachDayWithItsConditionTemperaturesAndRainChance() {
    show(ready())

    composeRule.onNodeWithText("Weather in Lisbon").assertIsDisplayed()
    val saturday = composeRule.onNodeWithTag(C.Tag.weather_day_prefix + "2026-11-28")
    saturday.assertIsDisplayed()
    for (shown in listOf("SAT", "Nov 28", "Sunny", "16°", "5%")) {
      saturday.assert(hasText(shown))
    }
    composeRule.onNodeWithText("Partly cloudy").assertIsDisplayed()
  }

  @Test
  fun aScreenReaderIsToldWhichTemperatureIsTheHighAndWhichTheLow() {
    show(ready())

    // One description for both numbers, so the merged day does not read just "16°, 10°".
    composeRule
        .onNodeWithTag(C.Tag.weather_day_prefix + "2026-11-28")
        .assert(hasContentDescription("High 16°, low 10°"))
    composeRule
        .onNodeWithTag(C.Tag.weather_day_prefix + "2026-12-03")
        .assert(hasContentDescription("High -1°, low -6°"))
  }

  @Test
  fun namesTheDaysToPlanIndoorsForAndSaysWhereTheDataComesFrom() {
    show(ready())

    composeRule.onNodeWithText("Rain likely on Monday and Tuesday.").assertIsDisplayed()
    composeRule.onNodeWithText("Open-Meteo · updated 2 hours ago").assertIsDisplayed()
  }

  @Test
  fun saysWhenNoRainIsExpected() {
    show(ForecastUiState.Ready(days.take(2), fetchedAt, fromCache = false))

    composeRule.onNodeWithText("No rain expected.").assertIsDisplayed()
  }

  @Test
  fun leavesOutTheRainChanceOfADayThatHasNone() {
    show(ForecastUiState.Ready(listOf(days[4]), fetchedAt, fromCache = false))

    composeRule.onNodeWithText("12°").assertIsDisplayed()
    composeRule.onNodeWithText("Cloudy").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.weather_day_prefix + "2026-12-02").assertIsDisplayed()
    composeRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
  }

  @Test
  fun showsSnowAsSnow() {
    show(ForecastUiState.Ready(listOf(days[5]), fetchedAt, fromCache = false))

    composeRule.onNodeWithText("Snow").assertIsDisplayed()
    composeRule.onNodeWithText("-1°").assertIsDisplayed()
  }

  @Test
  fun aSavedForecastSaysItCouldNotBeRefreshedAndHowOldItIs() {
    show(ready(fromCache = true))

    composeRule.onNodeWithTag(C.Tag.weather_stale_notice).assertIsDisplayed()
    composeRule
        .onNodeWithText("Couldn't refresh the forecast. Showing the one saved 2 hours ago.")
        .assertIsDisplayed()
    composeRule.onNodeWithText("Open-Meteo · saved 2 hours ago").assertIsDisplayed()
  }

  @Test
  fun aFreshForecastShowsNoOfflineNotice() {
    show(ready())

    composeRule.onNodeWithTag(C.Tag.weather_stale_notice).assertDoesNotExist()
  }

  @Test
  fun aForecastFromJustNowSaysSo() {
    composeRule.setContent {
      RoamigoTheme {
        TripForecastCard(ready(), city = "Lisbon", now = fetchedAt.plusSeconds(20), onRetry = {})
      }
    }

    composeRule.onNodeWithText("Open-Meteo · updated just now").assertIsDisplayed()
  }

  @Test
  fun showsAPlaceholderWhileLoading() {
    show(ForecastUiState.Loading)

    composeRule.onNodeWithTag(C.Tag.weather_loading).assertIsDisplayed()
    composeRule.onNodeWithText("Loading the forecast…").assertIsDisplayed()
  }

  @Test
  fun explainsWhenTheForecastWillComeForATripTooFarAhead() {
    show(ForecastUiState.TooFar(LocalDate.of(2026, 12, 6)))

    composeRule.onNodeWithText("The forecast comes 14 days before your trip.").assertIsDisplayed()
    composeRule
        .onNodeWithText(
            "Check back on December 6. Until then, weather this far ahead would only be a guess."
        )
        .assertIsDisplayed()
  }

  @Test
  fun aFailureSaysWhatHappenedAndOffersARetry() {
    show(ForecastUiState.Failed)

    composeRule.onNodeWithText("The weather can't be loaded.").assertIsDisplayed()
    composeRule
        .onNodeWithText("No forecast is saved on this phone yet. Try again in a moment.")
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.weather_retry).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun showsNothingWhenThereIsNoForecastToShow() {
    show(ForecastUiState.Hidden)

    composeRule.onNodeWithTag(C.Tag.weather_card).assertDoesNotExist()
  }

  @Test
  fun aTripOfMoreThanFiveDaysScrollsAndEveryDayCanBeReached() {
    show(ready())

    composeRule
        .onNodeWithTag(C.Tag.weather_day_prefix + "2026-12-03")
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun eachDayIsOneItemForAScreenReader() {
    show(ready())

    val day = composeRule.onNodeWithTag(C.Tag.weather_day_prefix + "2026-11-30")

    // The merged node carries the whole day, so it is read in one go.
    day.assert(hasText("Rain", substring = true))
    day.assert(hasText("13°", substring = true))
    day.assert(hasText("80%", substring = true))
  }

  @Test
  fun aBusyServiceSaysSoAndOffersARetry() {
    show(ForecastUiState.Busy)

    composeRule.onNodeWithText("The weather service is busy.").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.weather_retry).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun listsThreeRainyDaysTheWayTheLocaleWritesAList() {
    val rainy = days.take(3).map { it.copy(condition = WeatherCondition.RAIN) }
    show(ForecastUiState.Ready(rainy, fetchedAt, fromCache = false))

    composeRule.onNodeWithText("Rain likely on Saturday, Sunday, and Monday.").assertIsDisplayed()
  }

  @Test
  fun aTripOfMoreThanAWeekNamesTheRainyDaysWithTheirDates() {
    val long =
        (0 until 9).map {
          val rain = it == 0 || it == 8
          DailyForecast(
              LocalDate.of(2026, 11, 28).plusDays(it.toLong()),
              if (rain) WeatherCondition.RAIN else WeatherCondition.SUNNY,
              9,
              5,
              if (rain) 80 else 0,
          )
        }
    show(ForecastUiState.Ready(long, fetchedAt, fromCache = false))

    composeRule.onNodeWithText("Rain likely on Sat, Nov 28 and Sun, Dec 6.").assertIsDisplayed()
  }
}
