// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.photo.tripPhoto
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM tests for the photo states of [TripMapScreen] drawn around the map, and for the viewer it
 * shows for the selected photo group of its state. Selection itself is covered by the ViewModel.
 *
 * Mapbox cannot render under Robolectric, so the map is drawn in inspection mode and the markers
 * themselves, including tapping them, are covered by the instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripMapPhotoOverlayTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val uiState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)
  private var photoRetries = 0
  private var closes = 0

  @Test
  fun photoLoadingEmptyAndLoadedStatesAreToldApartWithoutCoveringTheMap() {
    setContent(loaded(photosLoadState = PhotosLoadState.LOADING))
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_retry_photos).assertDoesNotExist()

    uiState.value = loaded(photosLoadState = PhotosLoadState.LOADED)
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_empty).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_loading).assertDoesNotExist()

    uiState.value = loaded(markers = listOf(group(HERE, photo("one"))))
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_empty).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_error).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
  }

  @Test
  fun failureOffersRetryAndKeepsTheOpenGroupUsable() {
    val markers = listOf(group(HERE, photo("one")))
    setContent(loaded(markers, PhotosLoadState.ERROR, selected = HERE))

    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_error).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_retry_photos).performClick()
    composeRule.runOnIdle { assertEquals(1, photoRetries) }

    // Retrying keeps the markers, so a group stays open while photos reload.
    uiState.value = loaded(markers, PhotosLoadState.LOADING, selected = HERE)
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_retry_photos).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_loading).assertIsDisplayed()
  }

  @Test
  fun viewerBrowsesTheGroupNewestFirstWithPhotographerNamesAndFallbacks() {
    setContent(
        loaded(
            markers =
                listOf(
                    group(
                        HERE,
                        photo("newest", by = "alice", at = 3),
                        photo("middle", by = ME, at = 2),
                        photo("oldest", by = "stranger", at = 1),
                    ),
                    group(ELSEWHERE, photo("other")),
                ),
            names = mapOf("alice" to "Alice"),
            selected = HERE,
        )
    )

    assertPosition(1, 3)
    assertCaptionShows("Alice")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    assertPosition(2, 3)
    assertCaptionShows(string(R.string.photo_viewer_you))
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    assertPosition(3, 3)
    assertCaptionShows(string(R.string.photo_viewer_unknown_photographer))
  }

  @Test
  fun liveUpdatesKeepTheSelectedPhotoAndTheViewerFollowsTheSelection() {
    val older = photo("older", at = 1)
    setContent(loaded(listOf(group(HERE, photo("newer", at = 2), older)), selected = HERE))
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    assertPosition(2, 2)

    // A new photo at the same place keeps the photo being looked at.
    uiState.value =
        loaded(
            listOf(group(HERE, photo("newest", at = 3), photo("newer", at = 2), older)),
            selected = HERE,
        )
    assertPosition(3, 3)

    // A selection without a matching group shows the map.
    uiState.value = loaded(listOf(group(ELSEWHERE, photo("other"))), selected = HERE)
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(0, closes) }
  }

  @Test
  fun backAndCloseReturnToTheMap() {
    val markers = listOf(group(HERE, photo("one")))
    setContent(loaded(markers, selected = HERE))
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertIsDisplayed()

    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()

    uiState.value = loaded(markers, selected = HERE)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(2, closes) }
  }

  @Test
  fun markerShowsItsCountOnlyForGroupsOfSeveralPhotos() {
    val count = mutableStateOf(1)
    composeRule.setContent { RoamigoTheme { PhotoMarkerIcon(photoCount = count.value) } }
    composeRule.onNodeWithTag(C.Tag.trip_map_photo_marker_count).assertDoesNotExist()

    count.value = 4
    composeRule
        .onNodeWithTag(C.Tag.trip_map_photo_marker_count, useUnmergedTree = true)
        .assertExists()
    composeRule.onNodeWithText("4", useUnmergedTree = true).assertExists()
  }

  /** Shows the screen, closing the viewer as [TripMapViewModel.closePhotoViewer] does. */
  private fun setContent(initial: TripMapUiState) {
    uiState.value = initial
    composeRule.setContent {
      RoamigoTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) {
          TripMapScreen(
              uiState = uiState.value,
              isSharing = false,
              onRetry = {},
              onRetryActivities = {},
              onTurnOnSharing = {},
              onPauseSharing = {},
              onRetryPhotos = { photoRetries++ },
              onPhotoViewerClose = {
                closes++
                uiState.value =
                    (uiState.value as TripMapUiState.Loaded).copy(selectedPhotoCoordinate = null)
              },
              currentUserId = ME,
          )
        }
      }
    }
  }

  private fun assertPosition(index: Int, size: Int) {
    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_position)
        .assertTextEquals(
            composeRule.activity.getString(R.string.photo_viewer_position, index, size)
        )
  }

  private fun assertCaptionShows(name: String) {
    composeRule.onNodeWithText(name, useUnmergedTree = true).assertExists()
  }

  private fun string(id: Int) = composeRule.activity.getString(id)

  private fun loaded(
      markers: List<PhotoMarkerUiState> = emptyList(),
      photosLoadState: PhotosLoadState = PhotosLoadState.LOADED,
      names: Map<String, String> = emptyMap(),
      selected: MapCoordinate? = null,
  ) =
      TripMapUiState.Loaded(
          trip = TRIP,
          viewport = TripMapViewport.Centered(HERE.latitude, HERE.longitude),
          members = emptyMap(),
          activitiesLoadState = ActivitiesLoadState.LOADED,
          photoMarkers = markers,
          photosLoadState = photosLoadState,
          photographerNames = names,
          selectedPhotoCoordinate = selected,
      )

  private fun group(coordinate: MapCoordinate, vararg photos: TripPhoto) =
      PhotoMarkerUiState(coordinate, photos.toList())

  private fun photo(id: String, by: String = "alice", at: Long = 1) =
      tripPhoto(id, uploadedBy = by, takenAtMillis = at, latitude = 1.0, longitude = 2.0)

  private companion object {
    const val ME = "me"
    val HERE = MapCoordinate(38.7223, -9.1393)
    val ELSEWHERE = MapCoordinate(38.73, -9.15)
    val TRIP =
        Trip(
            uid = "trip",
            name = "Lisbon",
            description = null,
            destinations = listOf(TripLocation(HERE.latitude, HERE.longitude, "Lisbon")),
            startDate = Timestamp(0, 0),
            endDate = Timestamp(0, 0),
            flexibleDates = false,
            travelMode = TravelMode.GROUP,
            travelerCount = 1,
            ownerId = ME,
            memberIds = listOf(ME),
            status = TripStatus.PLANNED,
            createdAt = Timestamp(0, 0),
        )
  }
}
