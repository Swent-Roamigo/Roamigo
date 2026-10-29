// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises map navigation through the real app graph with in-memory data and local map styles. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RoamigoAppMapNavigationTest {
  // Native annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private lateinit var navController: NavHostController
  private val now = Instant.now().epochSecond
  private val mainTrip =
      Trip(
          uid = "paris-trip",
          name = "Paris with friends",
          description = null,
          destinations = listOf(TripLocation(48.8566, 2.3522, "Paris")),
          startDate = Timestamp(now - 86_400, 0),
          endDate = Timestamp(now + 604_800, 0),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
          ownerId = "alex",
          memberIds = listOf("alex"),
          status = TripStatus.ACTIVE,
          createdAt = Timestamp(0, 0),
      )
  private val otherTrip =
      mainTrip.copy(
          uid = "lyon",
          name = "Lyon & Alps",
          destinations = listOf(TripLocation(45.764, 4.8357, "Lyon")),
          startDate = Timestamp(now + 1_728_000, 0),
          endDate = Timestamp(now + 1_987_200, 0),
          status = TripStatus.PLANNED,
      )

  @Before
  fun setUp() {
    val destinationRepository =
        object : DestinationRepository {
          override suspend fun searchDestinations(query: String): List<Destination> = emptyList()

          override suspend fun findPhoto(destination: Destination): DestinationPhoto? = null
        }
    val authRepository =
        FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)
    val tripRepository = FakeTripRepository(trips = listOf(mainTrip, otherTrip))
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            tripRepository = tripRepository,
            authRepository = authRepository,
            destinationRepository = destinationRepository,
            navController = navController,
        )
      }
    }
    waitForTag(C.Tag.trips_main_trip_card)
  }

  @Test
  fun mapTabFromTripsOpensMapAndBackReturnsToTrips() {
    assertMapOpensAndBackReturnsTo(Route.TRIPS)
  }

  @Test
  fun mapTabFromFriendsOpensMapAndBackReturnsToFriends() {
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    waitForTag(C.Tag.friends_screen)
    assertMapOpensAndBackReturnsTo(Route.FRIENDS)
  }

  @Test
  fun mapTabFromProfileOpensMapAndBackReturnsToProfile() {
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_title)
    assertMapOpensAndBackReturnsTo(Route.PROFILE)
  }

  @Test
  fun mapTabFromHistoryOpensMapAndBackReturnsToHistory() {
    composeRule.onNodeWithTag(C.Tag.trips_history_button).performClick()
    waitForTag(C.Tag.trip_history_screen)
    assertMapOpensAndBackReturnsTo(Route.TRIP_HISTORY)
  }

  @Test
  fun mapTabFromTripDetailsOpensMapAndBackReturnsToDetails() {
    openMainTripDetails()
    assertMapOpensAndBackReturnsTo(Route.TRIP_DETAILS)
  }

  @Test
  fun mapTabFromVotesOpensMapAndBackReturnsToVotes() {
    openMainTripDetails()
    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_votes))
        .performClick()
    waitForRoute(Route.VOTES)
    assertMapOpensAndBackReturnsTo(Route.VOTES)
  }

  @Test
  fun markerOpensTheSelectedTripsDetailsWithItsDecodedId() {
    openMap()
    val markerTag = TripMarkerTestTags.markerFor(otherTrip.toTripMarkerUiStates().single().id)
    waitForTag(markerTag)
    composeRule.onNodeWithTag(markerTag).performClick()

    waitForRoute(Route.TRIP_DETAILS)
    composeRule.onNodeWithText(otherTrip.name).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(
          otherTrip.uid,
          navController.currentBackStackEntry?.arguments?.getString("tripId"),
      )
    }
    pressBack()
    waitForRoute(Route.TRIP_OVERVIEW_MAP)
    composeRule.loadLocalMapStyle()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).assertIsSelected()
  }

  @Test
  fun selectedMapTabDoesNotAddAnotherMapToTheBackStack() {
    openMap()
    repeat(2) {
      composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
      waitForRoute(Route.TRIP_OVERVIEW_MAP)
      composeRule.loadLocalMapStyle()
    }

    pressBack()

    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun tripsTabOnMapReturnsToRootInsteadOfThePreviousTripDetails() {
    openMainTripDetails()
    openMap()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()

    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    pressBack()
    composeRule.waitUntil { composeRule.activity.isFinishing }
    assertTrue(composeRule.activity.isFinishing)
  }

  @Test
  fun friendsTabOnMapOpensFriendsAndBackRestoresTheMap() {
    openMap()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()

    waitForRoute(Route.FRIENDS)
    composeRule.onNodeWithTag(C.Tag.friends_screen).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).assertIsSelected()
    pressBack()
    waitForRoute(Route.TRIP_OVERVIEW_MAP)
    composeRule.loadLocalMapStyle()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).assertIsSelected()
  }

  private fun openMainTripDetails() {
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    waitForRoute(Route.TRIP_DETAILS)
    composeRule.onNodeWithText(mainTrip.name).assertIsDisplayed()
  }

  private fun openMap() {
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    waitForRoute(Route.TRIP_OVERVIEW_MAP)
    composeRule.loadLocalMapStyle()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).assertIsSelected()
  }

  private fun assertMapOpensAndBackReturnsTo(originRoute: String) {
    val originEntryId = composeRule.runOnIdle { navController.currentBackStackEntry?.id }
    openMap()
    pressBack()
    waitForRoute(originRoute)
    composeRule.runOnIdle { assertEquals(originEntryId, navController.currentBackStackEntry?.id) }
  }

  private fun pressBack() {
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
  }

  private fun waitForRoute(route: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.runOnIdle { navController.currentDestination?.route == route }
    }
    composeRule.waitForIdle()
  }

  private fun waitForTag(tag: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.waitForIdle()
  }
}
