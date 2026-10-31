// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapScreenTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
  private val errorMessage = targetContext.getString(R.string.trip_map_trip_load_error)

  private val trip =
      Trip(
          uid = TRIP_ID,
          name = "Lisbon",
          description = null,
          destinations =
              listOf(TripLocation(latitude = 38.7223, longitude = -9.1393, name = "Lisbon")),
          startDate = Timestamp.now(),
          endDate = Timestamp.now(),
          flexibleDates = false,
          travelMode = TravelMode.SOLO,
          travelerCount = 1,
          ownerId = USER_ID,
          memberIds = emptyList(),
          status = TripStatus.PLANNED,
          createdAt = Timestamp.now(),
      )

  private val uiState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)

  @Test
  fun loadingStateShowsOverlayOverTheMap() {
    setContent(TripMapUiState.Loading)

    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertDoesNotExist()
  }

  @Test
  fun errorStateShowsFixedMessageOverTheMapAndRetryInvokesCallback() {
    val backendMessage = "PERMISSION_DENIED: backend detail"
    var retryRequests = 0
    setContent(TripMapUiState.Error(backendMessage), onRetry = { retryRequests++ })

    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()
    composeRule.onNodeWithText(errorMessage).assertIsDisplayed()
    composeRule.onNodeWithText(backendMessage).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()

    composeRule.onNodeWithTag(C.Tag.trip_map_retry).performClick()

    composeRule.runOnIdle { assertEquals(1, retryRequests) }
  }

  @Test
  fun loadedStateShowsOnlyTheMap() {
    setContent(TripMapUiState.Loading)

    composeRule.runOnIdle {
      uiState.value =
          TripMapUiState.Loaded(
              trip = trip,
              viewport = TripMapViewport.Centered(38.7223, -9.1393),
              members = emptyMap(),
          )
    }

    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertDoesNotExist()
  }

  @Test
  fun retryReloadsTheTripThroughTheViewModel() {
    val tripRepository = FailingOnceTripRepository(FakeTripRepository(trips = listOf(trip)))
    setScreen(tripRepository, RecordingLocationRepository())

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_error).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.trip_map_retry).performClick()

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_error).fetchSemanticsNodes().isEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    assertEquals(2, tripRepository.getTripsCalls.get())
  }

  @Test
  fun openingTheScreenDoesNotStartSharing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)

    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.waitForIdle()

    assertEquals(0, locationRepository.startSharingCalls.get())
    assertEquals(0, locationRepository.getCurrentLocationCalls.get())
  }

  private fun setContent(initialState: TripMapUiState, onRetry: () -> Unit = {}) {
    uiState.value = initialState
    composeRule.setContent {
      RoamigoTheme { TripMapContent(uiState = uiState.value, onRetry = onRetry) }
    }
    composeRule.loadLocalMapStyle()
  }

  private fun setScreen(tripRepository: TripRepository, locationRepository: LocationRepository) {
    composeRule.setContent {
      RoamigoTheme {
        TripMapScreen(
            tripId = TRIP_ID,
            tripMapViewModelFactory =
                TripMapViewModel.factory(
                    tripId = TRIP_ID,
                    tripRepository = tripRepository,
                    userRepository = FakeUserRepository(User(USER_ID, "Alex", null)),
                ),
            locationViewModelFactory =
                LocationViewModel.factory(
                    tripId = TRIP_ID,
                    currentUserId = USER_ID,
                    locationRepository = locationRepository,
                    connectivityRepository = AlwaysOnlineConnectivityRepository,
                ),
        )
      }
    }
    composeRule.loadLocalMapStyle()
  }

  private class FailingOnceTripRepository(private val delegate: TripRepository) :
      TripRepository by delegate {
    val getTripsCalls = AtomicInteger(0)

    override suspend fun getTrips(): List<Trip> {
      if (getTripsCalls.incrementAndGet() == 1) error("Network unavailable")
      return delegate.getTrips()
    }
  }

  private class RecordingLocationRepository : LocationRepository {
    val startSharingCalls = AtomicInteger(0)
    val getCurrentLocationCalls = AtomicInteger(0)
    private val sharing = MutableStateFlow(false)

    override fun hasLocationPermission(): Boolean = false

    override suspend fun getCurrentLocation(): DeviceLocation? {
      getCurrentLocationCalls.incrementAndGet()
      return null
    }

    override suspend fun startSharing(tripId: String) {
      startSharingCalls.incrementAndGet()
      sharing.value = true
    }

    override suspend fun stopSharing(tripId: String) {
      sharing.value = false
    }

    override suspend fun stopAllSharing() {
      sharing.value = false
    }

    override suspend fun restoreSharing() = Unit

    override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> =
        flowOf(emptyList())

    override fun isSharing(tripId: String): Flow<Boolean> = sharing
  }

  private object AlwaysOnlineConnectivityRepository : ConnectivityRepository {
    override val isOnline: Flow<Boolean> = flowOf(true)
  }

  private companion object {
    const val TRIP_ID = "trip_lisbon"
    const val USER_ID = "user_1"
  }
}
