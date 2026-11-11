// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.Activity as TripActivity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.isNotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [TripMapRoute] and [TripMapScreen].
 *
 * The permission flow runs for real, but the system stays out of it:
 * [FakeLocationPermissionChecker] answers the permission checks, [RecordingActivityResultRegistry]
 * records the permission requests and answers them on demand instead of showing the system dialog,
 * and the started intents are recorded instead of leaving the app, or stubbed through Espresso
 * Intents when the screen starts them itself.
 */
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
  private val startingSharingTitle =
      targetContext.getString(R.string.trip_map_sharing_starting_title)
  private val startingSharingLabel =
      targetContext.getString(R.string.trip_map_sharing_starting_button)
  private val turnOnSharingLabel = targetContext.getString(R.string.trip_map_turn_on_sharing)
  private val pauseSharingLabel = targetContext.getString(R.string.trip_map_pause_sharing)

  private val permissionChecker = FakeLocationPermissionChecker()
  private val registry = RecordingActivityResultRegistry()
  private val startedIntents = mutableListOf<Intent>()

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
  private val isStartingSharing = mutableStateOf(false)

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
  fun activitiesRetryIsVisibleOnlyForActivityErrorsAndInvokesItsOwnCallback() {
    var tripRetries = 0
    var activityRetries = 0
    setContent(
        loadedState.copy(activitiesLoadState = ActivitiesLoadState.LOADING),
        onRetry = { tripRetries++ },
        onRetryActivities = { activityRetries++ },
    )
    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).assertDoesNotExist()

    composeRule.runOnIdle {
      uiState.value = loadedState.copy(activitiesLoadState = ActivitiesLoadState.ERROR)
    }

    composeRule
        .onNodeWithTag(C.Tag.trip_map_retry_activities)
        .assertIsDisplayed()
        .assertTextEquals(targetContext.getString(R.string.trip_map_retry_activities))
        .performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(1, activityRetries)
      assertEquals(0, tripRetries)
    }

    listOf(
            loadedState.copy(activitiesLoadState = ActivitiesLoadState.LOADING),
            loadedState.copy(activitiesLoadState = ActivitiesLoadState.LOADED),
            TripMapUiState.Loading,
            TripMapUiState.Error("Trip unavailable"),
        )
        .forEach { state ->
          composeRule.runOnIdle { uiState.value = state }
          composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).assertDoesNotExist()
        }
  }

  @Test
  fun activitiesRetryRemainsAvailableOnReadOnlyMaps() {
    var activityRetries = 0
    setContent(
        loadedState.copy(activitiesLoadState = ActivitiesLoadState.ERROR),
        onRetryActivities = { activityRetries++ },
        readOnly = true,
    )

    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).assertIsDisplayed().performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, activityRetries) }
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
  fun activityAndSuggestionMarkersShowPlaceholderActionsAndFollowStateChanges() {
    val activity =
        TripMarkerUiState(
            id = "lisbon_activity",
            tripId = TRIP_ID,
            title = "Lisbon walking tour",
            locationName = "Lisbon",
            latitude = 38.7223,
            longitude = -9.1393,
            kind = TripMarkerKind.ACTIVITY,
        )
    val suggestion =
        activity.copy(
            id = "lisbon_suggestion",
            title = "Visit the viewpoint",
            locationName = "Viewpoint",
            longitude = -9.13,
            kind = TripMarkerKind.SUGGESTION,
        )
    val markers = listOf(activity, suggestion)
    val state =
        loadedState.copy(
            viewport = tripMapViewportOf(markers.map { MapCoordinate(it.latitude, it.longitude) }),
            activityMarkers = markers,
        )
    setContent(TripMapUiState.Loading)

    composeRule.runOnIdle { uiState.value = state }

    markers.forEach { marker ->
      val markerTag = TripMarkerTestTags.markerFor(marker.id)
      composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
        composeRule.onAllNodesWithTag(markerTag).fetchSemanticsNodes().isNotEmpty()
      }
      composeRule
          .onNodeWithTag(markerTag)
          .assertIsDisplayed()
          .assertContentDescriptionEquals(marker.locationName)
          .assert(isNotImplemented)
          .performClick()
    }

    composeRule.runOnIdle { uiState.value = state.copy(activityMarkers = listOf(suggestion)) }

    val activityTag = TripMarkerTestTags.markerFor(activity.id)
    val suggestionTag = TripMarkerTestTags.markerFor(suggestion.id)
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(activityTag).fetchSemanticsNodes().isEmpty()
    }
    composeRule.onNodeWithTag(suggestionTag).assertIsDisplayed()

    composeRule.runOnIdle { uiState.value = TripMapUiState.Loading }

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(suggestionTag).fetchSemanticsNodes().isEmpty()
    }
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
  fun activityRetryDisplaysDistantMarkersWithoutReloadingTheTrip() {
    val activities = distantActivities()
    val pendingActivities = CompletableDeferred<List<TripActivity>>()
    val tripRequests = AtomicInteger(0)
    val activityRequests = AtomicInteger(0)
    val repository =
        object : TripRepository by FakeTripRepository(trips = listOf(trip)) {
          override suspend fun getTrips(): List<Trip> {
            tripRequests.incrementAndGet()
            return listOf(trip)
          }

          override suspend fun getActivitiesForTrip(tripId: String): List<TripActivity> {
            assertEquals(TRIP_ID, tripId)
            if (activityRequests.incrementAndGet() == 1) error("Activities unavailable")
            return pendingActivities.await()
          }
        }
    setScreen(repository, RecordingLocationRepository())

    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertDoesNotExist()
    activities.forEach {
      composeRule
          .onNodeWithTag(TripMarkerTestTags.markerFor(it.toTripMarkerUiState().id))
          .assertDoesNotExist()
    }

    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    assertEquals(1, tripRequests.get())
    assertEquals(2, activityRequests.get())

    composeRule.runOnIdle { pendingActivities.complete(activities) }
    assertActivityMarkersDisplayed(activities)
    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertDoesNotExist()
    assertEquals(1, tripRequests.get())
    assertEquals(2, activityRequests.get())
  }

  @Test
  fun delayedInitialLoadDisplaysDistantActivityAndSuggestionMarkers() {
    val activities = distantActivities()
    val pendingActivities = CompletableDeferred<List<TripActivity>>()
    val repository =
        object : TripRepository by FakeTripRepository(trips = listOf(trip)) {
          override suspend fun getActivitiesForTrip(tripId: String): List<TripActivity> =
              pendingActivities.await()
        }
    setScreen(repository, RecordingLocationRepository())
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertIsDisplayed()

    composeRule.runOnIdle { pendingActivities.complete(activities) }

    assertActivityMarkersDisplayed(activities)
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()
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
  fun openingTheScreenDoesNotRequestAnyPermission() {
    val registry = RecordingActivityResultRegistry()
    setScreen(
        FakeTripRepository(trips = listOf(trip)),
        RecordingLocationRepository(),
        activityResultRegistry = registry,
    )

    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.waitForIdle()

    assertEquals(emptyList<Any?>(), registry.launchedInputs)
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
  fun startingSharingShowsLoadingIndicatorTurningOnBannerAndDisabledButton() {
    var turnOnRequests = 0
    setContent(loadedState, startingSharing = true, onTurnOnSharing = { turnOnRequests++ })

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_loading).assertIsDisplayed()
    composeRule.onNodeWithText(startingSharingTitle).assertIsDisplayed()
    composeRule.onNodeWithText(sharingOffTitle).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsNotEnabled()
    composeRule.onNodeWithText(startingSharingLabel).assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).performClick()

    composeRule.runOnIdle { assertEquals(0, turnOnRequests) }
  }

  @Test
  fun sharingOnHidesTheLoadingIndicatorAndKeepsPauseEnabled() {
    setContent(loadedState, sharing = true, startingSharing = true)

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_loading).assertDoesNotExist()
    composeRule.onNodeWithText(sharingOnTitle).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsEnabled()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertTextEquals(pauseSharingLabel)
  }

  @Test
  fun sharingOffHasNoLoadingIndicatorAndAnEnabledButton() {
    setContent(loadedState)

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_loading).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsEnabled()
  }

  @Test
  fun bannerAndButtonStayVisibleOverTheErrorOverlay() {
    setContent(TripMapUiState.Error("failure"))

    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
  }

  @Test
  fun turnOnWithGrantedPermissionShowsRationaleThenContinueStartsSharingAndPauseStopsIt() {
    permissionChecker.granted = true
    val locationRepository = RecordingLocationRepository(hasPermission = true)
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(0, locationRepository.startSharingCalls.get()) }

    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_continue).performClick()

    waitForText(pauseSharingLabel)
    composeRule.onNodeWithText(sharingOnTitle).assertIsDisplayed()
    assertEquals(1, locationRepository.startSharingCalls.get())
    assertNoPermissionSheet()
    assertEquals(emptyList<Any?>(), registry.launchedInputs)

    composeRule.onNodeWithText(pauseSharingLabel).performClick()

    // Checked before waiting for the UI, so that a delayed or deferred stop would fail.
    composeRule.runOnIdle { assertEquals(1, locationRepository.stopSharingCalls.get()) }
    waitForText(turnOnSharingLabel)
    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
  }

  @Test
  fun turnOnWithoutPermissionShowsRationaleAndLaunchesNothing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_continue).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_not_now).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(emptyList<Any?>(), registry.launchedInputs)
      assertEquals(0, locationRepository.startSharingCalls.get())
    }
  }

  @Test
  fun notNowClosesRationaleAndLaunchesNothing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_not_now).performClick()

    assertNoPermissionSheet()
    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(emptyList<Any?>(), registry.launchedInputs)
      assertEquals(0, locationRepository.startSharingCalls.get())
    }
  }

  @Test
  fun continueRequestsFineAndCoarseLocation() {
    setScreen(FakeTripRepository(trips = listOf(trip)), RecordingLocationRepository())
    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_continue).performClick()

    assertNoPermissionSheet()
    composeRule.runOnIdle {
      val requested = registry.launchedInputs.single() as Array<*>
      assertEquals(
          setOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION,
          ),
          requested.toSet(),
      )
    }
  }

  @Test
  fun grantedResultStartsSharing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    requestPermissionThroughRationale()

    permissionChecker.granted = true
    answerPermissionRequest(fine = false, coarse = true)

    waitForText(pauseSharingLabel)
    composeRule.onNodeWithText(sharingOnTitle).assertIsDisplayed()
    assertNoPermissionSheet()
    assertEquals(1, locationRepository.startSharingCalls.get())
  }

  @Test
  fun deniedResultShowsDeniedSheetAndGotItClosesIt() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    requestPermissionThroughRationale()

    permissionChecker.shouldShowRationale = true
    answerPermissionRequest(fine = false, coarse = false)

    composeRule.onNodeWithTag(C.Tag.trip_map_location_denied_sheet).assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.trip_map_location_denied_got_it).performClick()

    assertNoPermissionSheet()
    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(1, registry.launchedInputs.size)
      assertEquals(0, locationRepository.startSharingCalls.get())
    }
  }

  @Test
  fun refusalWithoutRationaleShowsBlockedSheetAndOpenSettingsStartsSettings() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    requestPermissionThroughRationale()

    permissionChecker.shouldShowRationale = false
    answerPermissionRequest(fine = false, coarse = false)

    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_sheet).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_not_now).assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_open_settings).performClick()

    assertNoPermissionSheet()
    composeRule.runOnIdle {
      val intent = startedIntents.single()
      assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
      assertEquals(Uri.fromParts("package", targetContext.packageName, null), intent.data)
      assertEquals(1, registry.launchedInputs.size)
      assertEquals(0, locationRepository.startSharingCalls.get())
    }
  }

  @Test
  fun permissionGrantedWithoutContinueDoesNotStartSharing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    composeRule.onNodeWithText(turnOnSharingLabel).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_not_now).performClick()
    assertNoPermissionSheet()

    // Granted outside the app, e.g. in the settings, then seen when the screen resumes.
    permissionChecker.granted = true
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitForIdle()

    composeRule.onNodeWithText(sharingOffTitle).assertIsDisplayed()
    composeRule.onNodeWithText(turnOnSharingLabel).assertIsDisplayed()
    assertTrue(registry.launchedInputs.isEmpty())
    assertEquals(0, locationRepository.startSharingCalls.get())
  }

  @Test
  fun openSettingsWithTheDefaultStarterOpensTheAppDetailsSettings() {
    Intents.init()
    try {
      // Answered at once, so that the settings never open over the test.
      intending(hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
          .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
      setScreen(
          FakeTripRepository(trips = listOf(trip)),
          RecordingLocationRepository(),
          recordStartedIntents = false,
      )
      requestPermissionThroughRationale()
      permissionChecker.shouldShowRationale = false
      answerPermissionRequest(fine = false, coarse = false)

      composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_open_settings).performClick()
      composeRule.waitForIdle()

      intended(
          allOf(
              hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
              hasData(Uri.fromParts("package", targetContext.packageName, null)),
          )
      )
      assertNoPermissionSheet()
    } finally {
      Intents.release()
    }
  }

  @Test
  fun permissionGrantedOutsideTheAppShowsRationaleThenContinueStartsSharing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()

    // Granted outside the app, e.g. in the settings, then seen when the screen resumes.
    permissionChecker.granted = true
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitForIdle()

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_continue).performClick()

    waitForText(pauseSharingLabel)
    composeRule.onNodeWithText(sharingOnTitle).assertIsDisplayed()
    assertNoPermissionSheet()
    composeRule.runOnIdle {
      assertTrue(registry.launchedInputs.isEmpty())
      assertEquals(1, locationRepository.startSharingCalls.get())
    }
  }

  @Test
  fun turnOnAfterPermanentRefusalShowsBlockedSheetAndLaunchesNothing() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    requestPermissionThroughRationale()
    permissionChecker.shouldShowRationale = false
    answerPermissionRequest(fine = false, coarse = false)
    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_not_now).performClick()
    assertNoPermissionSheet()

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_sheet).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertDoesNotExist()
    composeRule.runOnIdle {
      // Only the first request, made before the permanent refusal.
      assertEquals(1, registry.launchedInputs.size)
      assertEquals(0, locationRepository.startSharingCalls.get())
    }
  }

  @Test
  fun permissionGrantedInSettingsAfterPermanentRefusalShowsRationaleAgain() {
    val locationRepository = RecordingLocationRepository()
    setScreen(FakeTripRepository(trips = listOf(trip)), locationRepository)
    requestPermissionThroughRationale()
    permissionChecker.shouldShowRationale = false
    answerPermissionRequest(fine = false, coarse = false)
    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_not_now).performClick()

    // Allowed in the settings, then seen when the screen resumes.
    permissionChecker.granted = true
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitForIdle()

    composeRule.onNodeWithText(turnOnSharingLabel).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_sheet).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, registry.launchedInputs.size) }
  }

  /** Taps Turn on and Continue, so that the screen launches the permission request. */
  private fun requestPermissionThroughRationale() {
    composeRule.onNodeWithText(turnOnSharingLabel).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_continue).performClick()
    composeRule.runOnIdle { assertEquals(1, registry.launchedInputs.size) }
  }

  /** Answers the last permission request the way the system dialog would. */
  private fun answerPermissionRequest(fine: Boolean, coarse: Boolean) {
    composeRule.runOnIdle {
      registry.answerLastLaunch(
          mapOf(
              Manifest.permission.ACCESS_FINE_LOCATION to fine,
              Manifest.permission.ACCESS_COARSE_LOCATION to coarse,
          )
      )
    }
    composeRule.waitForIdle()
  }

  private fun assertNoPermissionSheet() {
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_location_denied_sheet).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_sheet).assertDoesNotExist()
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
      startingSharing: Boolean = false,
      onRetry: () -> Unit = {},
      onRetryActivities: () -> Unit = {},
      onTurnOnSharing: () -> Unit = {},
      onPauseSharing: () -> Unit = {},
      readOnly: Boolean = false,
  ) {
    uiState.value = initialState
    isSharing.value = sharing
    isStartingSharing.value = startingSharing
    composeRule.setContent {
      RoamigoTheme {
        TripMapScreen(
            uiState = uiState.value,
            isSharing = isSharing.value,
            isStartingSharing = isStartingSharing.value,
            onRetry = onRetry,
            onRetryActivities = onRetryActivities,
            onTurnOnSharing = onTurnOnSharing,
            onPauseSharing = onPauseSharing,
            readOnly = readOnly,
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

  private fun setScreen(
      tripRepository: TripRepository,
      locationRepository: LocationRepository,
      activityResultRegistry: ActivityResultRegistry = registry,
      recordStartedIntents: Boolean = true,
  ) {
    val registryOwner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = activityResultRegistry
        }
    val tripMapViewModelFactory =
        TripMapViewModel.factory(
            tripId = TRIP_ID,
            tripRepository = tripRepository,
            userRepository = FakeUserRepository(User(USER_ID, "Alex", null)),
        )
    val locationViewModelFactory =
        LocationViewModel.factory(
            tripId = TRIP_ID,
            currentUserId = USER_ID,
            locationRepository = locationRepository,
            connectivityRepository = AlwaysOnlineConnectivityRepository,
        )
    composeRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        RoamigoTheme {
          if (recordStartedIntents) {
            TripMapRoute(
                tripId = TRIP_ID,
                tripMapViewModelFactory = tripMapViewModelFactory,
                locationViewModelFactory = locationViewModelFactory,
                permissionChecker = permissionChecker,
                startActivity = { intent -> startedIntents += intent },
            )
          } else {
            // Leaves the intents to the route's default, which starts them from its context.
            TripMapRoute(
                tripId = TRIP_ID,
                tripMapViewModelFactory = tripMapViewModelFactory,
                locationViewModelFactory = locationViewModelFactory,
                permissionChecker = permissionChecker,
            )
          }
        }
      }
    }
    composeRule.loadLocalMapStyle()
  }

  private fun distantActivities(): List<TripActivity> =
      listOf(
          activity(
              uid = "london",
              location = TripLocation(latitude = 51.5074, longitude = -0.1278, name = "London"),
              activityType = ActivityType.ACCEPTED,
          ),
          activity(
              uid = "rome",
              location = TripLocation(latitude = 41.9028, longitude = 12.4964, name = "Rome"),
              activityType = ActivityType.SUGGESTION,
          ),
      )

  private fun activity(
      uid: String,
      location: TripLocation,
      activityType: ActivityType,
  ): TripActivity =
      TripActivity(
          uid = uid,
          tripId = TRIP_ID,
          activityType = activityType,
          name = "Visit ${location.name}",
          description = null,
          location = location,
          startTime = trip.startDate,
          endTime = null,
          createdByUserId = USER_ID,
          creationTime = trip.createdAt,
      )

  private fun assertActivityMarkersDisplayed(activities: List<TripActivity>) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      activities.all { activity ->
        val tag = TripMarkerTestTags.markerFor(activity.toTripMarkerUiState().id)
        composeRule.onNodeWithTag(tag).isDisplayed()
      }
    }
    activities.forEach { activity ->
      val tag = TripMarkerTestTags.markerFor(activity.toTripMarkerUiState().id)
      composeRule
          .onNodeWithTag(tag)
          .assertIsDisplayed()
          .assertContentDescriptionEquals(activity.location.name)
    }
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

  /**
   * Records what the screen launches through the Activity Result API, such as a system permission
   * request, without showing anything. [answerLastLaunch] then delivers the answer.
   */
  private class RecordingActivityResultRegistry : ActivityResultRegistry() {
    val launchedInputs = mutableListOf<Any?>()
    private val launchedRequestCodes = mutableListOf<Int>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launchedInputs += input
      launchedRequestCodes += requestCode
    }

    /** Delivers [result] to the callback of the last launch, as the launched activity would. */
    fun <O> answerLastLaunch(result: O) {
      check(dispatchResult(launchedRequestCodes.last(), result)) { "No callback received it" }
    }
  }

  /** Answers the permission checks of the screen with values the test sets. */
  private class FakeLocationPermissionChecker(
      var granted: Boolean = false,
      var shouldShowRationale: Boolean = true,
  ) : LocationPermissionChecker {
    override fun isGranted(): Boolean = granted

    override fun shouldShowRationale(): Boolean = shouldShowRationale
  }

  private object AlwaysOnlineConnectivityRepository : ConnectivityRepository {
    override val isOnline: Flow<Boolean> = flowOf(true)
  }

  private companion object {
    const val TRIP_ID = "trip_lisbon"
    const val USER_ID = "user_1"
  }
}
