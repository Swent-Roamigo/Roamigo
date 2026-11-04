// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.photo.tripPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {
  @get:Rule val composeRule = createComposeRule()

  private var closes = 0

  private fun show(uploadedBy: String, currentUserId: String?) {
    composeRule.setContent {
      RoamigoTheme {
        PhotoViewer(
            photo = tripPhoto("a", uploadedBy = uploadedBy),
            currentUserId = currentUserId,
            onClose = { closes++ },
        )
      }
    }
  }

  @Test
  fun showsThePhotoFullScreen() {
    show("me", "me")

    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_image).assertIsDisplayed()
  }

  @Test
  fun saysThePhotoIsTheUsersOwn() {
    show("me", "me")

    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_caption)
        .assertTextContains("You ·", substring = true)
  }

  @Test
  fun saysAMemberTookThePhoto() {
    show("sam", "me")

    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_caption)
        .assertTextContains("A member ·", substring = true)
  }

  @Test
  fun saysAMemberTookThePhotoWhenNobodyIsKnownToBeSignedIn() {
    show("sam", null)

    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_caption)
        .assertTextContains("A member ·", substring = true)
  }

  @Test
  fun closes() {
    show("me", "me")

    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()

    assertEquals(1, closes)
  }
}
