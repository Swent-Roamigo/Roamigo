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
import androidx.compose.ui.test.performScrollTo
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
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.TripReadyTestTags
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  private val authRepository =
      FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)
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

  private val completedTrip =
      mainTrip.copy(
          uid = "rome-past",
          name = "Rome memories",
          destinations = listOf(TripLocation(41.9028, 12.4964, "Rome")),
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          status = TripStatus.COMPLETED,
      )

  private val locationRepository = TestLocationRepository()

  @Before
  fun setUp() {
    val destinationRepository =
        object : DestinationRepository {
          override suspend fun searchDestinations(query: String): List<Destination> = emptyList()

          override suspend fun findPhoto(destination: Destination): DestinationPhoto? = null
        }
    val tripRepository = FakeTripRepository(trips = listOf(mainTrip, otherTrip, completedTrip))
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            tripRepository = tripRepository,
            authRepository = authRepository,
            destinationRepository = destinationRepository,
            navController = navController,
            locationRepository = locationRepository,
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
  fun mapTabFromFriendsOpensMapAndBackReturnsToTrips() {
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    waitForTag(C.Tag.friends_screen)
    assertMapOpensAndBackReturnsTo(Route.TRIPS)
  }

  @Test
  fun mapTabFromProfileOpensMapAndBackReturnsToTrips() {
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_title)
    assertMapOpensAndBackReturnsTo(Route.TRIPS)
  }

  @Test
  fun mapTabFromHistoryOpensMapAndBackReturnsToTrips() {
    composeRule.onNodeWithTag(C.Tag.trips_history_button).performClick()
    waitForTag(C.Tag.trip_history_screen)
    assertMapOpensAndBackReturnsTo(Route.TRIPS)
  }

  @Test
  fun mapCardOpensThatTripsMapAndBackReturnsToTrips() {
    openMainTripDetails()

    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_map))
        .performClick()

    waitForRoute(Route.TRIP_DETAILS)
    composeRule.runOnIdle {
      assertEquals(
          mainTrip.uid,
          navController.currentBackStackEntry?.arguments?.getString("tripId"),
      )
    }
    waitForTag(C.Tag.trip_map_sharing_banner)
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_loading).fetchSemanticsNodes().isEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertDoesNotExist()

    pressBack()
    waitForRoute(Route.TRIPS)
  }

  @Test
  fun expiredSessionRedirectsTheTripMapToAuthentication() {
    openMainTripDetails()
    composeRule.runOnIdle { authRepository.signOut() }

    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_map))
        .performClick()

    waitForRoute(Route.AUTHENTICATION)
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertDoesNotExist()
    composeRule.runOnIdle { assertNull(navController.previousBackStackEntry) }
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
    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsSelected()
  }

  @Test
  fun sharingCanBePausedAfterSwitchingFromMapToVotes() {
    openMainTripDetails()
    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_map))
        .performClick()
    waitForTag(C.Tag.trip_map_sharing_banner)
    val pause = composeRule.activity.getString(R.string.trip_map_pause_sharing)
    composeRule.runOnIdle { locationRepository.sharing.value = true }
    composeRule.onNodeWithText(pause).assertIsDisplayed()

    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText(pause).performClick()

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      locationRepository.stoppedTrip.value == mainTrip.uid
    }
    composeRule.onNodeWithText(pause).assertDoesNotExist()
  }

  @Test
  fun globalMapFromTripNavigationReturnsToTripsOnBack() {
    openMainTripDetails()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()

    waitForRoute(Route.TRIP_OVERVIEW_MAP)
    composeRule.loadLocalMapStyle()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).assertIsSelected()
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertDoesNotExist()

    pressBack()

    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun tripReadyLocationShortcutOpensTheCreatedTripsMapTab() {
    composeRule.runOnUiThread { navController.navigate(Route.tripReady(mainTrip.uid)) }
    waitForTag(TripReadyTestTags.LOCATION_CARD)

    composeRule.onNodeWithTag(TripReadyTestTags.LOCATION_CARD).performScrollTo().performClick()

    waitForRoute(Route.TRIP_DETAILS)
    composeRule.runOnIdle {
      assertEquals(
          mainTrip.uid,
          navController.currentBackStackEntry?.arguments?.getString("tripId"),
      )
      assertEquals(
          "map",
          navController.currentBackStackEntry?.arguments?.getString("initialTab"),
      )
    }
    waitForTag(C.Tag.trip_map_map)
    composeRule.loadLocalMapStyle()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
  }

  @Test
  fun completedTripOpenedFromGlobalMapStaysReadOnlyAcrossTripTabs() {
    openMap()
    val markerTag = TripMarkerTestTags.markerFor(completedTrip.toTripMarkerUiStates().single().id)
    waitForTag(markerTag)
    composeRule.onNodeWithTag(markerTag).performClick()

    waitForRoute(Route.TRIP_DETAILS)
    composeRule.runOnIdle {
      assertEquals(
          completedTrip.uid,
          navController.currentBackStackEntry?.arguments?.getString("tripId"),
      )
    }

    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_map))
        .performClick()
    waitForTag(C.Tag.trip_map_map)
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()

    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("New vote").assertDoesNotExist()

    composeRule.onNodeWithText("Photos").performClick()
    waitForTag(C.Tag.photos_screen)
    composeRule.onNodeWithTag(C.Tag.photos_take_photo).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(
          completedTrip.uid,
          navController.currentBackStackEntry?.arguments?.getString("tripId"),
      )
    }

    pressBack()
    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
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
  fun tripsTabOnGlobalMapReturnsToTripsRoot() {
    openMainTripDetails()
    pressBack()
    waitForRoute(Route.TRIPS)
    openMap()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()

    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    pressBack()
    composeRule.waitUntil { composeRule.activity.isFinishing }
    assertTrue(composeRule.activity.isFinishing)
  }

  @Test
  fun friendsTabOnMapOpensFriendsAndBackReturnsToTrips() {
    openMap()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()

    waitForRoute(Route.FRIENDS)
    composeRule.onNodeWithTag(C.Tag.friends_screen).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).assertIsSelected()
    pressBack()
    waitForRoute(Route.TRIPS)
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsSelected()
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
    openMap()
    pressBack()
    waitForRoute(originRoute)
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

  // Keeps the trip map away from the device location and the Realtime Database.
  private class TestLocationRepository : LocationRepository {
    val sharing = MutableStateFlow(false)
    val stoppedTrip = MutableStateFlow<String?>(null)

    override fun hasLocationPermission(): Boolean = false

    override suspend fun getCurrentLocation(): DeviceLocation? = null

    override suspend fun startSharing(tripId: String) = Unit

    override suspend fun stopSharing(tripId: String) {
      sharing.value = false
      stoppedTrip.value = tripId
    }

    override suspend fun stopAllSharing() = Unit

    override suspend fun restoreSharing() = Unit

    override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> =
        flowOf(emptyList())

    override fun isSharing(tripId: String): Flow<Boolean> = sharing
  }
}
