// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.friends.FriendRequest
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.users.UserRepositoryException
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsSearchViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = ViewModelStore()

  private val relationshipsStore = FakeFriendRepository.Store()
  private val aliceFriends = FakeFriendRepository("alice", relationshipsStore)
  private val bobFriends = FakeFriendRepository("bob", relationshipsStore)
  private val carolFriends = FakeFriendRepository("carol", relationshipsStore)
  private val daveFriends = FakeFriendRepository("dave", relationshipsStore)

  private val friends = ControlledFriends(aliceFriends)

  private val alice = User("alice", "Alice", null)
  private val bob = User("bob", "Bob", "https://example.com/bob.png")
  private val carol = User("carol", "Bonnie", null)
  private val dave = User("dave", "Boris", null)

  private val users =
      ControlledUsers(
          FakeUserRepository(
              alice,
              listOf(bob, carol, dave),
              emailsByUserId = mapOf("bob" to "bob@example.com"),
          )
      )

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  private fun model(): FriendsSearchViewModel =
      FriendsSearchViewModel(users, friends, "alice").also { store.put("search", it) }

  private fun FriendsSearchViewModel.status(uid: String): SearchRelationship? =
      uiState.value.results.find { it.user.uid == uid }?.relationship

  @Test
  fun emptyAndWhitespaceQueriesNeverReadTheDirectory() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.updateQuery("  ")
        advanceUntilIdle()

        assertTrue(model.uiState.value.results.isEmpty())
        assertFalse(model.uiState.value.isSearching)
        assertEquals(0, users.searchCount)
        assertEquals(3, aliceFriends.activeObservers)
      }

  @Test
  fun namePrefixAndExactEmailAreDelegatedAndSelfIsExcluded() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.updateQuery("  BO  ")
        advanceUntilIdle()

        assertEquals(
            listOf("bob", "carol", "dave"),
            model.uiState.value.results.map { it.user.uid },
        )
        assertEquals(
            "https://example.com/bob.png",
            model.uiState.value.results.first().user.profilePictureUrl,
        )

        model.updateQuery(" BOB@EXAMPLE.COM ")
        advanceUntilIdle()

        assertEquals(
            listOf("bob"),
            model.uiState.value.results.map { it.user.uid },
        )

        model.updateQuery("Ali")
        advanceUntilIdle()

        assertTrue(model.uiState.value.results.isEmpty())
        assertEquals(
            listOf("BO", "BOB@EXAMPLE.COM", "Ali"),
            users.queries,
        )
      }

  @Test
  fun invalidLongQueryDoesNotInvokeRepository() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.updateQuery("a".repeat(121))
        advanceUntilIdle()

        assertEquals(
            SearchFailure.INVALID_QUERY,
            model.uiState.value.searchFailure,
        )
        assertFalse(model.uiState.value.isSearching)
        assertEquals(0, users.searchCount)
      }

  @Test
  fun outgoingIncomingAndFriendStatusesComeFromActualRelationships() =
      runTest(dispatcher) {
        aliceFriends.sendFriendRequest("bob")
        carolFriends.sendFriendRequest("alice")

        val accepted = daveFriends.sendFriendRequest("alice")
        aliceFriends.acceptFriendRequest("dave", accepted.uid)

        val model = model()
        model.updateQuery("Bo")
        advanceUntilIdle()

        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
        assertEquals(
            SearchRelationship.INCOMING_PENDING,
            model.status("carol"),
        )
        assertEquals(
            SearchRelationship.FRIEND,
            model.status("dave"),
        )
      }

  @Test
  fun sendingCreatesOneRequestAndUpdatesTheResult() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(SearchRelationship.AVAILABLE, model.status("bob"))

        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(1, friends.sendCount)
        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
        assertEquals(1, bobFriends.getIncomingRequests().size)
        assertTrue(model.uiState.value.sendingIds.isEmpty())
        assertNull(model.uiState.value.sendFailure)
      }

  @Test
  fun cannotSendToSelfUnknownOrExistingRelationships() =
      runTest(dispatcher) {
        aliceFriends.sendFriendRequest("bob")
        carolFriends.sendFriendRequest("alice")

        val accepted = daveFriends.sendFriendRequest("alice")
        aliceFriends.acceptFriendRequest("dave", accepted.uid)

        val model = model()
        model.updateQuery("Bo")
        advanceUntilIdle()

        listOf("alice", "missing", "bob", "carol", "dave").forEach(model::sendRequest)

        advanceUntilIdle()
        assertEquals(0, friends.sendCount)
      }

  @Test
  fun rapidDuplicateClicksProduceOnlyOneWrite() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate

        model.sendRequest("bob")
        model.sendRequest("bob")
        runCurrent()

        assertEquals(1, friends.sendCount)
        assertEquals(setOf("bob"), model.uiState.value.sendingIds)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, friends.sendCount)
        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
      }

  @Test
  fun concurrentOppositeRequestIsAcceptedByTheRepository() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate

        model.sendRequest("bob")
        runCurrent()

        bobFriends.sendFriendRequest("alice")

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(SearchRelationship.FRIEND, model.status("bob"))
        assertEquals(1, aliceFriends.getFriends().size)
      }

  @Test
  fun sendFailureKeepsResultsAndAllowsRetry() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        friends.sendFailure = FriendRepositoryException.Network()
        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(
            SendRequestFailure("bob", SendFailure.NETWORK),
            model.uiState.value.sendFailure,
        )
        assertEquals(SearchRelationship.AVAILABLE, model.status("bob"))
        assertTrue(model.uiState.value.sendingIds.isEmpty())

        model.dismissSendFailure()
        assertNull(model.uiState.value.sendFailure)

        friends.sendFailure = null
        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
      }

  @Test
  fun committedRequestWithLostAcknowledgementDoesNotShowFalseFailure() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        // Simulate a committed write followed by a lost acknowledgement.
        friends.afterSendFailure = FriendRepositoryException.Network()
        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(1, bobFriends.getIncomingRequests().size)
        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
        assertNull(model.uiState.value.sendFailure)
      }

  @Test
  fun sendErrorsAreCategorizedWithoutChangingRelationships() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val cases =
            listOf(
                FriendRepositoryException.Conflict() to SendFailure.CONFLICT,
                FriendRepositoryException.PermissionDenied() to SendFailure.PERMISSION,
                FriendRepositoryException.Unknown() to SendFailure.OTHER,
            )

        for ((error, expected) in cases) {
          friends.sendFailure = error
          model.sendRequest("bob")
          advanceUntilIdle()

          assertEquals(
              SendRequestFailure("bob", expected),
              model.uiState.value.sendFailure,
          )
          assertEquals(
              SearchRelationship.AVAILABLE,
              model.status("bob"),
          )
        }
      }

  @Test
  fun signOutBeforeWriteCancelsTheLocalSend() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate

        model.sendRequest("bob")
        runCurrent()

        aliceFriends.currentUserId = null
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)
        assertTrue(bobFriends.getIncomingRequests().isEmpty())
        assertEquals(0, aliceFriends.activeObservers)
      }

  @Test
  fun failedSearchCanBeRetriedForTheCurrentQuery() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bo")
        advanceUntilIdle()

        users.searchFailure = UserRepositoryException.Network()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(
            SearchFailure.DIRECTORY,
            model.uiState.value.searchFailure,
        )
        assertFalse(model.uiState.value.isSearching)

        users.searchFailure = null
        model.retrySearch()
        advanceUntilIdle()

        assertEquals(
            listOf("bob"),
            model.uiState.value.results.map { it.user.uid },
        )
        assertNull(model.uiState.value.searchFailure)
      }

  @Test
  fun aPreviousQueryCannotOverwriteNewerResults() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        users.searchGate = gate

        model.updateQuery("Bob")
        advanceTimeBy(300.milliseconds)
        runCurrent()

        assertEquals(1, users.searchCount)

        users.searchGate = null
        model.updateQuery("Bon")
        advanceUntilIdle()

        assertEquals(
            listOf("carol"),
            model.uiState.value.results.map { it.user.uid },
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("carol"),
            model.uiState.value.results.map { it.user.uid },
        )
      }

  @Test
  fun failedRelationshipObservationDisablesSendingUntilRetry() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        aliceFriends.failure = FriendRepositoryException.Network()
        advanceUntilIdle()

        assertTrue(model.uiState.value.relationshipsFailed)
        assertEquals(SearchRelationship.UNKNOWN, model.status("bob"))

        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(0, friends.sendCount)
        assertEquals(0, aliceFriends.activeObservers)

        aliceFriends.failure = null
        model.retryRelationships()
        advanceUntilIdle()

        assertFalse(model.uiState.value.relationshipsFailed)
        assertEquals(SearchRelationship.AVAILABLE, model.status("bob"))
        assertEquals(3, aliceFriends.activeObservers)
      }

  @Test
  fun relationshipChangesFromOtherUserUpdateVisibleStatuses() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val request = bobFriends.sendFriendRequest("alice")
        advanceUntilIdle()

        assertEquals(
            SearchRelationship.INCOMING_PENDING,
            model.status("bob"),
        )

        aliceFriends.acceptFriendRequest("bob", request.uid)
        advanceUntilIdle()

        assertEquals(SearchRelationship.FRIEND, model.status("bob"))

        aliceFriends.removeFriend("bob", request.uid)
        advanceUntilIdle()

        assertEquals(SearchRelationship.AVAILABLE, model.status("bob"))
      }

  @Test
  fun sessionEndClearsSearchDataAndStopsAllSubscriptions() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(3, aliceFriends.activeObservers)

        aliceFriends.currentUserId = null
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)
        assertTrue(model.uiState.value.results.isEmpty())
        assertEquals(0, aliceFriends.activeObservers)

        model.sendRequest("bob")
        model.updateQuery("Bon")

        assertEquals(0, friends.sendCount)

        model.retrySearch()
        model.retryRelationships()
        advanceUntilIdle()

        assertEquals(0, aliceFriends.activeObservers)
        assertEquals(1, users.searchCount)
      }

  @Test
  fun anAccountMismatchCannotSearchOrSubscribeToAnotherUsersData() =
      runTest(dispatcher) {
        users.activeUserId = "someone-else"

        val model = model()
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)

        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(0, users.searchCount)
        assertEquals(0, aliceFriends.activeObservers)
      }

  @Test
  fun clearingTheViewModelReleasesActiveRelationshipObservers() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(3, aliceFriends.activeObservers)

        store.clear()
        advanceUntilIdle()

        assertEquals(0, aliceFriends.activeObservers)
      }

  @Test
  fun factoryCreatesViewModelWithExpectedAccount() =
      runTest(dispatcher) {
        val factory = FriendsSearchViewModel.factory(users, friends, "alice")

        val model = ViewModelProvider(store, factory)[FriendsSearchViewModel::class.java]

        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(
            listOf("bob"),
            model.uiState.value.results.map { it.user.uid },
        )
      }

  // Additional tests for validation and coverage.

  @Test
  fun debounceWaits300MillisAndIgnoresWhitespaceOnlyEdits() =
      runTest(dispatcher) {
        val model = model()

        model.updateQuery("B")
        runCurrent()
        advanceTimeBy(299.milliseconds)
        runCurrent()
        assertEquals(0, users.searchCount)

        model.updateQuery("Bo")
        runCurrent()

        // Whitespace-only edits must not restart the debounce.
        advanceTimeBy(150.milliseconds)
        model.updateQuery(" Bo ")
        runCurrent()
        assertEquals(" Bo ", model.uiState.value.query)

        advanceTimeBy(149.milliseconds)
        runCurrent()
        assertEquals(0, users.searchCount)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(listOf("Bo"), users.queries)

        // They must not clear loaded results or requery either.
        val results = model.uiState.value.results
        assertTrue(results.isNotEmpty())

        model.updateQuery("Bo  ")
        advanceUntilIdle()

        // Repeating exactly the same input must also be a no-op.
        model.updateQuery("Bo  ")
        advanceUntilIdle()

        assertEquals("Bo  ", model.uiState.value.query)
        assertEquals(results, model.uiState.value.results)
        assertEquals(1, users.searchCount)
      }

  @Test
  fun clearingQueryRemovesResultsAndAnInvalidQueryCannotBeRetried() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(
            listOf("bob"),
            model.uiState.value.results.map { it.user.uid },
        )

        model.updateQuery("   ")
        advanceUntilIdle()

        assertTrue(model.uiState.value.results.isEmpty())
        assertFalse(model.uiState.value.isSearching)
        assertNull(model.uiState.value.searchFailure)

        model.updateQuery("x".repeat(255) + "@")
        advanceUntilIdle()

        assertEquals(
            SearchFailure.INVALID_QUERY,
            model.uiState.value.searchFailure,
        )

        model.retrySearch()
        advanceUntilIdle()

        assertEquals(1, users.searchCount)
      }

  @Test
  fun emailLengthBoundaryIsAcceptedButLongerEmailIsRejected() =
      runTest(dispatcher) {
        val model = model()

        model.updateQuery("a".repeat(253) + "@")
        advanceUntilIdle()

        assertNull(model.uiState.value.searchFailure)
        assertEquals(1, users.searchCount)

        model.updateQuery("a".repeat(254) + "@")
        advanceUntilIdle()

        assertEquals(
            SearchFailure.INVALID_QUERY,
            model.uiState.value.searchFailure,
        )
        assertEquals(1, users.searchCount)
      }

  @Test
  fun malformedDuplicateAndSelfResultsAreExcludedAndResultsCappedAt20() =
      runTest(dispatcher) {
        val model = model()

        users.searchResultsOverride =
            listOf(
                alice,
                User("", "Blank", null),
                User(" ", "Whitespace", null),
                User("/bad", "Slash", null),
                User(".", "Dot", null),
                User("..", "Dots", null),
                bob,
                bob,
            ) + (1..25).map { User("extra-$it", "Person $it", null) }

        model.updateQuery("Bob")
        advanceUntilIdle()

        val ids = model.uiState.value.results.map { it.user.uid }

        assertEquals(20, ids.size)
        assertEquals(20, ids.distinct().size)
        assertEquals("bob", ids.first())
        assertFalse(ids.contains("alice"))
        assertFalse(ids.contains("/bad"))
      }

  @Test
  fun loadingRelationsKeepResultsUnknownAndBlockRequestCreation() =
      runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        friends.relationshipGate = gate

        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertTrue(model.uiState.value.relationshipsLoading)
        assertEquals(SearchRelationship.UNKNOWN, model.status("bob"))

        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(0, friends.sendCount)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.uiState.value.relationshipsLoading)
        assertEquals(SearchRelationship.AVAILABLE, model.status("bob"))
      }

  @Test
  fun laterObservedRequestClearsEarlierNetworkFailure() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        friends.sendFailure = FriendRepositoryException.Network()
        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(
            SendFailure.NETWORK,
            model.uiState.value.sendFailure?.reason,
        )

        aliceFriends.sendFriendRequest("bob")
        advanceUntilIdle()

        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
        assertNull(model.uiState.value.sendFailure)

        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(1, friends.sendCount)
      }

  @Test
  fun lateSendFailureDoesNotAppearForAUserNoLongerInSearchResults() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate
        friends.sendFailure = FriendRepositoryException.Network()

        model.sendRequest("bob")
        runCurrent()

        model.updateQuery("Bon")
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("carol"),
            model.uiState.value.results.map { it.user.uid },
        )
        assertNull(model.uiState.value.sendFailure)
        assertTrue(model.uiState.value.sendingIds.isEmpty())
      }

  @Test
  fun signingOutDuringSearchCannotPublishPrivateResults() =
      runTest(dispatcher) {
        val model = model()

        val gate = CompletableDeferred<Unit>()
        users.searchGate = gate

        model.updateQuery("Bob")
        advanceTimeBy(300.milliseconds)
        runCurrent()

        assertEquals(1, users.searchCount)

        aliceFriends.currentUserId = null
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)
        assertTrue(model.uiState.value.results.isEmpty())
        assertFalse(model.uiState.value.isSearching)
      }

  @Test
  fun authenticationFailureDuringSearchClearsPrivateState() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        users.searchFailure = UserRepositoryException.NotAuthenticated()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)
        assertTrue(model.uiState.value.results.isEmpty())
        assertEquals("", model.uiState.value.query)
        assertFalse(model.uiState.value.isSearching)
        assertEquals(0, aliceFriends.activeObservers)
      }

  @Test
  fun authenticationFailureDuringSendEndsSessionWithoutWriting() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        friends.sendFailure = FriendRepositoryException.NotAuthenticated()
        model.sendRequest("bob")
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)
        assertTrue(model.uiState.value.results.isEmpty())
        assertTrue(model.uiState.value.sendingIds.isEmpty())
        assertTrue(bobFriends.getIncomingRequests().isEmpty())
        assertEquals(0, aliceFriends.activeObservers)
      }

  @Test
  fun accountChangeAfterDirectoryResponseInvalidatesSearch() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        users.searchGate = gate

        model.updateQuery("Bob")
        advanceTimeBy(300.milliseconds)
        runCurrent()

        assertEquals(1, users.searchCount)

        users.activeUserId = "another-account"
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.uiState.value.sessionEnded)
        assertTrue(model.uiState.value.results.isEmpty())
        assertEquals(0, aliceFriends.activeObservers)
      }

  @Test
  fun independentRequestsToTwoUsersCanRunConcurrently() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bo")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate

        model.sendRequest("bob")
        model.sendRequest("carol")
        runCurrent()

        assertEquals(2, friends.sendCount)
        assertEquals(
            setOf("bob", "carol"),
            model.uiState.value.sendingIds,
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("bob"),
        )
        assertEquals(
            SearchRelationship.OUTGOING_PENDING,
            model.status("carol"),
        )
        assertTrue(model.uiState.value.sendingIds.isEmpty())
        assertEquals(1, bobFriends.getIncomingRequests().size)
        assertEquals(1, carolFriends.getIncomingRequests().size)
      }

  @Test
  fun lateSuccessfulSendDoesNotReplaceCurrentSearchResults() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate

        model.sendRequest("bob")
        runCurrent()

        model.updateQuery("Bon")
        advanceUntilIdle()

        assertEquals(
            listOf("carol"),
            model.uiState.value.results.map { it.user.uid },
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("carol"),
            model.uiState.value.results.map { it.user.uid },
        )
        assertEquals(1, bobFriends.getIncomingRequests().size)
        assertTrue(model.uiState.value.sendingIds.isEmpty())
        assertNull(model.uiState.value.sendFailure)
      }

  @Test
  fun retryWithoutErrorsDoesNotStartAdditionalWork() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(1, users.searchCount)
        assertEquals(3, aliceFriends.activeObservers)

        model.retrySearch()
        model.retryRelationships()
        advanceUntilIdle()

        assertEquals(1, users.searchCount)
        assertEquals(3, aliceFriends.activeObservers)
      }

  @Test
  fun maximumNameQueryLengthIsAccepted() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        val maximum = "a".repeat(120)
        model.updateQuery(maximum)
        advanceUntilIdle()

        assertEquals(1, users.searchCount)
        assertEquals(maximum, users.queries.single())
        assertNull(model.uiState.value.searchFailure)

        model.updateQuery(maximum + "b")
        advanceUntilIdle()

        assertEquals(
            SearchFailure.INVALID_QUERY,
            model.uiState.value.searchFailure,
        )
        assertEquals(1, users.searchCount)
      }

  @Test
  fun retrySearchRunsImmediatelyWithoutDebounce() =
      runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        users.searchFailure = UserRepositoryException.Network()
        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(
            SearchFailure.DIRECTORY,
            model.uiState.value.searchFailure,
        )
        assertEquals(1, users.searchCount)

        users.searchFailure = null
        model.retrySearch()
        runCurrent()

        assertEquals(2, users.searchCount)
        assertEquals(
            listOf("bob"),
            model.uiState.value.results.map { it.user.uid },
        )
        assertNull(model.uiState.value.searchFailure)
      }

  @Test
  fun searchWhileRelationshipsAreUnavailableKeepsUnknownStatus() =
      runTest(dispatcher) {
        aliceFriends.failure = FriendRepositoryException.Network()

        val model = model()
        advanceUntilIdle()

        assertTrue(model.uiState.value.relationshipsFailed)

        model.updateQuery("Bob")
        advanceUntilIdle()

        assertEquals(SearchRelationship.UNKNOWN, model.status("bob"))

        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(0, friends.sendCount)

        aliceFriends.failure = null
        model.retryRelationships()
        advanceUntilIdle()

        assertEquals(
            SearchRelationship.AVAILABLE,
            model.status("bob"),
        )
        assertFalse(model.uiState.value.relationshipsFailed)
      }

  @Test
  fun changingQueryClearsPreviousSendError() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        friends.sendFailure = FriendRepositoryException.Network()
        model.sendRequest("bob")
        advanceUntilIdle()

        assertEquals(
            SendFailure.NETWORK,
            model.uiState.value.sendFailure?.reason,
        )

        model.updateQuery("Bon")

        assertNull(model.uiState.value.sendFailure)
        assertTrue(model.uiState.value.isSearching)

        advanceUntilIdle()

        assertEquals(
            listOf("carol"),
            model.uiState.value.results.map { it.user.uid },
        )
      }

  @Test
  fun sendFailureAfterRelationshipObservationFailsIsReported() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bob")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate
        friends.sendFailure = FriendRepositoryException.Network()

        model.sendRequest("bob")
        runCurrent()

        aliceFriends.failure = FriendRepositoryException.Network()
        advanceUntilIdle()

        assertTrue(model.uiState.value.relationshipsFailed)
        assertEquals(SearchRelationship.UNKNOWN, model.status("bob"))

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            SendFailure.NETWORK,
            model.uiState.value.sendFailure?.reason,
        )
        assertTrue(model.uiState.value.sendingIds.isEmpty())
      }

  @Test
  fun staleSearchSuccessCannotReplaceResultsOfNewerQuery() =
      runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()

        val directory =
            object : UserRepository by users {
              override suspend fun searchUsers(query: String): List<User> =
                  if (query == "Bob") {
                    withContext(NonCancellable) {
                      gate.await()
                      listOf(bob)
                    }
                  } else {
                    listOf(carol)
                  }
            }

        val model =
            FriendsSearchViewModel(directory, friends, "alice").also { store.put("search", it) }

        model.updateQuery("Bob")
        advanceTimeBy(300.milliseconds)
        runCurrent()

        // The previous repository call ignores cancellation.
        model.updateQuery("Bon")
        advanceUntilIdle()

        assertEquals(listOf("carol"), model.uiState.value.results.map { it.user.uid })

        // The old request finishes after the newer one.
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("carol"), model.uiState.value.results.map { it.user.uid })
        assertNull(model.uiState.value.searchFailure)
      }

  @Test
  fun staleSearchFailureCannotReplaceSuccessfulNewerSearch() =
      runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()

        val directory =
            object : UserRepository by users {
              override suspend fun searchUsers(query: String): List<User> =
                  if (query == "Bob") {
                    withContext(NonCancellable) {
                      gate.await()
                      throw UserRepositoryException.Network()
                    }
                  } else {
                    listOf(carol)
                  }
            }

        val model =
            FriendsSearchViewModel(directory, friends, "alice").also { store.put("search", it) }

        model.updateQuery("Bob")
        advanceTimeBy(300.milliseconds)
        runCurrent()

        model.updateQuery("Bon")
        advanceUntilIdle()

        assertEquals(listOf("carol"), model.uiState.value.results.map { it.user.uid })

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("carol"), model.uiState.value.results.map { it.user.uid })
        assertNull(model.uiState.value.searchFailure)
        assertFalse(model.uiState.value.isSearching)
      }

  @Test
  fun relationshipMappingHandlesBothUserPositionsAndIgnoresUnrelatedFriendships() =
      runTest(dispatcher) {
        val observedFriendships =
            MutableStateFlow(
                listOf(
                    Friendship("f1", "alice", "bob", Instant.EPOCH),
                    Friendship("f2", "dave", "alice", Instant.EPOCH),
                    Friendship("f3", "carol", "someone-else", Instant.EPOCH),
                )
            )

        val repository =
            object : FriendRepository by friends {
              override fun observeFriends(): Flow<List<Friendship>> = observedFriendships
            }

        val model =
            FriendsSearchViewModel(users, repository, "alice").also { store.put("search", it) }

        model.updateQuery("Bo")
        advanceUntilIdle()

        assertEquals(SearchRelationship.FRIEND, model.status("bob"))
        assertEquals(SearchRelationship.FRIEND, model.status("dave"))
        assertEquals(SearchRelationship.AVAILABLE, model.status("carol"))

        observedFriendships.value = emptyList()
        advanceUntilIdle()

        assertEquals(SearchRelationship.AVAILABLE, model.status("bob"))
        assertEquals(SearchRelationship.AVAILABLE, model.status("dave"))
      }

  @Test
  fun concurrentRelationshipChangesSuppressMisleadingSendFailures() =
      runTest(dispatcher) {
        val model = model()
        model.updateQuery("Bo")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        friends.sendGate = gate
        friends.sendFailure = FriendRepositoryException.Network()

        model.sendRequest("bob")
        model.sendRequest("carol")
        runCurrent()

        assertEquals(2, friends.sendCount)

        // Bob sends an incoming request while Alice's send is pending.
        bobFriends.sendFriendRequest("alice")

        // Carol becomes a confirmed friend during the same interval.
        val carolRequest = carolFriends.sendFriendRequest("alice")
        aliceFriends.acceptFriendRequest("carol", carolRequest.uid)

        advanceUntilIdle()

        assertEquals(SearchRelationship.INCOMING_PENDING, model.status("bob"))
        assertEquals(SearchRelationship.FRIEND, model.status("carol"))

        // Both outgoing calls now fail, but the observed relationships
        // have already established the real current state.
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(model.uiState.value.sendFailure)
        assertTrue(model.uiState.value.sendingIds.isEmpty())
        assertEquals(SearchRelationship.INCOMING_PENDING, model.status("bob"))
        assertEquals(SearchRelationship.FRIEND, model.status("carol"))
      }

  private class ControlledFriends(
      private val delegate: FriendRepository,
  ) : FriendRepository by delegate {
    var sendCount = 0
    var sendGate: CompletableDeferred<Unit>? = null
    var sendFailure: Exception? = null
    var afterSendFailure: Exception? = null
    var relationshipGate: CompletableDeferred<Unit>? = null

    override fun observeFriends(): Flow<List<Friendship>> =
        delegate.observeFriends().onStart { relationshipGate?.await() }

    override suspend fun sendFriendRequest(otherUserId: String): FriendRequest {
      sendCount++
      sendGate?.await()
      sendFailure?.let { throw it }

      val request = delegate.sendFriendRequest(otherUserId)
      afterSendFailure?.let { throw it }

      return request
    }
  }

  private class ControlledUsers(
      private val delegate: UserRepository,
  ) : UserRepository by delegate {
    var activeUserId = "alice"
    var searchFailure: Exception? = null
    var searchGate: CompletableDeferred<Unit>? = null
    var searchResultsOverride: List<User>? = null
    var searchCount = 0
    val queries = mutableListOf<String>()

    override suspend fun getCurrentUser(): User =
        if (activeUserId == "alice") {
          delegate.getCurrentUser()
        } else {
          User(activeUserId, "Changed", null)
        }

    override suspend fun searchUsers(query: String): List<User> {
      searchCount++
      queries += query

      searchGate?.await()
      searchFailure?.let { throw it }

      return searchResultsOverride ?: delegate.searchUsers(query)
    }
  }
}
