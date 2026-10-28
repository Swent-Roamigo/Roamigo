package com.swent.roamigo.ui.trip.history.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.history.TripHistoryItemUiModel

/** Displays a completed trip with its destinations, dates, and travelers. */
@Composable
internal fun TripHistoryCard(trip: TripHistoryItemUiModel) {
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
