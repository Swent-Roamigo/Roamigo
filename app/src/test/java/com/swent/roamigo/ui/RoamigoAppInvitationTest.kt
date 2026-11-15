// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.CustomCredential
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class RoamigoAppInvitationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val token = "AbCdEfGh_ijkLMNo-pQrSt"
  private val service = FakeTripInvitationService()
  private val account = AuthenticatedUser("alex", "Alex", null, null)
  private val auth = FakeAuthRepository(account)
  private lateinit var navController: NavHostController

  private fun start(signedIn: Boolean) {
    if (signedIn) runBlocking { auth.signInWithGoogle(CustomCredential("test", Bundle())) }
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = auth,
            navController = navController,
            locationRepository = FakeLocationRepository(),
            invitationService = service,
        )
      }
    }
  }

  private fun route() = composeRule.runOnUiThread { navController.currentDestination?.route }

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
