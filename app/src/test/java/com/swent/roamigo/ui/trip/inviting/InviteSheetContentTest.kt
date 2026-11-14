// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class InviteSheetContentTest {
  @get:Rule val composeRule = createComposeRule()

  private val ready =
      InviteUiState.Ready("roamigo://invite/token-0123456789abcd", "token-0123456789abcd", null)
  private var shared: InviteUiState.Ready? = null
  private var copied: InviteUiState.Ready? = null
  private var stops = 0
  private var newLinks = 0

  private fun show(state: InviteUiState) {
    composeRule.setContent {
      MaterialTheme {
        InviteSheetContent(
            state = state,
            onShare = { shared = it },
            onCopyCode = { copied = it },
            onStopSharing = { stops++ },
            onCreateLink = { newLinks++ },
        )
      }
    }
  }

  private fun text(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun aReadyLinkShowsTheCodeAndCanBeSharedOrCopied() {
    show(ready)

    composeRule.onNodeWithTag(C.Tag.invite_code).assertTextEquals("token-0123456789abcd")
    composeRule.onNodeWithTag(C.Tag.invite_share).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_copy).performClick()

    assertEquals(ready, shared)
    assertEquals(ready, copied)
  }

  @Test
  fun stoppingAsksForConfirmationAndStatesTheConsequence() {
    show(ready)

    composeRule.onNodeWithTag(C.Tag.invite_stop).performClick()

    composeRule.onNodeWithText(text(R.string.invite_stop_message)).assertIsDisplayed()
    assertEquals(0, stops)
    composeRule.onNodeWithTag(C.Tag.invite_stop_confirm).performClick()
    assertEquals(1, stops)
  }

  @Test
  fun cancellingTheConfirmationKeepsTheLink() {
    show(ready)

    composeRule.onNodeWithTag(C.Tag.invite_stop).performClick()
    composeRule.onNodeWithText(text(R.string.invite_cancel)).performClick()

    assertEquals(0, stops)
    composeRule.onAllNodesWithTag(C.Tag.invite_stop_confirm).assertCountEquals(0)
    composeRule.onNodeWithTag(C.Tag.invite_share).assertIsDisplayed()
  }

  @Test
  fun aStoppedLinkOffersANewOne() {
    show(InviteUiState.Stopped)

    composeRule.onNodeWithText(text(R.string.invite_stopped)).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.invite_new_link).performClick()

    assertEquals(1, newLinks)
  }

  @Test
  fun aFailureCanBeRetried() {
    show(InviteUiState.Failed(notAllowed = false))

    composeRule.onNodeWithText(text(R.string.invite_failed)).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.invite_retry).performClick()

    assertEquals(1, newLinks)
  }

  @Test
  fun aMemberWhoIsNotTheOrganiserCannotRetry() {
    show(InviteUiState.Failed(notAllowed = true))

    composeRule.onNodeWithText(text(R.string.invite_not_allowed)).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.invite_retry).assertCountEquals(0)
  }

  @Test
  fun loadingShowsNoLinkYet() {
    show(InviteUiState.Loading)

    composeRule.onNodeWithTag(C.Tag.invite_sheet).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.invite_share).assertCountEquals(0)
  }
}
