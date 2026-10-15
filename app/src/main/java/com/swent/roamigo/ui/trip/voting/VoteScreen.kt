// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      when (state) {
        VoteUiState.Loading -> VoteLoadingContent()
        VoteUiState.Closed ->
            Text(stringResource(R.string.vote_closed), textAlign = TextAlign.Center)
        is VoteUiState.LoadError ->
            VoteErrorContent(
                messageResource =
                    if (state.reason == VoteLoadError.NOT_FOUND) R.string.vote_not_found
                    else R.string.vote_load_error,
                onRetry = if (state.canRetry) onRetry else null,
            )
        is VoteUiState.Ready ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
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
                      voteCount = state.voteCounts.getValue(option.uid),
                      onClick = { onSelectOption(option.uid) },
                  )
                }
              }
            }
      }
    }
    if (state is VoteUiState.Ready) {
      when (state.syncState) {
        VoteSyncState.Synced -> Unit
        VoteSyncState.Retrying ->
            Text(
                stringResource(R.string.vote_syncing),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        VoteSyncState.Failed ->
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
        showSubmit = ready != null,
    )
  }
}

@Composable
private fun VoteLoadingContent() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    CircularProgressIndicator()
    Text(stringResource(R.string.vote_loading))
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NoVotePreview() =
    VoteInteractionPreview(
        persistedOptionId = null,
        selectedOptionId = null,
    )

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ExistingVotePreview() =
    VoteInteractionPreview(
        persistedOptionId = "restaurant",
        selectedOptionId = "restaurant",
    )

@Composable
private fun VoteInteractionPreview(
    persistedOptionId: String?,
    selectedOptionId: String?,
) {
    val vote =
        Vote(
            uid = "vote",
            tripId = "trip",
            question = "Where should we have dinner?",
            options =
                listOf(
                    VoteOption(
                        uid = "market",
                        name = "Local market",
                    ),
                    VoteOption(
                        uid = "restaurant",
                        name = "Restaurant",
                    ),
                    VoteOption(
                        uid = "pizza",
                        name = "Pizza place",
                    ),
                ),
            createdByUserId = "member",
            status = VoteStatus.OPEN,
            creationTime = Timestamp(
                seconds = 0,
                nanoseconds = 0,
            ),
            closingTime = null,
            winningOptionId = null,
        )

    RoamigoTheme {
        VoteScreenContent(
            state =
                VoteUiState.Ready(
                    vote = vote,
                    persistedOptionId = persistedOptionId,
                    selectedOptionId = selectedOptionId,
                    voteCounts =
                        mapOf(
                            "market" to 0,
                            "restaurant" to 1,
                            "pizza" to 5,
                        ),
                ),
            onSelectOption = {},
            onSubmit = {},
            onRetry = {},
            onBack = {},
        )
    }
}
