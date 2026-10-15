// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
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

internal fun interactionVote() =
    Vote(
        "vote",
        "trip",
        "Dinner?",
        listOf(VoteOption("a", "Market"), VoteOption("b", "Restaurant")),
        "owner",
        VoteStatus.OPEN,
        Timestamp(0, 0),
        null,
        null,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class VoteViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = InteractionVoteRepository()
  private val store = ViewModelStore()
  private lateinit var viewModel: VoteViewModel
  private val now = Instant.parse("2026-10-14T10:00:00Z")

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  private fun createViewModel() {
    viewModel =
        VoteViewModel(repository, "trip", "vote", "member", Clock.fixed(now, ZoneOffset.UTC))
    store.put("vote", viewModel)
  }

  private fun ready() = viewModel.uiState.value as VoteUiState.Ready

  private fun ballot(optionId: String) = VoteBallot("member", optionId, Timestamp(1, 0))

  @Test
  fun loadsQuestionOptionsAndNoInitialChoice() = runTest {
    createViewModel()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    advanceUntilIdle()
    assertEquals("trip" to "vote", repository.lookup)
    assertEquals(repository.vote, ready().vote)
    assertEquals("Dinner?", ready().vote.question)
    assertEquals(listOf("Market", "Restaurant"), ready().vote.options.map { it.name })
    assertNull(ready().selectedOptionId)
    assertNull(ready().persistedOptionId)
    assertFalse(ready().canSubmit)
  }

  @Test
  fun waitsForInitialBallotsBeforeEnablingInteraction() = runTest {
    repository.observationGate = CompletableDeferred()
    repository.ballots.value = listOf(ballot("b"))
    createViewModel()
    runCurrent()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    viewModel.selectOption("a")
    viewModel.submitVote()
    assertTrue(repository.calls.isEmpty())
    repository.observationGate!!.complete(Unit)
    advanceUntilIdle()
    assertTrue(ready().canSelect)
    assertEquals("b", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
  }

  @Test
  fun restoresOnlyCurrentMembersBallot() = runTest {
    repository.ballots.value = listOf(VoteBallot("other", "a", Timestamp(1, 0)), ballot("b"))
    createViewModel()
    advanceUntilIdle()
    assertEquals("b", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    assertTrue(ready().hasPersistedBallot)
    assertFalse(ready().hasUnsavedSelection)
  }

  @Test
  fun selectionIsLocalAndInvalidSelectionIsIgnored() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    viewModel.selectOption("invalid")
    assertEquals("b", ready().selectedOptionId)
    assertEquals("a", ready().persistedOptionId)
    assertTrue(ready().hasUnsavedSelection)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun submitWithoutChoiceIsPrevented() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.submitVote()
    advanceUntilIdle()
    assertTrue(repository.calls.isEmpty())
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
  }

  @Test
  fun writesCorrectBallotAndUpdatesSameUserOnSecondSubmission() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("a")
    viewModel.submitVote()
    advanceUntilIdle()
    val expected = VoteBallot("member", "a", Timestamp(now.epochSecond, now.nano))
    assertEquals(Triple("trip", "vote", expected), repository.calls.single())
    assertEquals("a", ready().persistedOptionId)
    assertFalse(ready().hasUnsavedSelection)
    viewModel.selectOption("b")
    assertEquals("a", ready().persistedOptionId)
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(2, repository.calls.size)
    assertEquals(setOf("member"), repository.calls.map { it.third.userId }.toSet())
    assertEquals(1, repository.persisted.size)
    assertEquals("b", repository.persisted["member"]?.optionId)
    assertEquals("b", ready().persistedOptionId)
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
    assertTrue(ready().canSubmit)
  }

  @Test
  fun repeatedSubmissionAndSelectionWhileSubmittingArePrevented() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.submitGate = CompletableDeferred()
    viewModel.selectOption("a")
    viewModel.submitVote()
    viewModel.submitVote()
    viewModel.selectOption("b")
    assertEquals(VoteSubmissionState.Submitting, ready().submissionState)
    assertEquals("a", ready().selectedOptionId)
    assertFalse(ready().canSubmit)
    runCurrent()
    assertEquals(1, repository.calls.size)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
  }

  @Test
  fun failurePreservesSelectionAndRetrySucceeds() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.submitFailure = IllegalStateException("backend detail")
    viewModel.selectOption("b")
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Failed, ready().submissionState)
    assertEquals("b", ready().selectedOptionId)
    assertNull(ready().persistedOptionId)
    assertTrue(ready().canSubmit)
    repository.submitFailure = null
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
    assertEquals("b", ready().persistedOptionId)
  }

  @Test
  fun missingVoteIsNotRetryable() = runTest {
    repository.vote = null
    createViewModel()
    advanceUntilIdle()
    val error = viewModel.uiState.value as VoteUiState.LoadError
    assertEquals(VoteLoadError.NOT_FOUND, error.reason)
    assertFalse(error.canRetry)
    viewModel.retryLoad()
    advanceUntilIdle()
    assertEquals(error, viewModel.uiState.value)
    assertEquals(1, repository.lookupCount)
    assertEquals(0, repository.activeObservers)
  }

  @Test
  fun voteLoadFailureCanBeRetried() = runTest {
    repository.loadFailure = IllegalStateException("offline")
    createViewModel()
    advanceUntilIdle()
    val error = viewModel.uiState.value as VoteUiState.LoadError
    assertEquals(VoteLoadError.VOTE_FAILED, error.reason)
    assertTrue(error.canRetry)
    repository.loadFailure = null
    viewModel.retryLoad()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    advanceUntilIdle()
    assertEquals(repository.vote, ready().vote)
  }

  @Test
  fun initialObservationFailureExposesLoadErrorAndRetryRestoresBallot() = runTest {
    repository.observationFailure = IllegalStateException("listener failed")
    createViewModel()
    advanceUntilIdle()
    assertEquals(VoteUiState.LoadError(VoteLoadError.BALLOTS_FAILED), viewModel.uiState.value)
    assertTrue((viewModel.uiState.value as VoteUiState.LoadError).canRetry)
    repository.observationFailure = null
    repository.ballots.value = listOf(ballot("a"))
    viewModel.retryLoad()
    advanceUntilIdle()
    assertEquals("a", ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
  }

  @Test
  fun remoteChangesFollowCleanSelectionAndPreserveUnsavedDraft() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.ballots.value = listOf(ballot("a"))
    runCurrent()
    assertEquals("a", ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
    repository.ballots.value = listOf(ballot("b"))
    runCurrent()
    assertEquals("b", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    viewModel.selectOption("a")
    repository.ballots.value = emptyList()
    runCurrent()
    assertNull(ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
  }

  @Test
  fun matchingEmissionConvergesDraftThenLaterRemoteChangeFollowsIt() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("a")
    repository.ballots.value = listOf(ballot("a"))
    runCurrent()
    assertFalse(ready().hasUnsavedSelection)
    repository.ballots.value = listOf(ballot("b"))
    runCurrent()
    assertEquals("b", ready().selectedOptionId)
    assertEquals("b", ready().persistedOptionId)
  }

  @Test
  fun returningToPersistedChoiceLetsRemoteChangesFollowAgain() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    viewModel.selectOption("a")
    repository.ballots.value = listOf(ballot("b"))
    runCurrent()
    assertEquals("b", ready().selectedOptionId)
    assertFalse(ready().hasUnsavedSelection)
  }

  @Test
  fun otherMembersEmissionsDoNotDiscardDraft() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    repository.ballots.value = listOf(VoteBallot("other", "a", Timestamp(1, 0)))
    runCurrent()
    assertNull(ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
  }

  @Test
  fun observerFailureKeepsReadyStateAndRetriesOnlyListener() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    repository.observationFailure = IllegalStateException("listener disconnected")
    repository.ballots.value = listOf(ballot("a"), VoteBallot("other", "a", Timestamp(1, 0)))
    runCurrent()
    assertEquals(VoteSyncState.Failed, ready().syncState)
    assertEquals(interactionVote(), ready().vote)
    assertEquals("a", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    assertFalse(ready().canSelect)
    assertEquals(0, repository.activeObservers)
    repository.observationFailure = null
    repository.observationGate = CompletableDeferred()
    viewModel.retryLoad()
    viewModel.retryLoad()
    assertEquals(VoteSyncState.Retrying, ready().syncState)
    assertEquals("b", ready().selectedOptionId)
    assertFalse(ready().canSubmit)
    runCurrent()
    assertEquals(1, repository.activeObservers)
    repository.observationGate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(1, repository.lookupCount)
    assertEquals(VoteSyncState.Synced, ready().syncState)
    assertEquals("a", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    assertTrue(ready().canSubmit)
  }

  @Test
  fun repositoryEchoDuringWriteKeepsChoiceAndReopeningRestoresIt() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.submitGate = CompletableDeferred()
    viewModel.selectOption("b")
    viewModel.submitVote()
    runCurrent()
    val written = repository.calls.single().third
    repository.ballots.value = listOf(written)
    runCurrent()
    assertEquals("b", ready().selectedOptionId)
    assertEquals("b", ready().persistedOptionId)
    assertTrue(ready().isSubmitting)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    val reopened = VoteViewModel(repository, "trip", "vote", "member")
    store.put("reopened", reopened)
    advanceUntilIdle()
    val reopenedState = reopened.uiState.value as VoteUiState.Ready
    assertEquals("b", reopenedState.persistedOptionId)
    assertEquals("b", reopenedState.selectedOptionId)
  }

  @Test
  fun writeCompletionWaitsForStreamToConfirmPersistedChoice() = runTest {
    repository.emitOnSubmit = false
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("a")
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
    assertNull(ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
    repository.ballots.value = listOf(repository.calls.single().third)
    runCurrent()
    assertEquals("a", ready().persistedOptionId)
    assertFalse(ready().hasUnsavedSelection)
  }

  @Test
  fun writeCompletionDoesNotOverwriteNewerObservedBallot() = runTest {
    repository.emitOnSubmit = false
    repository.submitGate = CompletableDeferred()
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("a")
    viewModel.submitVote()
    runCurrent()
    repository.ballots.value = listOf(ballot("a"))
    runCurrent()
    repository.ballots.value = listOf(ballot("b"))
    runCurrent()
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
    assertEquals("b", ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
    assertTrue(ready().hasUnsavedSelection)
  }

  @Test
  fun failedUpdateRetainsPreviousPersistedChoiceAndNewDraft() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    repository.submitFailure = IllegalStateException("offline")
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals("a", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    assertEquals(VoteSubmissionState.Failed, ready().submissionState)
    assertTrue(ready().canSubmit)
  }

  @Test
  fun remoteChangeDuringResubmissionCannotResetInFlightChoice() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    repository.submitGate = CompletableDeferred()
    viewModel.submitVote()
    runCurrent()
    repository.ballots.value = listOf(ballot("b"))
    runCurrent()
    assertEquals("b", ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals("a", ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
  }

  @Test
  fun submissionAndObservationFailuresRemainIndependent() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.submitGate = CompletableDeferred()
    repository.submitFailure = IllegalStateException("write failed")
    viewModel.selectOption("b")
    viewModel.submitVote()
    runCurrent()
    repository.observationFailure = IllegalStateException("stream failed")
    repository.ballots.value = listOf(VoteBallot("other", "a", Timestamp(1, 0)))
    runCurrent()
    viewModel.retryLoad()
    assertEquals(VoteSyncState.Failed, ready().syncState)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Failed, ready().submissionState)
    assertEquals(VoteSyncState.Failed, ready().syncState)
    assertEquals("b", ready().selectedOptionId)
    repository.observationFailure = null
    viewModel.retryLoad()
    advanceUntilIdle()
    assertEquals(VoteSyncState.Synced, ready().syncState)
    assertEquals(VoteSubmissionState.Failed, ready().submissionState)
    repository.submitFailure = null
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Idle, ready().submissionState)
    assertEquals("b", ready().persistedOptionId)
  }

  @Test
  fun closedVoteIsTerminalWithoutObservingBallots() = runTest {
    repository.vote = interactionVote().copy(status = VoteStatus.CLOSED)
    repository.observationGate = CompletableDeferred()
    createViewModel()
    advanceUntilIdle()
    assertEquals(VoteUiState.Closed, viewModel.uiState.value)
    viewModel.selectOption("b")
    viewModel.submitVote()
    viewModel.retryLoad()
    assertEquals(VoteUiState.Closed, viewModel.uiState.value)
    assertTrue(repository.calls.isEmpty())
    assertEquals(0, repository.activeObservers)
  }

  @Test
  fun removalWithoutDraftClearsSelection() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    repository.ballots.value = emptyList()
    runCurrent()
    assertNull(ready().selectedOptionId)
    assertNull(ready().persistedOptionId)
  }

  @Test
  fun continuousObserverStaysActiveAndIsReleasedWithViewModel() = runTest {
    createViewModel()
    advanceUntilIdle()
    assertEquals(1, repository.activeObservers)
    repository.ballots.value = listOf(ballot("a"))
    runCurrent()
    repository.ballots.value = listOf(ballot("b"))
    runCurrent()
    assertEquals("b", ready().persistedOptionId)
    assertEquals(1, repository.activeObservers)
    store.clear()
    runCurrent()
    assertEquals(0, repository.activeObservers)
  }
}

internal class InteractionVoteRepository : VoteRepository {
  var vote: Vote? = interactionVote()
  var lookup: Pair<String, String>? = null
  var lookupCount = 0
  var activeObservers = 0
  var emitOnSubmit = true
  val ballots = MutableStateFlow<List<VoteBallot>>(emptyList())
  val calls = mutableListOf<Triple<String, String, VoteBallot>>()
  val persisted = mutableMapOf<String, VoteBallot>()
  var loadFailure: Exception? = null
  var observationFailure: Exception? = null
  var submitFailure: Exception? = null
  var submitGate: CompletableDeferred<Unit>? = null
  var observationGate: CompletableDeferred<Unit>? = null

  override suspend fun getVote(tripId: String, voteId: String): Vote? {
    lookupCount++
    lookup = tripId to voteId
    loadFailure?.let { throw it }
    return vote
  }

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> = flow {
    activeObservers++
    try {
      observationGate?.await()
      observationFailure?.let { throw it }
      ballots.collect {
        observationFailure?.let { failure -> throw failure }
        emit(it)
      }
    } finally {
      activeObservers--
    }
  }

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) {
    calls += Triple(tripId, voteId, ballot)
    submitGate?.await()
    submitFailure?.let { throw it }
    persisted[ballot.userId] = ballot
    if (emitOnSubmit)
        ballots.value = ballots.value.filterNot { it.userId == ballot.userId } + ballot
  }

  override suspend fun createVote(vote: Vote) = error("Not used")

  override suspend fun getVotes(tripId: String): List<Vote> = error("Not used")
}
