// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.mapbox.geojson.Point
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing

/** Renders destinations from [viewModel] and forwards marker clicks with their trip IDs. */
@Composable
fun TripOverviewMapScreen(
    viewModel: TripOverviewMapViewModel,
    modifier: Modifier = Modifier,
    onMarkerClick: (String) -> Unit = rememberNotImplementedAction(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Box(modifier = modifier.fillMaxSize()) {
    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = rememberTripDestinationsMapViewport(uiState.markers),
    ) {
      uiState.markers.forEach { marker ->
        ViewAnnotation(
            options =
                viewAnnotationOptions {
                  geometry(Point.fromLngLat(marker.longitude, marker.latitude))
                  annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                },
            modifier =
                Modifier.testTag(TripMarkerTestTags.markerFor(marker.id))
                    .markNotImplemented(onMarkerClick)
                    .clickable { onMarkerClick(marker.tripId) },
        ) {
          TripMarkerIcon(marker.locationName)
        }
      }
    }

    if (uiState.isLoading || uiState.errorMessage != null || uiState.markers.isEmpty()) {
      Surface(
          modifier = Modifier.align(Alignment.Center).padding(Spacing.screenPadding),
          shape = MaterialTheme.shapes.medium,
          shadowElevation = Spacing.extraSmall,
      ) {
        Column(
            modifier = Modifier.padding(Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          when {
            uiState.isLoading -> {
              CircularProgressIndicator()
              Text(stringResource(R.string.trip_map_loading), textAlign = TextAlign.Center)
            }
            uiState.errorMessage != null -> {
              Text(
                  text = uiState.errorMessage.orEmpty(),
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
              )
              Button(onClick = viewModel::loadTrips) {
                Text(stringResource(R.string.trip_map_retry))
              }
            }
            else -> Text(stringResource(R.string.trip_map_empty), textAlign = TextAlign.Center)
          }
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripOverviewMapScreenPreview() {
  val sampleTrip =
      Trip(
          uid = "trip_1",
          name = "Euro trip",
          description = "Exploring Europe",
          destinations =
              listOf(
                  TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris"),
                  TripLocation(latitude = 41.3851, longitude = 2.1734, name = "Barcelona"),
                  TripLocation(latitude = 51.5074, longitude = -0.1278, name = "London"),
              ),
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
  RoamigoTheme {
    TripOverviewMapScreen(
        viewModel =
            viewModel { TripOverviewMapViewModel(FakeTripRepository(trips = listOf(sampleTrip))) }
    )
  }
}
