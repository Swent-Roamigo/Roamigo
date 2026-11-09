// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM tests for the parts of [TripMapScreen] drawn around the map: status overlays, banners,
 * sharing controls and the location error snackbar.
 *
 * Mapbox cannot render under Robolectric, so the screen draws a stand-in map provided through
 * [LocalTripMapStandIn], which records what the screen gives it. The Mapbox map itself is covered
 * by the instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripMapScreenStandInTest {
  @get:Rule val composeRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val trip =
      Trip(
          uid = TRIP_ID,
          name = "Lisbon",
          description = null,
          destinations =
              listOf(TripLocation(latitude = 38.7223, longitude = -9.1393, name = "Lisbon")),
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          flexibleDates = false,
          travelMode = TravelMode.SOLO,
          travelerCount = 1,
          ownerId = USER_ID,
          memberIds = listOf(USER_ID),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  private val uiState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)
  private val isSharing = mutableStateOf(false)
  private val locationErrorMessage = mutableStateOf<Int?>(null)

  /** What the screen last gave the stand-in map. */
  private var mapInputs: MapInputs? = null

  private val standInMap:
      @Composable
      (
          TripMapViewport,
          List<TripMarkerUiState>,
          List<MemberLocation>,
          Map<String, MemberDisplayInfo>,
          Set<String>,
          MemberLocation?,
          Long,
          Modifier,
      ) -> Unit =
      { _, activityMarkers, memberLocations, members, _, myLocation, _, modifier ->
        mapInputs = MapInputs(activityMarkers, memberLocations, members, myLocation)
        Box(modifier)
      }

  @Test
  fun providedStandInIsDrawnAsTheMap() {
    setContent(TripMapUiState.Loading)

    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(MapInputs(emptyList(), emptyList(), emptyMap(), null), mapInputs)
    }
  }

  @Test
  fun activityMarkersReachTheMapOnceTheTripLoads() {
    val marker =
        TripMarkerUiState(
            id = "lisbon_activity",
            tripId = TRIP_ID,
            title = "Lisbon walking tour",
            locationName = "Lisbon",
            latitude = 38.7223,
            longitude = -9.1393,
            kind = TripMarkerKind.ACTIVITY,
        )
    setContent(TripMapUiState.Loading)

    composeRule.runOnIdle {
      uiState.value =
          TripMapUiState.Loaded(
              trip = trip,
              viewport = TripMapViewport.Centered(38.7223, -9.1393),
              members = emptyMap(),
              activityMarkers = listOf(marker),
          )
    }

    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(listOf(marker), mapInputs?.activityMarkers) }
  }

  @Test
  fun memberLocationsReachTheMapWhileTheTripLoadsOrFails() {
    val memberLocations = listOf(MemberLocation("alex", 38.72, -9.14, NOW_MILLIS))
    val myLocation = MemberLocation(USER_ID, 38.73, -9.15, NOW_MILLIS)
    setContent(TripMapUiState.Loading, memberLocations = memberLocations, myLocation = myLocation)

    // No member profile is known before the trip loads, so the markers use fallback names.
    composeRule.runOnIdle {
      assertEquals(MapInputs(emptyList(), memberLocations, emptyMap(), myLocation), mapInputs)
      uiState.value = TripMapUiState.Error("failure")
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(MapInputs(emptyList(), memberLocations, emptyMap(), myLocation), mapInputs)
    }
  }

  @Test
  fun readOnlyOfflineMapShowsTheOfflineBannerWithoutSharingControls() {
    setContent(TripMapUiState.Loading, readOnly = true, isOffline = true)

    composeRule.onNodeWithTag(C.Tag.trip_map_offline).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()
  }

  @Test
  fun readOnlyLoadingMapShowsTheLoadingOverlayWithoutSharingControls() {
    setContent(TripMapUiState.Loading, readOnly = true)

    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()
  }

  @Test
  fun readOnlyErrorMapShowsTheErrorAndRetryInvokesTheCallback() {
    var retryRequests = 0
    setContent(TripMapUiState.Error("failure"), readOnly = true, onRetry = { retryRequests++ })

    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()

    composeRule.onNodeWithTag(C.Tag.trip_map_retry).performClick()

    composeRule.runOnIdle { assertEquals(1, retryRequests) }
  }

  @Test
  fun newLocationErrorReplacesTheShownOne() {
    setContent(TripMapUiState.Loading)
    showError(R.string.trip_map_error_sharing_failed)
    assertSnackbarShows(R.string.trip_map_error_sharing_failed)

    showError(R.string.trip_map_error_locations_unavailable)

    assertSnackbarShows(R.string.trip_map_error_locations_unavailable)
    assertNoSnackbarText(R.string.trip_map_error_sharing_failed)
  }

  @Test
  fun dismissedLocationErrorIsNotShownAgainWhileItStays() {
    setContent(TripMapUiState.Loading)
    showError(R.string.trip_map_error_sharing_failed)
    dismissSnackbar()
    assertNoSnackbarText(R.string.trip_map_error_sharing_failed)

    // The screen recomposes with the same error, e.g. when sharing changes.
    composeRule.runOnIdle { isSharing.value = true }
    showError(R.string.trip_map_error_sharing_failed)

    assertNoSnackbarText(R.string.trip_map_error_sharing_failed)
  }

  @Test
  fun clearedLocationErrorIsShownAgainWhenItComesBack() {
    setContent(TripMapUiState.Loading)
    showError(R.string.trip_map_error_sharing_failed)
    assertSnackbarShows(R.string.trip_map_error_sharing_failed)

    showError(null)
    assertNoSnackbarText(R.string.trip_map_error_sharing_failed)

    showError(R.string.trip_map_error_sharing_failed)
    assertSnackbarShows(R.string.trip_map_error_sharing_failed)
  }

  @Test
  fun locationErrorClearedAfterItsDismissalIsShownAgainWhenItComesBack() {
    setContent(TripMapUiState.Loading)
    showError(R.string.trip_map_error_sharing_failed)
    dismissSnackbar()

    showError(null)
    assertNoSnackbarText(R.string.trip_map_error_sharing_failed)
    showError(R.string.trip_map_error_sharing_failed)

    assertSnackbarShows(R.string.trip_map_error_sharing_failed)
  }

  @Test
  fun locationErrorAlreadyShownIsNotShownAgainAfterRestoration() {
    val restorationTester = StateRestorationTester(composeRule)
    locationErrorMessage.value = R.string.trip_map_error_sharing_failed
    restorationTester.setContent { Screen() }
    assertSnackbarShows(R.string.trip_map_error_sharing_failed)
    dismissSnackbar()

    restorationTester.emulateSavedInstanceStateRestore()

    assertNoSnackbarText(R.string.trip_map_error_sharing_failed)
  }

  private fun setContent(
      initialState: TripMapUiState,
      readOnly: Boolean = false,
      isOffline: Boolean = false,
      memberLocations: List<MemberLocation> = emptyList(),
      myLocation: MemberLocation? = null,
      onRetry: () -> Unit = {},
  ) {
    uiState.value = initialState
    composeRule.setContent {
      Screen(
          readOnly = readOnly,
          isOffline = isOffline,
          memberLocations = memberLocations,
          myLocation = myLocation,
          onRetry = onRetry,
      )
    }
  }

  @Composable
  private fun Screen(
      readOnly: Boolean = false,
      isOffline: Boolean = false,
      memberLocations: List<MemberLocation> = emptyList(),
      myLocation: MemberLocation? = null,
      onRetry: () -> Unit = {},
  ) {
    RoamigoTheme {
      CompositionLocalProvider(LocalTripMapStandIn provides standInMap) {
        TripMapScreen(
            uiState = uiState.value,
            isSharing = isSharing.value,
            onRetry = onRetry,
            onRetryActivities = {},
            onTurnOnSharing = {},
            onPauseSharing = {},
            readOnly = readOnly,
            memberLocations = memberLocations,
            myLocation = myLocation,
            locationErrorMessage = locationErrorMessage.value,
            isOffline = isOffline,
            nowMillis = NOW_MILLIS,
        )
      }
    }
  }

  private fun showError(@StringRes message: Int?) {
    composeRule.runOnIdle { locationErrorMessage.value = message }
  }

  /** Closes the snackbar through its dismiss action, as the user would. */
  private fun dismissSnackbar() {
    composeRule.onNodeWithContentDescription(DISMISS_DESCRIPTION).performClick()
  }

  private fun assertSnackbarShows(@StringRes message: Int) {
    composeRule.onNodeWithText(context.getString(message)).assertIsDisplayed()
  }

  private fun assertNoSnackbarText(@StringRes message: Int) {
    composeRule.onNodeWithText(context.getString(message)).assertDoesNotExist()
  }

  private data class MapInputs(
      val activityMarkers: List<TripMarkerUiState>,
      val memberLocations: List<MemberLocation>,
      val members: Map<String, MemberDisplayInfo>,
      val myLocation: MemberLocation?,
  )

  private companion object {
    const val TRIP_ID = "trip_lisbon"
    const val USER_ID = "user_1"
    const val NOW_MILLIS = 1_700_000_000_000L

    // Label of the snackbar's dismiss action, from Material 3.
    const val DISMISS_DESCRIPTION = "Dismiss"
  }
}
