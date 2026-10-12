// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

@OptIn(ExperimentalCoroutinesApi::class)
class CreateVoteViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = FakeVoteRepository()
  private val now = Instant.parse("2026-10-12T10:00:00Z")
  private lateinit var viewModel: CreateVoteViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    viewModel = CreateVoteViewModel(repository, "trip", "member", Clock.fixed(now, ZoneOffset.UTC))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState() {
    assertEquals(CreateVoteUiState(), viewModel.uiState.value)
    assertFalse(viewModel.uiState.value.canSubmit)
  }

  @Test
  fun updatesQuestion() {
    viewModel.updateQuestion("Dinner?")
    assertEquals("Dinner?", viewModel.uiState.value.question)
  }

  @Test
  fun addsEditsAndRemovesOptionsInOrder() {
    viewModel.updateOption(0, "First")
    viewModel.addOption()
    viewModel.updateOption(2, "Third")
    viewModel.removeOption(1)
    assertEquals(listOf("First", "Third"), viewModel.uiState.value.options)
  }

  @Test
  fun fewerThanTwoOptionsCannotSubmit() = runTest {
    validDraft()
    viewModel.removeOption(0)
    assertInvalid()
    viewModel.removeOption(0)
    assertInvalid()
  }

  @Test
  fun blankQuestionCannotSubmit() = runTest {
    validDraft()
    viewModel.updateQuestion(" \n ")
    assertInvalid()
  }

  @Test
  fun anyBlankOptionCannotSubmitEvenWithTwoFilledOptions() = runTest {
    validDraft()
    viewModel.updateOption(1, " \t ")
    assertInvalid()
    viewModel.updateOption(1, "Second")
    viewModel.addOption()
    assertInvalid()
    viewModel.removeOption(2)
    assertTrue(viewModel.uiState.value.canSubmit)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun validDraftEnablesSubmission() {
    validDraft()
    assertTrue(viewModel.uiState.value.canSubmit)
  }

  @Test
  fun submissionCreatesExpectedVoteAndExposesSuccess() = runTest {
    validDraft()
    viewModel.submitVote()
    advanceUntilIdle()
    val vote = repository.created.single()
    UUID.fromString(vote.uid)
    vote.options.forEach { UUID.fromString(it.uid) }
    assertEquals(2, vote.options.map { it.uid }.distinct().size)
    assertEquals("trip", vote.tripId)
    assertEquals("member", vote.createdByUserId)
    assertEquals("Dinner?", vote.question)
    assertEquals(listOf("Market", "Restaurant"), vote.options.map { it.name })
    assertEquals(VoteStatus.OPEN, vote.status)
    assertEquals(Timestamp(now.epochSecond, now.nano), vote.creationTime)
    assertNull(vote.closingTime)
    assertNull(vote.winningOptionId)
    assertEquals(vote.uid, viewModel.uiState.value.createdVoteId)
    assertFalse(viewModel.uiState.value.isSubmitting)
    assertNull(viewModel.uiState.value.error)
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(1, repository.created.size)
  }

  @Test
  fun loadingPreventsRepeatedSubmissionAndDraftChanges() = runTest {
    repository.gate = CompletableDeferred()
    validDraft()
    val options = viewModel.uiState.value.options
    viewModel.submitVote()
    viewModel.submitVote()
    assertTrue(viewModel.uiState.value.isSubmitting)
    assertFalse(viewModel.uiState.value.canSubmit)
    runCurrent()
    assertEquals(1, repository.created.size)
    viewModel.updateQuestion("Changed")
    viewModel.addOption()
    viewModel.updateOption(0, "Changed")
    viewModel.removeOption(1)
    assertEquals("  Dinner?  ", viewModel.uiState.value.question)
    assertEquals(options, viewModel.uiState.value.options)
    repository.gate!!.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  @Test
  fun failureShowsErrorPreservesDraftAndAllowsRetry() = runTest {
    repository.failure = IllegalStateException("offline")
    validDraft()
    viewModel.submitVote()
    advanceUntilIdle()
    assertEquals(CreateVoteError.SUBMISSION_FAILED, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.createdVoteId)
    assertFalse(viewModel.uiState.value.isSubmitting)
    assertTrue(viewModel.uiState.value.canSubmit)
    assertEquals("  Dinner?  ", viewModel.uiState.value.question)
    repository.failure = null
    viewModel.submitVote()
    assertNull(viewModel.uiState.value.error)
    advanceUntilIdle()
    assertNotNull(viewModel.uiState.value.createdVoteId)
  }

  private fun validDraft() {
    viewModel.updateQuestion("  Dinner?  ")
    viewModel.updateOption(0, " Market ")
    viewModel.updateOption(1, " Restaurant ")
  }

  private fun assertInvalid() {
    assertFalse(viewModel.uiState.value.canSubmit)
    viewModel.submitVote()
    // The synchronous guard must avoid even scheduling a repository operation.
    assertEquals(CreateVoteError.INVALID_INPUT, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSubmitting)
    assertTrue(repository.created.isEmpty())
  }
}

internal class FakeVoteRepository : VoteRepository {
  val created = mutableListOf<Vote>()
  var gate: CompletableDeferred<Unit>? = null
  var failure: Exception? = null

  override suspend fun createVote(vote: Vote) {
    created += vote
    gate?.await()
    failure?.let { throw it }
  }

  override suspend fun getVotes(tripId: String): List<Vote> = error("Not used")

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) =
      error("Not used")

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      error("Not used")
}
