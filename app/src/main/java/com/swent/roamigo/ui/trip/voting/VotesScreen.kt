// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.components.TripTab
import com.swent.roamigo.ui.trip.components.TripTabs
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent
import com.swent.roamigo.ui.trip.voting.components.VotesList

/** Collects trip-level state; the caller navigates to the existing individual vote flow. */
@Composable
fun VotesScreen(
    viewModel: VotesViewModel,
    tripName: String,
    onOpenVote: (String) -> Unit,
    onCreateVote: () -> Unit,
    onTripsClick: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VotesScreenContent(
      state = state,
      tripName = tripName,
      onOpenVote = { id -> viewModel.selectVote(id)?.let(onOpenVote) },
      onCreateVote = onCreateVote,
      onRetry = viewModel::retry,
      onTripsClick = onTripsClick,
  )
}

/** Stateless Votes tab. Overview and Itinerary await their own trip destinations. */
@Composable
fun VotesScreenContent(
    state: VotesUiState,
    tripName: String,
    onOpenVote: (String) -> Unit,
    onCreateVote: () -> Unit,
    onRetry: () -> Unit,
    onTripsClick: () -> Unit,
) {
  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = rememberNotImplementedAction(),
            onPhotosClick = rememberNotImplementedAction(),
            tripsSelected = false,
        )
      },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(stringResource(R.string.votes_title), style = MaterialTheme.typography.headlineLarge)
        val ready = state as? VotesUiState.Ready
        Text(
            if (ready != null)
                stringResource(
                    R.string.votes_trip_summary,
                    tripName,
                    pluralStringResource(
                        R.plurals.votes_open_count,
                        ready.liveVotes.size,
                        ready.liveVotes.size,
                    ),
                )
            else tripName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TripTabs(
          selectedTab = TripTab.VOTES,
          onOverviewClick = rememberNotImplementedAction(),
          onItineraryClick = rememberNotImplementedAction(),
      )
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
          is VotesUiState.Ready -> VotesList(state, onOpenVote, onRetry)
        }
      }
      if (state is VotesUiState.Empty || state is VotesUiState.Ready) {
        Button(
            onClick = onCreateVote,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = MaterialTheme.shapes.small,
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(Spacing.small))
          Text(stringResource(R.string.vote_new))
        }
      }
    }
  }
}
