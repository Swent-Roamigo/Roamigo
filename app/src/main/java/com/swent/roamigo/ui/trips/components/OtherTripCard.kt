package com.swent.roamigo.ui.trips.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.ListRow
import com.swent.roamigo.ui.trips.OtherTripUiModel

/** Displays a secondary trip that can be opened. */
@Composable
internal fun OtherTripCard(trip: OtherTripUiModel, onClick: () -> Unit) {
  val travelersText =
      pluralStringResource(R.plurals.trips_traveler_count, trip.memberCount, trip.memberCount)
  ListRow(
      title = trip.name,
      onClick = onClick,
      subtitle = "${trip.dateRange} · $travelersText",
      afterTitle =
          if (trip.isInProgress) {
            { StatusPill(stringResource(R.string.trips_in_progress)) }
          } else null,
      trailing =
          if (trip.memberCount > 1) {
            { StatusPill(stringResource(R.string.trips_shared)) }
          } else null,
  )
}
