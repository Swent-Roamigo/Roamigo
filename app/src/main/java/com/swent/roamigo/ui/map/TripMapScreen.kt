// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.components.TripActionButtonPadding
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.photos.PhotoViewer
import com.swent.roamigo.ui.theme.MyLocationBlue
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.Success
import com.swent.roamigo.ui.theme.Warning
import com.swent.roamigo.ui.theme.WarningBorder
import com.swent.roamigo.ui.theme.WarningSurface

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
 * @param locationViewModelFactory creates the location ViewModel, or null while it is unavailable.
 * @param modifier modifier applied to the screen.
 * @param permissionChecker tells whether the location permission is granted, and whether the system
 *   would ask for it again after a refusal.
 * @param startActivity starts the activity of an intent, such as the app's settings.
 * @param readOnly whether location sharing controls and permission requests are disabled.
 * @param currentUserId the signed-in user, for the photo viewer's fallback photographer name.
 * @param uriNavigator opens directions to an activity in another app. A toast tells the user when
 *   no app can.
 */
@Composable
fun TripMapRoute(
    tripId: String,
    tripMapViewModelFactory: ViewModelProvider.Factory,
    locationViewModelFactory: ViewModelProvider.Factory?,
    modifier: Modifier = Modifier,
    permissionChecker: LocationPermissionChecker = rememberLocationPermissionChecker(),
    startActivity: (Intent) -> Unit = LocalContext.current::startActivity,
    readOnly: Boolean = false,
    currentUserId: String? = null,
    uriNavigator: UriNavigator = rememberUriNavigator(),
) {
  val tripMapViewModel: TripMapViewModel =
      viewModel(key = "trip_map_$tripId", factory = tripMapViewModelFactory)
  val locationViewModel: LocationViewModel? = locationViewModelFactory?.let {
    viewModel(key = "trip_map_location_$tripId", factory = it)
  }
  // This route is only composed for the map, so the trip's photos start loading here.
  LaunchedEffect(tripMapViewModel) { tripMapViewModel.onMapShown() }
  val tripUiState by tripMapViewModel.uiState.collectAsStateWithLifecycle()
  val locationUiState =
      locationViewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: LocationUiState()
  val context = LocalContext.current

  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          results ->
        // The trip may become read-only while the system permission dialog is open.
        if (readOnly) locationViewModel?.onPermissionSheetDismissed()
        // Asked after the answer: only then does the system tell a refusal it will repeat from one
        // that is final.
        locationViewModel?.onPermissionResult(
            granted = results.values.any { it },
            shouldShowRationale = permissionChecker.shouldShowRationale(),
        )
      }

  LaunchedEffect(locationViewModel, readOnly) {
    if (readOnly) locationViewModel?.onPermissionSheetDismissed()
    locationViewModel?.effects?.collect { effect ->
      if (readOnly) return@collect
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
    locationViewModel?.onPermissionChecked(permissionChecker.isGranted())
  }

  TripMapScreen(
      uiState = tripUiState,
      isSharing = locationUiState.isSharing,
      isStartingSharing = locationUiState.isStartingSharing,
      memberLocations = locationUiState.memberLocations,
      staleMemberIds = locationUiState.staleMemberIds,
      myLocation = locationUiState.shownMyLocation,
      locationErrorMessage = locationUiState.errorMessageRes,
      isOffline = locationUiState.isOffline,
      onRetry = tripMapViewModel::retryTrip,
      onRetryActivities = tripMapViewModel::retryActivities,
      onRetryPhotos = tripMapViewModel::retryPhotos,
      onPhotoMarkerClick = tripMapViewModel::selectPhotoGroup,
      onPhotoViewerClose = tripMapViewModel::closePhotoViewer,
      onActivityMarkerClick = tripMapViewModel::selectActivity,
      onActivityPopupDismiss = tripMapViewModel::clearActivitySelection,
      onDirectionsClick = { activity ->
        if (!uriNavigator.openDirectionsTo(activity)) {
          Toast.makeText(context, R.string.trip_map_directions_no_app, Toast.LENGTH_SHORT).show()
        }
      },
      currentUserId = currentUserId,
      onTurnOnSharing = { locationViewModel?.onTurnOnSharingClick(permissionChecker.isGranted()) },
      onPauseSharing = { locationViewModel?.stopSharing() },
      modifier = modifier,
      readOnly = readOnly || locationViewModel == null,
  )
  if (!readOnly) {
    LocationPermissionSheet(
        sheet = locationUiState.permissionSheet,
        onContinue = { locationViewModel?.onRationaleContinue(permissionChecker.isGranted()) },
        onOpenSettings = { locationViewModel?.onOpenSettings() },
        onDismiss = { locationViewModel?.onPermissionSheetDismissed() },
    )
  }
}

private val MyLocationDotSize = 18.dp
private val MapCardShape = RoundedCornerShape(20.dp)
private val SHARING_ICON_SIZE = 20.dp
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
 * @param isStartingSharing whether sharing is being turned on: the chip says so and the sharing
 *   button shows a progress indicator and cannot be clicked. Ignored once [isSharing] is true.
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
 * @param cameraState UI-owned camera interaction state for gestures and pending viewport updates.
 * @param modifier modifier applied to the screen.
 * @param onRetryPhotos called when the user retries loading the trip's photos.
 * @param onPhotoMarkerClick called with the coordinate of the photo group the user tapped. The
 *   viewer shows [TripMapUiState.Loaded.selectedPhotoGroup].
 * @param onPhotoViewerClose called when the user closes the viewer.
 * @param onActivityMarkerClick called with the marker ID of the activity or suggestion the user
 *   tapped. The popup shows [TripMapUiState.Loaded.selectedActivity].
 * @param onActivityPopupDismiss called when the user taps the map outside of any marker.
 * @param onDirectionsClick called with the activity whose popup asked for directions.
 * @param onActivityDetailsClick called when the popup's "Details" button is tapped. Placeholder
 *   feedback by default, until the activity details screen exists.
 * @param currentUserId the signed-in user, for the viewer's fallback photographer name.
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
    onRetryPhotos: () -> Unit = {},
    onPhotoMarkerClick: (MapCoordinate) -> Unit = {},
    onPhotoViewerClose: () -> Unit = {},
    onActivityMarkerClick: (String) -> Unit = {},
    onActivityPopupDismiss: () -> Unit = {},
    onDirectionsClick: (TripMarkerUiState) -> Unit = {},
    onActivityDetailsClick: () -> Unit = rememberNotImplementedAction(),
    currentUserId: String? = null,
    readOnly: Boolean = false,
    memberLocations: List<MemberLocation> = emptyList(),
    staleMemberIds: Set<String> = emptySet(),
    myLocation: MemberLocation? = null,
    @StringRes locationErrorMessage: Int? = null,
    isOffline: Boolean = false,
    nowMillis: Long = rememberCurrentTimeMillis(isTicking = memberLocations.isNotEmpty()),
    isStartingSharing: Boolean = false,
    cameraState: TripMapCameraState = rememberTripMapCameraState(),
) {
  // Until the trip is loaded the camera keeps its position, so the map stays usable underneath.
  val viewport = (uiState as? TripMapUiState.Loaded)?.viewport ?: TripMapViewport.None
  val mapState = rememberTripMapGesturesState { cameraState.onCameraGesture(viewport) }
  val mapViewportState =
      rememberTripMapViewportState(viewport, autoFrame = !cameraState.hasUserMovedCamera)
  val activityMarkerClickLabel = stringResource(R.string.trip_map_activity_marker_open)
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

  val loaded = uiState as? TripMapUiState.Loaded
  val selectedPhotoGroup = loaded?.selectedPhotoGroup
  val selectedActivity = loaded?.selectedActivity

  // The viewer is drawn over the map instead of replacing it, so the camera is kept when it closes.
  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      // The card has no bottom padding: the button's own padding spaces it from the map, as on the
      // other tabs, and keeps the button at the same place and width as their main action.
      Box(
          modifier =
              Modifier.weight(1f)
                  .fillMaxWidth()
                  .padding(
                      start = Spacing.screenPadding,
                      top = Spacing.medium,
                      end = Spacing.screenPadding,
                  )
                  .clip(MapCardShape)
      ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize().testTag(C.Tag.trip_map_map),
            mapViewportState = mapViewportState,
            mapState = mapState,
            // Top end: the banners are stacked at the top start, and the logo and attribution stay
            // at the bottom.
            scaleBar = {
              ScaleBar(
                  contentPadding = PaddingValues(top = Spacing.medium, end = Spacing.medium),
                  alignment = Alignment.TopEnd,
              )
            },
            attribution = {},
            // Taps on markers and on the popup are handled by them and never reach the map.
            onMapClickListener = {
              onActivityPopupDismiss()
              false
            },
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
                        Modifier.testTag(TripMarkerTestTags.markerFor(marker.id)).clickable(
                            onClickLabel = activityMarkerClickLabel,
                            role = Role.Button,
                        ) {
                          onActivityMarkerClick(marker.id)
                        },
                ) {
                  TripMarkerIcon(locationName = marker.locationName, kind = marker.kind)
                }
              }
            }
            uiState.photoMarkers.forEach { marker ->
              key(marker.coordinate) {
                PhotoMarkerAnnotation(marker = marker, onClick = onPhotoMarkerClick)
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
                        // Members travel together, so their markers often collide. Mapbox
                        // would hide all but one of them by default.
                        allowOverlap(true)
                      }
              ) {
                MemberLocationMarker(
                    location = location,
                    // Until the trip and profiles load, the member is shown with the fallback name.
                    member =
                        members.getOrElse(location.userId) {
                          memberDisplayInfo(location.userId, null)
                        },
                    nowMillis = nowMillis,
                    isStale = location.userId in staleMemberIds,
                )
              }
            }
          }
          // Declared after the member markers so that it is drawn above them.
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
          // Declared last so that the popup is drawn above every marker.
          selectedActivity?.let { activity ->
            key(activity.id) {
              ActivityPopupAnnotation(
                  activity = activity,
                  onDirectionsClick = { onDirectionsClick(activity) },
                  onDetailsClick = onActivityDetailsClick,
              )
            }
          }
        }

        // Stacked so that the status overlays never cover the sharing chip.
        Column(modifier = Modifier.fillMaxSize()) {
          TripMapStatusBanners(
              readOnly = readOnly,
              isSharing = isSharing,
              isStartingSharing = isStartingSharing,
              isOffline = isOffline,
              loaded = loaded,
          )
          Box(
              modifier =
                  Modifier.weight(1f)
                      .fillMaxWidth()
                      .windowInsetsPadding(
                          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                      ),
              contentAlignment = Alignment.Center,
          ) {
            TripStatusOverlay(uiState = uiState, onRetry = onRetry)
            TripMapBottomActions(
                snackbarHostState = snackbarHostState,
                showAllLocations =
                    if (cameraState.shouldShowAllLocations(viewport)) {
                      cameraState::showAllLocations
                    } else {
                      null
                    },
                onRetryActivities =
                    onRetryActivities.takeIf {
                      loaded?.activitiesLoadState == ActivitiesLoadState.ERROR
                    },
                onRetryPhotos =
                    onRetryPhotos.takeIf { loaded?.photosLoadState == PhotosLoadState.ERROR },
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        ),
            )
          }
        }
      }
      if (!readOnly) {
        SharingButtonBar(
            isSharing = isSharing,
            isStartingSharing = isStartingSharing,
            onTurnOnSharing = onTurnOnSharing,
            onPauseSharing = onPauseSharing,
        )
      }
    }
    if (selectedPhotoGroup != null) {
      // Keyed so that another group opens on its newest photo.
      key(selectedPhotoGroup.coordinate) {
        PhotoViewer(
            photos = selectedPhotoGroup.photos,
            photographerNames = loaded.photographerNames,
            onClose = onPhotoViewerClose,
            currentUserId = currentUserId,
        )
      }
    }
  }
}

/** A clickable photo group hanging below its coordinate. */
@Composable
private fun PhotoMarkerAnnotation(marker: PhotoMarkerUiState, onClick: (MapCoordinate) -> Unit) {
  val description =
      pluralStringResource(
          R.plurals.trip_map_photo_marker_description,
          marker.photoCount,
          marker.photoCount,
      )
  val clickLabel = stringResource(R.string.trip_map_photo_marker_open)
  val gapPx = with(LocalDensity.current) { PhotoMarkerGap.toPx().toDouble() }
  ViewAnnotation(
      options =
          viewAnnotationOptions {
            geometry(Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude))
            // Pins and member avatars hang above their point, so a photo group hanging below its
            // own does not collide with them when taken at the same place. Other collisions keep
            // Mapbox's default: a hidden group reappears once zooming in separates it.
            // Touching bounds already count as a collision, hence the gap. Mapbox's positive
            // offsets point up.
            annotationAnchor {
              anchor(ViewAnnotationAnchor.TOP)
              offsetY(-gapPx)
            }
          },
      modifier =
          Modifier.testTag(photoMarkerTag(marker.coordinate))
              .semantics { contentDescription = description }
              .clickable(onClickLabel = clickLabel, role = Role.Button) {
                onClick(marker.coordinate)
              },
  ) {
    PhotoMarkerIcon(photoCount = marker.photoCount)
  }
}

/**
 * The banners stacked at the top of the map, emitted into the caller's column so that the status
 * overlays below them never cover the sharing chip.
 */
@Composable
private fun TripMapStatusBanners(
    readOnly: Boolean,
    isSharing: Boolean,
    isStartingSharing: Boolean,
    isOffline: Boolean,
    loaded: TripMapUiState.Loaded?,
) {
  if (!readOnly) {
    SharingChip(
        isSharing = isSharing,
        isStartingSharing = isStartingSharing,
        modifier = Modifier.padding(Spacing.medium),
    )
  }
  if (isOffline) {
    OfflineBanner(modifier = Modifier.padding(horizontal = Spacing.medium))
  }
  if (loaded != null) {
    PhotosStatusPill(
        state = loaded.photosLoadState,
        hasMarkers = loaded.photoMarkers.isNotEmpty(),
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
    )
  }
}

/**
 * The location error snackbar and the map's actions, stacked so that the snackbar never covers
 * them. An action is shown only when its callback is not null.
 */
@Composable
private fun TripMapBottomActions(
    snackbarHostState: SnackbarHostState,
    showAllLocations: (() -> Unit)?,
    onRetryActivities: (() -> Unit)?,
    onRetryPhotos: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.testTag(C.Tag.trip_map_location_error),
    )
    showAllLocations?.let {
      TripMapActionButton(
          text = R.string.trip_map_show_all_locations,
          tag = C.Tag.trip_map_show_all_locations,
          onClick = it,
      )
    }
    onRetryActivities?.let {
      TripMapActionButton(
          text = R.string.trip_map_retry_activities,
          tag = C.Tag.trip_map_retry_activities,
          onClick = it,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
      )
    }
    onRetryPhotos?.let {
      TripMapActionButton(
          text = R.string.trip_map_retry_photos,
          tag = C.Tag.trip_map_retry_photos,
          onClick = it,
      )
    }
  }
}

@Composable
private fun TripMapActionButton(
    @StringRes text: Int,
    tag: String,
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
  Button(
      onClick = onClick,
      shape = MaterialTheme.shapes.medium,
      colors = colors,
      modifier =
          Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium)
              .testTag(tag),
  ) {
    Text(stringResource(text))
  }
}

/**
 * Tells whether the trip's photos are loading, missing or failed, without covering the map. Nothing
 * is shown once photo markers are displayed, except a failure, which keeps them visible.
 */
@Composable
private fun PhotosStatusPill(state: PhotosLoadState, hasMarkers: Boolean, modifier: Modifier) {
  val (tag, text) =
      when (state) {
        PhotosLoadState.LOADING -> C.Tag.trip_map_photos_loading to R.string.trip_map_photos_loading
        PhotosLoadState.ERROR -> C.Tag.trip_map_photos_error to R.string.trip_map_photos_error
        PhotosLoadState.LOADED ->
            if (hasMarkers) return
            else C.Tag.trip_map_photos_empty to R.string.trip_map_photos_empty
      }
  Surface(
      modifier = modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      if (state == PhotosLoadState.LOADING) {
        CircularProgressIndicator(
            modifier = Modifier.size(SharingProgressSize),
            strokeWidth = SharingProgressStrokeWidth,
        )
      }
      Text(
          text = stringResource(text),
          style = MaterialTheme.typography.labelLarge,
          color =
              if (state == PhotosLoadState.ERROR) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurface,
      )
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

/**
 * A pill over the map telling whether the user shares their location. The action that changes it is
 * the bottom button, the same shared button as on the other trip tabs. Sharing off is tinted, since
 * it is the state the user may want to change.
 */
@Composable
private fun SharingChip(
    isSharing: Boolean,
    isStartingSharing: Boolean,
    modifier: Modifier = Modifier,
) {
  val isStarting = isStartingSharing && !isSharing
  // Only "off" is tinted: it is the state the user may want to change.
  val isOff = !isSharing && !isStarting
  val title =
      when {
        isSharing -> R.string.trip_map_sharing_on_title
        isStarting -> R.string.trip_map_sharing_starting_title
        else -> R.string.trip_map_sharing_off_title
      }
  Surface(
      modifier = modifier.testTag(C.Tag.trip_map_sharing_banner),
      shape = CircleShape,
      color = if (isOff) WarningSurface else MaterialTheme.colorScheme.surface,
      border = if (isOff) BorderStroke(1.dp, WarningBorder) else null,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
          imageVector = if (isOff) Icons.Outlined.LocationOff else Icons.Outlined.LocationOn,
          contentDescription = null,
          modifier = Modifier.size(SHARING_ICON_SIZE),
          tint =
              when {
                isSharing -> Success
                isStarting -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> Warning
              },
      )
      Text(
          text = stringResource(title),
          style = MaterialTheme.typography.labelLarge,
          color = if (isOff) Warning else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/** Bottom bar holding the button that turns location sharing on or pauses it. */
@Composable
private fun SharingButtonBar(
    isSharing: Boolean,
    isStartingSharing: Boolean,
    onTurnOnSharing: () -> Unit,
    onPauseSharing: () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
    // Pausing stays possible once sharing is on, so only a pending start blocks clicks.
    val isStarting = isStartingSharing && !isSharing
    TripActionButton(
        onClick = if (isSharing) onPauseSharing else onTurnOnSharing,
        loading = isStarting,
        modifier =
            Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(TripActionButtonPadding)
                .testTag(C.Tag.trip_map_sharing_button),
    ) {
      if (isStarting) {
        CircularProgressIndicator(
            modifier = Modifier.size(SharingProgressSize).testTag(C.Tag.trip_map_sharing_loading),
            strokeWidth = SharingProgressStrokeWidth,
            color = LocalContentColor.current,
        )
        Spacer(Modifier.width(Spacing.small))
        // Doubles as the accessible description of the button while it is loading.
        Text(stringResource(R.string.trip_map_sharing_starting_button))
      } else {
        Text(
            stringResource(
                if (isSharing) R.string.trip_map_pause_sharing
                else R.string.trip_map_turn_on_sharing
            )
        )
      }
    }
  }
}

private val PhotoMarkerGap = 4.dp
private val SharingProgressSize = 20.dp
private val SharingProgressStrokeWidth = 2.dp

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
