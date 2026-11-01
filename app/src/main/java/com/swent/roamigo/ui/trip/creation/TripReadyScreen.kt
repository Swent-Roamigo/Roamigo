// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.PhotoCreditsDialogTestTags
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.trip.creation.components.TripReadyCard
import com.swent.roamigo.ui.trip.creation.components.TripReadyHeader
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object TripReadyTestTags {
  const val TITLE = "trip_ready_title"
  const val SUMMARY = "trip_ready_summary_title"
  const val ROUTE = "trip_ready_summary_detail"
  const val LOCATION = "trip_ready_location_title"
  const val RETRY = "trip_ready_retry"
  const val PHOTO_CREDITS = "trip_ready_photo_credits"
  const val PHOTO_TITLE = PhotoCreditsDialogTestTags.TITLE
  const val PHOTO_CREATOR = PhotoCreditsDialogTestTags.CREATOR
  const val PHOTO_CREDIT = PhotoCreditsDialogTestTags.CREDIT
  const val PHOTO_CLOSE = PhotoCreditsDialogTestTags.CLOSE
  const val LOADING = "trip_ready_loading"
  const val ERROR = "trip_ready_error"
  const val ITINERARY = "trip_ready_itinerary"
  const val SHARE = "trip_ready_share"
  const val INVITE_CARD = "trip_ready_invite"
  const val SUGGEST_CARD = "trip_ready_suggest"
  const val LOCATION_CARD = "trip_ready_location"
}

@Composable
fun TripReadyScreen(
    viewModel: TripReadyViewModel,
    photo: Bundle?,
    onInviteClick: () -> Unit,
    onSuggestClick: () -> Unit,
    onShareLocationClick: () -> Unit,
    onOpenItineraryClick: () -> Unit,
    onShareTripClick: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  TripReadyContent(
      state = state,
      photo = photo,
      onRetry = viewModel::retry,
      onInviteClick = onInviteClick,
      onSuggestClick = onSuggestClick,
      onShareLocationClick = onShareLocationClick,
      onOpenItineraryClick = onOpenItineraryClick,
      onShareTripClick = onShareTripClick,
  )
}

@Composable
internal fun TripReadyContent(
    state: TripReadyState,
    photo: Bundle?,
    onRetry: () -> Unit,
    onInviteClick: () -> Unit,
    onSuggestClick: () -> Unit,
    onShareLocationClick: () -> Unit,
    onOpenItineraryClick: () -> Unit,
    onShareTripClick: () -> Unit,
) {
  when (state) {
    TripReadyState.Loading ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.semantics { testTag = TripReadyTestTags.LOADING })
        }
    TripReadyState.Error ->
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          Text(
              stringResource(R.string.trip_ready_error),
              Modifier.semantics { testTag = TripReadyTestTags.ERROR },
          )
          Button(
              onClick = onRetry,
              modifier = Modifier.semantics { testTag = TripReadyTestTags.RETRY },
          ) {
            Text(stringResource(R.string.destination_search_retry))
          }
        }
    is TripReadyState.Ready ->
        TripReadySuccessContent(
            state,
            photo,
            onInviteClick,
            onSuggestClick,
            onShareLocationClick,
            onOpenItineraryClick,
            onShareTripClick,
        )
  }
}

@Composable
private fun TripReadySuccessContent(
    state: TripReadyState.Ready,
    photo: Bundle?,
    onInviteClick: () -> Unit,
    onSuggestClick: () -> Unit,
    onShareLocationClick: () -> Unit,
    onOpenItineraryClick: () -> Unit,
    onShareTripClick: () -> Unit,
) {
  val trip = state.trip
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
  val stopsText =
      pluralStringResource(
          R.plurals.trips_stop_count,
          trip.destinations.size,
          trip.destinations.size,
      )
  val durationText =
      pluralStringResource(R.plurals.trips_duration_days, state.days.toInt(), state.days)
  val dateSummary =
      stringResource(
          R.string.trip_ready_dates,
          trip.name,
          formatter.format(state.startDate),
          formatter.format(state.endDate),
      )

  Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).semantics {
        testTag = "trip_ready"
      }
  ) {
    TripReadyHeader(dateSummary = dateSummary, photo = photo)
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      TripReadyCard(
          title = stringResource(R.string.trip_ready_summary, stopsText, durationText),
          detail = trip.destinations.joinToString(" → ") { it.name },
          tag = "summary",
          onClick = null,
      )
      TripReadyCard(
          title = stringResource(R.string.trip_ready_invite),
          detail = stringResource(R.string.trip_ready_invite_detail),
          tag = "invite",
          onClick = onInviteClick,
      )
      TripReadyCard(
          title = stringResource(R.string.trip_ready_suggest),
          detail = stringResource(R.string.trip_ready_suggest_detail),
          tag = "suggest",
          onClick = onSuggestClick,
      )
      TripReadyCard(
          title = stringResource(R.string.trip_ready_location),
          detail = stringResource(R.string.trip_ready_location_detail),
          tag = "location",
          onClick = onShareLocationClick,
      )
      Button(
          onClick = onOpenItineraryClick,
          modifier =
              Modifier.fillMaxWidth().markNotImplemented(onOpenItineraryClick).semantics {
                testTag = TripReadyTestTags.ITINERARY
              },
      ) {
        Text(stringResource(R.string.trip_ready_itinerary))
      }
      OutlinedButton(
          onClick = onShareTripClick,
          modifier =
              Modifier.fillMaxWidth().markNotImplemented(onShareTripClick).semantics {
                testTag = TripReadyTestTags.SHARE
              },
      ) {
        Text(stringResource(R.string.trip_ready_share))
      }
    }
  }
}
