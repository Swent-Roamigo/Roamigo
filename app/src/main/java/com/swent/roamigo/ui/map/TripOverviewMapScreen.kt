// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Connects [tripRepository] to [TripOverviewMapScreen] through its ViewModel. */
@Composable
fun TripOverviewMapRoute(
    tripRepository: TripRepository,
    modifier: Modifier = Modifier,
    onMarkerClick: (String) -> Unit = rememberNotImplementedAction(),
    onTripsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
) {
  val viewModel = viewModel { TripOverviewMapViewModel(tripRepository) }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  TripOverviewMapScreen(
      uiState = uiState,
      modifier = modifier,
      onMarkerClick = onMarkerClick,
      onRetry = viewModel::loadTrips,
      onTripsClick = onTripsClick,
      onMapClick = onMapClick,
      onFriendsClick = onFriendsClick,
  )
}

/** Renders [uiState] and forwards marker clicks and retry actions to the caller. */
@Composable
fun TripOverviewMapScreen(
    uiState: TripOverviewMapUiState,
    modifier: Modifier = Modifier,
    onMarkerClick: (String) -> Unit = rememberNotImplementedAction(),
    onRetry: () -> Unit = {},
    onTripsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
) {
  Scaffold(
      modifier = modifier,
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onFriendsClick = onFriendsClick,
            tripsSelected = false,
            mapSelected = true,
        )
      },
  ) { padding ->
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
      val isLandscape = maxWidth > maxHeight
      Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(Spacing.large),
      ) {
        Column(
            modifier =
                Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
          Text(
              stringResource(R.string.trip_overview_map_title),
              style = MaterialTheme.typography.headlineMedium,
          )
          Text(
              stringResource(R.string.trip_overview_map_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        // In landscape the list goes beside the map: stacked below it, the list would take the
        // whole height and the map would disappear.
        if (isLandscape) {
          Row(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(Spacing.large),
          ) {
            OverviewMapCard(
                uiState,
                onMarkerClick,
                onRetry,
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .padding(start = Spacing.screenPadding, bottom = Spacing.medium),
            )
            TripRows(
                uiState.markers,
                onMarkerClick,
                modifier =
                    Modifier.width(LANDSCAPE_ROWS_WIDTH)
                        .fillMaxHeight()
                        .padding(bottom = Spacing.medium),
            )
          }
        } else {
          OverviewMapCard(
              uiState,
              onMarkerClick,
              onRetry,
              Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screenPadding),
          )
          TripRows(uiState.markers, onMarkerClick)
        }
      }
    }
  }
}

/** The map card with one pin per trip, and the loading, error and empty states over it. */
@Composable
private fun OverviewMapCard(
    uiState: TripOverviewMapUiState,
    onMarkerClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.clip(RoundedCornerShape(MAP_CARD_RADIUS))) {
    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = rememberTripDestinationsMapViewport(uiState.markers),
        // Top end: nothing else is drawn there, and the logo and attribution stay at the bottom.
        scaleBar = {
          ScaleBar(contentPadding = PaddingValues(Spacing.small), alignment = Alignment.TopEnd)
        },
    ) {
      uiState.markers.forEach { marker ->
        ViewAnnotation(
            options =
                viewAnnotationOptions {
                  geometry(Point.fromLngLat(marker.longitude, marker.latitude))
                  annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                  // The name pills make pins wider, and Mapbox hides a pin that overlaps
                  // another one. A hidden pin would hide a trip.
                  allowOverlap(true)
                },
            modifier =
                Modifier.testTag(TripMarkerTestTags.markerFor(marker.id))
                    .markNotImplemented(onMarkerClick)
                    .clickable { onMarkerClick(marker.tripId) },
        ) {
          TripMarkerIcon(locationName = marker.locationName, kind = marker.kind)
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
                  text = stringResource(R.string.trip_map_load_error),
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
              )
              Button(onClick = onRetry) { Text(stringResource(R.string.trip_map_retry)) }
            }

            else -> Text(stringResource(R.string.trip_map_empty), textAlign = TextAlign.Center)
          }
        }
      }
    }
  }
}

/**
 * One row per trip on the map, a second way to open a trip for people who find a list easier than a
 * pin. Capped in height so the map keeps most of the screen, and scrollable past that.
 */
@Composable
internal fun TripRows(
    markers: List<TripMarkerUiState>,
    onTripClick: (String) -> Unit,
    modifier: Modifier = Modifier.heightIn(max = TRIP_ROWS_MAX_HEIGHT),
    today: LocalDate = LocalDate.now(),
) {
  val trips = markers.groupBy { it.tripId }
  if (trips.isEmpty()) return
  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              .padding(horizontal = Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    trips.forEach { (tripId, tripMarkers) ->
      Surface(
          onClick = { onTripClick(tripId) },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.trip_overview_row_prefix + tripId),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
            modifier =
                Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
                tripMarkers.first().title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val first = tripMarkers.first()
            val subtitle = tripRowSubtitle(first, tripMarkers, today)
            // A trip named after its only destination would otherwise say the same thing twice.
            if (subtitle != first.title) {
              Text(
                  subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
            }
          }
          Icon(
              painter = painterResource(R.drawable.ic_c2_chevron_right),
              contentDescription = null,
              modifier = Modifier.size(CHEVRON_SIZE),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private val MAP_CARD_RADIUS = 20.dp

/**
 * The line under a trip's name: its dates and where it stands ("Sat 12 Oct – Sat 19 Oct · in 3
 * days"), or its destinations when the markers carry no dates.
 */
@Composable
private fun tripRowSubtitle(
    first: TripMarkerUiState,
    tripMarkers: List<TripMarkerUiState>,
    today: LocalDate,
): String {
  val start = first.startDate
  val end = first.endDate
  if (start == null || end == null) return tripMarkers.joinToString(" → ") { it.locationName }
  val formatter = DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0])
  val status =
      when (val schedule = tripScheduleOf(start, end, today)) {
        TripSchedule.Past -> stringResource(R.string.trip_schedule_past)
        TripSchedule.InProgress -> stringResource(R.string.trip_schedule_in_progress)
        is TripSchedule.StartsIn ->
            pluralStringResource(R.plurals.trip_schedule_starts_in, schedule.days, schedule.days)
        TripSchedule.Planning -> stringResource(R.string.trip_schedule_planning)
      }
  return "${formatter.format(start)} – ${formatter.format(end)} · $status"
}

private val TRIP_ROWS_MAX_HEIGHT = 168.dp
private val LANDSCAPE_ROWS_WIDTH = 320.dp
private val CHEVRON_SIZE = 18.dp
