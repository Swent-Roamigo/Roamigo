// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the "Flow map" of the Figma navigation check: every screen reachable from Trips home, and
 * Back from each one returning to the screen the map names.
 */
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val repository = FakeTripRepository(currentUserId = "alex")

  @Before
  fun setUp() {
    runBlocking {
      val now = Instant.now()
      repository.createTrip(
          "trip-1",
          TripDraft(
              name = "Lisbon",
              destinations = listOf(TripLocation(38.7, -9.1, "Lisbon")),
              startDate = now.minus(1, ChronoUnit.DAYS),
              endDate = now.plus(3, ChronoUnit.DAYS),
              flexibleDates = false,
              travelMode = TravelMode.SOLO,
              travelerCount = 1,
          ),
      )
    }
    val credentialManager =
        object : CredentialManager by CredentialManager.create(composeRule.activity) {
          override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {}
        }
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = true,
                ),
            tripRepository = repository,
            credentialManager = credentialManager,
            locationRepository = FakeLocationRepository(),
        )
      }
    }
    waitForTag(C.Tag.trips_main_trip_card)
  }

  private fun waitForTag(tag: String) {
    composeRule.waitUntil { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun pressBack() {
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
  }

  private fun assertOnTripsHome() {
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun backFromEveryTripTabReturnsToTripsHomeNotThePreviousTab() {
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    waitForTag("trip_details_screen")
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Photos").performClick()

    pressBack()

    assertOnTripsHome()
  }

  @Test
  fun tripsStaysHighlightedInsideATrip() {
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    waitForTag("trip_details_screen")

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsSelected()
  }

  @Test
  fun everyScreenOnTheFlowMapIsReachableFromTripsHomeAndBackReturnsThere() {
    // Profile.
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_title)
    pressBack()
    assertOnTripsHome()

    // History.
    composeRule.onNodeWithTag(C.Tag.trips_history_button).performClick()
    waitForTag(C.Tag.trip_history_screen)
    pressBack()
    assertOnTripsHome()

    // Friends, a bottom-bar tab: Back leaves it for Trips home.
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    waitForTag(C.Tag.friends_screen)
    pressBack()
    assertOnTripsHome()

    // New trip form.
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    waitForTag(com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags.CLOSE)
    pressBack()
    assertOnTripsHome()

    // A trip.
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    waitForTag("trip_details_screen")
    pressBack()
    assertOnTripsHome()

    // Back from Trips home exits the app: it is the only start screen.
    pressBack()
    assertTrue(composeRule.activity.isFinishing)
  }
}
