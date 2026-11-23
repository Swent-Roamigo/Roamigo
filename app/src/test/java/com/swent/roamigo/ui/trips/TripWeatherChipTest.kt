// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.weather.WeatherCondition
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.trips.components.MainTripCard
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripWeatherChipTest {
  @get:Rule val rule = createComposeRule()

  private val trip =
      MainTripUiModel("trip", "Lisbon", "12 – 19 Oct", 8, 2, emptyList(), MainTripBadge.StartsToday)

  private fun show(weather: WeatherChipUiModel?, onOpen: () -> Unit = {}) {
    rule.setContent {
      MaterialTheme { MainTripCard(trip, null, null, {}, onOpen, weather = weather) }
    }
  }

  private fun chip() = rule.onNodeWithTag(C.Tag.weather_chip, useUnmergedTree = true)

  @Test
  fun showsTheWeekdayAndTheHighOfTheFirstTripDay() {
    show(WeatherChipUiModel(LocalDate.of(2026, 10, 12), false, WeatherCondition.SUNNY, 16))

    chip().assertIsDisplayed()
    chip().assertContentDescriptionEquals("Monday: Sunny, 16 degrees")
  }

  @Test
  fun saysTodayWhileTheTripIsInProgress() {
    show(WeatherChipUiModel(LocalDate.of(2026, 10, 8), true, WeatherCondition.RAIN, 12))

    chip().assertContentDescriptionEquals("Today: Rain, 12 degrees")
  }

  @Test
  fun showsNoChipWithoutAForecast() {
    show(null)

    rule.onNodeWithTag(C.Tag.trips_main_trip_card).assertIsDisplayed()
    chip().assertDoesNotExist()
  }

  @Test
  fun theCardStillOpensTheTrip() {
    var opened = 0
    show(WeatherChipUiModel(LocalDate.of(2026, 10, 12), false, WeatherCondition.SUNNY, 16)) {
      opened++
    }

    rule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()

    assertEquals(1, opened)
  }
}
