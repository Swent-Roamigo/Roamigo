// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

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
  private fun TripMapOfflinePreview() {
    TripMapPreview(uiState = previewTripMapLoadedState, isSharing = true, isOffline = true)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapStartingSharingPreview() {
    TripMapPreview(
        uiState = previewTripMapLoadedState,
        isSharing = false,
        isStartingSharing = true,
    )
  }

  /**
   * Mapbox only draws a placeholder in previews, so the markers are shown on their own: a live, a
   * recent and a stale member location, then the user's own position.
   */
  @Preview(showBackground = true)
  @Composable
  private fun TripMapLocationMarkersPreview() {
    RoamigoTheme {
      Row(
          modifier = Modifier.padding(Spacing.medium),
          horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        listOf("Sam" to 2_000L, "Kim" to 25_000L, "Noa" to 3 * 60_000L).forEach { (name, ageMillis)
          ->
          MemberLocationMarker(
              location = MemberLocation(name, 0.0, 0.0, PREVIEW_NOW_MILLIS - ageMillis),
              member = memberDisplayInfo(name, User(name, name, null)),
              nowMillis = PREVIEW_NOW_MILLIS,
              isStale = ageMillis > 60_000L,
          )
        }
        MyLocationDot()
      }
    }
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

  /** Updated activity bounds are pending after the user moved the map; clicking frames them. */
  @Preview(name = "Show all locations", showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapShowAllLocationsPreview() {
    val cameraState = remember {
      TripMapCameraState().apply { onCameraGesture(previewTripMapLoadedState.viewport) }
    }
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
        cameraState = cameraState,
    )
  }

  /**
   * Three photos taken in different parts of Lisbon, shown as three separate markers. Mapbox draws
   * only a placeholder in the preview pane, so run the preview on a device to see the markers.
   */
  @Preview(name = "Photos around the city", showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapScatteredPhotosPreview() {
    TripMapPhotosPreview(
        tripId = "trip_lisbon_scattered_photos",
        photos =
            remember {
              listOf(
                  previewPhoto("alfama", PREVIEW_ALFAMA, PREVIEW_RED, minutesAgo = 10),
                  previewPhoto("belem", PREVIEW_BELEM, PREVIEW_GREEN, minutesAgo = 20),
                  previewPhoto("baixa", PREVIEW_BAIXA, PREVIEW_BLUE, minutesAgo = 30),
              )
            },
    )
  }

  /**
   * Three photos taken at the same place, shown as one marker with a count of three. Mapbox draws
   * only a placeholder in the preview pane, so run the preview on a device to see the marker.
   */
  @Preview(name = "Photos at one place", showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapStackedPhotosPreview() {
    TripMapPhotosPreview(
        tripId = "trip_lisbon_stacked_photos",
        photos =
            remember {
              listOf(
                  previewPhoto("first", PREVIEW_ALFAMA, PREVIEW_RED, minutesAgo = 10),
                  previewPhoto("second", PREVIEW_ALFAMA, PREVIEW_GREEN, minutesAgo = 20),
                  previewPhoto("third", PREVIEW_ALFAMA, PREVIEW_BLUE, minutesAgo = 30),
              )
            },
    )
  }

  @Composable
  /**
   * Shows [TripMapRoute] for a copy of the preview trip identified by [tripId], whose gallery holds
   * [photos]. The trip is given its own ID so each preview gets its own ViewModel.
   */
  private fun TripMapPhotosPreview(tripId: String, photos: List<TripPhoto>) {
    val factory = remember {
      val trip = previewTrip.copy(uid = tripId)
      TripMapViewModel.factory(
          tripId = trip.uid,
          tripRepository = FakeTripRepository(trips = listOf(trip)),
          activityRepository = FakeActivityRepository(),
          userRepository =
              FakeUserRepository(
                  User(uid = trip.ownerId, displayName = "Alex", profilePictureUrl = null)
              ),
          photoRepository = FakePhotoRepository().apply { setUploaded(photos) },
      )
    }
    RoamigoTheme {
      TripMapRoute(
          tripId = tripId,
          tripMapViewModelFactory = factory,
          locationViewModelFactory = null,
          currentUserId = previewTrip.ownerId,
      )
    }
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapFakeRepositoryPreview() {
    val factory = remember {
      val repository = FakeActivityRepository(previewTripActivities)
      TripMapViewModel.factory(
          tripId = previewTripWithActivities.uid,
          tripRepository = FakeTripRepository(trips = listOf(previewTripWithActivities)),
          activityRepository =
              object : ActivityRepository by repository {
                private var firstActivityRequest = true

                override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
                  if (firstActivityRequest) {
                    firstActivityRequest = false
                    throw IllegalStateException("Simulated activity loading failure")
                  }
                  emitAll(repository.observeActivities(tripId))
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
          photoRepository = FakePhotoRepository(),
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
      isOffline: Boolean = false,
      isStartingSharing: Boolean = false,
      cameraState: TripMapCameraState = rememberTripMapCameraState(),
  ) {
    RoamigoTheme {
      TripMapScreen(
          uiState = uiState,
          isSharing = isSharing,
          isStartingSharing = isStartingSharing,
          isOffline = isOffline,
          cameraState = cameraState,
          onRetry = onRetry,
          onRetryActivities = onRetryActivities,
          onTurnOnSharing = {},
          onPauseSharing = {},
      )
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

/** Fixed time of the marker preview, so that the freshness labels do not change between renders. */
private const val PREVIEW_NOW_MILLIS = 1_762_768_800_000L

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

private val PREVIEW_ALFAMA = MapCoordinate(38.7111, -9.1290)
private val PREVIEW_BELEM = MapCoordinate(38.6916, -9.2160)
private val PREVIEW_BAIXA = MapCoordinate(38.7139, -9.1394)
private const val PREVIEW_RED = 0xFFD9534F.toInt()
private const val PREVIEW_GREEN = 0xFF5CB85C.toInt()
private const val PREVIEW_BLUE = 0xFF428BCA.toInt()

/** A gallery photo whose image is a plain [color] square, encoded as a JPEG like real photos. */
private fun previewPhoto(
    id: String,
    coordinate: MapCoordinate,
    color: Int,
    minutesAgo: Long,
): TripPhoto {
  val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
  val image =
      ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        output.toByteArray()
      }
  return TripPhoto(
      id = id,
      uploadedBy = previewTrip.ownerId,
      takenAtMillis = PREVIEW_NOW_MILLIS - minutesAgo * 60_000L,
      latitude = coordinate.latitude,
      longitude = coordinate.longitude,
      image = image,
  )
}

private fun previewTimestamp(isoDateTime: String): Timestamp =
    Timestamp(Date.from(Instant.parse(isoDateTime)))
