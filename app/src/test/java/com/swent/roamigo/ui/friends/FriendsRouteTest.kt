// AI assistance was used for implementation support.
package com.swent.roamigo.ui.friends

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.friends.FriendRequest
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.users.UserRepositoryException
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class FriendsRouteTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val store = FakeFriendRepository.Store()
  private val alice = FakeFriendRepository("alice", store)
  private val bob = FakeFriendRepository("bob", store)
  private val auth =
      FakeAuthRepository(AuthenticatedUser("alice", "Alice", null, null), initiallySignedIn = true)
  private val users =
      FakeUserRepository(
          User("alice", "Alice", null),
          listOf(
              User(
                  "bob",
                  "Bob",
                  "android.resource://com.swent.roamigo/${R.drawable.destination_placeholder}",
              ),
              User("carol", "Carol", null),
          ),
      )
  private var endedSessions = 0

  private fun show(
      friendRepository: FriendRepository = alice,
      userRepository: UserRepository = users,
  ) {
    composeRule.setContent {
      RoamigoTheme {
        FriendsRoute(
            friendRepository = friendRepository,
            userRepository = userRepository,
            authRepository = auth,
            currentUserId = "alice",
            onSessionEnded = { endedSessions++ },
            onTripsClick = {},
            onMapClick = {},
        )
      }
    }
  }

  @Test
  fun displaysRepositoryProfilesAndPendingRequestsWithoutInventedStatistics() {
    runBlocking {
      val request = bob.sendFriendRequest("alice")
      alice.acceptFriendRequest("bob", request.uid)
      alice.sendFriendRequest("carol")
    }
    show()

    composeRule.onNodeWithText("Bob").assertIsDisplayed()
    // The avatar is a child of the clickable friend row's merged navigation node.
    composeRule
        .onNodeWithTag(C.Tag.friends_photo_prefix + "bob", useUnmergedTree = true)
        .assertExists()
    composeRule.onNodeWithText("Carol").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertIsDisplayed()
    composeRule.onNodeWithText("0 countries", substring = true).assertDoesNotExist()
    composeRule.onNodeWithText("0 trips", substring = true).assertDoesNotExist()
  }

  @Test
  fun anotherUsersAcceptanceAndRemovalUpdateTheScreenWithoutRefresh() {
    val request = runBlocking { alice.sendFriendRequest("bob") }
    show()
    composeRule.onNodeWithText("Pending").assertIsDisplayed()

    composeRule.runOnIdle { runBlocking { bob.acceptFriendRequest("alice", request.uid) } }
    composeRule.onNodeWithTag(C.Tag.friends_view_map_prefix + "bob").assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertDoesNotExist()

    composeRule.runOnIdle { runBlocking { bob.removeFriend("alice", request.uid) } }
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.onNodeWithText("Bob").assertDoesNotExist()
  }

  @Test
  fun initialLoadingDoesNotDisplayAnEmptyList() {
    val gate = CompletableDeferred<Unit>()
    val delayed =
        object : FriendRepository by alice {
          override fun observeFriends(): Flow<List<Friendship>> = flow {
            gate.await()
            emitAll(alice.observeFriends())
          }
        }
    show(delayed)

    composeRule.onNodeWithTag(C.Tag.friends_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertDoesNotExist()
    composeRule.runOnIdle { gate.complete(Unit) }
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_loading).assertDoesNotExist()
  }

  @Test
  fun failedObservationOffersRetryInsteadOfAnEmptyState() {
    alice.failure = FriendRepositoryException.Network()
    show()

    composeRule.onNodeWithTag(C.Tag.friends_load_error).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertDoesNotExist()
    composeRule.runOnIdle { alice.failure = null }
    composeRule.onNodeWithTag(C.Tag.friends_retry).performClick()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
  }

  @Test
  fun unavailableProfilesKeepTheirRowsAndRecoverOnRetry() {
    runBlocking { alice.sendFriendRequest("carol") }
    var failLookup = true
    val directory =
        object : UserRepository by users {
          override suspend fun getUsers(userIds: Set<String>): List<User> {
            if (failLookup) throw UserRepositoryException.Network()
            return users.getUsers(userIds)
          }
        }
    show(userRepository = directory)

    composeRule.onNodeWithText("Unknown traveler").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertIsDisplayed()
    composeRule.runOnIdle { failLookup = false }
    composeRule.onNodeWithTag(C.Tag.friends_retry).performScrollTo().performClick()
    composeRule.onNodeWithText("Carol").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_load_error).assertDoesNotExist()
  }

  @Test
  fun missingProfileUsesInitialFallbackWithoutHidingTheRelationship() {
    runBlocking { alice.sendFriendRequest("unpublished") }
    show()

    composeRule.onNodeWithText("Unknown traveler").assertIsDisplayed()
    composeRule
        .onNodeWithTag(C.Tag.friends_avatar_prefix + "unpublished")
        .assert(hasAnyDescendant(hasText("U")))
    composeRule.onNodeWithText("Pending").assertIsDisplayed()
  }

  @Test
  fun authSignOutAfterObservationFailureClearsRowsAndInvokesNavigation() {
    runBlocking { alice.sendFriendRequest("bob") }
    show()
    composeRule.onNodeWithText("Bob").assertIsDisplayed()

    composeRule.runOnIdle { alice.failure = FriendRepositoryException.Network() }
    composeRule.onNodeWithTag(C.Tag.friends_load_error).assertIsDisplayed()
    composeRule.onNodeWithText("Bob").assertIsDisplayed()

    composeRule.runOnIdle { auth.signOut() }

    composeRule.onNodeWithText("Bob").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.friends_retry).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, endedSessions) }
  }

  @Test
  fun endedSessionClearsTheListAndInvokesNavigation() {
    runBlocking { alice.sendFriendRequest("bob") }
    show()
    composeRule.onNodeWithText("Bob").assertIsDisplayed()

    composeRule.runOnIdle { alice.currentUserId = null }
    composeRule.onNodeWithText("Bob").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.friends_retry).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, endedSessions) }
  }

  @Test
  fun acceptingAnIncomingRequestMovesItToFriendsForBothParticipants() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    show()
    composeRule.onNodeWithText("Incoming requests").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.friends_photo_prefix + "bob").assertExists()
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .performScrollTo()
        .assertContentDescriptionEquals("Accept friend request from Bob")
        .performClick()

    composeRule
        .onNodeWithTag(C.Tag.friends_incoming_request_prefix + request.uid)
        .assertDoesNotExist()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
    composeRule.runOnIdle { runBlocking { assertEquals(alice.getFriends(), bob.getFriends()) } }
  }

  @Test
  fun decliningAnIncomingRequestClearsBothPendingLists() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    show()
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .performScrollTo()
        .assertContentDescriptionEquals("Decline friend request from Bob")
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.runOnIdle { runBlocking { assertTrue(bob.getOutgoingRequests().isEmpty()) } }
  }

  @Test
  fun removingAFriendClearsBothUsersFriendships() {
    val request = runBlocking {
      val request = bob.sendFriendRequest("alice")
      alice.acceptFriendRequest("bob", request.uid)
      request
    }
    show()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertContentDescriptionEquals("Remove Bob from friends")
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.runOnIdle { runBlocking { assertTrue(bob.getFriends().isEmpty()) } }
  }

  @Test
  fun answeringARequestDisablesBothActionsUntilTheRepositoryCompletes() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    val controlled = ActionRepository(alice)
    val gate = CompletableDeferred<Unit>()
    controlled.gate = gate
    show(controlled)
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .performScrollTo()
        .performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule.runOnIdle { assertEquals(1, controlled.calls) }

    composeRule.runOnIdle { gate.complete(Unit) }
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun acceptedWriteKeepsControlsDisabledUntilFriendshipSnapshotReplacesThePendingRow() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    val outgoing = runBlocking { alice.sendFriendRequest("carol") }
    val actions = ActionRepository(alice)
    val snapshots = IndependentSnapshotsRepository(actions)
    snapshots.incoming.value = listOf(request)
    snapshots.outgoing.value = listOf(outgoing)
    show(snapshots)
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .performScrollTo()
        .performClick()

    composeRule.runOnIdle {
      runBlocking { assertEquals(1, alice.getFriends().size) }
      assertEquals(1, actions.calls)
    }
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule.onNodeWithTag(C.Tag.friends_remove_prefix + request.uid).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(1, actions.calls)
      snapshots.friends.value = runBlocking { alice.getFriends() }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_incoming_request_prefix + request.uid)
        .assertDoesNotExist()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
        .assertIsEnabled()
    composeRule.onNodeWithText("Carol").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_pending_request_row).assertIsDisplayed()
  }

  @Test
  fun pendingRemovalBeforeFriendshipSnapshotShowsEmptyStateUntilTheFriendArrives() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    val actions = ActionRepository(alice)
    val snapshots = IndependentSnapshotsRepository(actions)
    snapshots.incoming.value = listOf(request)
    show(snapshots)
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .performScrollTo()
        .performClick()
    composeRule.onNodeWithTag(C.Tag.friends_accept_prefix + request.uid).assertIsNotEnabled()
    composeRule.runOnIdle {
      runBlocking { assertEquals(1, alice.getFriends().size) }
      snapshots.incoming.value = emptyList()
    }

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_remove_prefix + request.uid).assertDoesNotExist()
    composeRule.runOnIdle { snapshots.friends.value = runBlocking { alice.getFriends() } }
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertDoesNotExist()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
        .assertIsEnabled()
    composeRule.runOnIdle { assertEquals(1, actions.calls) }
  }

  @Test
  fun declinedWriteKeepsBothControlsDisabledUntilThePendingSnapshotCatchesUp() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    val actions = ActionRepository(alice)
    val snapshots = IndependentSnapshotsRepository(actions)
    snapshots.incoming.value = listOf(request)
    show(snapshots)
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .performScrollTo()
        .performClick()

    composeRule.runOnIdle { runBlocking { assertTrue(alice.getIncomingRequests().isEmpty()) } }
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule.runOnIdle {
      assertEquals(1, actions.calls)
      snapshots.incoming.value = emptyList()
    }
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule
        .onNodeWithTag(C.Tag.friends_incoming_request_prefix + request.uid)
        .assertDoesNotExist()
  }

  @Test
  fun removedFriendStaysVisibleWithDisabledControlUntilFriendshipSnapshotCatchesUp() {
    val friendship = runBlocking {
      val request = bob.sendFriendRequest("alice")
      alice.acceptFriendRequest("bob", request.uid)
    }
    val actions = ActionRepository(alice)
    val snapshots = IndependentSnapshotsRepository(actions)
    snapshots.friends.value = listOf(friendship)
    show(snapshots)
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + friendship.uid)
        .performScrollTo()
        .performClick()

    composeRule.runOnIdle { runBlocking { assertTrue(alice.getFriends().isEmpty()) } }
    composeRule.onNodeWithText("Bob").assertIsDisplayed()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + friendship.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule.runOnIdle {
      assertEquals(1, actions.calls)
      snapshots.friends.value = emptyList()
    }
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.onNodeWithText("Bob").assertDoesNotExist()
  }

  @Test
  fun friendshipSnapshotHidesAnOutgoingRequestBeforeItsPendingSnapshotCatchesUp() {
    val request = runBlocking { alice.sendFriendRequest("bob") }
    val snapshots = IndependentSnapshotsRepository(alice)
    snapshots.outgoing.value = listOf(request)
    show(snapshots)
    composeRule.onNodeWithTag(C.Tag.friends_pending_request_row).assertIsDisplayed()

    composeRule.runOnIdle {
      snapshots.friends.value =
          listOf(runBlocking { bob.acceptFriendRequest("alice", request.uid) })
    }
    composeRule.onNodeWithTag(C.Tag.friends_pending_request_row).assertDoesNotExist()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertDoesNotExist()

    composeRule.runOnIdle { snapshots.outgoing.value = emptyList() }
    composeRule.onNodeWithTag(C.Tag.friends_remove_prefix + request.uid).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_pending_request_row).assertDoesNotExist()
  }

  @Test
  fun failedAcceptanceKeepsTheRequestAndAllowsAnotherAttempt() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    val controlled = ActionRepository(alice)
    controlled.failure = FriendRepositoryException.Network()
    show(controlled)
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .performScrollTo()
        .performClick()

    composeRule
        .onNodeWithText("Could not accept the request. Please try again.")
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_accept_prefix + request.uid).assertIsEnabled()
    composeRule.onNodeWithTag(C.Tag.friends_decline_prefix + request.uid).assertIsEnabled()
    composeRule.runOnIdle {
      runBlocking { assertTrue(alice.getFriends().isEmpty()) }
      controlled.failure = null
    }
    composeRule.onNodeWithTag(C.Tag.friends_accept_prefix + request.uid).performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
    composeRule
        .onNodeWithText("Could not accept the request. Please try again.")
        .assertDoesNotExist()
  }

  @Test
  fun failedDeclineKeepsTheRequestAndAllowsAnotherAttempt() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    val controlled = ActionRepository(alice)
    controlled.failure = FriendRepositoryException.Conflict()
    show(controlled)
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .performScrollTo()
        .performClick()

    composeRule
        .onNodeWithText("Could not decline the request. Please try again.")
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_decline_prefix + request.uid).assertIsEnabled()
    composeRule.runOnIdle { controlled.failure = null }
    composeRule.onNodeWithContentDescription("Dismiss").performClick()
    composeRule
        .onNodeWithText("Could not decline the request. Please try again.")
        .assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.friends_decline_prefix + request.uid).performClick()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
  }

  @Test
  fun failedRemovalKeepsTheFriendAndDisablesDuplicateAttemptsWhileRetrying() {
    val request = runBlocking {
      val request = bob.sendFriendRequest("alice")
      alice.acceptFriendRequest("bob", request.uid)
      request
    }
    val controlled = ActionRepository(alice)
    controlled.failure = FriendRepositoryException.Network()
    show(controlled)
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .performClick()

    composeRule.onNodeWithText("Could not remove the friend. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_remove_prefix + request.uid).assertIsEnabled()
    val gate = CompletableDeferred<Unit>()
    composeRule.runOnIdle {
      controlled.failure = null
      controlled.gate = gate
    }
    composeRule.onNodeWithTag(C.Tag.friends_remove_prefix + request.uid).performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .assertIsNotEnabled()
        .performClick()
    composeRule.runOnIdle {
      assertEquals(2, controlled.calls)
      gate.complete(Unit)
    }
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w320dp-h480dp", fontScale = 1.5f)
  fun acceptanceAndRemovalRemainReachableOnASmallScreen() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    show()
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
        .performClick()
    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
        .performClick()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w640dp-h320dp-land")
  fun declineRemainsReachableInLandscape() {
    val request = runBlocking { bob.sendFriendRequest("alice") }
    show()
    composeRule
        .onNodeWithTag(C.Tag.friends_list)
        .performScrollToNode(hasTestTag(C.Tag.friends_decline_prefix + request.uid))
    composeRule
        .onNodeWithTag(C.Tag.friends_decline_prefix + request.uid)
        .performScrollTo()
        .assertIsDisplayed()
        .performClick()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w640dp-h320dp-land")
  fun retryAndNavigationRemainReachableInLandscape() {
    alice.failure = FriendRepositoryException.Network()
    show()

    composeRule.onNodeWithTag(C.Tag.friends_add_friend).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_retry).performScrollTo().assertIsDisplayed()
    composeRule.runOnIdle { alice.failure = null }
    composeRule.onNodeWithTag(C.Tag.friends_retry).performClick()
    composeRule.onNodeWithTag(C.Tag.friends_empty_state).performScrollTo().assertIsDisplayed()
  }

  private class IndependentSnapshotsRepository(delegate: FriendRepository) :
      FriendRepository by delegate {
    val friends = MutableStateFlow<List<Friendship>>(emptyList())
    val incoming = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoing = MutableStateFlow<List<FriendRequest>>(emptyList())

    override fun observeFriends(): Flow<List<Friendship>> = friends

    override fun observeIncomingRequests(): Flow<List<FriendRequest>> = incoming

    override fun observeOutgoingRequests(): Flow<List<FriendRequest>> = outgoing
  }

  private class ActionRepository(private val delegate: FriendRepository) :
      FriendRepository by delegate {
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null
    var calls = 0

    private suspend fun beforeAction() {
      calls++
      gate?.await()
      failure?.let { throw it }
    }

    override suspend fun acceptFriendRequest(otherUserId: String, requestId: String): Friendship {
      beforeAction()
      return delegate.acceptFriendRequest(otherUserId, requestId)
    }

    override suspend fun declineFriendRequest(otherUserId: String, requestId: String) {
      beforeAction()
      delegate.declineFriendRequest(otherUserId, requestId)
    }

    override suspend fun removeFriend(otherUserId: String, friendshipId: String) {
      beforeAction()
      delegate.removeFriend(otherUserId, friendshipId)
    }
  }
}
