// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
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
    RoamigoTheme {
      TripMapScreen(
          uiState = uiState,
          isSharing = isSharing,
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
