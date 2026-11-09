// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.swent.roamigo.R
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
 * Mapbox cannot render under Robolectric, so the screen is shown in inspection mode, where Mapbox
 * draws a static placeholder as in Android Studio previews. The Mapbox map itself is covered by the
 * instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripMapScreenOverlayTest {
  @get:Rule val composeRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val uiState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)
  private val isSharing = mutableStateOf(false)
  private val locationErrorMessage = mutableStateOf<Int?>(null)

  @Test
  fun readOnlyMapShowsTheLoadingOverlayAndOfflineBannerWithoutSharingControls() {
    setContent(TripMapUiState.Loading, readOnly = true, isOffline = true)

    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_offline).assertIsDisplayed()
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
    dismissSnackbar()

    // Cleared after its dismissal, so no snackbar is left to dismiss.
    showError(null)
    showError(R.string.trip_map_error_sharing_failed)
    assertSnackbarShows(R.string.trip_map_error_sharing_failed)

    // Cleared while shown, so its snackbar is dismissed.
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
      onRetry: () -> Unit = {},
  ) {
    uiState.value = initialState
    composeRule.setContent { Screen(readOnly = readOnly, isOffline = isOffline, onRetry = onRetry) }
  }

  @Composable
  private fun Screen(
      readOnly: Boolean = false,
      isOffline: Boolean = false,
      onRetry: () -> Unit = {},
  ) {
    RoamigoTheme {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        TripMapScreen(
            uiState = uiState.value,
            isSharing = isSharing.value,
            onRetry = onRetry,
            onRetryActivities = {},
            onTurnOnSharing = {},
            onPauseSharing = {},
            readOnly = readOnly,
            locationErrorMessage = locationErrorMessage.value,
            isOffline = isOffline,
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

  private companion object {
    // Label of the snackbar's dismiss action, from Material 3.
    const val DISMISS_DESCRIPTION = "Dismiss"
  }
}
