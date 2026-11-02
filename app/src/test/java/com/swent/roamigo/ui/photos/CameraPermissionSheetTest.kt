// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPermissionSheetTest {
  @get:Rule val composeRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private fun show(step: CameraStep) {
    composeRule.setContent {
      RoamigoTheme {
        CameraPermissionSheet(
            step = step,
            onContinue = { calls += "continue" },
            onOpenSettings = { calls += "settings" },
            onDismiss = { calls += "dismiss" },
        )
      }
    }
  }

  @Test
  fun showsNothingWhenThereIsNoStep() {
    show(CameraStep.NONE)

    listOf(
            C.Tag.camera_rationale_sheet,
            C.Tag.camera_denied_sheet,
            C.Tag.camera_blocked_sheet,
        )
        .forEach { composeRule.onNodeWithTag(it).assertDoesNotExist() }
  }

  @Test
  fun explainsWhyTheCameraIsNeededAndContinues() {
    show(CameraStep.RATIONALE)

    composeRule.onNodeWithTag(C.Tag.camera_rationale_sheet).assertIsDisplayed()
    composeRule.onNodeWithText("Take photos for the trip?").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.camera_rationale_continue).performClick()

    assertEquals(listOf("continue"), calls)
  }

  @Test
  fun letsTheUserDeclineTheExplanation() {
    show(CameraStep.RATIONALE)

    composeRule.onNodeWithTag(C.Tag.camera_rationale_not_now).performClick()

    assertEquals(listOf("dismiss"), calls)
  }

  @Test
  fun saysTheCameraStaysOffWhenDenied() {
    show(CameraStep.DENIED)

    composeRule.onNodeWithTag(C.Tag.camera_denied_sheet).assertIsDisplayed()
    composeRule.onNodeWithText("Camera stays off").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.camera_rationale_not_now).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.camera_denied_got_it).performClick()

    assertEquals(listOf("dismiss"), calls)
  }

  @Test
  fun pointsToSettingsWhenBlocked() {
    show(CameraStep.BLOCKED)

    composeRule.onNodeWithTag(C.Tag.camera_blocked_sheet).assertIsDisplayed()
    composeRule.onNodeWithText("Camera is blocked").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.camera_blocked_open_settings).performClick()

    assertEquals(listOf("settings"), calls)
  }

  @Test
  fun letsTheUserLeaveTheBlockedSheet() {
    show(CameraStep.BLOCKED)

    composeRule.onNodeWithTag(C.Tag.camera_blocked_not_now).performClick()

    assertEquals(listOf("dismiss"), calls)
  }
}
