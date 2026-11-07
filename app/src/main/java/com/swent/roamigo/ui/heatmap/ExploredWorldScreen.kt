package com.swent.roamigo.ui.heatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import java.util.Locale

@Composable
fun ExploredWorldScreen(
    uiState: TravelHeatmapUiState,
    completedTripCount: Int?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val counts = (uiState as? TravelHeatmapUiState.Success)?.visitCounts.orEmpty()

  val countries =
      counts.entries.sortedWith(
          compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { countryName(it.key) }
      )

  Scaffold(
      modifier = modifier.testTag(C.Tag.heatmap_screen),
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding =
            PaddingValues(
                horizontal = Spacing.screenPadding,
                vertical = Spacing.large,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
              onClick = onBackClick,
              modifier = Modifier.testTag(C.Tag.heatmap_back),
          ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.trip_history_back),
            )
          }

          Column {
            Text(
                text = stringResource(R.string.heatmap_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )

            if (completedTripCount != null && uiState is TravelHeatmapUiState.Success) {
              Text(
                  text =
                      pluralStringResource(
                          R.plurals.heatmap_country_count,
                          countries.size,
                          countries.size,
                      ) +
                          " · " +
                          pluralStringResource(
                              R.plurals.heatmap_trip_count,
                              completedTripCount,
                              completedTripCount,
                          ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
      }

      item { DottedWorldMap(visitCounts = counts) }

      when (uiState) {
        TravelHeatmapUiState.Loading -> {
          item {
            Text(
                text = stringResource(R.string.heatmap_loading),
                modifier = Modifier.testTag(C.Tag.heatmap_loading),
            )
          }
        }

        TravelHeatmapUiState.Error -> {
          item {
            Text(
                text = stringResource(R.string.heatmap_load_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(C.Tag.heatmap_error),
            )
          }
        }

        is TravelHeatmapUiState.Success -> {
          if (countries.isEmpty()) {
            item {
              Text(
                  text = stringResource(R.string.heatmap_empty),
                  modifier = Modifier.testTag(C.Tag.heatmap_empty),
              )
            }
          } else {
            items(
                items = countries,
                key = { it.key },
            ) { entry ->
              CountryVisitRow(
                  code = entry.key,
                  visits = entry.value,
              )
            }
          }
        }
      }
    }
  }
}

internal fun countryName(code: String): String {
  if (code.length != 2 || !code.all { it in 'A'..'Z' }) return code

  return runCatching {
        Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).takeIf {
          it.isNotBlank()
        } ?: code
      }
      .getOrDefault(code)
}

@Composable
private fun CountryVisitRow(
    code: String,
    visits: Int,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.heatmap_country_prefix + code),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        modifier = Modifier.padding(Spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
          imageVector = Icons.Outlined.Place,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )

      Text(
          text = countryName(code),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
      )

      Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface,
      ) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.heatmap_trip_count,
                    visits,
                    visits,
                ),
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 5.dp,
                ),
            style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }
}
