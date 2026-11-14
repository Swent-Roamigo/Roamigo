// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.inviting.InvitationPreview
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class RoamigoAppInvitationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val token = "AbCdEfGh_ijkLMNo-pQrSt"
  private val service =
      FakeTripInvitationService(
          preview = InvitationPreview("trip-1", "Lisbon with friends", "Sam", false)
      )
  private lateinit var navController: NavHostController

  private fun start(signedIn: Boolean) {
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = signedIn,
                ),
            navController = navController,
            locationRepository = FakeLocationRepository(),
            invitationService = service,
        )
      }
    }
  }

  private fun route() = composeRule.runOnUiThread { navController.currentDestination?.route }

  @Test
  fun anInvitationLinkOpensTheJoinScreenAndJoiningOpensTheTrip() {
    start(signedIn = true)

    composeRule.runOnUiThread { navController.navigate(Route.join(token)) }
    composeRule.waitUntil(5_000) { service.previewed.isNotEmpty() }
    composeRule.onNodeWithText("Lisbon with friends").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.join_button).performClick()
    composeRule.waitUntil(5_000) { route() == Route.TRIP_DETAILS }

    assertEquals(listOf(token), service.accepted)
  }

  @Test
  fun leavingTheJoinScreenReturnsToTrips() {
    start(signedIn = true)
    composeRule.runOnUiThread { navController.navigate(Route.join(token)) }
    composeRule.waitUntil(5_000) { service.previewed.isNotEmpty() }

    composeRule.onNodeWithTag(C.Tag.join_back).performClick()

    composeRule.waitUntil(5_000) { route() == Route.TRIPS }
  }

  @Test
  fun anInvitationOpenedWhileSignedOutAsksToSignInFirstAndJoinsNothing() {
    start(signedIn = false)

    composeRule.runOnUiThread { navController.navigate(Route.join(token)) }
    composeRule.waitUntil(5_000) { route() == Route.AUTHENTICATION }

    assertTrue(service.previewed.isEmpty())
    assertTrue(service.accepted.isEmpty())
  }

  @Test
  fun aCodeEnteredOnTheTripsScreenOpensTheJoinScreen() {
    start(signedIn = true)
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(C.Tag.trips_join_button).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(C.Tag.trips_join_button).performClick()
    composeRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("roamigo://invite/$token")
    composeRule.onNodeWithTag(C.Tag.join_code_submit).performClick()
    composeRule.waitUntil(5_000) { service.previewed == listOf(token) }

    assertEquals(Route.JOIN, route())
  }

  @Test
  fun inviteOnTripsHomeOpensTheInviteSheetForTheMainTrip() {
    val now = java.time.Instant.now()
    val timestamp = { instant: java.time.Instant ->
      com.google.firebase.Timestamp(instant.epochSecond, 0)
    }
    val trip =
        com.swent.roamigo.model.trip.Trip(
            uid = "main-trip",
            name = "Lisbon",
            description = null,
            destinations = listOf(com.swent.roamigo.model.TripLocation(38.72, -9.14, "Lisbon")),
            startDate = timestamp(now.minusSeconds(86_400)),
            endDate = timestamp(now.plusSeconds(3 * 86_400)),
            flexibleDates = false,
            travelMode = com.swent.roamigo.model.trip.TravelMode.GROUP,
            travelerCount = 2,
            ownerId = "alex",
            memberIds = listOf("alex"),
            status = com.swent.roamigo.model.trip.TripStatus.ACTIVE,
            createdAt = timestamp(now),
        )
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            tripRepository =
                com.swent.roamigo.model.trip.FakeTripRepository(
                    trips = listOf(trip),
                    membersByTripId =
                        mapOf(
                            "main-trip" to
                                listOf(
                                    com.swent.roamigo.model.trip.TripMember(
                                        "alex",
                                        com.swent.roamigo.model.trip.TripMemberRole.OWNER,
                                        timestamp(now),
                                    )
                                )
                        ),
                ),
            destinationRepository = sampleDestinationRepository(),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = true,
                ),
            navController = navController,
            locationRepository = FakeLocationRepository(),
            invitationService = service,
        )
      }
    }
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(C.Tag.trips_invite_button).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(C.Tag.trips_invite_button).performClick()
    composeRule.waitUntil(5_000) { service.createdFor.isNotEmpty() }

    assertEquals(listOf("main-trip"), service.createdFor)
    composeRule.onNodeWithTag(C.Tag.invite_sheet).assertIsDisplayed()
  }
}
