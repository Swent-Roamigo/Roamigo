// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.swent.roamigo.R
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.InfoRow
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.components.TripActionButtonPadding
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.MyLocationBlue
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.Warning

/**
 * Connects the trip and location ViewModels to [TripMapScreen] using caller-provided factories.
 *
 * Both ViewModels are scoped to [tripId], so that each trip gets its own. Opening the route neither
 * requests a permission nor starts sharing the user's location.
 *
 * Turning sharing on without the location permission goes through [LocationViewModel]'s permission
 * flow: this screen renders its sheets and carries out its effects, showing the system permission
 * dialog through the Activity Result API and opening the app's settings. The permission is checked
 * again each time the screen resumes, e.g. after the user visited the settings.
 *
 * @param tripId identifier of the trip to show. Must be the trip both factories were built for.
 * @param tripMapViewModelFactory creates the [TripMapViewModel] of the trip.
 * @param locationViewModelFactory creates the [LocationViewModel] of the trip.
 * @param modifier modifier applied to the screen.
 * @param permissionChecker tells whether the location permission is granted, and whether the system
 *   would ask for it again after a refusal.
 * @param startActivity starts the activity of an intent, such as the app's settings.
 */
@Composable
fun TripMapRoute(
    tripId: String,
    tripMapViewModelFactory: ViewModelProvider.Factory,
    locationViewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
    permissionChecker: LocationPermissionChecker = rememberLocationPermissionChecker(),
    startActivity: (Intent) -> Unit = LocalContext.current::startActivity,
) {
  val tripMapViewModel: TripMapViewModel =
      viewModel(key = "trip_map_$tripId", factory = tripMapViewModelFactory)
  val locationViewModel: LocationViewModel =
      viewModel(key = "trip_map_location_$tripId", factory = locationViewModelFactory)
  val tripUiState by tripMapViewModel.uiState.collectAsStateWithLifecycle()
  val locationUiState by locationViewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          results ->
        // Asked after the answer: only then does the system tell a refusal it will repeat from one
        // that is final.
        locationViewModel.onPermissionResult(
            granted = results.values.any { it },
            shouldShowRationale = permissionChecker.shouldShowRationale(),
        )
      }

  LaunchedEffect(locationViewModel) {
    locationViewModel.effects.collect { effect ->
      when (effect) {
        LocationEffect.RequestPermission -> permissionLauncher.launch(LOCATION_PERMISSIONS)
        LocationEffect.OpenSettings ->
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
      }
    }
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    locationViewModel.onPermissionChecked(permissionChecker.isGranted())
  }

  TripMapScreen(
      uiState = tripUiState,
      isSharing = locationUiState.isSharing,
      memberLocations = locationUiState.memberLocations,
      staleMemberIds = locationUiState.staleMemberIds,
      myLocation = locationUiState.shownMyLocation,
      locationErrorMessage = locationUiState.errorMessageRes,
      isOffline = locationUiState.isOffline,
      onRetry = tripMapViewModel::retryTrip,
      onRetryActivities = tripMapViewModel::retryActivities,
      onTurnOnSharing = { locationViewModel.onTurnOnSharingClick(permissionChecker.isGranted()) },
      onPauseSharing = locationViewModel::stopSharing,
      modifier = modifier,
  )
  LocationPermissionSheet(
      sheet = locationUiState.permissionSheet,
      onContinue = { locationViewModel.onRationaleContinue(permissionChecker.isGranted()) },
      onOpenSettings = locationViewModel::onOpenSettings,
      onDismiss = locationViewModel::onPermissionSheetDismissed,
  )
}

private val MyLocationDotSize = 18.dp
private val MyLocationDotBorderWidth = 3.dp

/**
 * Renders the trip map with its activity markers and the other members' locations for [uiState],
 * with a banner telling whether the user shares their location and a button to turn sharing on or
 * pause it. The map stays interactive and visible underneath the loading and error overlays.
 *
 * Member locations are shown whatever the user's own permission or sharing state, since seeing the
 * group does not depend on sharing with it.
 *
 * @param uiState state of the trip shown.
 * @param isSharing whether the user shares their location with the trip.
 * @param memberLocations locations of the other members currently sharing theirs.
 * @param staleMemberIds identifiers of the members whose location is outdated.
 * @param myLocation the user's own position, shown as a blue dot, or null to hide it. The caller
 *   passes it only while the user shares their location.
 * @param locationErrorMessage message telling why the last location request failed, shown once in a
 *   snackbar, or null if none. The snackbar is dismissed when it becomes null.
 * @param isOffline whether member locations cannot be refreshed because the device is offline.
 * @param nowMillis current time, in milliseconds since the epoch, used to tell how old each member
 *   location is. Refreshed every second by default while member locations are shown.
 * @param onRetry called when the user asks to load the trip again after an error.
 * @param onRetryActivities called when the user retries loading activities and suggestions.
 * @param onTurnOnSharing called when the user asks to start sharing their location.
 * @param onPauseSharing called when the user asks to stop sharing their location.
 * @param modifier modifier applied to the screen.
 */
@Composable
fun TripMapScreen(
    uiState: TripMapUiState,
    isSharing: Boolean,
    onRetry: () -> Unit,
    onRetryActivities: () -> Unit,
    onTurnOnSharing: () -> Unit,
    onPauseSharing: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    memberLocations: List<MemberLocation> = emptyList(),
    staleMemberIds: Set<String> = emptySet(),
    myLocation: MemberLocation? = null,
    @StringRes locationErrorMessage: Int? = null,
    isOffline: Boolean = false,
    nowMillis: Long = rememberCurrentTimeMillis(isTicking = memberLocations.isNotEmpty()),
) {
  // Until the trip is loaded the camera keeps its position, so the map stays usable underneath.
  val viewport = (uiState as? TripMapUiState.Loaded)?.viewport ?: TripMapViewport.None
  val onMarkerClick = rememberNotImplementedAction()
  val members = (uiState as? TripMapUiState.Loaded)?.members.orEmpty()
  val snackbarHostState = remember { SnackbarHostState() }
  val resources = LocalResources.current
  // Saved so that an error already shown is not shown again after a configuration change.
  var shownErrorMessage by rememberSaveable { mutableStateOf<Int?>(null) }
  LaunchedEffect(locationErrorMessage) {
    if (locationErrorMessage == null) {
      shownErrorMessage = null
      snackbarHostState.currentSnackbarData?.dismiss()
    } else if (locationErrorMessage != shownErrorMessage) {
      shownErrorMessage = locationErrorMessage
      snackbarHostState.showSnackbar(
          message = resources.getString(locationErrorMessage),
          withDismissAction = true,
      )
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    MapboxMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.trip_map_map),
        mapViewportState = rememberTripMapViewportState(viewport),
    ) {
      if (uiState is TripMapUiState.Loaded) {
        uiState.activityMarkers.forEach { marker ->
          key(marker.id) {
            ViewAnnotation(
                options =
                    viewAnnotationOptions {
                      geometry(Point.fromLngLat(marker.longitude, marker.latitude))
                      annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                    },
                modifier =
                    Modifier.testTag(TripMarkerTestTags.markerFor(marker.id))
                        .markNotImplemented(onMarkerClick)
                        .clickable(onClick = onMarkerClick),
            ) {
              TripMarkerIcon(locationName = marker.locationName, kind = marker.kind)
            }
          }
        }
      }
      memberLocations.forEach { location ->
        key(location.userId) {
          ViewAnnotation(
              options =
                  viewAnnotationOptions {
                    geometry(Point.fromLngLat(location.longitude, location.latitude))
                    annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                    // Members travel together, so their markers often collide. Mapbox would hide
                    // all but one of them by default.
                    allowOverlap(true)
                  }
          ) {
            MemberLocationMarker(
                location = location,
                // Until the trip and profiles load, the member is shown with the fallback name.
                member =
                    members.getOrElse(location.userId) { memberDisplayInfo(location.userId, null) },
                nowMillis = nowMillis,
                isStale = location.userId in staleMemberIds,
            )
          }
        }
      }
      // Declared last so that it is drawn above the member markers.
      if (myLocation != null) {
        ViewAnnotation(
            options =
                viewAnnotationOptions {
                  geometry(Point.fromLngLat(myLocation.longitude, myLocation.latitude))
                  annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
                  // Kept visible next to the members around the user.
                  allowOverlap(true)
                }
        ) {
          MyLocationDot()
        }
      }
    }

    // Stacked so that the status overlays never cover the banner or the sharing button.
    Column(modifier = Modifier.fillMaxSize()) {
      if (!readOnly) {
        SharingBanner(
            isSharing = isSharing,
            modifier =
                Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = Spacing.large, vertical = Spacing.cardPadding),
        )
      }
      if (isOffline) {
        OfflineBanner(
            modifier =
                Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            // Below the sharing banner, which already keeps clear of the top.
                            if (readOnly) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            else WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = Spacing.large)
        )
      }
      Box(
          modifier =
              Modifier.weight(1f)
                  .fillMaxWidth()
                  .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
          contentAlignment = Alignment.Center,
      ) {
        TripStatusOverlay(uiState = uiState, onRetry = onRetry)
        // Stacked so that the snackbar never covers the retry button.
        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          SnackbarHost(
              hostState = snackbarHostState,
              modifier = Modifier.testTag(C.Tag.trip_map_location_error),
          )
          if (
              uiState is TripMapUiState.Loaded &&
                  uiState.activitiesLoadState == ActivitiesLoadState.ERROR
          ) {
            Button(
                onClick = onRetryActivities,
                shape = MaterialTheme.shapes.medium,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier =
                    Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium)
                        .testTag(C.Tag.trip_map_retry_activities),
            ) {
              Text(stringResource(R.string.trip_map_retry_activities))
            }
          }
        }
      }
      if (!readOnly) {
        SharingButtonBar(
            isSharing = isSharing,
            onTurnOnSharing = onTurnOnSharing,
            onPauseSharing = onPauseSharing,
        )
      }
    }
  }
}

/** Tells the user that member locations cannot be refreshed while the device is offline. */
@Composable
private fun OfflineBanner(modifier: Modifier) {
  Surface(
      modifier =
          modifier.fillMaxWidth().testTag(C.Tag.trip_map_offline).semantics {
            liveRegion = LiveRegionMode.Polite
          },
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = Spacing.extraSmall,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(imageVector = Icons.Outlined.CloudOff, contentDescription = null, tint = Warning)
      Text(
          text = stringResource(R.string.trip_map_offline),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/** The user's own position: a blue dot, unlike the members' avatar markers. */
@Composable
internal fun MyLocationDot() {
  val description = stringResource(R.string.trip_map_my_location)
  Surface(
      modifier =
          Modifier.size(MyLocationDotSize)
              .semantics { contentDescription = description }
              .testTag(C.Tag.trip_map_my_location),
      shape = CircleShape,
      color = MyLocationBlue,
      border = BorderStroke(MyLocationDotBorderWidth, MaterialTheme.colorScheme.surface),
      shadowElevation = Spacing.extraSmall,
  ) {}
}

@Composable
private fun TripStatusOverlay(uiState: TripMapUiState, onRetry: () -> Unit) {
  val overlayModifier = Modifier.padding(Spacing.screenPadding)
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

/** Tells the user whether their location is shared with the trip. */
@Composable
private fun SharingBanner(isSharing: Boolean, modifier: Modifier = Modifier) {
  val (title, description) =
      if (isSharing) {
        R.string.trip_map_sharing_on_title to R.string.trip_map_sharing_on_description
      } else {
        R.string.trip_map_sharing_off_title to R.string.trip_map_sharing_off_description
      }
  InfoRow(
      icon = R.drawable.ic_bell,
      title = stringResource(title),
      subtitle = stringResource(description),
      modifier = modifier.testTag(C.Tag.trip_map_sharing_banner),
      containerColor = MaterialTheme.colorScheme.surface,
  )
}

/** Bottom bar holding the button that turns location sharing on or pauses it. */
@Composable
private fun SharingButtonBar(
    isSharing: Boolean,
    onTurnOnSharing: () -> Unit,
    onPauseSharing: () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
    TripActionButton(
        onClick = if (isSharing) onPauseSharing else onTurnOnSharing,
        modifier =
            Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(TripActionButtonPadding)
                .testTag(C.Tag.trip_map_sharing_button),
    ) {
      Text(
          stringResource(
              if (isSharing) R.string.trip_map_pause_sharing else R.string.trip_map_turn_on_sharing
          )
      )
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
