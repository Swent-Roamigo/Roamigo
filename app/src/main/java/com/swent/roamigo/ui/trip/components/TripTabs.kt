// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction

enum class TripTab {
  OVERVIEW,
  ITINERARY,
  VOTES,
}

/** Shared trip navigation; unfinished destinations give the standard placeholder feedback. */
@Composable
fun TripTabs(
    selectedTab: TripTab,
    onOverviewClick: () -> Unit = rememberNotImplementedAction(),
    onItineraryClick: () -> Unit = rememberNotImplementedAction(),
    onVotesClick: () -> Unit = {},
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
          onClick = onClick,
          modifier = Modifier.markNotImplemented(onClick),
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
