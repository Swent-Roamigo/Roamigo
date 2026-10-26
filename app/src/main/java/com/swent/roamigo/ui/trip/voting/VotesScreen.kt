// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteCard
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent

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
      VotesTripTabs()
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

@Composable
private fun VotesTripTabs() {
  SecondaryTabRow(selectedTabIndex = 2, containerColor = MaterialTheme.colorScheme.background) {
    Tab(
        selected = false,
        onClick = {},
        enabled = false,
        text = {
          Text(
              stringResource(R.string.trip_tab_overview),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
    )
    Tab(
        selected = false,
        onClick = {},
        enabled = false,
        text = {
          Text(
              stringResource(R.string.trip_tab_itinerary),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
    )
    Tab(
        selected = true,
        onClick = {},
        text = {
          Text(
              stringResource(R.string.votes_title),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
          )
        },
    )
  }
}

@Composable
private fun VotesList(
    state: VotesUiState.Ready,
    onOpenVote: (String) -> Unit,
    onRetry: () -> Unit,
) {
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    when (state.syncState) {
      VotesSyncState.SYNCED -> Unit
      VotesSyncState.RETRYING -> item { Text(stringResource(R.string.vote_syncing)) }
      VotesSyncState.FAILED -> item { VoteErrorContent(R.string.votes_sync_error, onRetry) }
    }
    if (state.liveVotes.isNotEmpty()) {
      items(state.liveVotes, key = { it.uid }) { vote ->
        VoteCard(vote, onClick = { onOpenVote(vote.uid) })
      }
    }
    if (state.closedVotes.isNotEmpty()) {
      item { VotesSectionTitle(stringResource(R.string.votes_decided)) }
      items(state.closedVotes, key = { it.uid }) { vote ->
        VoteCard(vote, onClick = { onOpenVote(vote.uid) })
      }
    }
  }
}

@Composable
private fun VotesSectionTitle(title: String) {
  Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { heading() },
  )
}

@Preview(name = "Votes · Ready", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VotesReadyPreview() {
  val dinner =
      Vote(
          uid = "dinner",
          tripId = "lisbon-porto",
          question = "Dinner tonight?",
          options =
              listOf(
                  VoteOption("market", "Time Out Market"),
                  VoteOption("taberna", "Taberna das Flores"),
              ),
          createdByUserId = "sam",
          status = VoteStatus.OPEN,
          creationTime = Timestamp(1_791_724_800, 0),
          closingTime = null,
          winningOptionId = null,
      )
  VotesPreview(
      VotesUiState.Ready(
          liveVotes =
              listOf(
                  dinner,
                  dinner.copy(
                      uid = "explore-porto",
                      question = "How should we explore Porto?",
                      options =
                          listOf(
                              VoteOption("walk", "Walk along the riverside"),
                              VoteOption("tram", "Take the historic tram"),
                          ),
                      creationTime = Timestamp(1_791_721_200, 0),
                  ),
              ),
          closedVotes =
              listOf(
                  dinner.copy(
                      uid = "lx-factory",
                      question = "When should we visit LX Factory?",
                      options =
                          listOf(
                              VoteOption("afternoon", "Early afternoon"),
                              VoteOption("evening", "Before dinner"),
                          ),
                      status = VoteStatus.CLOSED,
                      creationTime = Timestamp(1_791_637_200, 0),
                  )
              ),
      )
  )
}

@Preview(name = "Votes · Empty", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VotesEmptyPreview() = VotesPreview(VotesUiState.Empty)

@Composable
private fun VotesPreview(state: VotesUiState) {
  RoamigoTheme {
    VotesScreenContent(
        state = state,
        tripName = "Lisbon & Porto",
        onOpenVote = {},
        onCreateVote = {},
        onRetry = {},
        onTripsClick = {},
    )
  }
}
