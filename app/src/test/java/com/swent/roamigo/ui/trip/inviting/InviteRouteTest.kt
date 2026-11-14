// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class InviteRouteTest {
  @get:Rule val composeRule = createComposeRule()

  private val service = FakeTripInvitationService()
  private var open by mutableStateOf(true)

  private fun waitForTag(tag: String) =
      composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      }

  @Test
  fun reopeningTheSheetAfterStoppingShowsAFreshLink() {
    composeRule.setContent {
      MaterialTheme { if (open) InviteRoute("trip", service, onDismiss = { open = false }) }
    }
    waitForTag(C.Tag.invite_code)
    composeRule.onNodeWithTag(C.Tag.invite_stop).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_stop_confirm).performClick()
    waitForTag(C.Tag.invite_new_link)

    composeRule.runOnUiThread { open = false }
    composeRule.waitForIdle()
    composeRule.runOnUiThread { open = true }

    waitForTag(C.Tag.invite_code)
    composeRule.onNodeWithTag(C.Tag.invite_code).assertIsDisplayed()
    assertEquals(2, service.createdFor.size)
  }
}
