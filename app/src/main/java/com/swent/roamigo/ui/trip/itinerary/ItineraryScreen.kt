// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.details.ForecastUiState
import com.swent.roamigo.ui.trip.details.TripForecastCard
import com.swent.roamigo.ui.trip.details.TripForecastUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shows the live plan of [tripId].
 *
 * @param tripStartDate first day of the trip, or `null` while the trip is loading.
 * @param tripEndDate last day of the trip, or `null` while the trip is loading.
 * @param onAddActivity opens the activity form, or `null` when the trip is read-only.
 * @param tripFailed whether the trip itself failed to load; the plan then shows an error.
 * @param onRetryTrip reloads the trip itself, whose dates and header may have failed to load too.
 * @param forecast the weather of the trip's days, shown above the plan, or `null` when there is
 *   none to show.
 */
@Composable
fun ItineraryRoute(
    tripId: String,
    activityRepository: ActivityRepository,
    tripStartDate: LocalDate?,
    tripEndDate: LocalDate?,
    onAddActivity: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tripFailed: Boolean = false,
    onRetryTrip: () -> Unit = {},
    forecast: TripForecastUi? = null,
) {
  val model: ItineraryViewModel =
      viewModel(
          key = "itinerary_$tripId",
          factory = ItineraryViewModel.factory(tripId, activityRepository),
      )
  LaunchedEffect(model, tripStartDate, tripEndDate) {
    if (tripStartDate != null && tripEndDate != null) {
      model.setTripDates(tripStartDate, tripEndDate)
    }
  }
  val state by model.uiState.collectAsStateWithLifecycle()
  ItineraryScreen(
      if (tripFailed) ItineraryUiState.Error else state,
      onSelectDay = model::selectDay,
      onRetry = {
        model.retry()
        onRetryTrip()
      },
      onAddActivity = onAddActivity,
      modifier = modifier,
      forecast = forecast,
  )
}

/**
 * Renders the day chips and the selected day's activities, as in Figma UX-02, below the trip's
 * [forecast] when there is one.
 */
@Composable
fun ItineraryScreen(
    state: ItineraryUiState,
    onSelectDay: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onAddActivity: (() -> Unit)?,
    modifier: Modifier = Modifier,
    forecast: TripForecastUi? = null,
) {
  Box(modifier.fillMaxSize().testTag(C.Tag.itinerary_screen)) {
    when (state) {
      ItineraryUiState.Loading ->
          Column(
              Modifier.align(Alignment.Center),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.itinerary_loading))
          }
      ItineraryUiState.Error ->
          Column(
              Modifier.align(Alignment.Center).padding(Spacing.screenPadding),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(stringResource(R.string.itinerary_load_failed), textAlign = TextAlign.Center)
            Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.itinerary_retry)) {
              Text(stringResource(R.string.itinerary_retry))
            }
          }
      is ItineraryUiState.Ready ->
          ItineraryContent(state, onSelectDay, onRetry, onAddActivity, forecast = forecast)
    }
  }
}

@Composable
private fun ItineraryContent(
    state: ItineraryUiState.Ready,
    onSelectDay: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onAddActivity: (() -> Unit)?,
    forecast: TripForecastUi?,
) {
  val locale = LocalConfiguration.current.locales[0]
  val timeFormatter =
      remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
  // Chips and rows share one scrolling list so rows stay reachable in landscape.
  LazyColumn(
      Modifier.fillMaxSize().testTag(C.Tag.itinerary_list),
      contentPadding = PaddingValues(vertical = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    // A hidden card draws nothing, but an empty item would still add a gap above the chips.
    if (forecast != null && forecast.state != ForecastUiState.Hidden) {
      item {
        TripForecastCard(
            forecast.state,
            forecast.city,
            forecast.onRetry,
            Modifier.padding(horizontal = Spacing.screenPadding),
        )
      }
    }
    if (state.refreshFailed) {
      item { RefreshFailedBanner(onRetry) }
    }
    if (state.days.isNotEmpty()) {
      item { DayChips(state.days, state.selectedDay, onSelectDay) }
    }
    when {
      !state.hasActivities ->
          item { EmptyPlan(stringResource(R.string.itinerary_empty_trip), onAddActivity) }
      state.activities.isEmpty() ->
          item { EmptyPlan(stringResource(R.string.itinerary_empty_day), onAddActivity) }
      else ->
          items(state.activities, key = { it.id }) { activity ->
            ActivityRow(activity, timeFormatter.format(activity.startTime))
          }
    }
  }
}

@Composable
private fun RefreshFailedBanner(onRetry: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().padding(horizontal = Spacing.screenPadding),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        stringResource(R.string.itinerary_refresh_failed),
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(C.Tag.itinerary_retry)) {
      Text(stringResource(R.string.itinerary_retry))
    }
  }
}

@Composable
private fun DayChips(
    days: List<LocalDate>,
    selected: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  val shortFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
  val fullFormatter =
      remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
  val listState = rememberLazyListState()
  val selectedIndex = days.indexOf(selected)
  LaunchedEffect(selectedIndex) {
    if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
  }
  LazyRow(
      state = listState,
      contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    items(days, key = { it.toEpochDay() }) { day ->
      val fullDate = fullFormatter.format(day)
      FilterChip(
          selected = day == selected,
          onClick = { onSelectDay(day) },
          label = { Text(shortFormatter.format(day)) },
          modifier =
              Modifier.testTag(C.Tag.itineraryDay(day)).semantics { contentDescription = fullDate },
      )
    }
  }
}

@Composable
private fun ActivityRow(activity: ItineraryActivity, startTime: String) {
  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = Spacing.screenPadding)
              .testTag(C.Tag.itineraryActivity(activity.id))
              .semantics(mergeDescendants = true) {},
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        Modifier.padding(Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(startTime, style = MaterialTheme.typography.labelLarge)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(activity.name, style = MaterialTheme.typography.titleSmall)
        Text(
            activity.locationName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun EmptyPlan(message: String, onAddActivity: (() -> Unit)?) {
  Column(
      Modifier.fillMaxWidth().padding(Spacing.screenPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Text(message, textAlign = TextAlign.Center)
    if (onAddActivity != null) {
      Button(onClick = onAddActivity, modifier = Modifier.testTag(C.Tag.itinerary_add_activity)) {
        Text(stringResource(R.string.trip_add_activity))
      }
    }
  }
}
