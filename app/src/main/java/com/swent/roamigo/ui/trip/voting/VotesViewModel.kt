// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observes the votes belonging to one trip. The repository owns data access and listener cleanup;
 * the caller owns navigation to [VoteViewModel] for a selected vote.
 */
class VotesViewModel(
    private val voteRepository: VoteRepository,
    val tripId: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow<VotesUiState>(VotesUiState.Loading)
  val uiState: StateFlow<VotesUiState> = _uiState.asStateFlow()
  private var observationJob: Job? = null

  init {
    observeVotes()
  }

  /** Restarts a failed observation once, keeping any previously displayed votes visible. */
  fun retry() {
    val state = _uiState.value
    if (
        state == VotesUiState.Error ||
            state is VotesUiState.Ready && state.syncState == VotesSyncState.FAILED
    ) {
      observeVotes()
    }
  }

  /** Returns a displayed vote's ID for navigation, ignoring stale or unknown selections. */
  fun selectVote(voteId: String): String? {
    val state = _uiState.value as? VotesUiState.Ready ?: return null
    return (state.liveVotes + state.closedVotes).firstOrNull { it.uid == voteId }?.uid
  }

  private fun observeVotes() {
    val previous = observationJob
    previous?.cancel()
    val ready = _uiState.value as? VotesUiState.Ready
    _uiState.value = ready?.copy(syncState = VotesSyncState.RETRYING) ?: VotesUiState.Loading
    observationJob = viewModelScope.launch {
      // Finish cancellation before registering another listener, including during a fast retry.
      previous?.join()
      try {
        voteRepository.observeVotes(tripId).collect { votes ->
          val ordered =
              votes.sortedWith(compareByDescending<Vote> { it.creationTime }.thenBy { it.uid })
          _uiState.value =
              if (ordered.isEmpty()) VotesUiState.Empty
              else
                  VotesUiState.Ready(
                      liveVotes = ordered.filter { it.status == VoteStatus.OPEN },
                      closedVotes = ordered.filter { it.status == VoteStatus.CLOSED },
                  )
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        val latest = _uiState.value as? VotesUiState.Ready
        _uiState.value = latest?.copy(syncState = VotesSyncState.FAILED) ?: VotesUiState.Error
      }
    }
  }
}
