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
import org.junit.Assert.assertNotNull
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

  private fun createViewModel() {
    viewModel =
        VoteViewModel(repository, "trip", "vote", "member", Clock.fixed(now, ZoneOffset.UTC))
    store.put("vote", viewModel)
  }

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun loadsQuestionOptionsAndNoInitialChoice() = runTest {
    createViewModel()
    assertTrue(viewModel.uiState.value.isLoading)
    advanceUntilIdle()
    assertEquals("trip" to "vote", repository.lookup)
    assertEquals(repository.vote, viewModel.uiState.value.vote)
    assertEquals("Dinner?", viewModel.uiState.value.vote?.question)
    assertEquals(
        listOf("Market", "Restaurant"),
        viewModel.uiState.value.vote?.options?.map { it.name },
    )
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.selectedOptionId)
    assertNull(viewModel.uiState.value.currentBallot)
  }

  @Test
  fun waitsForInitialBallotsBeforeEnablingInteraction() = runTest {
    createViewModel()
    repository.observationGate = CompletableDeferred()
    runCurrent()
    assertNotNull(viewModel.uiState.value.vote)
    assertTrue(viewModel.uiState.value.isLoading)
    viewModel.selectOption("a")
    assertNull(viewModel.uiState.value.selectedOptionId)
    repository.observationGate!!.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun restoresOnlyCurrentMembersBallot() = runTest {
    createViewModel()
    val ballot = VoteBallot("member", "b", Timestamp(1, 0))
    repository.ballots.value = listOf(VoteBallot("other", "a", Timestamp(1, 0)), ballot)
    advanceUntilIdle()
    assertEquals(ballot, viewModel.uiState.value.currentBallot)
    assertEquals("b", viewModel.uiState.value.selectedOptionId)
  }

  @Test
  fun selectionIsLocalAndInvalidSelectionIsIgnored() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("a")
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
    viewModel.selectOption("invalid")
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
    assertNull(viewModel.uiState.value.currentBallot)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun submitWithoutChoiceIsPrevented() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.submitVote()
    advanceUntilIdle()
    assertTrue(repository.calls.isEmpty())
    assertFalse(viewModel.uiState.value.isSubmitting)
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
    assertEquals(expected, viewModel.uiState.value.currentBallot)
    viewModel.selectOption("b")
    assertEquals("a", viewModel.uiState.value.currentBallot?.optionId)
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(2, repository.calls.size)
    assertEquals(setOf("member"), repository.calls.map { it.third.userId }.toSet())
    assertEquals(1, repository.persisted.size)
    assertEquals("b", repository.persisted["member"]?.optionId)
    assertEquals("b", viewModel.uiState.value.currentBallot?.optionId)
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.canSubmit)
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
    assertTrue(viewModel.uiState.value.isSubmitting)
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
    runCurrent()
    assertEquals(1, repository.calls.size)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  @Test
  fun failurePreservesSelectionAndRetrySucceeds() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.submitFailure = IllegalStateException("backend detail")
    viewModel.selectOption("b")
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(VoteError.SUBMISSION_FAILED, viewModel.uiState.value.error)
    assertEquals("b", viewModel.uiState.value.selectedOptionId)
    assertNull(viewModel.uiState.value.currentBallot)
    assertTrue(viewModel.uiState.value.canSubmit)
    repository.submitFailure = null
    viewModel.submitVote()
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.error)
    assertEquals("b", viewModel.uiState.value.currentBallot?.optionId)
  }

  @Test
  fun missingVoteAndLoadFailureCanBeRetried() = runTest {
    createViewModel()
    repository.vote = null
    advanceUntilIdle()
    assertEquals(VoteError.NOT_FOUND, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.canSubmit)
    repository.loadFailure = IllegalStateException("offline")
    viewModel.retryLoad()
    advanceUntilIdle()
    assertEquals(VoteError.LOAD_FAILED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
    repository.loadFailure = null
    repository.vote = interactionVote()
    viewModel.retryLoad()
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.error)
    assertNotNull(viewModel.uiState.value.vote)
  }

  @Test
  fun observationFailureExposesErrorAndRetryRestoresBallot() = runTest {
    createViewModel()
    repository.observationFailure = IllegalStateException("listener failed")
    advanceUntilIdle()
    assertEquals(VoteError.BALLOTS_FAILED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.canSelect)
    repository.observationFailure = null
    repository.ballots.value = listOf(VoteBallot("member", "a", Timestamp(1, 0)))
    viewModel.retryLoad()
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.error)
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
  }

  @Test
  fun observationFollowsPersistedChoiceButPreservesUnsavedDraft() = runTest {
    createViewModel()
    advanceUntilIdle()
    val first = VoteBallot("member", "a", Timestamp(1, 0))
    repository.ballots.value = listOf(first)
    runCurrent()
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
    val changed = first.copy(optionId = "b")
    repository.ballots.value = listOf(changed)
    runCurrent()
    assertEquals("b", viewModel.uiState.value.selectedOptionId)
    viewModel.selectOption("a")
    repository.ballots.value = listOf(changed.copy(updatedAt = Timestamp(2, 0)))
    runCurrent()
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
    assertEquals(Timestamp(2, 0), viewModel.uiState.value.currentBallot?.updatedAt)
    repository.ballots.value = emptyList()
    runCurrent()
    assertNull(viewModel.uiState.value.currentBallot)
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
  }

  @Test
  fun observerFailureAfterEditingPreservesDraftOnRetry() = runTest {
    createViewModel()
    advanceUntilIdle()
    viewModel.selectOption("b")
    repository.observationFailure = IllegalStateException("listener disconnected")
    repository.ballots.value = listOf(VoteBallot("other", "a", Timestamp(1, 0)))
    runCurrent()
    assertEquals(VoteError.BALLOTS_FAILED, viewModel.uiState.value.error)
    assertEquals("b", viewModel.uiState.value.selectedOptionId)
    repository.observationFailure = null
    repository.ballots.value = listOf(VoteBallot("member", "a", Timestamp(1, 0)))
    viewModel.retryLoad()
    advanceUntilIdle()
    assertEquals("a", viewModel.uiState.value.currentBallot?.optionId)
    assertEquals("b", viewModel.uiState.value.selectedOptionId)
  }

  @Test
  fun repositoryEchoDuringWriteKeepsChoiceAndReopeningRestoresIt() = runTest {
    createViewModel()
    advanceUntilIdle()
    repository.submitGate = CompletableDeferred()
    viewModel.selectOption("b")
    viewModel.submitVote()
    runCurrent()
    val ballot = repository.calls.single().third
    repository.ballots.value = listOf(ballot)
    runCurrent()
    assertEquals("b", viewModel.uiState.value.selectedOptionId)
    assertTrue(viewModel.uiState.value.isSubmitting)
    repository.submitGate!!.complete(Unit)
    advanceUntilIdle()
    val reopened = VoteViewModel(repository, "trip", "vote", "member")
    store.put("reopened", reopened)
    advanceUntilIdle()
    assertEquals(ballot, reopened.uiState.value.currentBallot)
    assertEquals("b", reopened.uiState.value.selectedOptionId)
  }

  @Test
  fun removalWithoutDraftClearsSelectionAndClosedVoteBlocksSubmission() = runTest {
    createViewModel()
    repository.ballots.value = listOf(VoteBallot("member", "a", Timestamp(1, 0)))
    repository.vote = interactionVote().copy(status = VoteStatus.CLOSED)
    advanceUntilIdle()
    assertEquals("a", viewModel.uiState.value.selectedOptionId)
    viewModel.selectOption("b")
    viewModel.submitVote()
    assertTrue(repository.calls.isEmpty())
    repository.ballots.value = emptyList()
    runCurrent()
    assertNull(viewModel.uiState.value.selectedOptionId)
  }
}

internal class InteractionVoteRepository : VoteRepository {
  var vote: Vote? = interactionVote()
  var lookup: Pair<String, String>? = null
  val ballots = MutableStateFlow<List<VoteBallot>>(emptyList())
  val calls = mutableListOf<Triple<String, String, VoteBallot>>()
  val persisted = mutableMapOf<String, VoteBallot>()
  var loadFailure: Exception? = null
  var observationFailure: Exception? = null
  var submitFailure: Exception? = null
  var submitGate: CompletableDeferred<Unit>? = null
  var observationGate: CompletableDeferred<Unit>? = null

  override suspend fun getVote(tripId: String, voteId: String): Vote? {
    lookup = tripId to voteId
    loadFailure?.let { throw it }
    return vote
  }

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> = flow {
    observationGate?.await()
    observationFailure?.let { throw it }
    ballots.collect {
      observationFailure?.let { failure -> throw failure }
      emit(it)
    }
  }

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) {
    calls += Triple(tripId, voteId, ballot)
    submitGate?.await()
    submitFailure?.let { throw it }
    persisted[ballot.userId] = ballot
  }

  override suspend fun createVote(vote: Vote) = error("Not used")

  override suspend fun getVotes(tripId: String): List<Vote> = error("Not used")
}
