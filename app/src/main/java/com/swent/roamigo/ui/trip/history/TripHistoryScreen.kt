package com.swent.roamigo.ui.trip.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.history.components.OpenWorldMapButton
import com.swent.roamigo.ui.trip.history.components.TripHistoryCard
import com.swent.roamigo.ui.trip.history.components.TripHistoryHeader

@Composable
fun TripHistoryScreen(
    uiState: TripHistoryUiState,
    onBackClick: () -> Unit = rememberNotImplementedAction(),
    onOpenWorldMapClick: () -> Unit = rememberNotImplementedAction(),
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    onRetryClick: () -> Unit = rememberNotImplementedAction(),
) {
  Scaffold(
      modifier = Modifier.testTag(C.Tag.trip_history_screen),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        Column {
          if (!uiState.isLoading && uiState.errorMessage == null && uiState.trips.isNotEmpty()) {
            OpenWorldMapButton(onClick = onOpenWorldMapClick)
          }

          AppBottomBar(
              onTripsClick = onTripsClick,
              onMapClick = onMapClick,
              onPhotosClick = onPhotosClick,
              tripsSelected = false,
              onFriendsClick = onFriendsClick,
          )
        }
      },
  ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding =
            PaddingValues(
                horizontal = Spacing.screenPadding,
                vertical = Spacing.extraLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      item {
        TripHistoryHeader(
            onBackClick = onBackClick,
            completedTripCount =
                if (!uiState.isLoading && uiState.errorMessage == null) {
                  uiState.trips.size
                } else {
                  null
                },
        )
      }

      when {
        uiState.isLoading ->
            item {
              Box(
                  modifier =
                      Modifier.fillMaxWidth().height(120.dp).testTag(C.Tag.trip_history_loading),
                  contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }

        uiState.errorMessage != null ->
            item {
              Column(
                  modifier =
                      Modifier.fillMaxWidth()
                          .padding(vertical = Spacing.large)
                          .testTag(C.Tag.trip_history_error),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(Spacing.medium),
              ) {
                Text(
                    text =
                        uiState.errorMessage?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.trip_history_load_error),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.testTag(C.Tag.trip_history_retry),
                ) {
                  Text(stringResource(R.string.trip_history_retry))
                }
              }
            }

        uiState.trips.isEmpty() ->
            item {
              Box(
                  modifier =
                      Modifier.fillMaxWidth().height(160.dp).testTag(C.Tag.trip_history_empty),
                  contentAlignment = Alignment.Center,
              ) {
                Text(
                    text = stringResource(R.string.trip_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

        else -> {
          items(uiState.trips, key = { it.id }) { trip -> TripHistoryCard(trip) }
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripHistoryPreview() {
  RoamigoTheme {
    TripHistoryScreen(
        uiState =
            TripHistoryUiState(
                isLoading = false,
                trips =
                    listOf(
                        TripHistoryItemUiModel(
                            id = "rome",
                            name = "Summer in Italy",
                            destinations = listOf("Rome", "Florence"),
                            dateRange = "5 Jun 2016 – 12 Jun 2016",
                            travelerCount = 3,
                        ),
                    ),
            ),
    )
  }
}
