// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trips

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.trips.components.HistoryButton
import com.swent.roamigo.ui.trips.components.MainTripCard
import com.swent.roamigo.ui.trips.components.NextActivityCard
import com.swent.roamigo.ui.trips.components.OtherTripCard
import com.swent.roamigo.ui.trips.components.SectionTitle
import com.swent.roamigo.ui.trips.components.TripsHeader
import java.time.Clock

/**
 * Connects repository-backed trip data to the stateless [TripsScreen].
 *
 * This entry point owns the [TripsViewModel] integration while keeping navigation and user actions
 * delegated to the caller.
 *
 * @param tripRepository the repository providing trips, activities, and trip members.
 * @param userRepository the repository providing the current user and member profiles.
 * @param mainTripImageRes the optional image resource displayed for the highlighted trip.
 * @param nextActivityImageRes the optional image resource displayed for the next activity.
 * @param onInviteClick invoked when the user selects the invite action.
 * @param onNewTripClick invoked when the user selects the new-trip action.
 * @param onTripsClick invoked when the Trips navigation destination is selected.
 * @param onMapClick invoked when the Map navigation destination is selected.
 * @param onPhotosClick invoked when the Photos navigation destination is selected and there is no
 *   highlighted trip to show the photos of.
 * @param onOpenTripPhotos opens the photos of the highlighted trip, when the Photos destination is
 *   selected. Without it, or without a highlighted trip, [onPhotosClick] is invoked instead.
 * @param onFriendsClick invoked when the Friends navigation destination is selected.
 * @param onProfileClick invoked when the user selects their profile avatar.
 * @param onHistoryClick invoked when the user opens Trip History.
 * @param clock the clock used for time-dependent presentation logic.
 * @param refreshRequested whether a completed creation requires fresh trip data.
 * @param onRefreshConsumed clears the one-time creation result after starting the refresh.
 * @param onOpenTrip opens the selected trip's details.
 */
@Composable
fun TripsRoute(
    tripRepository: TripRepository,
    userRepository: UserRepository,
    @DrawableRes mainTripImageRes: Int? = null,
    @DrawableRes nextActivityImageRes: Int? = null,
    onInviteClick: () -> Unit = rememberNotImplementedAction(),
    onNewTripClick: () -> Unit = rememberNotImplementedAction(),
    onTripsClick: () -> Unit = {},
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    onOpenTripPhotos: ((String, String) -> Unit)? = null,
    onProfileClick: () -> Unit = rememberNotImplementedAction(),
    clock: Clock = Clock.systemDefaultZone(),
    refreshRequested: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    onHistoryClick: () -> Unit = rememberNotImplementedAction(),
    onOpenTrip: (String) -> Unit = {},
) {
  val viewModel: TripsViewModel =
      viewModel(
          factory =
              TripsViewModel.factory(
                  tripRepository = tripRepository,
                  userRepository = userRepository,
                  clock = clock,
              )
      )

  LaunchedEffect(refreshRequested) {
    if (refreshRequested) {
      viewModel.refresh()
      onRefreshConsumed()
    }
  }
  val uiState by viewModel.uiState.collectAsState()

  TripsScreen(
      uiState = uiState,
      mainTripImageRes = mainTripImageRes,
      nextActivityImageRes = nextActivityImageRes,
      onInviteClick = onInviteClick,
      onNewTripClick = onNewTripClick,
      onTripsClick = onTripsClick,
      onMapClick = onMapClick,
      onPhotosClick = onPhotosClick,
      onFriendsClick = onFriendsClick,
      onOpenTripPhotos = onOpenTripPhotos,
      onProfileClick = onProfileClick,
      onHistoryClick = onHistoryClick,
      onOpenTrip = onOpenTrip,
  )
}

/**
 * Renders the Trips Home screen from presentation-ready state.
 *
 * The composable performs no repository access or trip-selection logic. Navigation and user actions
 * are exposed through callbacks so they can be handled outside the screen.
 *
 * @param uiState the state rendered by the screen.
 * @param mainTripImageRes the optional image resource displayed for the highlighted trip.
 * @param nextActivityImageRes the optional image resource displayed for the next activity.
 * @param onInviteClick invoked when the user selects the invite action.
 * @param onNewTripClick invoked when the user selects the new-trip action.
 * @param onTripsClick invoked when the Trips navigation destination is selected.
 * @param onMapClick invoked when the Map navigation destination is selected.
 * @param onPhotosClick invoked when the Photos navigation destination is selected and there is no
 *   highlighted trip to show the photos of.
 * @param onOpenTripPhotos opens the photos of the highlighted trip, when the Photos destination is
 *   selected. Without it, or without a highlighted trip, [onPhotosClick] is invoked instead.
 * @param onFriendsClick invoked when the Friends navigation destination is selected.
 * @param onProfileClick invoked when the user selects their profile avatar.
 * @param onHistoryClick invoked when the user opens Trip History.
 * @param onOpenTrip opens the selected trip's details.
 */
@Composable
fun TripsScreen(
    uiState: TripsUiState,
    @DrawableRes mainTripImageRes: Int? = null,
    @DrawableRes nextActivityImageRes: Int? = null,
    onInviteClick: () -> Unit = rememberNotImplementedAction(),
    onNewTripClick: () -> Unit = rememberNotImplementedAction(),
    onTripsClick: () -> Unit = {},
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    onOpenTripPhotos: ((String, String) -> Unit)? = null,
    onProfileClick: () -> Unit = rememberNotImplementedAction(),
    onHistoryClick: () -> Unit = rememberNotImplementedAction(),
    onOpenTrip: (String) -> Unit = {},
) {
  val mainTrip = uiState.mainTrip
  val photosClick: () -> Unit =
      if (onOpenTripPhotos != null && mainTrip != null) {
        { onOpenTripPhotos(mainTrip.id, mainTrip.name) }
      } else {
        onPhotosClick
      }
  Scaffold(
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.trips_screen },
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onPhotosClick = photosClick,
            onFriendsClick = onFriendsClick,
        )
      },
  ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        TripsHeader(
            greeting = uiState.greeting,
            userName = uiState.currentUserName,
            userInitial = uiState.currentUserInitial,
            onProfileClick = onProfileClick,
        )
      }

      if (uiState.isLoading) {
        item {
          Box(
              modifier = Modifier.fillMaxWidth().height(160.dp),
              contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      } else {
        uiState.errorMessage?.let { errorMessage ->
          item {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }

        if (uiState.errorMessage == null) {
          uiState.mainTrip?.let { trip ->
            item {
              MainTripCard(
                  trip = trip,
                  imageRes = mainTripImageRes,
                  onInviteClick = onInviteClick,
                  onOpenTrip = { onOpenTrip(trip.id) },
              )
            }
          }

          uiState.nextActivity?.let { activity ->
            item {
              SectionTitle(stringResource(R.string.trips_next_up))

              Spacer(modifier = Modifier.height(12.dp))

              NextActivityCard(
                  activity = activity,
                  imageRes = nextActivityImageRes,
              )
            }
          }
        }

        item {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            if (uiState.errorMessage == null) {
              SectionTitle(stringResource(R.string.trips_other_trips))
            }

            HistoryButton(onClick = onHistoryClick)
          }
        }
        if (uiState.errorMessage == null) {
          items(
              items = uiState.otherTrips,
              key = { trip -> trip.id },
          ) { trip ->
            OtherTripCard(
                trip,
                onClick = { onOpenTrip(trip.id) },
            )
          }
        }
      }

      item {
        Button(
            onClick = onNewTripClick,
            modifier =
                Modifier.fillMaxWidth().height(58.dp).markNotImplemented(onNewTripClick).semantics {
                  testTag = C.Tag.trips_new_trip_button
                },
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
          Icon(
              imageVector = Icons.Default.Add,
              contentDescription = null,
          )

          Spacer(modifier = Modifier.width(8.dp))

          Text(
              text = stringResource(R.string.trips_new_trip),
              style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }
  }
}
