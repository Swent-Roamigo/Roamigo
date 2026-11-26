// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    chip().assertContentDescriptionEquals("Monday: Sunny, high of 16 degrees")
  }

  @Test
  fun saysTodayWhileTheTripIsInProgress() {
    show(WeatherChipUiModel(LocalDate.of(2026, 10, 8), true, WeatherCondition.RAIN, 12))

    chip().assertContentDescriptionEquals("Today: Rain, high of 12 degrees")
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

  @Test
  fun aHighOfOneDegreeIsReadInTheSingular() {
    show(WeatherChipUiModel(LocalDate.of(2026, 10, 12), false, WeatherCondition.SNOW, 1))

    chip().assertContentDescriptionEquals("Monday: Snow, high of 1 degree")
  }

  private fun showNarrow(widthDp: Int, fontScale: Float, onInvite: () -> Unit) {
    rule.setContent {
      CompositionLocalProvider(
          LocalDensity provides Density(LocalDensity.current.density, fontScale)
      ) {
        MaterialTheme {
          Box(Modifier.requiredWidth(widthDp.dp)) {
            MainTripCard(
                trip.copy(memberInitials = listOf("A", "B", "C", "D", "E")),
                null,
                null,
                onInvite,
                {},
                weather =
                    WeatherChipUiModel(
                        LocalDate.of(2026, 10, 12),
                        false,
                        WeatherCondition.SUNNY,
                        16,
                    ),
            )
          }
        }
      }
    }
  }

  @Test
  fun theChipSharesTheRowWithTheInviteButtonWhenThereIsRoom() {
    var invited = 0
    showNarrow(widthDp = 411, fontScale = 1f) { invited++ }

    chip().assertIsDisplayed()
    rule.onNodeWithTag(C.Tag.trips_invite_button).performClick()

    assertEquals(1, invited)
  }

  @Test
  fun theChipStepsAsideInsteadOfCoveringTheInviteButtonOnANarrowScreenWithALargeFont() {
    var invited = 0
    showNarrow(widthDp = 320, fontScale = 1.5f) { invited++ }

    // Left out of the layout, so it takes no room and covers nothing.
    chip().assertIsNotDisplayed()
    rule.onNodeWithTag(C.Tag.trips_invite_button).performClick()

    assertEquals(1, invited)
  }
}
