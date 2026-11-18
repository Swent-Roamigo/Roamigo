// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PhotoPreviewTest {
  @get:Rule val composeRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private fun show(
      isOnline: Boolean = true,
      saving: Boolean = false,
      saveFailed: Boolean = false,
      location: PhotoLocationStatus? = null,
  ) {
    composeRule.setContent {
      RoamigoTheme {
        PhotoPreview(
            file = File("/nowhere/photo.jpg"),
            isOnline = isOnline,
            saving = saving,
            saveFailed = saveFailed,
            location = location,
            onSave = { calls += "save" },
            onRetake = { calls += "retake" },
            onClose = { calls += "close" },
            onOpenMap = { calls += "map" },
        )
      }
    }
  }

  @Test
  fun showsThePhotoWithItsActionsOnline() {
    show(isOnline = true)

    composeRule.onNodeWithTag(C.Tag.photo_preview).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_preview_image).assertIsDisplayed()
    composeRule
        .onNodeWithTag(C.Tag.photo_preview_note)
        .assertTextEquals("Shared with the group once saved")
    composeRule.onNodeWithTag(C.Tag.photo_preview_save).assertTextEquals("Save to trip")
    composeRule.onNodeWithTag(C.Tag.photo_preview_offline).assertDoesNotExist()
  }

  @Test
  fun saysThePhotoStaysOnThePhoneWhenOffline() {
    show(isOnline = false)

    composeRule.onNodeWithTag(C.Tag.photo_preview_offline).assertIsDisplayed()
    composeRule
        .onNodeWithTag(C.Tag.photo_preview_note)
        .assertTextEquals("It will upload when you’re back online")
    composeRule.onNodeWithTag(C.Tag.photo_preview_save).assertTextEquals("Save on this phone")
  }

  @Test
  fun savesRetakesAndCloses() {
    show()

    composeRule.onNodeWithTag(C.Tag.photo_preview_save).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_preview_retake).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_preview_close).performClick()

    assertEquals(listOf("save", "retake", "close"), calls)
  }

  @Test
  fun cancelIsALabelledButtonSoItIsNotLostOnABrightPhoto() {
    show()

    composeRule
        .onNodeWithTag(C.Tag.photo_preview_close)
        .assertIsDisplayed()
        .assertTextContains("Cancel")
  }

  @Test
  fun waitsForTheSaveToFinish() {
    show(saving = true)

    composeRule.onNodeWithTag(C.Tag.photo_preview_save).assertIsNotEnabled()
    composeRule.onNodeWithTag(C.Tag.photo_preview_retake).assertIsNotEnabled()
    composeRule.onNodeWithTag(C.Tag.photo_preview_close).assertIsNotEnabled()
  }

  @Test
  fun leavesTheButtonsEnabledOtherwise() {
    show(saving = false)

    composeRule.onNodeWithTag(C.Tag.photo_preview_save).assertIsEnabled()
    composeRule.onNodeWithTag(C.Tag.photo_preview_retake).assertIsEnabled()
    composeRule.onNodeWithTag(C.Tag.photo_preview_close).assertIsEnabled()
  }

  @Test
  fun tellsTheUserWhenSavingFailed() {
    show(saveFailed = true)

    composeRule
        .onNodeWithTag(C.Tag.photo_preview_error)
        .assertTextEquals("Could not save the photo. Try again.")
  }

  @Test
  fun hidesTheErrorOtherwise() {
    show(saveFailed = false)

    composeRule.onNodeWithTag(C.Tag.photo_preview_error).assertDoesNotExist()
  }

  @Test
  fun showsNoLocationPillWithoutAStatus() {
    show(location = null)

    composeRule.onNodeWithTag(C.Tag.photo_preview_location).assertDoesNotExist()
  }

  @Test
  fun showsEachLocationStatus() {
    val status = mutableStateOf<PhotoLocationStatus?>(PhotoLocationStatus.Locating)
    composeRule.setContent {
      RoamigoTheme {
        PhotoPreview(
            file = File("/nowhere/photo.jpg"),
            isOnline = true,
            saving = false,
            saveFailed = false,
            location = status.value,
            onSave = {},
            onRetake = {},
            onClose = {},
            onOpenMap = {},
        )
      }
    }

    listOf(
            PhotoLocationStatus.Locating to "Getting location…",
            PhotoLocationStatus.Attached(46.52, 6.63) to "Location attached",
            PhotoLocationStatus.Unavailable to "Location unavailable",
            PhotoLocationStatus.Off to "Location off · Turn on in Map",
        )
        .forEach { (value, text) ->
          status.value = value
          composeRule.waitForIdle()
          composeRule.onNodeWithTag(C.Tag.photo_preview_location).assertTextEquals(text)
          composeRule.onNodeWithTag(C.Tag.photo_preview_save).assertIsDisplayed()
          composeRule.onNodeWithTag(C.Tag.photo_preview_retake).assertIsDisplayed()
        }
  }

  @Test
  fun offLocationPillOpensTheMap() {
    show(location = PhotoLocationStatus.Off)

    composeRule.onNodeWithTag(C.Tag.photo_preview_location).performClick()

    assertEquals(listOf("map"), calls)
  }

  @Test fun locationPillIsDisabledWhileSaving() = assertLocationPillDisabledWhileSaving()

  @Test
  @Config(qualifiers = "w640dp-h360dp-land")
  fun locationPillIsDisabledWhileSavingInLandscape() = assertLocationPillDisabledWhileSaving()

  private fun assertLocationPillDisabledWhileSaving() {
    val status = mutableStateOf<PhotoLocationStatus>(PhotoLocationStatus.Off)
    composeRule.setContent {
      RoamigoTheme {
        PhotoPreview(
            file = File("/nowhere/photo.jpg"),
            isOnline = true,
            saving = true,
            saveFailed = false,
            location = status.value,
            onSave = { calls += "save" },
            onRetake = { calls += "retake" },
            onClose = { calls += "close" },
            onOpenMap = { calls += "map" },
        )
      }
    }

    listOf(PhotoLocationStatus.Off, PhotoLocationStatus.NextPhotoOnly).forEach {
      status.value = it
      composeRule.waitForIdle()
      composeRule
          .onNodeWithTag(C.Tag.photo_preview_location)
          .assertIsNotEnabled()
          .assertHasNoClickAction()
          .performClick()
    }

    assertEquals(emptyList<String>(), calls)
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp-land")
  fun locationPillStaysClearOfTheActionsInLandscape() = assertLocationPillClearOfActions()

  @Test
  @Config(qualifiers = "w320dp-h480dp")
  fun locationPillStaysClearOfTheActionsOnASmallScreen() = assertLocationPillClearOfActions()

  private fun assertLocationPillClearOfActions() {
    show(isOnline = false, saveFailed = true, location = PhotoLocationStatus.Off)

    val pill = composeRule.onNodeWithTag(C.Tag.photo_preview_location).assertIsDisplayed()
    val save = composeRule.onNodeWithTag(C.Tag.photo_preview_save).assertIsDisplayed()
    val retake = composeRule.onNodeWithTag(C.Tag.photo_preview_retake).assertIsDisplayed()
    val rootHeight =
        composeRule.onNodeWithTag(C.Tag.photo_preview).getUnclippedBoundsInRoot().bottom
    assertTrue(pill.getUnclippedBoundsInRoot().bottom <= save.getUnclippedBoundsInRoot().top)
    assertTrue(retake.getUnclippedBoundsInRoot().bottom <= rootHeight)
  }
}
