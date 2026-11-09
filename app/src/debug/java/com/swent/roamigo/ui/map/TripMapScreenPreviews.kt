// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.util.Date
import kotlin.math.roundToInt

class TripMapScreenPreviews {
  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapLoadingPreview() {
    TripMapPreview(uiState = TripMapUiState.Loading, isSharing = false)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapErrorPreview() {
    TripMapPreview(
        uiState = TripMapUiState.Error(TripMapViewModel.LOAD_FAILED_MESSAGE),
        isSharing = false,
    )
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapSharingOffPreview() {
    TripMapPreview(uiState = previewTripMapLoadedState, isSharing = false)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapSharingOnPreview() {
    TripMapPreview(uiState = previewTripMapLoadedState, isSharing = true)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapSharingWithMembersPreview() {
    TripMapPreview(
        uiState = previewTripMapWithMembersState,
        locationState = previewSharingLocationState,
    )
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapSharingOffWithMembersPreview() {
    TripMapPreview(
        uiState = previewTripMapWithMembersState,
        locationState = previewSharingLocationState.copy(isSharing = false),
    )
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapOfflinePreview() {
    // Offline, every member location shown is outdated.
    TripMapPreview(
        uiState = previewTripMapWithMembersState,
        locationState =
            previewSharingLocationState.copy(
                isOffline = true,
                staleMemberIds = previewMemberLocations.mapTo(mutableSetOf()) { it.userId },
            ),
    )
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapActivitiesAndSuggestionsPreview() {
    val locations =
        previewTripWithActivities.destinations + previewTripActivities.map { it.location }
    TripMapPreview(
        uiState =
            previewTripMapLoadedState.copy(
                trip = previewTripWithActivities,
                viewport =
                    tripMapViewportOf(locations.map { MapCoordinate(it.latitude, it.longitude) }),
                activityMarkers = previewTripActivities.toTripMarkerUiStates(),
            ),
        isSharing = false,
    )
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapFakeRepositoryPreview() {
    val factory = remember {
      val repository =
          FakeTripRepository(
              trips = listOf(previewTripWithActivities),
              activitiesByTripId = mapOf(previewTripWithActivities.uid to previewTripActivities),
          )
      TripMapViewModel.factory(
          tripId = previewTripWithActivities.uid,
          tripRepository =
              object : TripRepository by repository {
                private var firstActivityRequest = true

                override suspend fun getActivitiesForTrip(tripId: String): List<Activity> {
                  if (firstActivityRequest) {
                    firstActivityRequest = false
                    throw IllegalStateException("Simulated activity loading failure")
                  }
                  return repository.getActivitiesForTrip(tripId)
                }
              },
          userRepository =
              FakeUserRepository(
                  User(
                      uid = previewTripWithActivities.ownerId,
                      displayName = "Alex",
                      profilePictureUrl = null,
                  )
              ),
      )
    }
    val tripMapViewModel: TripMapViewModel =
        viewModel(key = "trip_map_fake_repository_preview", factory = factory)
    val uiState by tripMapViewModel.uiState.collectAsStateWithLifecycle()

    TripMapPreview(
        uiState = uiState,
        isSharing = false,
        onRetry = tripMapViewModel::retryTrip,
        onRetryActivities = tripMapViewModel::retryActivities,
    )
  }

  @Composable
  private fun TripMapPreview(
      uiState: TripMapUiState,
      isSharing: Boolean,
      onRetry: () -> Unit = {},
      onRetryActivities: () -> Unit = {},
  ) {
    TripMapPreview(
        uiState = uiState,
        locationState = LocationUiState(isSharing = isSharing),
        onRetry = onRetry,
        onRetryActivities = onRetryActivities,
    )
  }

  /** Passes [locationState] to the screen the way [TripMapRoute] does. */
  @Composable
  private fun TripMapPreview(
      uiState: TripMapUiState,
      locationState: LocationUiState,
      onRetry: () -> Unit = {},
      onRetryActivities: () -> Unit = {},
  ) {
    RoamigoTheme {
      CompositionLocalProvider(
          LocalTripMapStandIn provides
              { content, modifier ->
                TripMapPreviewMap(content = content, modifier = modifier)
              }
      ) {
        TripMapScreen(
            uiState = uiState,
            isSharing = locationState.isSharing,
            memberLocations = locationState.memberLocations,
            staleMemberIds = locationState.staleMemberIds,
            myLocation = locationState.shownMyLocation,
            locationErrorMessage = locationState.errorMessageRes,
            isOffline = locationState.isOffline,
            nowMillis = PREVIEW_NOW_MILLIS,
            onRetry = onRetry,
            onRetryActivities = onRetryActivities,
            onTurnOnSharing = {},
            onPauseSharing = {},
        )
      }
    }
  }
}

private val previewTrip =
    Trip(
        uid = "trip_lisbon",
        name = "Lisbon & Porto",
        description = null,
        destinations =
            listOf(TripLocation(latitude = 38.7223, longitude = -9.1393, name = "Lisbon")),
        startDate = Timestamp.now(),
        endDate = Timestamp.now(),
        flexibleDates = false,
        travelMode = TravelMode.GROUP,
        travelerCount = 3,
        ownerId = "user_1",
        memberIds = listOf("user_1"),
        status = TripStatus.PLANNED,
        createdAt = Timestamp.now(),
    )

/** A loaded trip map, shared by the trip map previews. */
internal val previewTripMapLoadedState =
    TripMapUiState.Loaded(
        trip = previewTrip,
        viewport = TripMapViewport.Centered(latitude = 38.7223, longitude = -9.1393),
        members = mapOf("user_1" to memberDisplayInfo("user_1", null)),
    )

/** Fixed time of the previews, so that the freshness labels do not change between renders. */
private const val PREVIEW_NOW_MILLIS = 1_762_768_800_000L

// Members around Baixa, close enough to the destination to share the screen with it.
private val previewMemberLocations =
    listOf(
        MemberLocation("user_2", 38.7238, -9.1412, PREVIEW_NOW_MILLIS - 2_000),
        MemberLocation("user_3", 38.7205, -9.1370, PREVIEW_NOW_MILLIS - 25_000),
        MemberLocation("user_4", 38.7250, -9.1360, PREVIEW_NOW_MILLIS - 3 * 60_000),
    )

private val previewMyLocation = MemberLocation("user_1", 38.7215, -9.1400, PREVIEW_NOW_MILLIS)

/** A loaded trip map with four members, framed on the destination and every shown location. */
private val previewTripMapWithMembersState =
    previewTripMapLoadedState.copy(
        trip = previewTrip.copy(memberIds = listOf("user_1", "user_2", "user_3", "user_4")),
        viewport =
            tripMapViewportOf(
                (previewTrip.destinations.map { MapCoordinate(it.latitude, it.longitude) } +
                    (previewMemberLocations + previewMyLocation).map {
                      MapCoordinate(it.latitude, it.longitude)
                    })
            ),
        members =
            mapOf(
                "user_1" to memberDisplayInfo("user_1", User("user_1", "Alex", null)),
                "user_2" to memberDisplayInfo("user_2", User("user_2", "Sam", null)),
                "user_3" to memberDisplayInfo("user_3", User("user_3", "Kim", null)),
                "user_4" to memberDisplayInfo("user_4", User("user_4", "Noa", null)),
            ),
    )

/** The user shares their position, and Noa's location is outdated. */
private val previewSharingLocationState =
    LocationUiState(
        permissionState = LocationPermissionState.Granted,
        isSharing = true,
        myLocation = previewMyLocation,
        memberLocations = previewMemberLocations,
        staleMemberIds = setOf("user_4"),
    )

private val previewTripWithActivities =
    previewTrip.copy(
        name = "Three days in Lisbon",
        startDate = previewTimestamp("2026-11-10T00:00:00Z"),
        endDate = previewTimestamp("2026-11-12T23:59:59Z"),
        createdAt = previewTimestamp("2026-11-01T00:00:00Z"),
    )

private val previewTripActivities =
    listOf(
        previewActivity(
            uid = "alfama_walk",
            name = "Alfama walking tour",
            location = TripLocation(latitude = 38.7111, longitude = -9.1290, name = "Alfama"),
            startTime = "2026-11-10T10:00:00Z",
        ),
        previewActivity(
            uid = "belem_tower",
            name = "Visit Belém Tower",
            location = TripLocation(latitude = 38.6916, longitude = -9.2160, name = "Belém Tower"),
            startTime = "2026-11-11T10:00:00Z",
        ),
        previewActivity(
            uid = "oceanarium",
            name = "Explore the oceanarium",
            location = TripLocation(latitude = 38.7636, longitude = -9.0938, name = "Oceanarium"),
            startTime = "2026-11-12T10:00:00Z",
        ),
        previewActivity(
            uid = "lx_factory",
            name = "Explore LX Factory",
            location = TripLocation(latitude = 38.7034, longitude = -9.1787, name = "LX Factory"),
            startTime = "2026-11-11T14:00:00Z",
            activityType = ActivityType.SUGGESTION,
        ),
        previewActivity(
            uid = "gulbenkian_museum",
            name = "Visit Gulbenkian Museum",
            location =
                TripLocation(latitude = 38.7379, longitude = -9.1544, name = "Gulbenkian Museum"),
            startTime = "2026-11-12T14:00:00Z",
            activityType = ActivityType.SUGGESTION,
        ),
    )

private fun previewActivity(
    uid: String,
    name: String,
    location: TripLocation,
    startTime: String,
    activityType: ActivityType = ActivityType.ACCEPTED,
): Activity =
    Activity(
        uid = uid,
        tripId = previewTripWithActivities.uid,
        activityType = activityType,
        name = name,
        description = null,
        location = location,
        startTime = previewTimestamp(startTime),
        endTime = null,
        createdByUserId = previewTripWithActivities.ownerId,
        creationTime = previewTripWithActivities.createdAt,
    )

private fun previewTimestamp(isoDateTime: String): Timestamp =
    Timestamp(Date.from(Instant.parse(isoDateTime)))

/**
 * Share of the stand-in's width and height kept free on each side, so that markers anchored on the
 * outermost locations stay on screen.
 */
internal const val PREVIEW_MAP_PADDING = 0.15f

/**
 * Projects coordinates linearly into a unit box, framing all of [frame] with [padding] on each
 * side. North is up. An axis along which the frame has no extent is centered.
 *
 * @param frame the coordinates that must fit in the box.
 * @param padding share of the box kept free on each side, between 0 and 0.5.
 * @return a function giving the position of a coordinate, as fractions of the box's width and
 *   height.
 */
internal fun previewMapProjection(
    frame: List<MapCoordinate>,
    padding: Float = PREVIEW_MAP_PADDING,
): (MapCoordinate) -> Offset {
  val minLatitude = frame.minOfOrNull { it.latitude } ?: 0.0
  val maxLatitude = frame.maxOfOrNull { it.latitude } ?: 0.0
  val minLongitude = frame.minOfOrNull { it.longitude } ?: 0.0
  val maxLongitude = frame.maxOfOrNull { it.longitude } ?: 0.0

  fun scale(value: Double, min: Double, max: Double): Float =
      if (max == min) 0.5f
      else padding + ((value - min) / (max - min)).toFloat() * (1 - 2 * padding)

  return { coordinate ->
    Offset(
        x = scale(coordinate.longitude, minLongitude, maxLongitude),
        y = 1f - scale(coordinate.latitude, minLatitude, maxLatitude),
    )
  }
}

/**
 * Static stand-in for the Mapbox map of [TripMapScreen], used only in Android Studio previews.
 * Mapbox and its view annotations cannot render there, so the previews would show none of the
 * markers.
 *
 * The markers are the real map's, placed with [previewMapProjection] so that the viewport and every
 * marker are framed, and anchored like the real map's annotations.
 */
@Composable
internal fun TripMapPreviewMap(content: TripMapStandInContent, modifier: Modifier = Modifier) {
  Layout(
      content = {
        content.activityMarkers.forEach { marker ->
          Box(Modifier.pin(marker.latitude, marker.longitude, PinAnchor.BOTTOM)) {
            TripMarkerIcon(locationName = marker.locationName, kind = marker.kind)
          }
        }
        content.memberLocations.forEach { location ->
          MemberLocationMarker(
              location = location,
              member = content.members[location.userId] ?: memberDisplayInfo(location.userId, null),
              nowMillis = content.nowMillis,
              isStale = location.userId in content.staleMemberIds,
              modifier = Modifier.pin(location.latitude, location.longitude, PinAnchor.BOTTOM),
          )
        }
        content.myLocation?.let { myLocation ->
          Box(Modifier.pin(myLocation.latitude, myLocation.longitude, PinAnchor.CENTER)) {
            MyLocationDot()
          }
        }
      },
      modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
  ) { measurables, constraints ->
    val pins = measurables.map { it.layoutId as Pin }
    val project = previewMapProjection(content.viewport.coordinates() + pins.map { it.coordinate })
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeables.zip(pins).forEach { (placeable, pin) ->
        val position = project(pin.coordinate)
        val anchorY =
            when (pin.anchor) {
              PinAnchor.BOTTOM -> placeable.height
              PinAnchor.CENTER -> placeable.height / 2
            }
        placeable.place(
            x = (position.x * constraints.maxWidth).roundToInt() - placeable.width / 2,
            y = (position.y * constraints.maxHeight).roundToInt() - anchorY,
        )
      }
    }
  }
}

private enum class PinAnchor {
  BOTTOM,
  CENTER,
}

private data class Pin(val coordinate: MapCoordinate, val anchor: PinAnchor)

private fun Modifier.pin(latitude: Double, longitude: Double, anchor: PinAnchor): Modifier =
    layoutId(Pin(MapCoordinate(latitude, longitude), anchor))

private fun TripMapViewport.coordinates(): List<MapCoordinate> =
    when (this) {
      TripMapViewport.None -> emptyList()
      is TripMapViewport.Centered -> listOf(MapCoordinate(latitude, longitude))
      is TripMapViewport.FitAll -> coordinates
    }
