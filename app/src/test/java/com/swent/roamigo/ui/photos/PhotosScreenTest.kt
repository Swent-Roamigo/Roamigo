// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.photo.PhotoUploadStatus
import com.swent.roamigo.model.photo.pendingPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotosScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private val taps = mutableListOf<String>()

  private fun show(state: PhotosUiState) {
    composeRule.setContent {
      RoamigoTheme {
        PhotosScreen(
            state = state,
            tripName = "Lisbon & Porto",
            fileOf = { File("/nowhere/${it.fileName}") },
            onTakePhotoClick = { taps += "take" },
            onRetryClick = { taps += "retry $it" },
            onTripsClick = { taps += "trips" },
        )
      }
    }
  }

  private fun waiting(count: Int, status: PhotoUploadStatus = PhotoUploadStatus.WAITING) =
      List(count) { pendingPhoto("p$it", status = status) }

  private fun bannerText(text: String) {
    composeRule.onNodeWithTag(C.Tag.photos_banner).assertTextContains(text, substring = true)
  }

  private fun noBanner() {
    composeRule.onNodeWithTag(C.Tag.photos_banner).assertDoesNotExist()
  }

  @Test
  fun showsTheTitleAndTheTripName() {
    show(PhotosUiState())

    composeRule.onNodeWithTag(C.Tag.photos_title).assertTextEquals("Trip photos")
    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    composeRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()
  }

  @Test
  fun explainsAnEmptyListAndShowsNoBannerOnline() {
    show(PhotosUiState(isOnline = true))

    composeRule.onNodeWithTag(C.Tag.photos_empty).assertIsDisplayed()
    noBanner()
  }

  @Test
  fun saysPhotosAreKeptOnThePhoneWhenOfflineWithNothingWaiting() {
    show(PhotosUiState(isOnline = false))

    bannerText("You’re offline")
    composeRule.onNodeWithTag(C.Tag.photos_empty).assertIsDisplayed()
  }

  @Test
  fun countsOnePhotoWaitingForAConnection() {
    show(PhotosUiState(isOnline = false, photos = waiting(1)))

    bannerText("1 photo saved on this phone · waiting for a connection")
  }

  @Test
  fun countsSeveralPhotosWaitingForAConnection() {
    show(PhotosUiState(isOnline = false, photos = waiting(3)))

    bannerText("3 photos saved on this phone · waiting for a connection")
  }

  @Test
  fun countsPhotosWaitingToUploadOnline() {
    show(PhotosUiState(isOnline = true, photos = waiting(2)))

    bannerText("2 photos waiting to upload")
    composeRule.onNodeWithTag(C.Tag.photos_progress).assertDoesNotExist()
  }

  @Test
  fun showsProgressWhileUploading() {
    show(
        PhotosUiState(
            isOnline = true,
            photos = waiting(1, PhotoUploadStatus.UPLOADING) + waiting(1).map { it.copy(id = "z") },
        )
    )

    bannerText("Back online · uploading 2 photos")
    composeRule.onNodeWithTag(C.Tag.photos_progress).assertIsDisplayed()
  }

  @Test
  fun doesNotShowProgressWhenTheConnectionDropsMidUpload() {
    show(PhotosUiState(isOnline = false, photos = waiting(1, PhotoUploadStatus.UPLOADING)))

    bannerText("waiting for a connection")
    composeRule.onNodeWithTag(C.Tag.photos_progress).assertDoesNotExist()
  }

  @Test
  fun listsEachPhotoWithItsStatus() {
    val photos =
        listOf(
            pendingPhoto("w", status = PhotoUploadStatus.WAITING),
            pendingPhoto("u", status = PhotoUploadStatus.UPLOADING),
            pendingPhoto("f", status = PhotoUploadStatus.FAILED),
        )
    show(PhotosUiState(photos = photos))

    composeRule.onNodeWithTag(C.Tag.photos_empty).assertDoesNotExist()
    composeRule
        .onNodeWithTag(C.Tag.photos_status_prefix + "w")
        .assertTextContains("Waiting to upload", substring = true)
    composeRule
        .onNodeWithTag(C.Tag.photos_status_prefix + "u")
        .assertTextContains("Uploading", substring = true)
    composeRule
        .onNodeWithTag(C.Tag.photos_status_prefix + "f")
        .assertTextContains("Upload failed", substring = true)
  }

  @Test
  fun offersARetryOnlyForAFailedPhoto() {
    show(
        PhotosUiState(
            photos =
                listOf(
                    pendingPhoto("w", status = PhotoUploadStatus.WAITING),
                    pendingPhoto("f", status = PhotoUploadStatus.FAILED),
                )
        )
    )

    composeRule.onNodeWithTag(C.Tag.photos_retry_prefix + "w").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photos_retry_prefix + "f").performClick()

    assertEquals(listOf("retry f"), taps)
  }

  @Test
  fun takingAPhotoIsOneTapAway() {
    show(PhotosUiState())

    composeRule.onNodeWithTag(C.Tag.photos_take_photo).performClick()

    assertEquals(listOf("take"), taps)
  }

  @Test
  fun highlightsThePhotosTabAndLeadsBackToTrips() {
    show(PhotosUiState())

    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).assertIsSelected()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(listOf("trips"), taps)
  }
}
