// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.resources.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class InviteRouteActionsTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val service = FakeTripInvitationService()

  private fun showSheet() {
    composeRule.setContent { MaterialTheme { InviteRoute("trip", service, onDismiss = {}) } }
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(C.Tag.invite_share).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun sharingOpensTheSystemChooserWithTheLinkAndTheCode() {
    showSheet()

    composeRule.onNodeWithTag(C.Tag.invite_share).performClick()

    val chooser = shadowOf(composeRule.activity).nextStartedActivity
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    assertNotNull(send)
    assertEquals(Intent.ACTION_SEND, send!!.action)
    assertEquals("text/plain", send.type)
    val message = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    assertTrue(message, message.contains("roamigo://invite/fake-invitation-token"))
    assertTrue(message, message.contains("fake-invitation-token"))
  }

  @Test
  fun copyingPutsTheCodeOnTheClipboardAndSaysSo() {
    showSheet()
    ShadowToast.reset()

    composeRule.onNodeWithTag(C.Tag.invite_copy).performClick()

    val clipboard =
        composeRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertEquals("fake-invitation-token", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    assertEquals(
        composeRule.activity.getString(R.string.invite_copied),
        ShadowToast.getTextOfLatestToast(),
    )
  }
}
