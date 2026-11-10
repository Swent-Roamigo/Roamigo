// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

/** Test tags for identifying trip markers in UI tests. */
object TripMarkerTestTags {
  fun markerFor(id: String): String = "trip_marker_$id"
}

enum class TripMarkerKind {
  DESTINATION,
  ACTIVITY,
  SUGGESTION,
}

/**
 * Represents the presentation state for a trip destination, activity or suggestion marker rendered
 * on a map.
 *
 * Encapsulates location and trip metadata required by map frameworks (e.g., Mapbox) to render
 * markers and handle interactions without coupling the UI state to specific map SDK types.
 *
 * @property id Unique identifier for the marker.
 * @property tripId Unique identifier of the trip this marker belongs to.
 * @property title Display title of the trip, activity or suggestion.
 * @property locationName Display name of the marked location.
 * @property latitude Geographic latitude coordinate.
 * @property longitude Geographic longitude coordinate.
 * @property snippet Optional subtitle or callout description displayed with the marker.
 * @property isSelected Whether the marker is currently selected.
 * @property kind Kind of location represented by the marker.
 * @property startDate First day of the trip, for markers that stand for a trip's destination.
 * @property endDate Last day of the trip, for markers that stand for a trip's destination.
 */
data class TripMarkerUiState(
    val id: String,
    val tripId: String,
    val title: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val snippet: String? = null,
    val isSelected: Boolean = false,
    val kind: TripMarkerKind = TripMarkerKind.DESTINATION,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

/**
 * Extension function to convert a [TripLocation] associated with a [Trip] into a
 * [TripMarkerUiState].
 */
fun TripLocation.toTripMarkerUiState(
    trip: Trip,
    id: String = "${trip.uid}_${this.name}_${this.latitude}_${this.longitude}",
    zone: ZoneId = ZoneId.systemDefault(),
): TripMarkerUiState =
    TripMarkerUiState(
        id = id,
        tripId = trip.uid,
        title = trip.name,
        locationName = this.name,
        latitude = this.latitude,
        longitude = this.longitude,
        snippet = this.name,
        startDate = trip.startDate.toDate().toInstant().atZone(zone).toLocalDate(),
        endDate = trip.endDate.toDate().toInstant().atZone(zone).toLocalDate(),
    )

/**
 * Extension function to convert all destination locations of a [Trip] into a list of
 * [TripMarkerUiState]s.
 */
fun Trip.toTripMarkerUiStates(): List<TripMarkerUiState> = destinations.map { location ->
  location.toTripMarkerUiState(this)
}

/**
 * Extension function to convert an [Activity] into a [TripMarkerUiState]. Suggestions use
 * [TripMarkerKind.SUGGESTION], and accepted activities use [TripMarkerKind.ACTIVITY].
 */
fun Activity.toTripMarkerUiState(
    id: String = "${this.tripId}_activity_${this.uid}",
): TripMarkerUiState =
    TripMarkerUiState(
        id = id,
        tripId = this.tripId,
        title = this.name,
        locationName = this.location.name,
        latitude = this.location.latitude,
        longitude = this.location.longitude,
        snippet = this.description,
        kind =
            when (this.activityType) {
              ActivityType.ACCEPTED -> TripMarkerKind.ACTIVITY
              ActivityType.SUGGESTION -> TripMarkerKind.SUGGESTION
            },
    )

/** Extension function to convert a list of [Activity]s into a list of [TripMarkerUiState]s. */
fun List<Activity>.toTripMarkerUiStates(): List<TripMarkerUiState> = map { activity ->
  activity.toTripMarkerUiState()
}

/**
 * A map pin with its place name on a small pill above it, so a marker can be read without opening
 * it. The pill sits above the pin because the annotation is anchored at the pin's tip.
 */
@Composable
fun TripMarkerIcon(locationName: String, kind: TripMarkerKind) {
  val iconRes =
      when (kind) {
        TripMarkerKind.DESTINATION,
        TripMarkerKind.ACTIVITY -> R.drawable.ic_map_marker
        TripMarkerKind.SUGGESTION -> R.drawable.ic_map_marker_monochrome
      }
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Spacing.extraSmall,
    ) {
      Text(
          text = locationName,
          modifier =
              Modifier.widthIn(max = MARKER_LABEL_MAX_WIDTH)
                  .padding(horizontal = Spacing.small, vertical = MARKER_LABEL_VERTICAL_PADDING),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    Icon(
        painter = painterResource(id = iconRes),
        // The label already carries the name, so the pill is not read twice.
        contentDescription = null,
        tint = Color.Unspecified,
    )
  }
}

private val MARKER_LABEL_MAX_WIDTH = 140.dp
private val MARKER_LABEL_VERTICAL_PADDING = 3.dp
