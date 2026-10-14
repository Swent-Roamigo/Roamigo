// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing

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
      state.vote?.let { vote ->
        Text(vote.question, style = MaterialTheme.typography.headlineLarge)
        Column(
            Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          vote.options.forEach { option ->
            val selected = state.selectedOptionId == option.uid
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small,
                    )
                    .selectable(
                        selected = selected,
                        enabled = state.canSelect,
                        role = Role.RadioButton,
                        onClick = { onSelectOption(option.uid) },
                    )
                    .padding(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected, onClick = null, enabled = state.canSelect)
              Spacer(Modifier.width(Spacing.medium))
              Text(
                  option.name,
                  style = MaterialTheme.typography.bodyLarge,
                  color =
                      if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        if (vote.status == VoteStatus.CLOSED) Text(stringResource(R.string.vote_closed))
      }
      if (state.isLoading) {
        CircularProgressIndicator()
        Text(stringResource(R.string.vote_loading))
      }
    }
    state.error?.let { error ->
      Text(
          stringResource(
              when (error) {
                VoteError.NOT_FOUND -> R.string.vote_not_found
                VoteError.LOAD_FAILED -> R.string.vote_load_error
                VoteError.BALLOTS_FAILED -> R.string.vote_ballots_error
                VoteError.SUBMISSION_FAILED -> R.string.vote_ballot_submission_error
              }
          ),
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      if (state.canRetryLoad)
          OutlinedButton(onClick = onRetry, enabled = !state.isSubmitting) {
            Text(stringResource(R.string.vote_retry))
          }
    }
    Button(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
      if (state.isSubmitting) {
        CircularProgressIndicator(Modifier.size(Spacing.extraLarge), strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacing.small))
      }
      Text(
          stringResource(
              when {
                state.isSubmitting -> R.string.vote_submitting
                state.currentBallot != null -> R.string.vote_update
                else -> R.string.vote_submit
              }
          )
      )
    }
    OutlinedButton(
        onClick = onBack,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
      Text(stringResource(R.string.vote_back))
    }
  }
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
        VoteUiState(
            isLoading = false,
            vote = vote,
            ballotsLoaded = true,
            selectedOptionId = if (selected) "market" else null,
            currentBallot = if (selected) VoteBallot("member", "market", Timestamp(0, 0)) else null,
        ),
        {},
        {},
        {},
        {},
    )
  }
}
