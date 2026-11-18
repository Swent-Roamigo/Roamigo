// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.photo.tripPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PhotoMarkerUiStateTest {

  @Test
  fun emptyGalleryHasNoMarkers() {
    assertEquals(emptyList<PhotoMarkerUiState>(), emptyList<TripPhoto>().toPhotoMarkerUiStates())
  }

  @Test
  fun validCoordinateBoundariesAreKept() {
    val coordinates =
        listOf(
            MapCoordinate(-90.0, -180.0),
            MapCoordinate(-90.0, 180.0),
            MapCoordinate(90.0, -180.0),
            MapCoordinate(90.0, 180.0),
        )
    val photos = coordinates.mapIndexed { index, (latitude, longitude) ->
      tripPhoto("boundary-$index", latitude = latitude, longitude = longitude)
    }

    val markers = photos.toPhotoMarkerUiStates()

    assertEquals(coordinates, markers.map { it.coordinate })
    assertEquals(listOf(1, 1, 1, 1), markers.map { it.photoCount })
  }

  @Test
  fun photosWithMissingOrInvalidCoordinatesAreOmitted() {
    val invalid =
        listOf(
            null to null,
            46.5 to null,
            null to 6.6,
            Double.NaN to 6.6,
            46.5 to Double.NaN,
            Double.POSITIVE_INFINITY to 6.6,
            Double.NEGATIVE_INFINITY to 6.6,
            46.5 to Double.POSITIVE_INFINITY,
            46.5 to Double.NEGATIVE_INFINITY,
            90.0001 to 6.6,
            -90.0001 to 6.6,
            46.5 to 180.0001,
            46.5 to -180.0001,
        )
    val photos = invalid.mapIndexed { index, (latitude, longitude) ->
      tripPhoto("invalid-$index", latitude = latitude, longitude = longitude)
    }

    val valid = tripPhoto("valid", latitude = 46.5, longitude = 6.6)
    val markers = (photos + valid).toPhotoMarkerUiStates()

    assertEquals(1, markers.size)
    assertEquals(MapCoordinate(46.5, 6.6), markers.single().coordinate)
    assertEquals(listOf("valid"), markers.single().photos.map { it.id })
  }

  @Test
  fun identicalCoordinatesShareAMarkerAndNearbyCoordinatesStaySeparate() {
    val latitude = 46.5
    val longitude = 6.6
    val nextLatitude = Math.nextUp(latitude)
    val nextLongitude = Math.nextUp(longitude)
    val photos =
        listOf(
            tripPhoto("same-a", latitude = latitude, longitude = longitude),
            tripPhoto("near-latitude", latitude = nextLatitude, longitude = longitude),
            tripPhoto("same-b", latitude = latitude, longitude = longitude),
            tripPhoto("near-longitude", latitude = latitude, longitude = nextLongitude),
        )

    val markers = photos.toPhotoMarkerUiStates().associateBy { it.coordinate }

    assertEquals(3, markers.size)
    assertEquals(2, markers.getValue(MapCoordinate(latitude, longitude)).photoCount)
    assertEquals(1, markers.getValue(MapCoordinate(nextLatitude, longitude)).photoCount)
    assertEquals(1, markers.getValue(MapCoordinate(latitude, nextLongitude)).photoCount)
    assertEquals(
        setOf("same-a", "same-b"),
        markers.getValue(MapCoordinate(latitude, longitude)).photos.map { it.id }.toSet(),
    )
  }

  @Test
  fun signedZeroCoordinatesShareOneSelectionKey() {
    val photos =
        listOf(
            tripPhoto("negative", latitude = -0.0, longitude = 0.0),
            tripPhoto("positive", latitude = 0.0, longitude = -0.0),
        )

    val marker = photos.toPhotoMarkerUiStates().single()

    assertEquals(MapCoordinate(0.0, 0.0), marker.coordinate)
    assertEquals(2, marker.photoCount)
    assertEquals(
        marker.coordinate,
        photos.reversed().toPhotoMarkerUiStates().single().coordinate,
    )
  }

  @Test
  fun groupedPhotosAreNewestFirstWithPhotoIdAsTheTieBreaker() {
    val older = tripPhoto("older", takenAtMillis = 1, latitude = 46.5, longitude = 6.6)
    val tiedB = tripPhoto("b", takenAtMillis = 2, latitude = 46.5, longitude = 6.6)
    val tiedA = tripPhoto("a", takenAtMillis = 2, latitude = 46.5, longitude = 6.6)
    val newer = tripPhoto("newer", takenAtMillis = 3, latitude = 46.5, longitude = 6.6)

    val marker = listOf(older, tiedB, newer, tiedA).toPhotoMarkerUiStates().single()

    assertEquals(4, marker.photoCount)
    assertEquals(listOf("newer", "a", "b", "older"), marker.photos.map { it.id })
    assertSame(newer, marker.photos.first())
    assertEquals(
        marker.photos.map { it.id },
        listOf(tiedA, newer, tiedB, older).toPhotoMarkerUiStates().single().photos.map { it.id },
    )
  }
}
