// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent
import com.swent.roamigo.ui.trip.voting.components.VotesHintCard
import com.swent.roamigo.ui.trip.voting.components.VotesList

/** Connects [voteRepository] state and actions to [VotesScreen]. */
@Composable
fun VotesRoute(
    voteRepository: VoteRepository,
    tripId: String,
    currentUserId: String,
    tripName: String,
    onOpenVote: (String) -> Unit,
    onOpenLiveVote: (String) -> Unit,
    onCreateVote: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseVote: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
    showHint: Boolean = false,
    onDismissHint: () -> Unit = {},
) {
  val viewModel = viewModel { VotesViewModel(voteRepository, tripId, currentUserId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VotesScreen(
      state = state,
      tripName = tripName,
      onOpenVote = { id ->
        viewModel.selectVote(id)?.let { voteId ->
          val ready = viewModel.uiState.value as? VotesUiState.Ready
          if (!readOnly && ready?.liveVotes?.any { it.uid == voteId } == true)
              onOpenLiveVote(voteId)
          else onOpenVote(voteId)
        }
      },
      onCreateVote = onCreateVote,
      onRetry = viewModel::retry,
      onCloseVote = onCloseVote,
      readOnly = readOnly,
      showHint = showHint,
      onDismissHint = onDismissHint,
      modifier = modifier,
  )
}

/** Stateless content of the Votes tab. */
@Composable
fun VotesScreen(
    state: VotesUiState,
    tripName: String,
    onOpenVote: (String) -> Unit,
    onCreateVote: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseVote: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
    showHint: Boolean = false,
    onDismissHint: () -> Unit = {},
) {
  Scaffold(
      modifier = modifier,
      containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      if (showHint && !readOnly && state is VotesUiState.Ready) {
        VotesHintCard(onDismiss = onDismissHint)
      }
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (state) {
          VotesUiState.Loading ->
              Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(Spacing.medium),
              ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.votes_loading))
              }
          VotesUiState.Empty ->
              Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(Spacing.small),
              ) {
                Text(
                    stringResource(R.string.votes_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.votes_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
          VotesUiState.Error -> VoteErrorContent(R.string.votes_load_error, onRetry)
          is VotesUiState.Ready ->
              VotesList(
                  state,
                  onOpenVote,
                  onRetry,
                  onCloseVote = onCloseVote.takeUnless { readOnly },
              )
        }
      }
      if (!readOnly && (state is VotesUiState.Empty || state is VotesUiState.Ready)) {
        TripActionButton(onClick = onCreateVote) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(Spacing.small))
          Text(
              stringResource(
                  if (state is VotesUiState.Empty) R.string.votes_empty_start else R.string.vote_new
              )
          )
        }
      }
    }
  }
}
