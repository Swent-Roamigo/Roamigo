// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.Trip

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
)

/**
 * Extension function to convert a [TripLocation] associated with a [Trip] into a
 * [TripMarkerUiState].
 */
fun TripLocation.toTripMarkerUiState(
    trip: Trip,
    id: String = "${trip.uid}_${this.name}_${this.latitude}_${this.longitude}",
): TripMarkerUiState =
    TripMarkerUiState(
        id = id,
        tripId = trip.uid,
        title = trip.name,
        locationName = this.name,
        latitude = this.latitude,
        longitude = this.longitude,
        snippet = this.name,
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

@Composable
fun TripMarkerIcon(locationName: String, kind: TripMarkerKind) {
  val iconRes =
      when (kind) {
        TripMarkerKind.DESTINATION,
        TripMarkerKind.ACTIVITY -> R.drawable.ic_map_marker
        TripMarkerKind.SUGGESTION -> R.drawable.ic_map_marker_monochrome
      }
  Icon(
      painter = painterResource(id = iconRes),
      contentDescription = locationName,
      tint = Color.Unspecified,
  )
}
