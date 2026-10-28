// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.ui.theme.RoamigoTheme
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
    val repository = FakeTripRepository(trips = listOf(detailsTestTrip()))
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsRoute(
            tripId = "trip",
            tripRepository = repository,
            onBack = {},
            onTripsClick = { actions += "trips" },
            onShareClick = { actions += "share" },
            onInviteClick = { actions += "invite" },
            onItineraryClick = { actions += "itinerary" },
            onVotesClick = { actions += "votes" },
            onMapClick = { actions += "map" },
            onPhotosClick = { actions += "photos" },
        )
      }
    }
    composeRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Share trip").performClick()
    composeRule.onNodeWithText("Invite").performClick()
    composeRule.onNodeWithText("Itinerary").performClick()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Open trip map").performClick()
    composeRule.onNodeWithText("Trips").performClick()
    composeRule.onNodeWithText("Photos").performClick()
    assertEquals(listOf("share", "invite", "itinerary", "votes", "map", "trips", "photos"), actions)
  }

  @Test
  fun backIsForwardedAndRetryReloadsThroughTheViewModel() {
    var fails = true
    var backed = false
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            if (fails) error("Unavailable")
            return listOf(detailsTestTrip())
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsRoute("trip", repository, onBack = { backed = true }, onTripsClick = {})
      }
    }
    composeRule.onNodeWithText("Unable to load trip details.").assertIsDisplayed()
    composeRule.onNodeWithText("Back").performClick()
    assertTrue(backed)
    composeRule.runOnIdle { fails = false }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()
  }
}
