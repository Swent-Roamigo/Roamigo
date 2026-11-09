// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.model.trip.voting.calculateVoteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Observes the votes belonging to one trip. The repository owns data access and listener cleanup;
 * ballot summaries are collected only while the UI is subscribed. Drafts belong to [VoteViewModel].
 */
class VotesViewModel(
    private val voteRepository: VoteRepository,
    val tripId: String,
    private val userId: String,
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
      _uiState.subscriptionCount
          .map { it > 0 }
          .distinctUntilChanged()
          .collectLatest { active ->
            if (!active) return@collectLatest
            try {
              observeTripVotes()
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              val latest = _uiState.value as? VotesUiState.Ready
              _uiState.value = latest?.copy(syncState = VotesSyncState.FAILED) ?: VotesUiState.Error
            }
          }
    }
  }

  private suspend fun observeTripVotes() = coroutineScope {
    var votes = emptyList<Vote>()
    // Mutations stay on the ViewModel Main dispatcher in this structured scope and its
    // children, so ordinary mutable maps are safe without synchronization.
    val listeners = mutableMapOf<String, Job>()
    val results = mutableMapOf<String, VoteResult>()

    fun publish() {
      if (votes.isEmpty()) {
        _uiState.value = VotesUiState.Empty
        return
      }
      val open = votes.filter { it.status == VoteStatus.OPEN }
      val closed = votes.filter { it.status == VoteStatus.CLOSED }
      _uiState.value =
          VotesUiState.Ready(
              open,
              closed,
              liveSummaries =
                  open
                      .mapNotNull { vote ->
                        results[vote.uid]?.let { result ->
                          vote.uid to
                              LiveVoteSummary(
                                  result.counts,
                                  result.currentUserOptionId != null,
                                  vote.createdByUserId == userId,
                              )
                        }
                      }
                      .toMap(),
              finalSummaries =
                  closed.mapNotNull { vote -> results[vote.uid]?.let { vote.uid to it } }.toMap(),
          )
    }

    voteRepository.observeVotes(tripId).distinctUntilChanged().collect { snapshot ->
      val next =
          snapshot.sortedWith(compareByDescending<Vote> { it.creationTime }.thenBy { it.uid })
      val byId = next.associateBy { it.uid }
      val previousById = votes.associateBy { it.uid }
      // Retain unchanged listeners. At closure, discard the live snapshot and wait for a fresh
      // ballot observation rather than announcing its potentially stale leader as the winner.
      listeners.keys.toList().forEach { id ->
        val previous = previousById[id]
        val current = byId[id]
        if (
            current == null ||
                previous == null ||
                previous.status != current.status ||
                previous.options != current.options
        ) {
          listeners.remove(id)?.cancelAndJoin()
          results.remove(id)
        }
      }
      votes = next
      publish()
      votes
          .filter { it.uid !in listeners }
          .forEach { vote ->
            listeners[vote.uid] = launch {
              val ballotsFlow =
                  if (vote.status == VoteStatus.CLOSED)
                      voteRepository.observeFinalBallots(tripId, vote.uid)
                  else voteRepository.observeBallots(tripId, vote.uid)
              ballotsFlow.collect { ballots ->
                results[vote.uid] = calculateVoteResult(vote.options, ballots, userId)
                publish()
              }
            }
          }
    }
  }
}
