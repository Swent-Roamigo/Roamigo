// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
  private val sharingOffTitle = targetContext.getString(R.string.trip_map_sharing_off_title)
  private val sharingOnTitle = targetContext.getString(R.string.trip_map_sharing_on_title)
  private val turnOnSharingLabel = targetContext.getString(R.string.trip_map_turn_on_sharing)
  private val pauseSharingLabel = targetContext.getString(R.string.trip_map_pause_sharing)

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
  private val isSharing = mutableStateOf(false)

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

  @Test
  fun sharingOffShowsOffBannerAndTurnOnButton() {
    var turnOnRequests = 0
    var pauseRequests = 0
    setContent(
        loadedState,
        sharing = false,
        onTurnOnSharing = { turnOnRequests++ },
        onPauseSharing = { pauseRequests++ },
    )

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertIsDisplayed()
    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
    composeRule.onNodeWithText(sharingOnTitle).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertTextEquals(turnOnSharingLabel)

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).performClick()

    composeRule.runOnIdle {
      assertEquals(1, turnOnRequests)
      assertEquals(0, pauseRequests)
    }
  }

  @Test
  fun sharingOnShowsOnBannerAndPauseButton() {
    var turnOnRequests = 0
    var pauseRequests = 0
    setContent(
        loadedState,
        sharing = true,
        onTurnOnSharing = { turnOnRequests++ },
        onPauseSharing = { pauseRequests++ },
    )

    composeRule.onNodeWithText(sharingOnTitle).assertIsDisplayed()
    composeRule.onNodeWithText(sharingOffTitle).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertTextEquals(pauseSharingLabel)

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).performClick()

    composeRule.runOnIdle {
      assertEquals(0, turnOnRequests)
      assertEquals(1, pauseRequests)
    }
  }

  @Test
  fun bannerAndButtonStayVisibleOverTheErrorOverlay() {
    setContent(TripMapUiState.Error("failure"))

    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
  }

  @Test
  fun turnOnWithGrantedPermissionStartsSharingAndPauseStopsIt() {
    val locationRepository = RecordingLocationRepository(hasPermission = true)
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    waitForText(pauseSharingLabel)
    composeRule.onNodeWithText(sharingOnTitle).assertIsDisplayed()
    assertEquals(1, locationRepository.startSharingCalls.get())

    composeRule.onNodeWithText(pauseSharingLabel).performClick()

    waitForText(turnOnSharingLabel)
    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
    assertEquals(1, locationRepository.stopSharingCalls.get())
  }

  @Test
  fun turnOnWithoutPermissionDoesNothing() {
    val locationRepository = RecordingLocationRepository(hasPermission = false)
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
    composeRule.onNodeWithText(turnOnSharingLabel).assertIsDisplayed()
    assertEquals(0, locationRepository.startSharingCalls.get())
  }

  private val loadedState
    get() =
        TripMapUiState.Loaded(
            trip = trip,
            viewport = TripMapViewport.Centered(38.7223, -9.1393),
            members = emptyMap(),
        )

  private fun setContent(
      initialState: TripMapUiState,
      sharing: Boolean = false,
      onRetry: () -> Unit = {},
      onTurnOnSharing: () -> Unit = {},
      onPauseSharing: () -> Unit = {},
  ) {
    uiState.value = initialState
    isSharing.value = sharing
    composeRule.setContent {
      RoamigoTheme {
        TripMapContent(
            uiState = uiState.value,
            isSharing = isSharing.value,
            onRetry = onRetry,
            onTurnOnSharing = onTurnOnSharing,
            onPauseSharing = onPauseSharing,
        )
      }
    }
    composeRule.loadLocalMapStyle()
  }

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
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

  private class RecordingLocationRepository(private val hasPermission: Boolean = false) :
      LocationRepository {
    val startSharingCalls = AtomicInteger(0)
    val stopSharingCalls = AtomicInteger(0)
    val getCurrentLocationCalls = AtomicInteger(0)
    private val sharing = MutableStateFlow(false)

    override fun hasLocationPermission(): Boolean = hasPermission

    override suspend fun getCurrentLocation(): DeviceLocation? {
      getCurrentLocationCalls.incrementAndGet()
      return null
    }

    override suspend fun startSharing(tripId: String) {
      startSharingCalls.incrementAndGet()
      sharing.value = true
    }

    override suspend fun stopSharing(tripId: String) {
      stopSharingCalls.incrementAndGet()
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
