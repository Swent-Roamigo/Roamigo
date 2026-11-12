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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.rememberLocalDate
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent
import com.swent.roamigo.ui.trip.voting.components.VotesHintCard
import com.swent.roamigo.ui.trip.voting.components.VotesList
import java.time.ZoneId

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
    tripRepository: TripRepository,
    votesViewModelFactory: ViewModelProvider.Factory =
        VotesViewModel.factory(voteRepository, tripId, currentUserId),
    resolutionViewModelFactory: ViewModelProvider.Factory =
        TieResolutionViewModel.factory(
            voteRepository,
            tripRepository,
            tripId,
            currentUserId,
            readOnly,
        ),
) {
  val scopeKey = "$tripId-$currentUserId-$readOnly"
  val viewModel: VotesViewModel =
      viewModel(
          key = "votes-$scopeKey",
          factory = votesViewModelFactory,
      )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val resolution: TieResolutionViewModel =
      viewModel(
          key = "resolution-$scopeKey",
          factory = resolutionViewModelFactory,
      )
  val resolutionState by resolution.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val today by rememberLocalDate(ZoneId.systemDefault())
  LaunchedEffect(today) { resolution.refreshEligibility() }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resolution.refreshEligibility() }
  val source = resolutionState.revoteSource
  if (source != null) {
    key(source.uid) {
      // A cancelled form releases its draft ViewModel; configuration changes retain it.
      CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
        CreateVoteRoute(
            voteRepository,
            tripId,
            currentUserId,
            onBack = { resolution.dismiss() },
            onVoteCreated = { id ->
              resolution.dismiss()
              onOpenLiveVote(id)
            },
            revoteSource = source,
            eligibility = resolutionState.eligibility,
            onRetryEligibility = resolution::refreshEligibility,
            beforeSubmit = resolution::validateRevoteSubmission,
        )
      }
    }
    return
  }
  OwnerDecisionHost(
      state,
      resolutionState,
      resolution::selectOwnerOption,
      resolution::confirmOwnerDecision,
      resolution::dismiss,
      resolution::refreshEligibility,
  )
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
      onRevote =
          if (resolutionState.canResolve && !resolutionState.submitting) resolution::startRevote
          else null,
      onOwnerDecides =
          if (resolutionState.canResolve && resolutionState.isOwner && !resolutionState.submitting)
              resolution::startOwnerDecision
          else null,
      onCloseVote = onCloseVote,
      readOnly = readOnly,
      showHint = showHint,
      onDismissHint = onDismissHint,
      modifier = modifier,
      listState = listState,
      eligibility = resolutionState.eligibility,
      onRetryEligibility = resolution::refreshEligibility,
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
    onRevote: ((VoteUiState.Closed) -> Unit)? = null,
    onOwnerDecides: ((VoteUiState.Closed) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    eligibility: ResolutionEligibility? = null,
    onRetryEligibility: () -> Unit = {},
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
      eligibility?.let { ResolutionEligibilityContent(it, onRetryEligibility) }
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
                  onRevote = onRevote.takeUnless { readOnly },
                  onOwnerDecides = onOwnerDecides.takeUnless { readOnly },
                  listState = listState,
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
