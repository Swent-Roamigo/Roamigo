// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
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
      InviteUiState.Ready("roamigo://invite/token-0123456789abcd", "token-0123456789abcd")
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
  fun theSheetFollowsTheStateAsItChanges() {
    var state by mutableStateOf<InviteUiState>(InviteUiState.Loading)
    composeRule.setContent {
      MaterialTheme {
        InviteSheetContent(
            state = state,
            onShare = {},
            onCopyCode = {},
            onStopSharing = {},
            onCreateLink = {},
        )
      }
    }
    composeRule.onAllNodesWithTag(C.Tag.invite_code).assertCountEquals(0)

    state = ready
    composeRule.onNodeWithTag(C.Tag.invite_code).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.invite_stop_failed).assertCountEquals(0)

    state = ready.copy(stopFailed = true)
    composeRule.onNodeWithTag(C.Tag.invite_stop_failed).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.invite_code).assertIsDisplayed()

    state = InviteUiState.Stopped
    composeRule.onNodeWithTag(C.Tag.invite_new_link).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.invite_code).assertCountEquals(0)

    state = InviteUiState.Failed(notAllowed = false)
    composeRule.onNodeWithTag(C.Tag.invite_retry).assertIsDisplayed()

    state = InviteUiState.Failed(notAllowed = true)
    composeRule.onAllNodesWithTag(C.Tag.invite_retry).assertCountEquals(0)

    state = ready
    composeRule.onNodeWithTag(C.Tag.invite_code).assertIsDisplayed()
  }

  @Test
  fun theSheetCallsTheLatestHandlersWhenTheyChange() {
    val calls = mutableListOf<String>()
    var handlers by mutableStateOf(1)
    composeRule.setContent {
      MaterialTheme {
        val round = handlers
        InviteSheetContent(
            state = ready,
            onShare = { calls += "share$round" },
            onCopyCode = { calls += "copy$round" },
            onStopSharing = { calls += "stop$round" },
            onCreateLink = { calls += "new$round" },
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.invite_share).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_copy).performClick()

    handlers = 2
    composeRule.onNodeWithTag(C.Tag.invite_share).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_copy).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_stop).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_stop_confirm).performClick()

    assertEquals(listOf("share1", "copy1", "share2", "copy2", "stop2"), calls)
  }

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
  fun aLinkWithADateSaysWhenItStopsWorking() {
    val expiry = Instant.parse("2026-11-26T10:00:00Z")
    show(ready.copy(expiresAt = expiry))

    val date =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(expiry)
    composeRule
        .onNodeWithTag(C.Tag.invite_description)
        .assertTextEquals("Anyone with this link can join the trip until $date.")
  }

  @Test
  fun aLinkWithoutADateDoesNotInventOne() {
    show(ready)

    composeRule
        .onNodeWithTag(C.Tag.invite_description)
        .assertTextEquals(text(R.string.invite_description))
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

  @Test
  fun aLinkThatCouldNotBeStoppedSaysSoAndStillOffersToStopIt() {
    show(ready.copy(stopFailed = true))

    composeRule.onNodeWithTag(C.Tag.invite_stop_failed).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.invite_code).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.invite_stop).performClick()
    composeRule.onNodeWithTag(C.Tag.invite_stop_confirm).performClick()

    assertEquals(1, stops)
  }

  @Test
  fun aLinkThatWasStoppedNormallyShowsNoStopError() {
    show(ready)

    composeRule.onAllNodesWithTag(C.Tag.invite_stop_failed).assertCountEquals(0)
  }
}
