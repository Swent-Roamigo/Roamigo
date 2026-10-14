// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteStatus

sealed interface VoteUiState {
  data object Loading : VoteUiState

  data class LoadError(val reason: VoteLoadError) : VoteUiState

  /**
   * [persistedOptionId] is the latest choice from the repository stream. [selectedOptionId] is the
   * local choice displayed by the screen and may differ while editing or awaiting a write's echo.
   */
  data class Ready(
      val vote: Vote,
      val persistedOptionId: String? = null,
      val selectedOptionId: String? = persistedOptionId,
      val submissionState: VoteSubmissionState = VoteSubmissionState.Idle,
      val observationState: BallotObservationState = BallotObservationState.Active,
  ) : VoteUiState {
    val hasUnsavedSelection: Boolean
      get() = selectedOptionId != persistedOptionId

    val isSubmitting: Boolean
      get() = submissionState == VoteSubmissionState.Submitting

    val canSelect: Boolean
      get() =
          observationState == BallotObservationState.Active &&
              !isSubmitting &&
              vote.status == VoteStatus.OPEN

    val canSubmit: Boolean
      get() = canSelect && vote.options.any { it.uid == selectedOptionId }

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
sealed interface BallotObservationState {
  data object Active : BallotObservationState

  data object Retrying : BallotObservationState

  data object Failed : BallotObservationState
}
