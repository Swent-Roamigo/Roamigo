// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.inviting.InvitationPreview
import com.swent.roamigo.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class JoinTripRouteTest {
  @get:Rule val composeRule = createComposeRule()

  private val service =
      FakeTripInvitationService(preview = InvitationPreview("trip-7", "Lisbon", "Alex", false))
  private var joined: String? = null
  private var closes = 0

  @Test
  fun joiningReportsTheTripAndLeavingClosesTheScreen() {
    composeRule.setContent {
      MaterialTheme {
        JoinTripRoute("tok", service, onJoined = { joined = it }, onClose = { closes++ })
      }
    }
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(C.Tag.join_button).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(C.Tag.join_button).performClick()
    composeRule.waitUntil(5_000) { joined != null }
    composeRule.onNodeWithTag(C.Tag.join_back).performClick()

    assertEquals("trip-7", joined)
    assertEquals(listOf("tok"), service.accepted)
    assertEquals(1, closes)
  }
}
