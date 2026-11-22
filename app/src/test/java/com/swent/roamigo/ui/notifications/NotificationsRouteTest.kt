// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationEvent
import com.swent.roamigo.model.notification.NotificationRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsRouteTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun liveStateActionsAndSessionEndConnectThroughTheRoute() {
    val user = AuthenticatedUser("recipient", "Recipient", null, null)
    val auth = FakeAuthRepository(user, initiallySignedIn = true)
    val entry =
        Notification(
            "vote",
            user.uid,
            NotificationEvent.TripVoteCreated("trip", "vote"),
            Instant.EPOCH,
            false,
        )
    val entries = MutableStateFlow(listOf(entry))
    val repository =
        object : NotificationRepository {
          override fun observeNotifications() = entries

          override fun observeUnreadCount() = emptyFlow<Int>()

          override suspend fun markAsRead(notificationId: String) {
            assertEquals(entry.uid, notificationId)
            entries.value = listOf(entry.copy(isRead = true))
          }
        }
    val opened = mutableListOf<Notification>()
    var sessionEnds = 0
    composeRule.setContent {
      RoamigoTheme {
        NotificationsRoute(
            notificationRepository = repository,
            authRepository = auth,
            currentUserId = user.uid,
            onBack = {},
            onSessionEnded = { sessionEnds++ },
            onOpenNotification = { opened += it },
        )
      }
    }

    val row = composeRule.onNodeWithTag(C.Tag.notifications_row_prefix + entry.uid)
    val markRead = composeRule.onNodeWithTag(C.Tag.notifications_mark_read_prefix + entry.uid)
    row.assertIsDisplayed().performClick()
    assertEquals(listOf(entry), opened)
    markRead.performClick()
    markRead.assertDoesNotExist()
    row.assertIsDisplayed()

    composeRule.runOnIdle { auth.signOut() }
    row.assertDoesNotExist()
    assertEquals(1, sessionEnds)
  }
}
