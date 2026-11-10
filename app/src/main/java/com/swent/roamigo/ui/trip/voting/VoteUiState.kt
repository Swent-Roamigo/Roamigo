// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.model.trip.voting.VoteStatus

/** Single-vote draft, persisted ballot, and submission state owned by [VoteViewModel]. */
sealed interface VoteUiState {
  data object Loading : VoteUiState

  data class Closed(val vote: Vote, val result: VoteResult) : VoteUiState

  data class LoadError(val reason: VoteLoadError) : VoteUiState {
    val canRetry: Boolean
      get() = reason != VoteLoadError.NOT_FOUND
  }

  /**
   * A loaded vote with the current ballot selection, submission, and live results.
   *
   * @property vote The vote currently displayed and interacted with.
   * @property persistedOptionId The latest choice from the repository stream, or null if no ballot
   *   has been persisted.
   * @property selectedOptionId The local choice displayed by the screen, or null if none is
   *   selected. May differ from [persistedOptionId] while editing or awaiting a write's echo.
   * @property submissionState The current ballot submission state.
   * @property syncState The state of realtime ballot synchronization after initial readiness.
   * @property isCreator Whether the authenticated member created this vote.
   * @property closingState The close operation's progress or failure, independent of ballots.
   * @property voteCounts Map from option ID to the current count derived from ballots. Options
   *   normally have an entry initialized to 0.
   */
  data class Ready(
      val vote: Vote,
      val persistedOptionId: String? = null,
      val selectedOptionId: String? = persistedOptionId,
      val submissionState: VoteSubmissionState = VoteSubmissionState.Idle,
      val syncState: VoteSyncState = VoteSyncState.Synced,
      val isCreator: Boolean = false,
      val closingState: VoteSubmissionState = VoteSubmissionState.Idle,
      val voteCounts: Map<String, Int> = vote.options.associate { it.uid to 0 },
  ) : VoteUiState {
    val hasUnsavedSelection: Boolean
      get() = selectedOptionId != persistedOptionId

    val isSubmitting: Boolean
      get() = submissionState == VoteSubmissionState.Submitting

    val isClosing: Boolean
      get() = closingState == VoteSubmissionState.Submitting

    val canClose: Boolean
      get() = isCreator && vote.status == VoteStatus.OPEN && canSelect

    val canSelect: Boolean
      get() =
          vote.status == VoteStatus.OPEN &&
              syncState == VoteSyncState.Synced &&
              !isSubmitting &&
              !isClosing

    val canSubmit: Boolean
      get() =
          vote.status == VoteStatus.OPEN &&
              canSelect &&
              vote.options.any { it.uid == selectedOptionId }

    val hasPersistedBallot: Boolean
      get() = persistedOptionId != null
  }
}

enum class VoteLoadError {
  NOT_FOUND,
  VOTE_FAILED,
  BALLOTS_FAILED,
}

/** A write's outcome is independent of the continuous ballot observation. */
sealed interface VoteSubmissionState {
  data object Idle : VoteSubmissionState

  data object Submitting : VoteSubmissionState

  data object Failed : VoteSubmissionState
}

/** A failed listener keeps the last ready state; retry waits for a fresh repository snapshot. */
sealed interface VoteSyncState {
  data object Synced : VoteSyncState

  data object Retrying : VoteSyncState

  data object Failed : VoteSyncState
}
