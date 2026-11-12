// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteResult

/**
 * Trip discovery and live card summaries owned by [VotesViewModel]; drafts belong to
 * [VoteViewModel].
 */
sealed interface VotesUiState {
  data object Loading : VotesUiState

  data object Empty : VotesUiState

  data object Error : VotesUiState

  /**
   * The last complete trip snapshot, grouped using the existing vote status.
   *
   * @property liveVotes OPEN votes, newest first with vote ID tie breaking.
   * @property closedVotes CLOSED votes in the same order.
   * @property liveSummaries Ballot-derived counts and current-user ballot presence, keyed by OPEN
   *   vote ID.
   * @property finalSummaries Ballot-derived results keyed by CLOSED vote ID. A missing entry means
   *   a fresh ballot snapshot is still pending; it must not be presented as zero votes.
   * @property syncState Whether the snapshot is current, reconnecting, or retained after failure.
   */
  data class Ready(
      val liveVotes: List<Vote>,
      val closedVotes: List<Vote>,
      val syncState: VotesSyncState = VotesSyncState.SYNCED,
      val liveSummaries: Map<String, LiveVoteSummary> = emptyMap(),
      val finalSummaries: Map<String, VoteResult> = emptyMap(),
  ) : VotesUiState {
    /** Final ballot winners and explicit owner outcomes belong under Decided. */
    val decidedVotes: List<Vote>
      get() = closedVotes.filter {
        it.nextRoundVoteId == null &&
            (it.resolvedOptionId != null || finalSummaries[it.uid]?.hasUniqueWinner == true)
      }

    /** Delegated rounds stay stored but are represented by their successor in the normal list. */
    val unresolvedClosedVotes: List<Vote>
      get() = closedVotes.filter {
        it.nextRoundVoteId == null &&
            it.resolvedOptionId == null &&
            finalSummaries[it.uid]?.hasUniqueWinner != true
      }
  }
}

/** Observation status after a non-empty trip vote snapshot has been displayed. */
enum class VotesSyncState {
  SYNCED,
  RETRYING,
  FAILED,
}

/** Ballot-derived card snapshot owned by [VotesViewModel], including options with no ballots. */
data class LiveVoteSummary(
    val counts: Map<String, Int>,
    val hasPersistedBallot: Boolean,
    val isCreator: Boolean = false,
)
