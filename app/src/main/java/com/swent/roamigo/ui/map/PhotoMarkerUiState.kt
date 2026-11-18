// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import com.swent.roamigo.model.photo.PhotoCoordinates
import com.swent.roamigo.model.photo.TripPhoto

/**
 * Photos sharing a location on the trip map.
 *
 * @property coordinate validated location, also usable as a stable selection key.
 * @property photos gallery photos at this location, in browsing order.
 */
data class PhotoMarkerUiState(
    val coordinate: MapCoordinate,
    val photos: List<TripPhoto>,
) {
  val photoCount: Int
    get() = photos.size
}

/**
 * Groups the provided gallery photos by exact valid coordinates. Within each group, photos are
 * newest first; equal capture times are ordered by photo ID. The gallery's limit has already been
 * applied, so photos without usable coordinates are simply omitted.
 */
fun List<TripPhoto>.toPhotoMarkerUiStates(): List<PhotoMarkerUiState> {
  val groups = linkedMapOf<MapCoordinate, MutableList<TripPhoto>>()
  for (photo in this) {
    val latitude = photo.latitude ?: continue
    val longitude = photo.longitude ?: continue
    val coordinates = PhotoCoordinates.orNull(latitude, longitude) ?: continue
    val coordinate =
        MapCoordinate(
            latitude = canonicalZero(coordinates.latitude),
            longitude = canonicalZero(coordinates.longitude),
        )
    groups.getOrPut(coordinate) { mutableListOf() }.add(photo)
  }

  val photoOrder = compareByDescending<TripPhoto> { it.takenAtMillis }.thenBy { it.id }
  return groups.map { (coordinate, photos) ->
    PhotoMarkerUiState(coordinate = coordinate, photos = photos.sortedWith(photoOrder))
  }
}

// Double-valued data class keys distinguish -0.0 from 0.0, though they denote the same position.
private fun canonicalZero(value: Double): Double = if (value == 0.0) 0.0 else value
