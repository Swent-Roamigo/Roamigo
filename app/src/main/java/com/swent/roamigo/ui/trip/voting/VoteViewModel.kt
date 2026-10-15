// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Interacts with an existing vote. The caller supplies the authenticated member's ID; membership
 * authorization and one ballot per user are enforced by the repository/backend.
 */
class VoteViewModel(
    private val voteRepository: VoteRepository,
    private val tripId: String,
    private val voteId: String,
    private val userId: String,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
  private val _uiState = MutableStateFlow<VoteUiState>(VoteUiState.Loading)
  val uiState: StateFlow<VoteUiState> = _uiState.asStateFlow()
  private var observationJob: Job? = null

  init {
    loadVote()
  }

  fun retryLoad() {
    when (val state = _uiState.value) {
      is VoteUiState.LoadError -> if (state.canRetry) loadVote()
      is VoteUiState.Ready -> {
        if (state.syncState != VoteSyncState.Failed || state.isSubmitting) return
        observationJob?.cancel()
        updateReady { it.copy(syncState = VoteSyncState.Retrying) }
        observationJob = viewModelScope.launch { observeBallots(state.vote) }
      }
      VoteUiState.Loading,
      VoteUiState.Closed -> Unit
    }
  }

  private fun loadVote() {
    observationJob?.cancel()
    _uiState.value = VoteUiState.Loading
    observationJob = viewModelScope.launch {
      val vote =
          try {
            voteRepository.getVote(tripId, voteId)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            _uiState.value = VoteUiState.LoadError(VoteLoadError.VOTE_FAILED)
            return@launch
          }
      if (vote == null) {
        _uiState.value = VoteUiState.LoadError(VoteLoadError.NOT_FOUND)
        return@launch
      }
      if (vote.status == VoteStatus.CLOSED) {
        _uiState.value = VoteUiState.Closed
        return@launch
      }
      observeBallots(vote)
    }
  }

  private suspend fun observeBallots(vote: Vote) {
    try {
      // Keep collecting complete lists: this is the source of persisted ballot state, and the
      // same emissions can later supply live results without a second listener.
      voteRepository.observeBallots(tripId, voteId).collect { ballots ->
        val persistedOptionId = ballots.firstOrNull { it.userId == userId }?.optionId
        _uiState.update { state ->
          when (state) {
            is VoteUiState.Ready -> state.withPersistedOption(persistedOptionId)
            else -> VoteUiState.Ready(vote, persistedOptionId)
          }
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      _uiState.update { state ->
        when (state) {
          is VoteUiState.Ready -> state.copy(syncState = VoteSyncState.Failed)
          else -> VoteUiState.LoadError(VoteLoadError.BALLOTS_FAILED)
        }
      }
    }
  }

  fun selectOption(optionId: String) {
    val state = _uiState.value as? VoteUiState.Ready ?: return
    if (!state.canSelect || state.vote.options.none { it.uid == optionId }) return
    updateReady { it.copy(selectedOptionId = optionId, submissionState = VoteSubmissionState.Idle) }
  }

  fun submitVote() {
    val state = _uiState.value as? VoteUiState.Ready ?: return
    if (!state.canSubmit) return
    val optionId = requireNotNull(state.selectedOptionId)
    // Claim submission synchronously so repeated taps cannot schedule multiple writes.
    updateReady { it.copy(submissionState = VoteSubmissionState.Submitting) }
    viewModelScope.launch {
      try {
        val now = clock.instant()
        val ballot = VoteBallot(userId, optionId, Timestamp(now.epochSecond, now.nano))
        voteRepository.submitBallot(tripId, voteId, ballot)
        // The listener, not this write's completion, confirms the persisted selection. It may
        // already have emitted the echo, or emit it later; neither ordering discards the draft.
        updateReady { it.copy(submissionState = VoteSubmissionState.Idle) }
      } catch (cancelled: CancellationException) {
        updateReady { it.copy(submissionState = VoteSubmissionState.Idle) }
        throw cancelled
      } catch (_: Exception) {
        updateReady { it.copy(submissionState = VoteSubmissionState.Failed) }
      }
    }
  }

  private fun updateReady(transform: (VoteUiState.Ready) -> VoteUiState.Ready) {
    _uiState.update { if (it is VoteUiState.Ready) transform(it) else it }
  }
}

/** Remote changes follow a clean selection, but cannot replace a draft or an in-flight choice. */
private fun VoteUiState.Ready.withPersistedOption(optionId: String?): VoteUiState.Ready =
    copy(
        persistedOptionId = optionId,
        selectedOptionId = if (hasUnsavedSelection || isSubmitting) selectedOptionId else optionId,
        syncState = VoteSyncState.Synced,
    )
