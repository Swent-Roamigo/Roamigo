// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.swent.roamigo.R

enum class TripTab {
  OVERVIEW,
  ITINERARY,
  VOTES,
}

/** A null navigation callback marks a destination that is not available yet. */
@Composable
fun TripTabs(
    selectedTab: TripTab,
    onOverviewClick: (() -> Unit)? = null,
    onItineraryClick: (() -> Unit)? = null,
    onVotesClick: (() -> Unit)? = null,
) {
  val callbacks = listOf(onOverviewClick, onItineraryClick, onVotesClick)
  val labels = listOf(R.string.trip_tab_overview, R.string.trip_tab_itinerary, R.string.votes_title)
  SecondaryTabRow(
      selectedTabIndex = selectedTab.ordinal,
      containerColor = MaterialTheme.colorScheme.background,
  ) {
    TripTab.entries.forEach { tab ->
      val selected = tab == selectedTab
      val onClick = callbacks[tab.ordinal]
      Tab(
          selected = selected,
          enabled = selected || onClick != null,
          onClick = { onClick?.invoke() },
          text = {
            Text(
                stringResource(labels[tab.ordinal]),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
          },
      )
    }
  }
}
