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

data class VoteUiState(
    val isLoading: Boolean = true,
    val vote: Vote? = null,
    val selectedOptionId: String? = null,
    val currentBallot: VoteBallot? = null,
    val ballotsLoaded: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: VoteError? = null,
) {
  val canSelect: Boolean
    get() = !isLoading && ballotsLoaded && !isSubmitting && vote?.status == VoteStatus.OPEN

  val canSubmit: Boolean
    get() = canSelect && vote?.options?.any { it.uid == selectedOptionId } == true

  val canRetryLoad: Boolean
    get() =
        error == VoteError.LOAD_FAILED ||
            error == VoteError.BALLOTS_FAILED ||
            error == VoteError.NOT_FOUND
}

enum class VoteError {
  NOT_FOUND,
  LOAD_FAILED,
  BALLOTS_FAILED,
  SUBMISSION_FAILED,
}

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
  private val _uiState = MutableStateFlow(VoteUiState())
  val uiState: StateFlow<VoteUiState> = _uiState.asStateFlow()
  private var loadJob: Job? = null

  init {
    load()
  }

  fun retryLoad() {
    if (_uiState.value.canRetryLoad && !_uiState.value.isSubmitting) load()
  }

  private fun load() {
    loadJob?.cancel()
    _uiState.update { it.copy(isLoading = true, ballotsLoaded = false, error = null) }
    loadJob = viewModelScope.launch {
      val vote =
          try {
            voteRepository.getVote(tripId, voteId)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            _uiState.update { it.copy(isLoading = false, error = VoteError.LOAD_FAILED) }
            return@launch
          }
      if (vote == null) {
        _uiState.update { it.copy(isLoading = false, vote = null, error = VoteError.NOT_FOUND) }
        return@launch
      }
      _uiState.update { it.copy(vote = vote) }
      try {
        voteRepository.observeBallots(tripId, voteId).collect { ballots ->
          val ballot = ballots.firstOrNull { it.userId == userId }
          _uiState.update { state ->
            // Follow remote changes only when there is no unsaved local choice. Other members'
            // ballots and listener echoes must not discard a draft selection.
            val hasDraft =
                state.isSubmitting || state.selectedOptionId != state.currentBallot?.optionId
            state.copy(
                isLoading = false,
                ballotsLoaded = true,
                currentBallot = ballot,
                selectedOptionId = if (hasDraft) state.selectedOptionId else ballot?.optionId,
            )
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        _uiState.update {
          it.copy(isLoading = false, ballotsLoaded = false, error = VoteError.BALLOTS_FAILED)
        }
      }
    }
  }

  fun selectOption(optionId: String) {
    val state = _uiState.value
    if (!state.canSelect || state.vote?.options?.none { it.uid == optionId } != false) return
    _uiState.update { it.copy(selectedOptionId = optionId, error = null) }
  }

  fun submitVote() {
    val state = _uiState.value
    if (!state.canSubmit) return
    val optionId = requireNotNull(state.selectedOptionId)
    // Claim submission synchronously so repeated taps cannot schedule multiple writes.
    _uiState.update { it.copy(isSubmitting = true, error = null) }
    viewModelScope.launch {
      try {
        val now = clock.instant()
        val ballot = VoteBallot(userId, optionId, Timestamp(now.epochSecond, now.nano))
        voteRepository.submitBallot(tripId, voteId, ballot)
        _uiState.update {
          it.copy(
              isSubmitting = false,
              currentBallot = ballot,
              error = if (it.error == VoteError.SUBMISSION_FAILED) null else it.error,
          )
        }
      } catch (cancelled: CancellationException) {
        _uiState.update { it.copy(isSubmitting = false) }
        throw cancelled
      } catch (_: Exception) {
        _uiState.update {
          it.copy(
              isSubmitting = false,
              error =
                  if (it.error == VoteError.BALLOTS_FAILED) it.error
                  else VoteError.SUBMISSION_FAILED,
          )
        }
      }
    }
  }
}
