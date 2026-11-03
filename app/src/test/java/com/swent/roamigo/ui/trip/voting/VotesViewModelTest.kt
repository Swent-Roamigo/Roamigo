// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VotesViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = InMemoryVoteRepository()
  private val store = ViewModelStore()
  private val subscribers = CoroutineScope(dispatcher + SupervisorJob())
  private lateinit var viewModel: VotesViewModel
  private val first = interactionVote().copy(uid = "first", creationTime = Timestamp(1, 0))
  private val second = interactionVote().copy(uid = "second", creationTime = Timestamp(2, 0))

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    subscribers.cancel()
    store.clear()
    Dispatchers.resetMain()
  }

  private fun createViewModel(tripId: String = "trip") {
    viewModel = VotesViewModel(repository, tripId, "member")
    subscribers.launch { viewModel.uiState.collect() }
    store.put("votes", viewModel)
  }

  private fun ready() = viewModel.uiState.value as VotesUiState.Ready

  @Test
  fun closingMovesVoteToClosedAndStopsBallotObservationWithoutRefresh() = runTest {
    repository.snapshots.value = Result.success(listOf(first))
    createViewModel()
    runCurrent()
    assertEquals(listOf(first), ready().liveVotes)
    repository.closeVote("trip", first.uid)
    runCurrent()
    assertTrue(ready().liveVotes.isEmpty())
    assertEquals(listOf(first.copy(status = VoteStatus.CLOSED)), ready().closedVotes)
    assertTrue(ready().liveSummaries.isEmpty())
    assertEquals(0, repository.activeBallotObservers[first.uid])
  }

  @Test
  fun liveSummaryIncludesZeroCountsAndMovesBallotsWithoutRefresh() = runTest {
    repository.snapshots.value = Result.success(listOf(first))
    createViewModel()
    runCurrent()
    fun summary() = ready().liveSummaries.getValue(first.uid)
    assertEquals(mapOf("a" to 0, "b" to 0), summary().counts)
    assertFalse(summary().hasPersistedBallot)
    repository.submitBallot(
        "trip",
        first.uid,
        VoteBallot("other", "a", Timestamp(1, 0)),
    )
    runCurrent()
    assertEquals(mapOf("a" to 1, "b" to 0), summary().counts)
    assertFalse(summary().hasPersistedBallot)
    val ballotModel = VoteViewModel(repository, "trip", first.uid, "member")
    store.put("ballot", ballotModel)
    runCurrent()
    ballotModel.selectOption("a")
    ballotModel.submitVote()
    runCurrent()
    assertEquals(mapOf("a" to 2, "b" to 0), summary().counts)
    assertTrue(summary().hasPersistedBallot)
    ballotModel.selectOption("b")
    ballotModel.submitVote()
    runCurrent()
    assertEquals(mapOf("a" to 1, "b" to 1), summary().counts)
    assertTrue(summary().hasPersistedBallot)
  }

  @Test
  fun ballotObserversFollowOpenVoteSetAndStopWithLastSubscriber() = runTest {
    repository.snapshots.value = Result.success(listOf(first, second))
    createViewModel()
    runCurrent()
    assertEquals(mapOf(first.uid to 1, second.uid to 1), repository.activeBallotObservers)
    repository.snapshots.value = Result.success(listOf(second.copy(status = VoteStatus.CLOSED)))
    runCurrent()
    assertTrue(repository.activeBallotObservers.values.all { it == 0 })
    repository.snapshots.value = Result.success(listOf(first))
    runCurrent()
    assertEquals(1, repository.activeBallotObservers[first.uid])
    subscribers.cancel()
    runCurrent()
    assertEquals(0, repository.activeObservers)
    assertTrue(repository.activeBallotObservers.values.all { it == 0 })
  }

  @Test
  fun noSubscribersMeansNoRepositoryListeners() = runTest {
    viewModel = VotesViewModel(repository, "trip", "member")
    store.put("votes", viewModel)
    runCurrent()
    assertEquals(0, repository.activeObservers)
    assertTrue(repository.activeBallotObservers.isEmpty())
  }

  @Test
  fun resubscribingRestoresFreshCountsWithoutOverlappingListeners() = runTest {
    repository.snapshots.value = Result.success(listOf(first))
    viewModel = VotesViewModel(repository, "trip", "member")
    store.put("votes", viewModel)
    val firstCollector = subscribers.launch { viewModel.uiState.collect() }
    runCurrent()
    firstCollector.cancel()
    runCurrent()
    assertEquals(0, repository.activeObservers)
    assertEquals(0, repository.activeBallotObservers[first.uid])
    repository.submitBallot("trip", first.uid, VoteBallot("member", "b", Timestamp(1, 0)))
    subscribers.launch { viewModel.uiState.collect() }
    runCurrent()
    assertEquals(mapOf("a" to 0, "b" to 1), ready().liveSummaries.getValue(first.uid).counts)
    assertEquals(1, repository.activeObservers)
    assertEquals(1, repository.maximumObservers)
    assertEquals(1, repository.activeBallotObservers[first.uid])
  }

  @Test
  fun startsLoadingAndDoesNotSelectBeforeFirstSnapshot() = runTest {
    createViewModel()
    assertEquals("trip", viewModel.tripId)
    assertEquals(VotesUiState.Loading, viewModel.uiState.value)
    assertNull(viewModel.selectVote("first"))
  }

  @Test
  fun emptySnapshotShowsEmptyAndKeepsObservationActive() = runTest {
    createViewModel()
    runCurrent()
    assertEquals(VotesUiState.Empty, viewModel.uiState.value)
    assertEquals(listOf("trip"), repository.observedTrips)
    assertEquals(1, repository.activeObservers)
    assertNull(viewModel.selectVote("first"))
  }

  @Test
  fun observesOnlyTheCurrentTripAndDisplaysMultipleVotes() = runTest {
    repository.snapshots.value = Result.success(listOf(first, second, first.copy(tripId = "other")))
    createViewModel()
    runCurrent()
    assertEquals(listOf(second, first), ready().liveVotes)
    assertTrue(ready().closedVotes.isEmpty())
    assertEquals(VotesSyncState.SYNCED, ready().syncState)
    assertEquals(listOf("trip"), repository.observedTrips)
    assertTrue(repository.lookups.isEmpty())
    assertTrue(repository.submissions.isEmpty())
  }

  @Test
  fun observesTheSuppliedTripInsteadOfAStoredDefault() = runTest {
    val other = first.copy(tripId = "other")
    repository.snapshots.value = Result.success(listOf(first, other))
    createViewModel("other")
    runCurrent()
    assertEquals(listOf(other), ready().liveVotes)
    assertEquals(listOf("other"), repository.observedTrips)
  }

  @Test
  fun creationAppearsAutomaticallyFromEmptyWithoutRefreshing() = runTest {
    createViewModel()
    runCurrent()
    repository.createVote(first)
    runCurrent()
    assertEquals(listOf(first), ready().liveVotes)
    repository.createVote(second)
    runCurrent()
    assertEquals(listOf(second, first), ready().liveVotes)
    assertEquals(listOf("trip"), repository.observedTrips)
  }

  @Test
  fun changedAndRemovedVotesReplaceTheSnapshotIncludingReturningToEmpty() = runTest {
    repository.snapshots.value = Result.success(listOf(first, second))
    createViewModel()
    runCurrent()
    val updated = first.copy(question = "Updated question", status = VoteStatus.CLOSED)
    repository.snapshots.value = Result.success(listOf(updated))
    runCurrent()
    assertTrue(ready().liveVotes.isEmpty())
    assertEquals(listOf(updated), ready().closedVotes)
    assertNull(viewModel.selectVote(second.uid))
    repository.snapshots.value = Result.success(emptyList())
    runCurrent()
    assertEquals(VotesUiState.Empty, viewModel.uiState.value)
  }

  @Test
  fun groupsByStatusAndSortsByFullTimestampThenVoteId() = runTest {
    val older = first.copy(uid = "older", creationTime = Timestamp(1, 1))
    val a = first.copy(uid = "a", creationTime = Timestamp(1, 2))
    val b = a.copy(uid = "b")
    val oldClosed = older.copy(uid = "oldClosed", status = VoteStatus.CLOSED)
    val newClosed = second.copy(status = VoteStatus.CLOSED)
    repository.snapshots.value = Result.success(listOf(b, oldClosed, older, newClosed, a))
    createViewModel()
    runCurrent()
    assertEquals(listOf(a, b, older), ready().liveVotes)
    assertEquals(listOf(newClosed, oldClosed), ready().closedVotes)
  }

  @Test
  fun initialObservationFailureShowsErrorAndRetryRestartsOnce() = runTest {
    repository.snapshots.value = Result.failure(IllegalStateException("Unavailable"))
    createViewModel()
    runCurrent()
    assertEquals(VotesUiState.Error, viewModel.uiState.value)
    assertNull(viewModel.selectVote("first"))
    assertEquals(0, repository.activeObservers)
    repository.snapshots.value = Result.success(listOf(first))
    viewModel.retry()
    viewModel.retry()
    assertEquals(VotesUiState.Loading, viewModel.uiState.value)
    runCurrent()
    assertEquals(listOf(first), ready().liveVotes)
    assertEquals(listOf("trip", "trip"), repository.observedTrips)
    assertEquals(1, repository.maximumObservers)
  }

  @Test
  fun failureAfterEmptyOffersInitialErrorAndCanRetryToEmpty() = runTest {
    createViewModel()
    runCurrent()
    repository.snapshots.value = Result.failure(IllegalStateException("Unavailable"))
    runCurrent()
    assertEquals(VotesUiState.Error, viewModel.uiState.value)
    repository.snapshots.value = Result.success(emptyList())
    viewModel.retry()
    runCurrent()
    assertEquals(VotesUiState.Empty, viewModel.uiState.value)
  }

  @Test
  fun failureAfterReadyKeepsLatestContentAndRetryRetainsItUntilNewSnapshot() = runTest {
    repository.snapshots.value = Result.success(listOf(first))
    createViewModel()
    runCurrent()
    repository.snapshots.value = Result.success(listOf(first, second))
    runCurrent()
    repository.snapshots.value = Result.failure(IllegalStateException("Unavailable"))
    runCurrent()
    assertEquals(listOf(second, first), ready().liveVotes)
    assertEquals(VotesSyncState.FAILED, ready().syncState)
    assertEquals(first.uid, viewModel.selectVote(first.uid))
    viewModel.retry()
    viewModel.retry()
    assertEquals(listOf(second, first), ready().liveVotes)
    assertEquals(VotesSyncState.RETRYING, ready().syncState)
    repository.snapshots.value = Result.success(listOf(second))
    runCurrent()
    assertEquals(listOf(second), ready().liveVotes)
    assertEquals(VotesSyncState.SYNCED, ready().syncState)
    assertEquals(1, repository.maximumObservers)
  }

  @Test
  fun retryFailureKeepsThePreviousReadySnapshot() = runTest {
    repository.snapshots.value = Result.success(listOf(first))
    createViewModel()
    runCurrent()
    repository.snapshots.value = Result.failure(IllegalStateException("Unavailable"))
    runCurrent()
    viewModel.retry()
    runCurrent()
    assertEquals(listOf(first), ready().liveVotes)
    assertEquals(VotesSyncState.FAILED, ready().syncState)
    assertEquals(0, repository.activeObservers)
  }

  @Test
  fun retryOutsideFailureDoesNotStartAdditionalObservation() = runTest {
    createViewModel()
    viewModel.retry()
    runCurrent()
    viewModel.retry()
    repository.snapshots.value = Result.success(listOf(first))
    runCurrent()
    viewModel.retry()
    runCurrent()
    assertEquals(listOf("trip"), repository.observedTrips)
    assertEquals(1, repository.activeObservers)
  }

  @Test
  fun selectsOpenAndClosedVotesButRejectsUnknownIds() = runTest {
    repository.snapshots.value =
        Result.success(listOf(first, second.copy(status = VoteStatus.CLOSED)))
    createViewModel()
    runCurrent()
    assertEquals(first.uid, viewModel.selectVote(first.uid))
    assertEquals(second.uid, viewModel.selectVote(second.uid))
    assertNull(viewModel.selectVote("missing"))
  }

  @Test
  fun clearingViewModelCancelsObservationWithoutAnError() = runTest {
    repository.snapshots.value = Result.success(listOf(first))
    createViewModel()
    runCurrent()
    store.clear()
    runCurrent()
    assertEquals(0, repository.activeObservers)
    assertTrue(repository.activeBallotObservers.values.all { it == 0 })
    assertEquals(VotesSyncState.SYNCED, ready().syncState)
  }

  @Test
  fun cancellationIsNotReportedAsAnObservationError() = runTest {
    repository.snapshots.value = Result.failure(CancellationException("Cancelled"))
    createViewModel()
    runCurrent()
    assertEquals(VotesUiState.Loading, viewModel.uiState.value)
    assertEquals(0, repository.activeObservers)
  }
}
