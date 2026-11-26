// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationEvent
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class NotificationsScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private val context
    get() = ApplicationProvider.getApplicationContext<Context>()

  private val request =
      Notification(
          uid = "request",
          recipientUserId = "recipient",
          event = NotificationEvent.FriendRequestReceived("request-id", "sender"),
          createdAt = Instant.parse("2026-11-22T09:00:00Z"),
          isRead = true,
      )
  private val vote =
      request.copy(
          uid = "vote",
          event = NotificationEvent.TripVoteCreated("trip-id", "vote-id"),
          createdAt = Instant.parse("2026-11-22T10:00:00Z"),
          isRead = false,
      )

  @Test
  fun firstSnapshotReplacesLoadingWithTheEmptyState() {
    val state = mutableStateOf(NotificationsUiState())
    show { state.value }

    composeRule.onNodeWithText(context.getString(R.string.notifications_title)).assertIsDisplayed()
    composeRule
        .onNodeWithContentDescription(context.getString(R.string.notifications_loading))
        .assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.notifications_empty)).assertDoesNotExist()

    composeRule.runOnIdle { state.value = ready() }

    composeRule.onNodeWithText(context.getString(R.string.notifications_empty)).assertIsDisplayed()
    composeRule
        .onNodeWithContentDescription(context.getString(R.string.notifications_loading))
        .assertDoesNotExist()
  }

  @Test
  fun entriesDisplayEventTypesTimestampsAndAccessibleReadStatesInOrder() {
    show { ready(vote, request) }

    val first = row(vote)
    val second = row(request)
    first
        .assertIsDisplayed()
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.notifications_unread),
            )
        )
    second
        .assertIsDisplayed()
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.notifications_read),
            )
        )
    assertTrue(
        first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top
    )
    composeRule
        .onNodeWithText(context.getString(R.string.notifications_trip_vote))
        .assertIsDisplayed()
    composeRule
        .onNodeWithText(context.getString(R.string.notifications_friend_request))
        .assertIsDisplayed()
    val timestamp =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(context.resources.configuration.locales[0])
            .withZone(ZoneId.systemDefault())
            .format(vote.createdAt)
    composeRule.onNodeWithText(timestamp).assertIsDisplayed()
    markRead(request).assertDoesNotExist()
  }

  @Test
  fun openMarkReadAndBackForwardIndependentActions() {
    val opened = mutableListOf<Notification>()
    val marked = mutableListOf<String>()
    var backClicks = 0
    show(
        onOpen = { opened += it },
        onMarkRead = { marked += it },
        onBack = { backClicks++ },
    ) {
      ready(vote)
    }

    markRead(vote).performClick()
    assertEquals(listOf(vote.uid), marked)
    assertTrue(opened.isEmpty())

    row(vote).performClick()
    assertEquals(listOf(vote), opened)
    assertEquals(listOf(vote.uid), marked)

    composeRule
        .onNodeWithContentDescription(context.getString(R.string.notifications_back))
        .performClick()
    assertEquals(1, backClicks)
  }

  @Test
  fun pendingAndFailedReadUpdatesShowFeedbackForTheAffectedEntries() {
    val pending = vote.copy(uid = "pending")
    val failed = request.copy(isRead = false)
    val marked = mutableListOf<String>()
    show(onMarkRead = { marked += it }) {
      ready(pending, failed)
          .copy(
              markingReadIds = setOf(pending.uid),
              failedReadIds = setOf(failed.uid),
          )
    }

    markRead(pending).assertIsNotEnabled()
    composeRule
        .onNodeWithContentDescription(
            context.getString(
                R.string.notifications_marking_read,
                context.getString(R.string.notifications_trip_vote),
            )
        )
        .assertIsDisplayed()
    composeRule
        .onNodeWithText(context.getString(R.string.notifications_mark_read_failed))
        .assertIsDisplayed()
    markRead(failed).performClick()
    assertEquals(listOf(failed.uid), marked)
  }

  @Config(qualifiers = "w480dp-h240dp-land")
  @Test
  fun shortScreenKeepsRetryAndEntriesReachable() {
    val entries = List(10) { vote.copy(uid = "vote-$it") }
    var retries = 0
    show(onRetry = { retries++ }) {
      ready(*entries.toTypedArray()).copy(loadState = NotificationsLoadState.FAILED)
    }

    val list = composeRule.onNodeWithTag(C.Tag.notifications_list)
    composeRule
        .onNodeWithText(context.getString(R.string.notifications_load_failed))
        .assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.notifications_retry)).performClick()
    assertEquals(1, retries)

    list.performScrollToNode(hasTestTag(C.Tag.notifications_row_prefix + entries.last().uid))
    row(entries.last()).assertIsDisplayed()
  }

  @Test
  fun endedSessionHidesTheInbox() {
    val state = mutableStateOf(ready(vote))
    show { state.value }
    row(vote).assertIsDisplayed()

    composeRule.runOnIdle {
      state.value = state.value.copy(loadState = NotificationsLoadState.SESSION_ENDED)
    }

    row(vote).assertDoesNotExist()
    markRead(vote).assertDoesNotExist()
  }

  private fun show(
      onBack: () -> Unit = {},
      onRetry: () -> Unit = {},
      onOpen: (Notification) -> Unit = {},
      onMarkRead: (String) -> Unit = {},
      state: () -> NotificationsUiState,
  ) {
    composeRule.setContent {
      RoamigoTheme { NotificationsScreen(state(), onBack, onRetry, onOpen, onMarkRead) }
    }
  }

  private fun ready(vararg entries: Notification) =
      NotificationsUiState(NotificationsLoadState.READY, entries.toList())

  private fun row(entry: Notification) =
      composeRule.onNodeWithTag(C.Tag.notifications_row_prefix + entry.uid)

  private fun markRead(entry: Notification) =
      composeRule.onNodeWithTag(C.Tag.notifications_mark_read_prefix + entry.uid)
}
