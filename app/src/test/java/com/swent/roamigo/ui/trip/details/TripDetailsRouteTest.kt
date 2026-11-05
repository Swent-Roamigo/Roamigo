// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
}
