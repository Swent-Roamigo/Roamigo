// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.model.trip.voting.calculateVoteResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

  private fun assertClosedResult() {
    val closed = viewModel.uiState.value as VoteUiState.Closed
    assertEquals(repository.vote, closed.vote)
    assertEquals(VoteStatus.CLOSED, closed.vote.status)
    assertEquals(
        calculateVoteResult(closed.vote.options, repository.ballots.value, "member"),
        closed.result,
    )
  }

  private fun ballot(optionId: String) = VoteBallot("member", optionId, Timestamp(1, 0))

  @Test
  fun selectedVoteIgnoresUnrelatedVotesAndCollectionErrorsWhileDraftAndBallotsStayLive() = runTest {
    createViewModel()
    runCurrent()
    viewModel.selectOption("b")
    val emissions = repository.selectedVoteEmissions
    repository.addUnrelatedVote(
        interactionVote().copy(uid = "unrelated", status = VoteStatus.CLOSED)
    )
    runCurrent()
    assertEquals(emissions, repository.selectedVoteEmissions)
    assertEquals("b", ready().selectedOptionId)
    assertEquals(0, repository.collectionObservations)
    repository.ballots.value = listOf(ballot("a"))
    runCurrent()
    assertEquals("a", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    assertEquals(1, ready().voteCounts["a"])
    assertEquals("trip" to "vote", repository.selectedObservation)
  }

  @Test
  fun closingFinalizesPersistedBallotWithoutSubmitting() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    val original = ballot("a")
    repository.submitBallot("trip", "vote", original)
    createViewModel()
    advanceUntilIdle()
    assertEquals("a", ready().persistedOptionId)
    viewModel.closeVote()
    advanceUntilIdle()
    assertClosedResult()
    assertEquals(listOf(original), repository.ballots.value)
    assertEquals(original, repository.persisted["member"])
    assertEquals(1, repository.calls.size)
    assertEquals(1, repository.closeCalls)
  }

  @Test
  fun closingWithoutABallotDoesNotCreateOne() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    createViewModel()
    advanceUntilIdle()
    viewModel.closeVote()
    advanceUntilIdle()
    assertClosedResult()
    assertTrue(repository.ballots.value.isEmpty())
    assertTrue(repository.persisted.isEmpty())
    assertTrue(repository.calls.isEmpty())
    assertEquals(1, repository.closeCalls)
  }

  @Test
  fun creatorCanCloseAndRepeatedActionsAreBlocked() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    createViewModel()
    advanceUntilIdle()
    assertTrue(ready().canClose)
    repository.closeGate = CompletableDeferred()
    viewModel.closeVote()
    viewModel.closeVote()
    viewModel.selectOption("a")
    viewModel.submitVote()
    assertTrue(ready().isClosing)
    assertFalse(ready().canSubmit)
    runCurrent()
    assertEquals(1, repository.closeCalls)
    repository.closeGate!!.complete(Unit)
    advanceUntilIdle()
    assertClosedResult()
    assertEquals(VoteStatus.CLOSED, repository.vote?.status)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun failedCloseAndFailedReconciliationReadKeepVoteOpenForRetry() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    createViewModel()
    advanceUntilIdle()
    repository.closeFailure = IllegalStateException("write failed")
    repository.loadFailure = IllegalStateException("offline")
    viewModel.closeVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Failed, ready().closingState)
    assertTrue(ready().canClose)
    repository.closeFailure = null
    repository.loadFailure = null
    viewModel.closeVote()
    advanceUntilIdle()
    assertClosedResult()
  }

  @Test
  fun cancelledCloseRestoresInteractionsWithoutClosing() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    createViewModel()
    advanceUntilIdle()
    repository.closeFailure = kotlinx.coroutines.CancellationException("cancelled")
    viewModel.closeVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Idle, ready().closingState)
    assertTrue(ready().canClose)
    assertEquals(VoteStatus.OPEN, repository.vote?.status)
  }

  @Test
  fun closingIsRejectedWhileBallotIsBeingSubmitted() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    createViewModel()
    advanceUntilIdle()
    repository.submitGate = CompletableDeferred()
    viewModel.selectOption("a")
    viewModel.submitVote()
    viewModel.closeVote()
    runCurrent()
    assertEquals(0, repository.closeCalls)
    assertFalse(ready().canClose)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    assertTrue(ready().canClose)
  }

  @Test
  fun nonCreatorCannotClose() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "other")
    createViewModel()
    advanceUntilIdle()
    assertFalse(ready().canClose)
    viewModel.closeVote()
    advanceUntilIdle()
    assertEquals(0, repository.closeCalls)
  }

  @Test
  fun failedCloseKeepsOpenVoteAndAllowsRetry() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    repository.closeFailure = IllegalStateException("offline")
    createViewModel()
    advanceUntilIdle()
    viewModel.closeVote()
    advanceUntilIdle()
    assertEquals(VoteSubmissionState.Failed, ready().closingState)
    assertEquals(VoteStatus.OPEN, ready().vote.status)
    assertTrue(ready().canClose)
    repository.closeFailure = null
    viewModel.closeVote()
    advanceUntilIdle()
    assertClosedResult()
  }

  @Test
  fun remoteCloseIsTerminalEvenDuringFailedWriteAndLateBallots() = runTest {
    repository.vote = interactionVote().copy(createdByUserId = "member")
    createViewModel()
    advanceUntilIdle()
    repository.closeGate = CompletableDeferred()
    repository.closeFailure = IllegalStateException("already closed")
    viewModel.closeVote()
    runCurrent()
    repository.vote = repository.vote!!.copy(status = VoteStatus.CLOSED)
    runCurrent()
    repository.ballots.value = listOf(ballot("a"))
    repository.closeGate!!.complete(Unit)
    advanceUntilIdle()
    assertClosedResult()
    viewModel.submitVote()
    viewModel.closeVote()
    assertTrue(repository.calls.isEmpty())
    assertEquals(1, repository.closeCalls)
  }

  @Test
  fun voteRemovedBeforeSubmissionDoesNotWrite() = runTest {
    createViewModel()
    runCurrent()
    viewModel.selectOption("a")
    repository.vote = null
    viewModel.submitVote()
    runCurrent()
    assertEquals(VoteUiState.LoadError(VoteLoadError.NOT_FOUND), viewModel.uiState.value)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun voteClosedBeforeSubmissionDoesNotWrite() = runTest {
    createViewModel()
    runCurrent()
    viewModel.selectOption("a")
    repository.vote = repository.vote!!.copy(status = VoteStatus.CLOSED)
    viewModel.submitVote()
    runCurrent()
    assertClosedResult()
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun optionRemovedBeforeSubmissionDoesNotWrite() = runTest {
    createViewModel()
    runCurrent()
    viewModel.selectOption("a")
    repository.vote = repository.vote!!.copy(options = listOf(VoteOption("b", "Restaurant")))
    viewModel.submitVote()
    runCurrent()
    assertEquals(VoteSubmissionState.Failed, ready().submissionState)
    assertTrue(repository.calls.isEmpty())
  }

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
    assertEquals(mapOf("a" to 0, "b" to 0), ready().voteCounts)
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
    assertEquals(mapOf("a" to 0, "b" to 1), ready().voteCounts)
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
    assertEquals(mapOf("a" to 1, "b" to 0), ready().voteCounts)
    assertFalse(ready().hasUnsavedSelection)
    viewModel.selectOption("b")
    assertEquals("a", ready().persistedOptionId)
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(2, repository.calls.size)
    assertEquals(setOf("member"), repository.calls.map { it.third.userId }.toSet())
    assertEquals(1, repository.persisted.size)
    assertEquals("b", repository.persisted["member"]?.optionId)
    assertEquals(mapOf("a" to 0, "b" to 1), ready().voteCounts)
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
    assertEquals(mapOf("a" to 1, "b" to 0), ready().voteCounts)
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
    assertEquals(mapOf("a" to 1, "b" to 0), ready().voteCounts)
    assertEquals("b", ready().selectedOptionId)
    assertFalse(ready().canSubmit)
    runCurrent()
    assertEquals(1, repository.activeObservers)
    repository.observationGate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(1, repository.lookupCount)
    assertEquals(mapOf("a" to 2, "b" to 0), ready().voteCounts)
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
    assertEquals(mapOf("a" to 0, "b" to 1), ready().voteCounts)
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
  fun alreadyClosedVoteWaitsForConfirmedBallotsAndBlocksInteraction() = runTest {
    repository.vote = interactionVote().copy(status = VoteStatus.CLOSED)
    repository.finalConfirmation = CompletableDeferred()
    createViewModel()
    advanceUntilIdle()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    repository.finalConfirmation!!.complete(Unit)
    advanceUntilIdle()
    val closed = viewModel.uiState.value as VoteUiState.Closed
    assertEquals(mapOf("a" to 0, "b" to 0), closed.result.counts)
    assertTrue(closed.result.hasNoVotes)
    viewModel.selectOption("b")
    viewModel.submitVote()
    viewModel.retryLoad()
    assertClosedResult()
    assertTrue(repository.calls.isEmpty())
    assertEquals(1, repository.activeFinalObservers)
  }

  @Test
  fun closedVoteReopensWithIdenticalBallotDerivedResultsIgnoringStoredWinner() = runTest {
    repository.vote =
        interactionVote()
            .copy(
                status = VoteStatus.CLOSED,
                winningOptionId = "b",
                options = interactionVote().options + VoteOption("c", "Cafe"),
            )
    repository.ballots.value = listOf(ballot("a"), VoteBallot("other", "a", Timestamp(1, 0)))
    createViewModel()
    advanceUntilIdle()
    val first = viewModel.uiState.value as VoteUiState.Closed
    assertEquals(mapOf("a" to 2, "b" to 0, "c" to 0), first.result.counts)
    assertEquals(setOf("a"), first.result.winningOptionIds)
    createViewModel()
    advanceUntilIdle()
    assertEquals(first, viewModel.uiState.value)
    viewModel.selectOption("b")
    viewModel.submitVote()
    viewModel.closeVote()
    advanceUntilIdle()
    assertEquals(first, viewModel.uiState.value)
    assertTrue(repository.calls.isEmpty())
    assertEquals(0, repository.closeCalls)
  }

  @Test
  fun remoteClosureDiscardsLiveSnapshotAndWaitsForConfirmedFinalBallots() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    repository.finalConfirmation = CompletableDeferred()
    repository.vote = repository.vote!!.copy(status = VoteStatus.CLOSED)
    runCurrent()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    assertEquals(0, repository.activeObservers)
    assertEquals(1, repository.activeFinalObservers)
    viewModel.selectOption("b")
    viewModel.submitVote()
    repository.ballots.value = listOf(ballot("a"), VoteBallot("other", "b", Timestamp(1, 0)))
    runCurrent()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    repository.finalConfirmation!!.complete(Unit)
    runCurrent()
    val closed = viewModel.uiState.value as VoteUiState.Closed
    assertEquals(mapOf("a" to 1, "b" to 1), closed.result.counts)
    assertTrue(closed.result.isTie)
    assertEquals(0, repository.activeObservers)
    assertEquals(1, repository.activeFinalObservers)
    assertEquals(1, repository.lookupCount)
    viewModel.submitVote()
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun closedBallotLoadFailureCanRetryWithoutExposingVoting() = runTest {
    repository.vote = interactionVote().copy(status = VoteStatus.CLOSED)
    repository.observationFailure = IllegalStateException("offline")
    createViewModel()
    advanceUntilIdle()
    assertEquals(VoteUiState.LoadError(VoteLoadError.BALLOTS_FAILED), viewModel.uiState.value)
    repository.observationFailure = null
    viewModel.retryLoad()
    advanceUntilIdle()
    assertTrue((viewModel.uiState.value as VoteUiState.Closed).result.hasNoVotes)
  }

  @Test
  fun remoteClosureFinalFailureRetriesWithoutReusingLiveCounts() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    runCurrent()
    repository.observationFailure = IllegalStateException("final listener failed")
    repository.vote = repository.vote!!.copy(status = VoteStatus.CLOSED)
    runCurrent()
    assertEquals(VoteUiState.LoadError(VoteLoadError.BALLOTS_FAILED), viewModel.uiState.value)
    assertEquals(0, repository.activeObservers)
    assertEquals(0, repository.activeFinalObservers)
    repository.observationFailure = null
    repository.finalConfirmation = CompletableDeferred()
    viewModel.retryLoad()
    viewModel.retryLoad()
    runCurrent()
    assertEquals(VoteUiState.Loading, viewModel.uiState.value)
    assertEquals(1, repository.activeFinalObservers)
    viewModel.selectOption("b")
    viewModel.submitVote()
    assertTrue(repository.calls.isEmpty())
    repository.ballots.value = listOf(ballot("b"))
    repository.finalConfirmation!!.complete(Unit)
    runCurrent()
    val closed = viewModel.uiState.value as VoteUiState.Closed
    assertEquals(setOf("b"), closed.result.winningOptionIds)
    assertEquals("b", closed.result.currentUserOptionId)
    store.clear()
    runCurrent()
    assertEquals(0, repository.activeFinalObservers)
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
  fun initialCountsIncludeEveryOptionAndCurrentUsersBallot() = runTest {
    repository.vote =
        interactionVote().copy(options = interactionVote().options + VoteOption("c", "Cafe"))
    repository.ballots.value =
        listOf(
            ballot("a"),
            VoteBallot("other", "a", Timestamp(1, 0)),
            VoteBallot("third", "b", Timestamp(1, 0)),
        )
    createViewModel()
    advanceUntilIdle()
    assertEquals(mapOf("a" to 2, "b" to 1, "c" to 0), ready().voteCounts)
    assertEquals("a", ready().persistedOptionId)
    assertEquals("a", ready().selectedOptionId)
    assertEquals(1, repository.activeObservers)
  }

  @Test
  fun remoteAdditionChangeAndRemovalRecomputeCountsWithoutReloading() = runTest {
    val first = VoteBallot("other", "a", Timestamp(1, 0))
    val second = VoteBallot("third", "b", Timestamp(1, 0))
    repository.ballots.value = listOf(first)
    createViewModel()
    advanceUntilIdle()
    assertEquals(mapOf("a" to 1, "b" to 0), ready().voteCounts)
    repository.ballots.value = listOf(first, second)
    runCurrent()
    assertEquals(mapOf("a" to 1, "b" to 1), ready().voteCounts)
    repository.ballots.value = listOf(first.copy(optionId = "b"), second)
    runCurrent()
    assertEquals(mapOf("a" to 0, "b" to 2), ready().voteCounts)
    repository.ballots.value = listOf(second)
    runCurrent()
    assertEquals(mapOf("a" to 0, "b" to 1), ready().voteCounts)
    repository.ballots.value = emptyList()
    runCurrent()
    assertEquals(mapOf("a" to 0, "b" to 0), ready().voteCounts)
    assertEquals(1, repository.lookupCount)
    assertEquals(1, repository.activeObservers)
  }

  @Test
  fun repeatedResultSnapshotsPreserveDraftAndFollowPersistedSelection() = runTest {
    repository.ballots.value = listOf(ballot("a"))
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    assertEquals(mapOf("a" to 1, "b" to 0), ready().voteCounts)
    val other = VoteBallot("other", "b", Timestamp(1, 0))
    repository.ballots.value = listOf(ballot("a"), other)
    runCurrent()
    assertEquals(mapOf("a" to 1, "b" to 1), ready().voteCounts)
    assertEquals("a", ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    // A duplicate backend snapshot must neither accumulate counts nor discard a draft.
    repository.ballots.value = repository.ballots.value.reversed()
    runCurrent()
    assertEquals(mapOf("a" to 1, "b" to 1), ready().voteCounts)
    assertEquals("b", ready().selectedOptionId)
    repository.ballots.value = listOf(other)
    runCurrent()
    assertEquals(mapOf("a" to 0, "b" to 1), ready().voteCounts)
    assertNull(ready().persistedOptionId)
    assertEquals("b", ready().selectedOptionId)
    assertTrue(ready().hasUnsavedSelection)
  }

  @Test
  fun unknownOptionBallotIsIgnoredForDisplayedCounts() = runTest {
    repository.ballots.value = listOf(ballot("a"), VoteBallot("other", "removed", Timestamp(1, 0)))
    createViewModel()
    advanceUntilIdle()
    assertEquals(mapOf("a" to 1, "b" to 0), ready().voteCounts)
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
  private val votes = MutableStateFlow<List<Vote>>(listOf(interactionVote()))
  var selectedVoteEmissions = 0
  var collectionObservations = 0
  var selectedObservation: Pair<String, String>? = null

  fun addUnrelatedVote(vote: Vote) {
    votes.value = votes.value + vote
  }

  var vote: Vote?
    get() = votes.value.firstOrNull()
    set(value) {
      votes.value = listOfNotNull(value)
    }

  var closeFailure: Exception? = null
  var closeGate: CompletableDeferred<Unit>? = null
  var closeCalls = 0

  override suspend fun closeVote(tripId: String, voteId: String) {
    closeCalls++
    closeGate?.await()
    closeFailure?.let { throw it }
    check(vote?.status == VoteStatus.OPEN)
    vote = vote!!.copy(status = VoteStatus.CLOSED)
  }

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

  var finalConfirmation: CompletableDeferred<Unit>? = null
  var activeFinalObservers = 0

  override fun observeFinalBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> = flow {
    activeFinalObservers++
    try {
      observationGate?.await()
      finalConfirmation?.await()
      observationFailure?.let { throw it }
      ballots.collect {
        observationFailure?.let { failure -> throw failure }
        emit(it)
      }
    } finally {
      activeFinalObservers--
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

  override fun observeVote(tripId: String, voteId: String): Flow<Vote?> =
      votes
          .map { snapshot -> snapshot.firstOrNull { it.uid == voteId } }
          .distinctUntilChanged()
          .onEach {
            selectedObservation = tripId to voteId
            selectedVoteEmissions++
          }

  override fun observeVotes(tripId: String): Flow<List<Vote>> {
    collectionObservations++
    error("Malformed unrelated vote: selected interaction must not observe the collection")
  }
}
