// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateVoteUiState(
    val question: String = "",
    val options: List<String> = listOf("", ""),
    val isSubmitting: Boolean = false,
    val error: CreateVoteError? = null,
    val createdVoteId: String? = null,
    val pendingVoteCreatedId: String? = null,
) {
  val isValid: Boolean
    get() = question.isNotBlank() && options.size >= 2 && options.all { it.isNotBlank() }

  val canSubmit: Boolean
    get() = isValid && !isSubmitting && createdVoteId == null
}

enum class CreateVoteError {
  INVALID_INPUT,
  SUBMISSION_FAILED,
}

/**
 * Holds one vote draft. The caller supplies the current trip and authenticated member, and handles
 * navigation using [consumeVoteCreated]. Membership authorization belongs to the backend.
 */
class CreateVoteViewModel(
    private val voteRepository: VoteRepository,
    private val tripId: String,
    private val createdByUserId: String,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(CreateVoteUiState())
  val uiState: StateFlow<CreateVoteUiState> = _uiState.asStateFlow()

  // Retain the request after failure: the server may have committed it before the response was
  // lost.
  private var pendingVote: Vote? = null

  /** Claims the success event once while keeping createdVoteId as durable completion state. */
  fun consumeVoteCreated(): String? =
      _uiState.getAndUpdate { it.copy(pendingVoteCreatedId = null) }.pendingVoteCreatedId

  private fun edit(transform: (CreateVoteUiState) -> CreateVoteUiState) {
    val state = _uiState.value
    if (state.isSubmitting || state.createdVoteId != null) return
    val updated = transform(state).copy(error = null)
    if (updated.question != state.question || updated.options != state.options) pendingVote = null
    _uiState.value = updated
  }

  fun updateQuestion(question: String) = edit { it.copy(question = question) }

  fun addOption() = edit { it.copy(options = it.options + "") }

  fun updateOption(index: Int, text: String) = edit { state ->
    state.copy(options = state.options.mapIndexed { i, option -> if (i == index) text else option })
  }

  fun removeOption(index: Int) = edit { state ->
    state.copy(options = state.options.filterIndexed { i, _ -> i != index })
  }

  fun submitVote() {
    val draft = _uiState.value
    if (draft.isSubmitting || draft.createdVoteId != null) return
    if (!draft.isValid) {
      _uiState.update { it.copy(error = CreateVoteError.INVALID_INPUT) }
      return
    }
    // Set this before launching so consecutive taps cannot start concurrent writes.
    _uiState.update { it.copy(isSubmitting = true, error = null) }
    viewModelScope.launch {
      try {
        val vote = pendingVote ?: buildVote(draft).also { pendingVote = it }
        voteRepository.createVote(vote)
        _uiState.update {
          it.copy(isSubmitting = false, createdVoteId = vote.uid, pendingVoteCreatedId = vote.uid)
        }
      } catch (cancelled: CancellationException) {
        _uiState.update { it.copy(isSubmitting = false) }
        throw cancelled
      } catch (_: Exception) {
        _uiState.update { it.copy(isSubmitting = false, error = CreateVoteError.SUBMISSION_FAILED) }
      }
    }
  }

  private fun buildVote(draft: CreateVoteUiState): Vote {
    val now = clock.instant()
    return Vote(
        uid = UUID.randomUUID().toString(),
        tripId = tripId,
        question = draft.question.trim(),
        options = draft.options.map { VoteOption(UUID.randomUUID().toString(), it.trim()) },
        createdByUserId = createdByUserId,
        status = VoteStatus.OPEN,
        creationTime = Timestamp(now.epochSecond, now.nano),
        closingTime = null,
        winningOptionId = null,
    )
  }
}
