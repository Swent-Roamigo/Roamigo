// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Trip

/** Test tags for identifying trip markers in UI tests. */
object TripMarkerTestTags {
  fun markerFor(id: String): String = "trip_marker_$id"
}

/**
 * Represents the presentation state for a trip marker rendered on a map.
 *
 * Encapsulates location and trip metadata required by map frameworks (e.g., Mapbox) to render
 * markers and handle interactions without coupling the UI state to specific map SDK types.
 *
 * @property id Unique identifier for the marker.
 * @property tripId Unique identifier of the trip this destination belongs to.
 * @property tripTitle Title or name of the trip.
 * @property locationName Display name of the destination location.
 * @property latitude Geographic latitude coordinate.
 * @property longitude Geographic longitude coordinate.
 * @property snippet Optional subtitle or callout description displayed with the marker.
 * @property isSelected Whether the marker is currently selected.
 */
data class TripMarkerUiState(
    val id: String,
    val tripId: String,
    val tripTitle: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val snippet: String? = null,
    val isSelected: Boolean = false,
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
        tripTitle = trip.name,
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

@Composable
fun TripMarkerIcon(locationName: String) {
  Icon(
      painter = painterResource(id = R.drawable.ic_map_marker),
      contentDescription = locationName,
      tint = Color.Unspecified,
  )
}
