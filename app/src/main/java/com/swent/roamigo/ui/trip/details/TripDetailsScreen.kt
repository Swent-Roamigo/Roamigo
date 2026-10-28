// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Standalone repository-backed entry point; the caller owns navigation and feature actions. */
@Composable
fun TripDetailsRoute(
    tripId: String,
    tripRepository: TripRepository,
    onBack: () -> Unit,
    onTripsClick: () -> Unit,
    onShareClick: () -> Unit = rememberNotImplementedAction(),
    onInviteClick: () -> Unit = rememberNotImplementedAction(),
    onItineraryClick: () -> Unit = rememberNotImplementedAction(),
    onVotesClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
) {
  val model = viewModel(key = tripId) { TripDetailsViewModel(tripId, tripRepository) }
  val state by model.uiState.collectAsState()
  TripDetailsScreen(
      state,
      onRetry = model::refresh,
      onBack = onBack,
      onTripsClick = onTripsClick,
      onShareClick = onShareClick,
      onInviteClick = onInviteClick,
      onItineraryClick = onItineraryClick,
      onVotesClick = onVotesClick,
      onMapClick = onMapClick,
      onPhotosClick = onPhotosClick,
  )
}

/** Renders trip details and delegates navigation without depending on Firebase or Mapbox. */
@Composable
fun TripDetailsScreen(
    state: TripDetailsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onTripsClick: () -> Unit,
    onShareClick: () -> Unit = rememberNotImplementedAction(),
    onInviteClick: () -> Unit = rememberNotImplementedAction(),
    onItineraryClick: () -> Unit = rememberNotImplementedAction(),
    onVotesClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
) {
  Scaffold(
      modifier = Modifier.testTag("trip_details_screen"),
      bottomBar = { AppBottomBar(onTripsClick, onMapClick, onPhotosClick) },
  ) { padding ->
    when {
      state.isLoading ->
          Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator()
              Text(stringResource(R.string.trip_details_loading))
            }
          }
      state.error != null ->
          Column(
              Modifier.fillMaxSize().padding(padding).padding(Spacing.screenPadding),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
                stringResource(
                    if (state.error == TripDetailsError.NOT_FOUND) R.string.trip_details_not_found
                    else R.string.trip_details_load_failed
                )
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.trip_details_retry)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.trip_details_back)) }
          }
      state.details != null ->
          TripDetailsContent(
              details = state.details,
              modifier = Modifier.fillMaxSize().padding(padding),
              onShareClick = onShareClick,
              onInviteClick = onInviteClick,
              onItineraryClick = onItineraryClick,
              onVotesClick = onVotesClick,
              onMapClick = onMapClick,
          )
    }
  }
}

/** The compact single-trip layout; summaries remain limited to repository-backed data. */
@Composable
private fun TripDetailsContent(
    details: TripDetails,
    modifier: Modifier,
    onShareClick: () -> Unit,
    onInviteClick: () -> Unit,
    onItineraryClick: () -> Unit,
    onVotesClick: () -> Unit,
    onMapClick: () -> Unit,
) {
  Column(
      modifier.padding(
          start = Spacing.screenPadding,
          end = Spacing.screenPadding,
          top = 12.dp,
          bottom = 16.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(details.name, style = MaterialTheme.typography.titleLarge.copy(lineHeight = 38.sp))
          val locale = LocalConfiguration.current.locales[0]
          val travelers =
              pluralStringResource(
                  R.plurals.trip_details_travelers,
                  details.travelerCount,
                  details.travelerCount,
              )
          Text(
              stringResource(
                  R.string.trip_details_subtitle,
                  formatTripDetailsDates(details.startDate, details.endDate, locale),
                  travelers,
              ),
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Box(
            modifier =
                Modifier.size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
          IconButton(onClick = onShareClick, modifier = Modifier.markNotImplemented(onShareClick)) {
            Icon(
                painterResource(R.drawable.ic_trip_details_share),
                stringResource(R.string.trip_details_share),
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
      TripDetailsTabs(onItineraryClick, onVotesClick)
      TripDetailsSummaryCard(
          R.drawable.ic_trip_details_members,
          stringResource(R.string.trip_details_members),
          pluralStringResource(
              R.plurals.trip_details_member_count,
              details.memberCount,
              details.memberCount,
          ),
      ) {
        // A full touch target surrounds the small Figma badge.
        Box(
            Modifier.size(48.dp)
                .markNotImplemented(onInviteClick)
                .clickable(role = Role.Button, onClick = onInviteClick),
            contentAlignment = Alignment.CenterEnd,
        ) {
          TripDetailsBadge(stringResource(R.string.trip_details_invite))
        }
      }
      TripDetailsSummaryCard(
          R.drawable.ic_trip_details_places,
          stringResource(R.string.trip_details_places),
          pluralStringResource(
              R.plurals.trip_details_suggestions,
              details.suggestedPlaceCount,
              details.suggestedPlaceCount,
          ),
      ) {
        TripDetailsBadge(details.suggestedPlaceCount.toString())
      }
      TripDetailsSummaryCard(
          R.drawable.ic_trip_details_check,
          stringResource(R.string.trip_details_live_votes),
          stringResource(R.string.trip_details_unavailable),
      ) {
        TripDetailsBadge(stringResource(R.string.trip_details_unavailable_count))
      }
      TripDetailsSummaryCard(
          R.drawable.ic_trip_details_photos,
          stringResource(R.string.trip_details_photos),
          stringResource(R.string.trip_details_unavailable),
      ) {
        TripDetailsBadge(stringResource(R.string.trip_details_unavailable_count))
      }
    }
    OutlinedButton(
        onClick = onMapClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 53.dp).markNotImplemented(onMapClick),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
      Text(stringResource(R.string.trip_details_map), style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun TripDetailsTabs(onItineraryClick: () -> Unit, onVotesClick: () -> Unit) {
  SecondaryTabRow(
      selectedTabIndex = 0,
      modifier = Modifier.height(42.dp),
      containerColor = MaterialTheme.colorScheme.background,
      indicator = {
        TabRowDefaults.SecondaryIndicator(
            Modifier.tabIndicatorOffset(0),
            height = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
      },
      divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
  ) {
    val labels =
        listOf(
            R.string.trip_details_tab,
            R.string.trip_details_itinerary,
            R.string.trip_details_votes,
        )
    val actions = listOf({}, onItineraryClick, onVotesClick)
    labels.forEachIndexed { index, label ->
      Tab(
          selected = index == 0,
          onClick = actions[index],
          modifier = Modifier.height(42.dp).markNotImplemented(actions[index]),
          text = {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Medium,
                color =
                    if (index == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
      )
    }
  }
}

@Composable
private fun TripDetailsSummaryCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        Modifier.heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(icon),
            null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(lineHeight = 20.sp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      trailingContent()
    }
  }
}

@Composable
private fun TripDetailsBadge(text: String) {
  Surface(
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.primary,
  ) {
    Text(
        text,
        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp,
            ),
    )
  }
}

/** Compact Figma date ranges retain both months or years when the trip crosses a boundary. */
internal fun formatTripDetailsDates(start: LocalDate, end: LocalDate, locale: Locale): String {
  val pattern = if (start.year == end.year) "d MMM" else "d MMM yyyy"
  val formatter = DateTimeFormatter.ofPattern(pattern, locale)
  val startText =
      if (start.year == end.year && start.month == end.month) start.dayOfMonth.toString()
      else start.format(formatter)
  return "$startText – ${end.format(formatter)}"
}
