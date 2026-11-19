// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the real ViewModel-to-screen connection with injected fake repositories. */
@RunWith(AndroidJUnit4::class)
class FriendsSearchRouteTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val store = FakeFriendRepository.Store()
  private val aliceFriends = FakeFriendRepository("alice", store)
  private val bobFriends = FakeFriendRepository("bob", store)

  private val users =
      FakeUserRepository(
          User("alice", "Alice", null),
          listOf(
              User("bob", "Bob", null),
              User("carol", "Bonnie", null),
          ),
          emailsByUserId = mapOf("bob" to "bob@example.com"),
      )

  private val auth =
      FakeAuthRepository(
          AuthenticatedUser("alice", "Alice", null, null),
          initiallySignedIn = true,
      )

  private var ended = 0
  private var backs = 0

  private fun show() {
    compose.setContent {
      RoamigoTheme {
        FriendsSearchRoute(
            userRepository = users,
            friendRepository = aliceFriends,
            authRepository = auth,
            currentUserId = "alice",
            onBack = { backs++ },
            onSessionEnded = { ended++ },
        )
      }
    }
  }

  private fun waitForTag(tag: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun nameAndEmailSearchUseTheRealViewModelAndRepository() {
    show()

    val query = compose.onNodeWithTag(C.Tag.friends_directory_query)

    // Search by display name.
    query.performTextInput("Bob")
    waitForTag(C.Tag.friends_directory_user_prefix + "bob")

    // Switch to a different result to avoid accidentally passing
    // the email assertion with a stale result.
    query.performTextReplacement("Bon")
    waitForTag(C.Tag.friends_directory_user_prefix + "carol")

    // Search by exact email.
    query.performTextReplacement("bob@example.com")
    waitForTag(C.Tag.friends_directory_user_prefix + "bob")

    compose.onNodeWithTag(C.Tag.friends_directory_user_prefix + "bob").assertIsDisplayed()
  }

  @Test
  fun sendingRequestUpdatesTheRelationshipWithoutRefresh() {
    show()

    compose.onNodeWithTag(C.Tag.friends_directory_query).performTextInput("Bob")

    waitForTag(C.Tag.friends_directory_send_prefix + "bob")
    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").performClick()

    compose.waitUntil(5_000) { runBlocking { bobFriends.getIncomingRequests().size == 1 } }

    assertEquals(
        1,
        runBlocking { bobFriends.getIncomingRequests().size },
    )

    compose
        .onNodeWithText(compose.activity.getString(R.string.friends_search_pending))
        .assertIsDisplayed()
  }

  @Test
  fun relationshipFailureDisablesSendUntilRetry() {
    aliceFriends.failure = FriendRepositoryException.Network()
    show()

    compose.onNodeWithTag(C.Tag.friends_directory_query).performTextInput("Bob")

    waitForTag(C.Tag.friends_directory_user_prefix + "bob")

    // Relationship observation failed.
    compose.onNodeWithTag(C.Tag.friends_directory_relationship_retry).assertIsDisplayed()

    // UNKNOWN shows a status label, not a disabled Add button.
    compose
        .onNodeWithText(compose.activity.getString(R.string.friends_search_status_unknown))
        .assertIsDisplayed()

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertDoesNotExist()

    // Restore repository access and retry observation.
    compose.runOnIdle { aliceFriends.failure = null }

    compose.onNodeWithTag(C.Tag.friends_directory_relationship_retry).performClick()

    // Wait for the refreshed relationship status.
    compose.waitUntil(5_000) {
      compose
          .onAllNodesWithTag(C.Tag.friends_directory_send_prefix + "bob")
          .fetchSemanticsNodes()
          .any { !it.config.contains(SemanticsProperties.Disabled) }
    }

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertIsEnabled()
  }

  @Test
  fun signingOutAfterRelationshipFailureEndsTheSession() {
    show()

    compose.onNodeWithTag(C.Tag.friends_directory_query).performTextInput("Bob")
    waitForTag(C.Tag.friends_directory_user_prefix + "bob")

    compose.runOnIdle { aliceFriends.failure = FriendRepositoryException.Network() }

    waitForTag(C.Tag.friends_directory_relationship_retry)
    compose.runOnIdle { auth.signOut() }

    compose.waitUntil(5_000) { ended == 1 }
    assertEquals(0, aliceFriends.activeObservers)
  }

  @Test
  fun backIsForwardedToTheNavigator() {
    show()
    compose.onNodeWithTag(C.Tag.friends_search_back).performClick()
    assertEquals(1, backs)
  }
}
