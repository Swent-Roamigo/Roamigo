// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapbox.maps.extension.compose.MapboxMap
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/**
 * Shows the map of a trip, with its loading and error states.
 *
 * Both ViewModels are scoped to [tripId], so that each trip gets its own. Opening the screen
 * neither requests a permission nor starts sharing the user's location.
 *
 * @param tripId identifier of the trip to show. Must be the trip both factories were built for.
 * @param tripMapViewModelFactory creates the [TripMapViewModel] of the trip.
 * @param locationViewModelFactory creates the [LocationViewModel] of the trip.
 * @param modifier modifier applied to the screen.
 */
@Composable
fun TripMapScreen(
    tripId: String,
    tripMapViewModelFactory: ViewModelProvider.Factory,
    locationViewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
  val tripMapViewModel: TripMapViewModel =
      viewModel(key = "trip_map_$tripId", factory = tripMapViewModelFactory)
  val locationViewModel: LocationViewModel =
      viewModel(key = "trip_map_location_$tripId", factory = locationViewModelFactory)
  val tripUiState by tripMapViewModel.uiState.collectAsStateWithLifecycle()
  // Collected with the screen's lifecycle, but not rendered yet: the sharing banner and the member
  // markers will use it.
  locationViewModel.uiState.collectAsStateWithLifecycle()

  TripMapContent(
      uiState = tripUiState,
      onRetry = tripMapViewModel::retry,
      modifier = modifier,
  )
}

/**
 * Renders the trip map for [uiState]. The map stays interactive and visible underneath the loading
 * and error overlays.
 *
 * @param uiState state of the trip shown.
 * @param onRetry called when the user asks to load the trip again after an error.
 * @param modifier modifier applied to the content.
 */
@Composable
fun TripMapContent(
    uiState: TripMapUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Until the trip is loaded the camera keeps its position, so the map stays usable underneath.
  val viewport = (uiState as? TripMapUiState.Loaded)?.viewport ?: TripMapViewport.None

  Box(modifier = modifier.fillMaxSize()) {
    MapboxMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.trip_map_map),
        mapViewportState = rememberTripMapViewportState(viewport),
    )

    val overlayModifier =
        Modifier.align(Alignment.Center)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Spacing.screenPadding)
    when (uiState) {
      TripMapUiState.Loading ->
          TripMapStatusCard(modifier = overlayModifier.testTag(C.Tag.trip_map_loading)) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.trip_map_trip_loading),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
          }
      is TripMapUiState.Error ->
          // The ViewModel's message is not localized, so a fixed string resource is shown instead.
          TripMapStatusCard(
              modifier =
                  overlayModifier.testTag(C.Tag.trip_map_error).semantics {
                    liveRegion = LiveRegionMode.Polite
                  }
          ) {
            Text(
                text = stringResource(R.string.trip_map_trip_load_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.trip_map_retry)) {
              Text(stringResource(R.string.trip_map_retry))
            }
          }
      is TripMapUiState.Loaded -> Unit
    }
  }
}

/** Floating card drawn over the map, styled like the map's notification banner. */
@Composable
private fun TripMapStatusCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
      modifier = modifier,
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = Spacing.extraSmall,
  ) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        content = content,
    )
  }
}
