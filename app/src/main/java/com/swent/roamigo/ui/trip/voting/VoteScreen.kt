// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteActions
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent
import com.swent.roamigo.ui.trip.voting.components.VoteOptionRow

/** Standalone voting interaction; the caller owns the ViewModel and navigation. */
@Composable
fun VoteScreen(viewModel: VoteViewModel, onBack: () -> Unit) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VoteScreenContent(
      state,
      viewModel::selectOption,
      viewModel::submitVote,
      viewModel::retryLoad,
      onBack,
  )
}

@Composable
fun VoteScreenContent(
    state: VoteUiState,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
  Column(
      Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .safeDrawingPadding()
          .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      when (state) {
        VoteUiState.Loading -> VoteLoadingContent()
        is VoteUiState.LoadError ->
            VoteErrorContent(
                messageResource =
                    when (state.reason) {
                      VoteLoadError.NOT_FOUND -> R.string.vote_not_found
                      VoteLoadError.VOTE_FAILED -> R.string.vote_load_error
                      VoteLoadError.BALLOTS_FAILED -> R.string.vote_ballots_error
                    },
                onRetry = onRetry,
            )
        is VoteUiState.Ready -> {
          Text(state.vote.question, style = MaterialTheme.typography.headlineLarge)
          Column(
              Modifier.selectableGroup(),
              verticalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
            state.vote.options.forEach { option ->
              VoteOptionRow(
                  option.name,
                  state.selectedOptionId == option.uid,
                  state.canSelect,
                  onClick = { onSelectOption(option.uid) },
              )
            }
          }
          if (state.vote.status == VoteStatus.CLOSED) Text(stringResource(R.string.vote_closed))
        }
      }
    }
    if (state is VoteUiState.Ready) {
      when (state.observationState) {
        BallotObservationState.Active -> Unit
        BallotObservationState.Retrying -> VoteLoadingContent()
        BallotObservationState.Failed ->
            VoteErrorContent(
                R.string.vote_ballots_error,
                onRetry,
                retryEnabled = !state.isSubmitting,
            )
      }
      if (state.submissionState == VoteSubmissionState.Failed) {
        VoteErrorContent(R.string.vote_ballot_submission_error)
      }
    }
    val ready = state as? VoteUiState.Ready
    VoteActions(
        canSubmit = ready?.canSubmit == true,
        isSubmitting = ready?.isSubmitting == true,
        hasPersistedBallot = ready?.hasPersistedBallot == true,
        onSubmit = onSubmit,
        onBack = onBack,
    )
  }
}

@Composable
private fun VoteLoadingContent() {
  CircularProgressIndicator()
  Text(stringResource(R.string.vote_loading))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VotePreview() = VoteInteractionPreview(false)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SelectedVotePreview() = VoteInteractionPreview(true)

@Composable
private fun VoteInteractionPreview(selected: Boolean) {
  val vote =
      Vote(
          "vote",
          "trip",
          "Where should we have dinner?",
          listOf(VoteOption("market", "Local market"), VoteOption("restaurant", "Restaurant")),
          "member",
          VoteStatus.OPEN,
          Timestamp(0, 0),
          null,
          null,
      )
  RoamigoTheme {
    VoteScreenContent(
        VoteUiState.Ready(vote, persistedOptionId = if (selected) "market" else null),
        {},
        {},
        {},
        {},
    )
  }
}
