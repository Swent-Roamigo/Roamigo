// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationEvent
import com.swent.roamigo.model.notification.NotificationRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks that the profile shows the account the user signed in with. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppProfileTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun tripsAndProfileShowTheSameSignedInAccount() {
    val authRepository =
        FakeAuthRepository(
            AuthenticatedUser("u1", "Sam Rivers", null, "https://example.com/sam.jpg"),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        TestRoamigoApp(
            friendRepository = FakeFriendRepository("u1"),
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("Sam", substring = true).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_profile_photo, useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam Rivers")
    composeRule.onNodeWithTag(C.Tag.profile_photo).assertExists()
  }

  @Test
  fun tripsAndProfileUseInitialWhenAccountHasNoPhoto() {
    val authRepository =
        FakeAuthRepository(
            AuthenticatedUser("u1", "Sam Rivers", null, null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        TestRoamigoApp(
            friendRepository = FakeFriendRepository("u1"),
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule
        .onAllNodesWithTag(C.Tag.trips_profile_photo, useUnmergedTree = true)
        .assertCountEquals(0)
    composeRule.onNodeWithText("S", useUnmergedTree = true).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_avatar).assertTextEquals("S")
    composeRule.onAllNodesWithTag(C.Tag.profile_photo).assertCountEquals(0)
  }

  @Test
  fun profileShowsTheSignedInAccount() {
    val authRepository =
        FakeAuthRepository(
            AuthenticatedUser("u1", "Sam Rivers", "sam@gmail.com", null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        TestRoamigoApp(
            friendRepository = FakeFriendRepository("u1"),
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()

    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam Rivers")
    composeRule
        .onNodeWithTag(C.Tag.profile_email)
        .assertTextContains("sam@gmail.com", substring = true)
  }

  @Test
  @Config(qualifiers = "w480dp-h320dp-land")
  fun notificationRowOpensInboxAndUpdatesTheCountAfterMarkRead() {
    val auth =
        FakeAuthRepository(AuthenticatedUser("u1", "Sam", null, null), initiallySignedIn = true)
    val inbox = TestInbox()
    openProfile(auth, inbox)
    val row = composeRule.onNodeWithTag(C.Tag.profile_notifications_row).performScrollTo()
    val noBadge = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)
    row.assert(noBadge)

    composeRule.runOnIdle {
      inbox.entries.value = listOf(notification("first"), notification("second"))
    }
    row.assert(unreadBadge(2)).performClick()
    composeRule.onNodeWithTag(C.Tag.notifications_screen).assertIsDisplayed()
    markRead("first")
    backToProfile()
    row.performScrollTo().assert(unreadBadge(1)).performClick()
    markRead("second")
    backToProfile()
    row.performScrollTo().assert(noBadge).assertIsDisplayed()
  }

  @Test
  fun notificationCountClearsWhenTheProfileAccountChanges() {
    val auth =
        FakeAuthRepository(AuthenticatedUser("u1", "Sam", null, null), initiallySignedIn = true)
    val observedAccount = MutableStateFlow(auth.getCurrentUser())
    val delayedAuth =
        object : AuthRepository by auth {
          override fun observeCurrentUser() = observedAccount
        }
    val inbox = TestInbox(listOf(notification("first")))
    openProfile(delayedAuth, inbox)
    val row = composeRule.onNodeWithTag(C.Tag.profile_notifications_row).performScrollTo()
    row.assert(unreadBadge(1))

    composeRule.runOnIdle {
      auth.setCurrentAccount(AuthenticatedUser("other", "Other", null, null))
      inbox.entries.value = listOf(notification("first"), notification("second"))
    }

    row.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))

    composeRule.runOnIdle { observedAccount.value = auth.getCurrentUser() }
    row.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
  }

  private fun openProfile(auth: AuthRepository, inbox: NotificationRepository) {
    composeRule.setContent {
      RoamigoTheme {
        TestRoamigoApp(
            friendRepository = FakeFriendRepository("u1"),
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = auth,
            notificationRepository = inbox,
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
  }

  private fun unreadBadge(count: Int) =
      SemanticsMatcher.expectValue(
          SemanticsProperties.StateDescription,
          composeRule.activity.resources.getQuantityString(
              R.plurals.notifications_unread_count,
              count,
              count,
          ),
      )

  private fun backToProfile() {
    composeRule
        .onNodeWithContentDescription(
            composeRule.activity.getString(R.string.notifications_back),
        )
        .performClick()
  }

  private fun markRead(id: String) {
    val mark = composeRule.onNodeWithTag(C.Tag.notifications_mark_read_prefix + id)
    mark.performClick()
    mark.assertDoesNotExist()
  }

  private fun notification(id: String) =
      Notification(id, "u1", NotificationEvent.TripVoteCreated("trip", id), Instant.EPOCH, false)

  private class TestInbox(initial: List<Notification> = emptyList()) : NotificationRepository {
    val entries = MutableStateFlow(initial)

    override fun observeNotifications() = entries

    override fun observeUnreadCount() = entries.map { notifications ->
      notifications.count { !it.isRead }
    }

    override suspend fun markAsRead(notificationId: String) {
      entries.update { notifications ->
        notifications.map { if (it.uid == notificationId) it.copy(isRead = true) else it }
      }
    }
  }
}
