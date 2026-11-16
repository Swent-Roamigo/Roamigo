// AI assistance was used for implementation support.
package com.swent.roamigo.ui.friends

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.users.UserRepositoryException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val viewModelStore = ViewModelStore()
  private val store = FakeFriendRepository.Store()
  private val alice = FakeFriendRepository("alice", store)
  private val bob = FakeFriendRepository("bob", store)
  private val carol = FakeFriendRepository("carol", store)
  private val repository = ControlledFriendRepository(alice)
  private val users = ControlledUserRepository()
  private val auth =
      FakeAuthRepository(AuthenticatedUser("alice", "Alice", null, null), initiallySignedIn = true)

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After
  fun tearDown() {
    viewModelStore.clear()
    Dispatchers.resetMain()
  }

  private fun viewModel(authRepository: AuthRepository = auth): FriendsViewModel =
      FriendsViewModel(repository, users, authRepository, "alice").also {
        viewModelStore.put("friends", it)
      }

  @Test
  fun factoryCreatesAnObservingViewModelAndHealthyRetryKeepsItsListeners() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val factory = FriendsViewModel.factory(repository, users, auth, "alice")
        val model = ViewModelProvider(viewModelStore, factory)[FriendsViewModel::class.java]
        advanceUntilIdle()

        assertEquals(listOf(request), model.uiState.value.incomingRequests)
        assertEquals("Bob", model.uiState.value.profiles["bob"]?.displayName)
        assertEquals(3, alice.activeObservers)

        model.retry()
        advanceUntilIdle()
        assertEquals(3, alice.activeObservers)
        assertEquals(1, users.lookupCount)
      }

  @Test
  fun loadsSeparateRelationshipListsAndAvailableProfiles() =
      runTest(dispatcher) {
        val accepted = bob.sendFriendRequest("alice")
        alice.acceptFriendRequest("bob", accepted.uid)
        val incoming = carol.sendFriendRequest("alice")
        val outgoing = alice.sendFriendRequest("dana")
        val model = viewModel()
        assertTrue(model.uiState.value.isLoading)
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.loadError)
        assertEquals(listOf(accepted.uid), state.friends.map { it.uid })
        assertEquals(listOf(incoming), state.incomingRequests)
        assertEquals(listOf(outgoing), state.outgoingRequests)
        assertEquals(setOf("bob", "carol", "dana"), state.profiles.keys)
        assertEquals("https://example.com/bob.png", state.profiles["bob"]?.profilePictureUrl)
      }

  @Test
  fun emptyRelationshipsFinishLoadingWithoutProfileLookup() =
      runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(FriendsState(isLoading = false), model.uiState.value)
        assertEquals(0, users.lookupCount)
      }

  @Test
  fun acceptanceUpdatesBothUsersAndTheObservedLists() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        model.acceptRequest(request.uid)
        advanceUntilIdle()

        assertTrue(model.uiState.value.incomingRequests.isEmpty())
        assertEquals(listOf(request.uid), model.uiState.value.friends.map { it.uid })
        assertEquals(alice.getFriends(), bob.getFriends())
        assertEquals("bob" to request.uid, repository.lastTarget)
      }

  @Test
  fun declineRemovesThePendingRequestWithoutEstablishingFriendship() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        model.declineRequest(request.uid)
        advanceUntilIdle()

        assertTrue(model.uiState.value.incomingRequests.isEmpty())
        assertTrue(model.uiState.value.friends.isEmpty())
        assertTrue(bob.getOutgoingRequests().isEmpty())
        assertEquals("bob" to request.uid, repository.lastTarget)
      }

  @Test
  fun removalClearsFriendshipForBothParticipants() =
      runTest(dispatcher) {
        val request = alice.sendFriendRequest("bob")
        bob.acceptFriendRequest("alice", request.uid)
        val model = viewModel()
        advanceUntilIdle()
        model.removeFriend(request.uid)
        advanceUntilIdle()

        assertTrue(model.uiState.value.friends.isEmpty())
        assertTrue(bob.getFriends().isEmpty())
        assertEquals("bob" to request.uid, repository.lastTarget)
      }

  @Test
  fun recipientCanDismissRemovalFailureAndRetryWithoutLosingFriendship() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        alice.acceptFriendRequest("bob", request.uid)
        val model = viewModel()
        advanceUntilIdle()
        repository.actionFailure = FriendRepositoryException.Network()

        model.removeFriend(request.uid)
        advanceUntilIdle()

        assertEquals(listOf(request.uid), model.uiState.value.friends.map { it.uid })
        assertEquals(alice.getFriends(), bob.getFriends())
        assertEquals("bob" to request.uid, repository.lastTarget)
        assertTrue(model.uiState.value.processingIds.isEmpty())
        assertEquals(
            FriendsActionFailure(request.uid, FriendsAction.REMOVE),
            model.uiState.value.actionFailure,
        )

        model.dismissActionFailure()
        assertNull(model.uiState.value.actionFailure)
        assertEquals(listOf(request.uid), model.uiState.value.friends.map { it.uid })

        repository.actionFailure = null
        model.removeFriend(request.uid)
        advanceUntilIdle()

        assertTrue(model.uiState.value.friends.isEmpty())
        assertTrue(model.uiState.value.profiles.isEmpty())
        assertTrue(bob.getFriends().isEmpty())
        assertEquals(2, repository.actionCount)
      }

  @Test
  fun observesChangesMadeByTheOtherParticipantWithoutRefresh() =
      runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        val request = alice.sendFriendRequest("bob")
        advanceUntilIdle()
        assertEquals(listOf(request), model.uiState.value.outgoingRequests)

        bob.acceptFriendRequest("alice", request.uid)
        advanceUntilIdle()
        assertTrue(model.uiState.value.outgoingRequests.isEmpty())
        assertEquals(1, model.uiState.value.friends.size)

        bob.removeFriend("alice", request.uid)
        advanceUntilIdle()
        assertTrue(model.uiState.value.friends.isEmpty())
      }

  @Test
  fun duplicateAndOpposingActionsAreIgnoredWhileProcessing() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        model.acceptRequest(request.uid)
        model.acceptRequest(request.uid)
        model.declineRequest(request.uid)
        runCurrent()
        assertEquals(1, repository.actionCount)
        assertEquals(setOf(request.uid), model.uiState.value.processingIds)
        assertEquals(listOf(request), model.uiState.value.incomingRequests)
        assertTrue(model.uiState.value.friends.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.uiState.value.processingIds.isEmpty())
        assertEquals(1, model.uiState.value.friends.size)
      }

  @Test
  fun actionFailuresPreserveRelationshipsAndAllowRetry() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        repository.actionFailure = FriendRepositoryException.Network()
        model.acceptRequest(request.uid)
        advanceUntilIdle()

        assertEquals(listOf(request), model.uiState.value.incomingRequests)
        assertTrue(model.uiState.value.friends.isEmpty())
        assertTrue(model.uiState.value.processingIds.isEmpty())
        assertEquals(
            FriendsActionFailure(request.uid, FriendsAction.ACCEPT),
            model.uiState.value.actionFailure,
        )

        repository.actionFailure = null
        model.acceptRequest(request.uid)
        advanceUntilIdle()
        assertNull(model.uiState.value.actionFailure)
        assertEquals(1, model.uiState.value.friends.size)
      }

  @Test
  fun outgoingUnknownAndStaleRequestActionsDoNotReachTheRepository() =
      runTest(dispatcher) {
        val outgoing = alice.sendFriendRequest("bob")
        val incoming = carol.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        alice.declineFriendRequest("carol", incoming.uid)
        val replacement = carol.sendFriendRequest("alice")
        advanceUntilIdle()

        model.acceptRequest(outgoing.uid)
        model.declineRequest(outgoing.uid)
        model.removeFriend(outgoing.uid)
        model.acceptRequest(incoming.uid)
        model.declineRequest("unknown")
        advanceUntilIdle()

        assertEquals(0, repository.actionCount)
        assertEquals(listOf(replacement), model.uiState.value.incomingRequests)
      }

  @Test
  fun observationFailureKeepsDataAndRetryRestartsListeners() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        alice.failure = FriendRepositoryException.Network()
        advanceUntilIdle()

        assertEquals(FriendsLoadError.RELATIONSHIPS, model.uiState.value.loadError)
        assertEquals(listOf(request), model.uiState.value.incomingRequests)
        assertFalse(model.uiState.value.isLoading)
        assertEquals(0, alice.activeObservers)

        alice.failure = null
        model.retry()
        model.retry()
        advanceUntilIdle()
        assertNull(model.uiState.value.loadError)
        assertEquals(3, alice.activeObservers)
        alice.declineFriendRequest("bob", request.uid)
        advanceUntilIdle()
        assertTrue(model.uiState.value.incomingRequests.isEmpty())
      }

  @Test
  fun profileFailureDoesNotHideRelationshipsAndCanBeRetried() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        users.lookupFailure = UserRepositoryException.Network()
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(request), model.uiState.value.incomingRequests)
        assertEquals(FriendsLoadError.PROFILES, model.uiState.value.loadError)
        users.lookupFailure = null
        model.retry()
        advanceUntilIdle()
        assertNull(model.uiState.value.loadError)
        assertEquals("Bob", model.uiState.value.profiles["bob"]?.displayName)
      }

  @Test
  fun newerRelationshipsCancelPendingProfileLookupAndPublishCurrentProfiles() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        val gate = CompletableDeferred<Unit>()
        users.lookupGate = gate
        val model = viewModel()
        runCurrent()
        assertEquals(listOf(incoming), model.uiState.value.incomingRequests)
        assertEquals(1, users.lookupCount)

        val newerIncoming = carol.sendFriendRequest("alice")
        runCurrent()
        assertEquals(2, users.lookupCount)
        assertNull(model.uiState.value.loadError)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            setOf(incoming.uid, newerIncoming.uid),
            model.uiState.value.incomingRequests.map { it.uid }.toSet(),
        )
        assertEquals(setOf("bob", "carol"), model.uiState.value.profiles.keys)
        assertNull(model.uiState.value.loadError)
      }

  @Test
  fun profileAuthenticationFailureClearsPrivateStateAndReleasesListeners() =
      runTest(dispatcher) {
        bob.sendFriendRequest("alice")
        users.lookupFailure = UserRepositoryException.NotAuthenticated()
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        assertEquals(0, alice.activeObservers)

        users.lookupFailure = null
        model.retry()
        advanceUntilIdle()
        assertEquals(FriendsLoadError.SESSION, model.uiState.value.loadError)
        assertEquals(0, alice.activeObservers)
      }

  @Test
  fun missingProfileDoesNotDropTheRequest() =
      runTest(dispatcher) {
        val request = FakeFriendRepository("unpublished", store).sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(request), model.uiState.value.incomingRequests)
        assertTrue(model.uiState.value.profiles.isEmpty())
        assertNull(model.uiState.value.loadError)
      }

  @Test
  fun accountChangeClearsPrivateStateAndCancelsPendingActions() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.acceptRequest(request.uid)
        runCurrent()
        alice.currentUserId = "other"
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        assertEquals(0, alice.activeObservers)
        gate.complete(Unit)
        model.retry()
        model.acceptRequest(request.uid)
        advanceUntilIdle()
        assertTrue(bob.getFriends().isEmpty())
        assertEquals(FriendsLoadError.SESSION, model.uiState.value.loadError)
      }

  @Test
  fun mismatchedAccountNeverSubscribesToRelationships() =
      runTest(dispatcher) {
        users.currentUser = User("other", "Other", null)
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(FriendsLoadError.SESSION, model.uiState.value.loadError)
        assertEquals(0, alice.activeObservers)
      }

  @Test
  fun accountMismatchDuringActionCancelsOtherWritesAndClearsPrivateState() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        val otherIncoming = carol.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.acceptRequest(incoming.uid)
        runCurrent()
        assertEquals(setOf(incoming.uid), model.uiState.value.processingIds)

        users.currentUser = User("other", "Other", null)
        model.declineRequest(otherIncoming.uid)
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        assertEquals(0, alice.activeObservers)
        assertEquals(1, repository.actionCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(alice.getFriends().isEmpty())
        assertEquals(listOf(incoming), bob.getOutgoingRequests())
        assertEquals(listOf(otherIncoming), carol.getOutgoingRequests())
      }

  @Test
  fun signOutAfterObservationFailureClearsAllRetainedPrivateState() =
      runTest(dispatcher) {
        val friendship = bob.sendFriendRequest("alice")
        alice.acceptFriendRequest("bob", friendship.uid)
        val incoming = carol.sendFriendRequest("alice")
        val outgoing = alice.sendFriendRequest("dana")
        val model = viewModel()
        advanceUntilIdle()
        repository.actionFailure = FriendRepositoryException.Network()
        model.declineRequest(incoming.uid)
        advanceUntilIdle()

        alice.failure = FriendRepositoryException.Network()
        advanceUntilIdle()
        assertEquals(FriendsLoadError.RELATIONSHIPS, model.uiState.value.loadError)
        assertEquals(listOf(friendship.uid), model.uiState.value.friends.map { it.uid })
        assertEquals(listOf(incoming), model.uiState.value.incomingRequests)
        assertEquals(listOf(outgoing), model.uiState.value.outgoingRequests)
        assertEquals(setOf("bob", "carol", "dana"), model.uiState.value.profiles.keys)
        assertEquals(
            FriendsActionFailure(incoming.uid, FriendsAction.DECLINE),
            model.uiState.value.actionFailure,
        )
        assertEquals(0, alice.activeObservers)

        auth.signOut()
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        alice.failure = null
        model.retry()
        model.acceptRequest(incoming.uid)
        advanceUntilIdle()
        assertEquals(FriendsLoadError.SESSION, model.uiState.value.loadError)
        assertEquals(0, alice.activeObservers)
        assertEquals(1, repository.actionCount)
      }

  @Test
  fun accountChangeAfterObservationFailureClearsPreviousAccountData() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        alice.failure = FriendRepositoryException.Network()
        advanceUntilIdle()
        assertEquals(listOf(incoming), model.uiState.value.incomingRequests)
        assertEquals(FriendsLoadError.RELATIONSHIPS, model.uiState.value.loadError)
        assertEquals(0, alice.activeObservers)

        auth.setCurrentAccount(AuthenticatedUser("other", "Other", null, null))
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        assertEquals(0, alice.activeObservers)
      }

  @Test
  fun signOutAfterObservationFailureCancelsPendingWrites() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        val model = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.acceptRequest(incoming.uid)
        runCurrent()
        assertEquals(setOf(incoming.uid), model.uiState.value.processingIds)

        alice.failure = FriendRepositoryException.Network()
        advanceUntilIdle()
        assertEquals(FriendsLoadError.RELATIONSHIPS, model.uiState.value.loadError)
        assertEquals(0, alice.activeObservers)
        auth.signOut()
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        alice.failure = null
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(bob.getFriends().isEmpty())
        assertEquals(listOf(incoming), bob.getOutgoingRequests())
        assertEquals(FriendsLoadError.SESSION, model.uiState.value.loadError)
      }

  @Test
  fun signOutAfterProfileFailureStillClearsRetainedRelationships() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        users.lookupFailure = UserRepositoryException.Network()
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(FriendsLoadError.PROFILES, model.uiState.value.loadError)
        assertEquals(listOf(incoming), model.uiState.value.incomingRequests)

        auth.signOut()
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        assertEquals(0, alice.activeObservers)
      }

  @Test
  fun signOutCancelsPendingProfileLookupWithoutRestoringPrivateState() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        val gate = CompletableDeferred<Unit>()
        users.lookupGate = gate
        val model = viewModel()
        runCurrent()
        assertEquals(listOf(incoming), model.uiState.value.incomingRequests)
        assertEquals(1, users.lookupCount)

        auth.signOut()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
            model.uiState.value,
        )
        assertEquals(0, alice.activeObservers)
      }

  @Test
  fun invalidInitialAuthCannotStartOrReopenRelationshipObservation() =
      runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(dispatcher.scheduler))
        val expected = FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION)
        for (account in listOf(null, AuthenticatedUser("other", "Other", null, null))) {
          auth.setCurrentAccount(account)
          val model = viewModel()
          assertEquals(expected, model.uiState.value)
          assertEquals(0, alice.activeObservers)

          auth.setCurrentAccount(AuthenticatedUser("alice", "Alice", null, null))
          model.retry()
          advanceUntilIdle()
          assertEquals(expected, model.uiState.value)
          assertEquals(0, alice.activeObservers)
          viewModelStore.clear()
        }
      }

  @Test
  fun lostAuthObservationClearsStateOnCompletionOrFailure() =
      runTest(dispatcher) {
        val incoming = bob.sendFriendRequest("alice")
        for (failure in listOf(null, IllegalStateException("auth observer failed"))) {
          val controlledAuth = ControlledAuthRepository(auth)
          val model = viewModel(controlledAuth)
          advanceUntilIdle()
          assertEquals(listOf(incoming), model.uiState.value.incomingRequests)
          assertEquals(1, controlledAuth.activeObservers)

          controlledAuth.updates.close(failure)
          advanceUntilIdle()

          assertEquals(
              FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION),
              model.uiState.value,
          )
          assertEquals(0, controlledAuth.activeObservers)
          assertEquals(0, alice.activeObservers)
          viewModelStore.clear()
        }
      }

  @Test
  fun clearingTheViewModelReleasesListenersAndCancelsWrites() =
      runTest(dispatcher) {
        val request = bob.sendFriendRequest("alice")
        val controlledAuth = ControlledAuthRepository(auth)
        val model = viewModel(controlledAuth)
        advanceUntilIdle()
        assertEquals(1, controlledAuth.activeObservers)
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.acceptRequest(request.uid)
        runCurrent()
        viewModelStore.clear()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, alice.activeObservers)
        assertEquals(0, controlledAuth.activeObservers)
        assertTrue(bob.getFriends().isEmpty())
        assertNull(model.uiState.value.actionFailure)
      }

  private class ControlledAuthRepository(delegate: AuthRepository) : AuthRepository by delegate {
    val updates =
        Channel<AuthenticatedUser?>(Channel.UNLIMITED).also {
          it.trySend(delegate.getCurrentUser())
        }
    var activeObservers = 0
      private set

    override fun observeCurrentUser(): Flow<AuthenticatedUser?> = flow {
      activeObservers++
      try {
        emitAll(updates.receiveAsFlow())
      } finally {
        activeObservers--
      }
    }
  }

  private class ControlledFriendRepository(private val delegate: FriendRepository) :
      FriendRepository by delegate {
    var gate: CompletableDeferred<Unit>? = null
    var actionFailure: Exception? = null
    var actionCount = 0
    var lastTarget: Pair<String, String>? = null

    private suspend fun beforeAction(otherUserId: String, id: String) {
      actionCount++
      lastTarget = otherUserId to id
      gate?.await()
      actionFailure?.let { throw it }
    }

    override suspend fun acceptFriendRequest(otherUserId: String, requestId: String): Friendship {
      beforeAction(otherUserId, requestId)
      return delegate.acceptFriendRequest(otherUserId, requestId)
    }

    override suspend fun declineFriendRequest(otherUserId: String, requestId: String) {
      beforeAction(otherUserId, requestId)
      delegate.declineFriendRequest(otherUserId, requestId)
    }

    override suspend fun removeFriend(otherUserId: String, friendshipId: String) {
      beforeAction(otherUserId, friendshipId)
      delegate.removeFriend(otherUserId, friendshipId)
    }
  }

  private class ControlledUserRepository :
      UserRepository by FakeUserRepository(User("alice", "Alice", null)) {
    var currentUser = User("alice", "Alice", null)
    var lookupFailure: Exception? = null
    var lookupGate: CompletableDeferred<Unit>? = null
    var lookupCount = 0
    private val profiles =
        listOf(
            User("bob", "Bob", "https://example.com/bob.png"),
            User("carol", "Carol", null),
            User("dana", "Dana", null),
        )

    override suspend fun getCurrentUser(): User = currentUser

    override suspend fun getUsers(userIds: Set<String>): List<User> {
      lookupCount++
      lookupGate?.await()
      lookupFailure?.let { throw it }
      return profiles.filter { it.uid in userIds }
    }
  }
}
