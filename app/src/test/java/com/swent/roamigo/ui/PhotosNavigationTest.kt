// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotosNavigationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val tripId = "trip #1"
  private val tripName = "Lisbon & Porto / 2026?"
  private val connectivity = FakeConnectivityRepository()
  private lateinit var navController: NavHostController

  private fun openPhotos() {
    val now = Instant.now().epochSecond
    val trip =
        Trip(
            tripId,
            tripName,
            null,
            listOf(TripLocation(38.7, -9.1, "Lisbon")),
            Timestamp(now - 3600, 0),
            Timestamp(now + 86400, 0),
            false,
            TravelMode.GROUP,
            2,
            "owner",
            listOf("owner", "member"),
            TripStatus.ACTIVE,
            Timestamp(now - 7200, 0),
        )
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(emptyList()),
            destinationRepository = sampleDestinationRepository(),
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("member", "Member", null, null),
                    initiallySignedIn = true,
                ),
            photoRepository = FakePhotoRepository(),
            connectivityRepository = connectivity,
            navController = navController,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    // A trip opens its details; Photos is reached from its Votes tab.
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()
  }

  @Test
  fun thePhotosTabOfATripOpensItsPhotos() {
    openPhotos()

    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photos_title).assertTextEquals("Trip photos")
    composeRule.runOnIdle {
      assertEquals(Route.PHOTOS, navController.currentBackStackEntry?.destination?.route)
      assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getString("tripId"))
      assertEquals(tripName, navController.currentBackStackEntry?.arguments?.getString("tripName"))
    }
  }

  @Test
  fun followsTheConnectionInjectedIntoTheApp() {
    connectivity.online.value = false
    openPhotos()

    composeRule.onNodeWithTag(C.Tag.photos_banner).assertIsDisplayed()
  }

  @Test
  fun theTripsTabGoesBackToTheTrips() {
    openPhotos()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun buildsARouteThatKeepsSpecialCharacters() {
    assertEquals(
        "trip/a%20b%2Fc/photos?tripName=x%26y%3F",
        Route.photos("a b/c", "x&y?"),
    )
  }
}
