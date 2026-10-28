// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPreviewTest {
  @get:Rule val composeRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private fun show(
      isOnline: Boolean = true,
      saving: Boolean = false,
      saveFailed: Boolean = false,
  ) {
    composeRule.setContent {
      RoamigoTheme {
        PhotoPreview(
            file = File("/nowhere/photo.jpg"),
            isOnline = isOnline,
            saving = saving,
            saveFailed = saveFailed,
            onSave = { calls += "save" },
            onRetake = { calls += "retake" },
            onClose = { calls += "close" },
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
}
