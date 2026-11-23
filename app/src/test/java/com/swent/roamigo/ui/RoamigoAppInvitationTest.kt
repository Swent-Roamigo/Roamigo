// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripMember
import com.swent.roamigo.model.trip.TripMemberRole
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.TripReadyTestTags
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every place that offers "Invite" opens the invite sheet for its own trip. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class RoamigoAppInvitationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val service = FakeTripInvitationService()
  // The app reads the real clock, so the trip must be running now.
  private val now = Instant.now()
  private lateinit var navController: NavHostController

  private fun timestamp(instant: Instant) = Timestamp(instant.epochSecond, 0)

  private val trip =
      Trip(
          uid = "main-trip",
          name = "Lisbon",
          description = null,
          destinations = listOf(TripLocation(38.72, -9.14, "Lisbon")),
          startDate = timestamp(now.minusSeconds(86_400)),
          endDate = timestamp(now.plusSeconds(3 * 86_400)),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 2,
          ownerId = "alex",
          memberIds = listOf("alex"),
          status = TripStatus.ACTIVE,
          createdAt = timestamp(now),
      )

  private fun start() {
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoAppWithFakes(
            voteRepository = InMemoryVoteRepository(),
            tripRepository =
                FakeTripRepository(
                    trips = listOf(trip),
                    membersByTripId =
                        mapOf(
                            trip.uid to
                                listOf(TripMember("alex", TripMemberRole.OWNER, timestamp(now)))
                        ),
                ),
            destinationRepository = sampleDestinationRepository(),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = true,
                ),
            friendRepository = FakeFriendRepository("alex"),
            navController = navController,
            locationRepository = FakeLocationRepository(),
            invitationService = service,
        )
      }
    }
  }

  private fun waitForTag(tag: String) =
      composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      }

  private fun assertSheetOpenedForTheTrip() {
    composeRule.waitUntil(5_000) { service.createdFor.isNotEmpty() }
    assertEquals(listOf(trip.uid), service.createdFor)
    composeRule.onNodeWithTag(C.Tag.invite_sheet).assertIsDisplayed()
  }

  @Test
  fun inviteOnTripsHomeOpensTheInviteSheetForTheMainTrip() {
    start()
    waitForTag(C.Tag.trips_invite_button)

    composeRule.onNodeWithTag(C.Tag.trips_invite_button).performClick()

    assertSheetOpenedForTheTrip()
  }

  @Test
  fun inviteOnTripReadyOpensTheInviteSheetForThatTrip() {
    start()
    composeRule.runOnUiThread { navController.navigate(Route.tripReady(trip.uid)) }
    waitForTag(TripReadyTestTags.INVITE_CARD)

    composeRule.onNodeWithTag(TripReadyTestTags.INVITE_CARD).performScrollTo().performClick()

    assertSheetOpenedForTheTrip()
  }

  @Test
  fun inviteOnThePlanTabOpensTheInviteSheetForThatTrip() {
    start()
    composeRule.runOnUiThread { navController.navigate(Route.tripDetails(trip.uid)) }
    waitForTag(C.Tag.trip_details_screen)

    composeRule.onNodeWithTag(C.Tag.trip_details_invite).performScrollTo().performClick()

    assertSheetOpenedForTheTrip()
  }
}
