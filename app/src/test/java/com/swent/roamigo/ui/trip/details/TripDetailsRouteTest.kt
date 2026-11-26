// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.weather.DailyForecast
import com.swent.roamigo.model.weather.FakeWeatherRepository
import com.swent.roamigo.model.weather.WeatherCondition
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDetailsRouteTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun loadsTheSelectedTripAndForwardsActionsWithoutANavHost() {
    val actions = mutableListOf<String>()
    val votedTripNames = mutableListOf<String>()
    val repository = FakeTripRepository(trips = listOf(detailsTestTrip()))
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsRoute(
            tripId = "trip",
            tripRepository = repository,
            onBack = {},
            onMembersClick = { actions += "invite" },
            onVotesClick = { tripName ->
              actions += "votes"
              votedTripNames += tripName
            },
            onTripMapClick = { actions += "tripMap" },
            onPhotosClick = { actions += "photos" },
        )
      }
    }
    composeRule.onNodeWithText("Members").performScrollTo().performClick()
    composeRule.onNodeWithText("Live votes").performScrollTo().performClick()
    composeRule.onNodeWithText("Open trip map").performClick()
    composeRule.onNodeWithText("Trip photos").performScrollTo().performClick()
    assertEquals(
        listOf("invite", "votes", "tripMap", "photos"),
        actions,
    )
    assertEquals(listOf("Lisbon & Porto"), votedTripNames)
  }

  @Test
  fun backIsForwardedAndRetryReloadsThroughTheViewModel() {
    var fails = true
    var backed = false
    val votedTripNames = mutableListOf<String>()
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            if (fails) error("Unavailable")
            return listOf(detailsTestTrip())
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsRoute(
            "trip",
            repository,
            onBack = { backed = true },
            onVotesClick = { votedTripNames += it },
            onTripMapClick = {},
        )
      }
    }
    composeRule.onNodeWithText("Unable to load trip details.").assertIsDisplayed()
    composeRule.onNodeWithText("Back").performClick()
    assertTrue(backed)
    composeRule.runOnIdle { fails = false }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Live votes").performScrollTo().performClick()
    assertEquals(listOf("Lisbon & Porto"), votedTripNames)
  }

  /** A trip that starts tomorrow and lasts three days, so the forecast window covers it. */
  private fun upcomingTrip(destinations: List<TripLocation>): Trip {
    val start = Instant.now().plus(1, ChronoUnit.DAYS)
    return detailsTestTrip()
        .copy(
            destinations = destinations,
            startDate = Timestamp(start.epochSecond, 0),
            endDate = Timestamp(start.plus(3, ChronoUnit.DAYS).epochSecond, 0),
        )
  }

  private fun showDetails(trip: Trip, weather: FakeWeatherRepository?) {
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsRoute(
            tripId = "trip",
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            onBack = {},
            onVotesClick = {},
            onTripMapClick = {},
            weatherRepository = weather,
        )
      }
    }
  }

  private fun forecastFor(trip: Trip) =
      FakeWeatherRepository(
          listOf(
              DailyForecast(
                  Instant.ofEpochSecond(trip.startDate.seconds)
                      .atZone(ZoneId.systemDefault())
                      .toLocalDate(),
                  WeatherCondition.SUNNY,
                  16,
                  10,
                  5,
              )
          )
      )

  @Test
  fun showsTheForecastOfTheFirstDestination() {
    val trip = upcomingTrip(listOf(TripLocation(38.72, -9.14, "Lisbon", "PT")))
    val weather = forecastFor(trip)

    showDetails(trip, weather)

    composeRule.onNodeWithTag(C.Tag.weather_card).assertIsDisplayed()
    composeRule.onNodeWithText("Weather in Lisbon").assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(1, weather.requests.size) }
  }

  @Test
  fun showsNoForecastWithoutAWeatherRepository() {
    showDetails(upcomingTrip(listOf(TripLocation(38.72, -9.14, "Lisbon", "PT"))), weather = null)

    composeRule.onNodeWithTag(C.Tag.trip_details_invite).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.weather_card).assertDoesNotExist()
  }

  @Test
  fun showsNoForecastForATripWithoutADestination() {
    val trip = upcomingTrip(emptyList())
    val weather = forecastFor(trip)

    showDetails(trip, weather)

    composeRule.onNodeWithTag(C.Tag.trip_details_invite).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.weather_card).assertDoesNotExist()
    composeRule.runOnIdle { assertTrue(weather.requests.isEmpty()) }
  }
}
