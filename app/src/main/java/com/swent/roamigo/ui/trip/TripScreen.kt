// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class TripHeaderUiState(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val memberCount: Int,
)

internal enum class TripSection(val label: Int) {
  Detail(R.string.trip_details_tab),
  Map(R.string.trips_nav_map),
  Votes(R.string.trip_details_votes),
  Photos(R.string.trips_nav_photos),
}

/** Renders the shared trip header and navigation around the selected tab's [content]. */
@Composable
internal fun TripScreen(
    trip: TripHeaderUiState?,
    selectedTab: TripSection,
    today: LocalDate,
    photoCount: Int?,
    isSharing: Boolean,
    onBack: () -> Unit,
    onTabSelected: (TripSection) -> Unit,
    onPauseSharing: () -> Unit,
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onFriendsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
  Column(modifier.fillMaxSize().safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.trip_details_back),
        )
      }
      Column(Modifier.weight(1f)) {
        if (trip != null) {
          Text(trip.name, style = MaterialTheme.typography.headlineMedium)
          val locale = LocalConfiguration.current.locales[0]
          val formatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
          val subtitle =
              when (selectedTab) {
                TripSection.Detail ->
                    "${formatter.format(trip.startDate)} – ${formatter.format(trip.endDate)}"
                TripSection.Map -> formatter.format(today)
                TripSection.Votes -> stringResource(R.string.votes_title)
                TripSection.Photos ->
                    photoCount?.let { count ->
                      stringResource(
                          R.string.trip_photo_summary,
                          pluralStringResource(R.plurals.trip_photo_count, count, count),
                          pluralStringResource(
                              R.plurals.trip_person_count,
                              trip.memberCount,
                              trip.memberCount,
                          ),
                      )
                    } ?: stringResource(R.string.photos_title)
              }
          Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
      }
      val share = rememberNotImplementedAction()
      if (selectedTab == TripSection.Detail) {
        IconButton(onClick = share, modifier = Modifier.markNotImplemented(share)) {
          Icon(
              painter = painterResource(R.drawable.ic_trip_details_share),
              contentDescription = stringResource(R.string.trip_details_share),
          )
        }
      }
      if (selectedTab == TripSection.Detail && onAddClick != null) {
        IconButton(onClick = onAddClick, modifier = Modifier.testTag(C.Tag.trip_add_button)) {
          Icon(Icons.Default.Add, contentDescription = stringResource(R.string.trip_add_menu))
        }
      }
      if (selectedTab != TripSection.Map && isSharing) {
        TextButton(onClick = onPauseSharing) {
          Text(stringResource(R.string.trip_map_pause_sharing))
        }
      }
    }
    SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
      TripSection.entries.forEach { section ->
        Tab(
            selected = section == selectedTab,
            onClick = { onTabSelected(section) },
            text = { Text(stringResource(section.label)) },
        )
      }
    }
    Box(Modifier.weight(1f)) { content() }
    // Global destinations leave the current trip. A trip belongs to the Trips destination, so that
    // tab stays highlighted inside it.
    AppBottomBar(
        onTripsClick = onTripsClick,
        onMapClick = onMapClick,
        onFriendsClick = onFriendsClick,
        tripsSelected = true,
    )
  }
}
