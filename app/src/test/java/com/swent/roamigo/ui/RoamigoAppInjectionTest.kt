// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripCreationException
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.searchSampleDestinations
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that [RoamigoApp] uses the repositories and nav controller it is given. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppInjectionTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private fun timestamp(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)

  @Test
  fun usesTheProvidedNavControllerAndRepositories() {
    val now = Instant.now()
    val trip =
        Trip(
            uid = "t1",
            name = "Injected Escape",
            description = null,
            destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon")),
            startDate = timestamp(now.minusSeconds(3600)),
            endDate = timestamp(now.plusSeconds(86_400)),
            flexibleDates = false,
            travelMode = TravelMode.SOLO,
            travelerCount = 1,
            ownerId = "sam",
            memberIds = listOf("sam"),
            status = TripStatus.ACTIVE,
            createdAt = timestamp(now.minusSeconds(7200)),
        )
    lateinit var navController: NavHostController
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            destinationRepository = sampleDestinationRepository(),
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            userRepository = FakeUserRepository(User("sam", "Sam", null)),
            navController = navController,
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("sam", "Sam", null, null),
                    initiallySignedIn = true,
                ),
        )
      }
    }

    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule
        .onNodeWithTag(C.Tag.trips_main_trip_card)
        .assert(hasAnyDescendant(hasText(trip.name, substring = true)))
    assertEquals(Route.TRIPS, navController.currentBackStackEntry?.destination?.route)
  }

  @Test
  fun pendingSaveDisablesExitAndSystemBackUntilCompletion() {
    val gate = CompletableDeferred<Unit>()
    val delegate = FakeTripRepository(currentUserId = "alex")
    val repository =
        object : TripRepository by delegate {
          override suspend fun createTrip(tripId: String, draft: TripDraft): Trip {
            gate.await()
            return delegate.createTrip(tripId, draft)
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            destinationRepository = sampleDestinationRepository(),
            tripRepository = repository,
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = true,
                ),
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_new_trip_button).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.searchSampleDestinations()
    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(sampleDestination("Lisbon").stableId))
        .performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.BACK).assertIsNotEnabled()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.CLOSE).assertIsNotEnabled()
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
    gate.complete(Unit)
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun networkFailureOffersRetryAndReturnsToTripsAfterSuccess() {
    val delegate = FakeTripRepository(currentUserId = "alex")
    var fail = true
    val repository =
        object : TripRepository by delegate {
          override suspend fun createTrip(tripId: String, draft: TripDraft): Trip {
            if (fail) throw TripCreationException.Network()
            return delegate.createTrip(tripId, draft)
          }
        }
    openCreation(repository)
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextContains(composeRule.activity.getString(R.string.create_trip_retry))
    fail = false
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun expiredAuthenticationOffersSignInAndRemovesCreationBackStack() {
    lateinit var navController: NavHostController
    val auth =
        FakeAuthRepository(
            AuthenticatedUser("alex", "Alex", null, null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            destinationRepository = sampleDestinationRepository(),
            tripRepository = FakeTripRepository(currentUserId = null),
            authRepository = auth,
            navController = navController,
        )
      }
    }
    enterDatesStep()
    auth.signOut()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextContains(composeRule.activity.getString(R.string.create_trip_sign_in))
        .performClick()
    composeRule.runOnIdle {
      assertEquals(Route.AUTHENTICATION, navController.currentBackStackEntry?.destination?.route)
      assertEquals(null, navController.previousBackStackEntry)
    }
  }

  private fun openCreation(repository: TripRepository) {
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            destinationRepository = sampleDestinationRepository(),
            tripRepository = repository,
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = true,
                ),
        )
      }
    }
    enterDatesStep()
  }

  private fun enterDatesStep() {
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_new_trip_button).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.searchSampleDestinations()
    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(sampleDestination("Lisbon").stableId))
        .performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
  }
}
