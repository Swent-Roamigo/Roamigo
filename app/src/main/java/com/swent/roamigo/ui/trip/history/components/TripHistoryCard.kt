package com.swent.roamigo.ui.trip.history.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.ListRow
import com.swent.roamigo.ui.components.RoamigoBadge
import com.swent.roamigo.ui.trip.history.TripHistoryItemUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Displays a clickable completed trip with its destinations, dates, and travelers. */
@Composable
internal fun TripHistoryCard(
    trip: TripHistoryItemUiModel,
    onClick: () -> Unit,
) {
  ListRow(
      title = trip.name,
      onClick = onClick,
      modifier = Modifier.testTag(C.Tag.trip_history_card_prefix + trip.id),
      subtitle =
          trip.destinations.joinToString(" · ").ifBlank {
            stringResource(R.string.trip_history_unknown_destination)
          },
      details =
          "${formatTripHistoryDateRange(trip.startDate, trip.endDate)} · " +
              pluralStringResource(
                  R.plurals.trips_traveler_count,
                  trip.travelerCount,
                  trip.travelerCount,
              ),
      leading = {
        Icon(
            imageVector = Icons.Outlined.CardTravel,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
      },
      trailing = { RoamigoBadge(stringResource(R.string.trip_history_completed)) },
  )
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Formats the start and end dates of a completed trip for display. */
internal fun formatTripHistoryDateRange(
    startDate: LocalDate,
    endDate: LocalDate,
): String = "${startDate.format(dateFormatter)} – ${endDate.format(dateFormatter)}"
