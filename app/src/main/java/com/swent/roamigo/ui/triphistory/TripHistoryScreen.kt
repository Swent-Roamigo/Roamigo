package com.swent.roamigo.ui.triphistory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing

@Composable
fun TripHistoryScreen(
    uiState: TripHistoryUiState,
    onBackClick: () -> Unit = rememberNotImplementedAction(),
    onOpenWorldMapClick: () -> Unit = rememberNotImplementedAction(),
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
) {
  Scaffold(
      modifier = Modifier.testTag(C.Tag.trip_history_screen),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onPhotosClick = onPhotosClick,
            tripsSelected = false,
        )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag(C.Tag.trip_history_back).markNotImplemented(onBackClick),
          ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.trip_history_back),
            )
          }
          Column {
            Text(
                text = stringResource(R.string.trip_history_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!uiState.isLoading && uiState.errorMessage == null) {
              Text(
                  text =
                      pluralStringResource(
                          R.plurals.trip_history_count,
                          uiState.trips.size,
                          uiState.trips.size,
                      ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
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
              Text(
                  text = uiState.errorMessage,
                  modifier = Modifier.testTag(C.Tag.trip_history_error),
                  color = MaterialTheme.colorScheme.error,
              )
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
          item {
            OutlinedButton(
                onClick = onOpenWorldMapClick,
                modifier =
                    Modifier.fillMaxWidth()
                        .testTag(C.Tag.trip_history_world_map)
                        .markNotImplemented(onOpenWorldMapClick),
            ) {
              Icon(Icons.Outlined.Map, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(R.string.trip_history_world_map))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TripHistoryCard(trip: TripHistoryItemUiModel) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.trip_history_card_prefix + trip.id),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      border =
          BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant,
          ),
  ) {
    Row(
        modifier = Modifier.padding(Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.CardTravel,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(Spacing.medium))
      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = trip.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                trip.destinations.joinToString(" · ").ifBlank {
                  stringResource(R.string.trip_history_unknown_destination)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                "${trip.dateRange} · " +
                    pluralStringResource(
                        R.plurals.trips_traveler_count,
                        trip.travelerCount,
                        trip.travelerCount,
                    ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(Spacing.small))
      Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = MaterialTheme.shapes.small,
      ) {
        Text(
            text = stringResource(R.string.trip_history_completed),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
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
