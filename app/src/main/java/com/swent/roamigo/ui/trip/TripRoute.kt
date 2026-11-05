// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.trip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.RoamigoApplication
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.connectivity.DefaultConnectivityRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.isCompletedOn
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.map.LocationViewModel
import com.swent.roamigo.ui.map.TripMapContent
import com.swent.roamigo.ui.map.TripMapScreen
import com.swent.roamigo.ui.map.TripMapUiState
import com.swent.roamigo.ui.map.TripMapViewModel
import com.swent.roamigo.ui.photos.PhotosRoute
import com.swent.roamigo.ui.photos.PhotosViewModel
import com.swent.roamigo.ui.trip.details.TripDetailsRoute
import com.swent.roamigo.ui.trip.voting.VoteCloseDialog
import com.swent.roamigo.ui.trip.voting.VoteDialog
import com.swent.roamigo.ui.trip.voting.VoteViewModel
import com.swent.roamigo.ui.trip.voting.VotesScreen
import com.swent.roamigo.ui.trip.voting.VotesUiState
import com.swent.roamigo.ui.trip.voting.VotesViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Hosts the tabs and shared UI state for a single trip. */
@Composable
internal fun TripRoute(
    tripId: String,
    fromHistory: Boolean,
    openMap: Boolean,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    userRepository: UserRepository,
    voteRepository: VoteRepository,
    photoRepository: PhotoRepository,
    connectivityRepository: ConnectivityRepository?,
    locationRepository: LocationRepository?,
    onBack: () -> Unit,
    onAuthenticationRequired: () -> Unit,
    onOpenVote: (String, Boolean) -> Unit,
    onCreateVote: () -> Unit,
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onFriendsClick: () -> Unit,
) {
  val context = LocalContext.current
  var tab by rememberSaveable {
    mutableStateOf(if (openMap) TripSection.Map else TripSection.Detail)
  }
  val notImplemented = rememberNotImplementedAction()
  val userId = authRepository.getCurrentUser()?.uid
  if (userId == null) {
    LaunchedEffect(Unit) { onAuthenticationRequired() }
    return
  }
  val tripMapFactory =
      remember(tripId, tripRepository, userRepository) {
        TripMapViewModel.factory(tripId, tripRepository, userRepository)
      }
  val tripModel: TripMapViewModel = viewModel(key = "trip_map_$tripId", factory = tripMapFactory)
  val tripState by tripModel.uiState.collectAsState()
  val loaded = tripState as? TripMapUiState.Loaded
  val zone = ZoneId.systemDefault()
  // Keep trip actions read-only until the trip has loaded and its end date is known.
  val readOnly = fromHistory || loaded?.trip?.isCompletedOn(LocalDate.now(zone), zone) != false
  var hasOpenedMap by rememberSaveable { mutableStateOf(false) }
  if (tab == TripSection.Map && !readOnly) SideEffect { hasOpenedMap = true }
  val locationFactory =
      if (!readOnly && (tab == TripSection.Map || hasOpenedMap)) {
        val location =
            locationRepository
                ?: (context.applicationContext as RoamigoApplication).locationRepository
        val connectivity =
            connectivityRepository ?: remember(context) { DefaultConnectivityRepository(context) }
        remember(tripId, userId, location, connectivity) {
          LocationViewModel.factory(tripId, userId, location, connectivity)
        }
      } else null
  val locationModel: LocationViewModel? = locationFactory?.let {
    viewModel(key = "trip_map_location_$tripId", factory = it)
  }
  val locationState = locationModel?.uiState?.collectAsState()?.value
  val photosCount by
      remember(tripId, tab) {
            if (tab == TripSection.Photos) {
              photoRepository.tripPhotos(tripId).map { it.size as Int? }.catch { emit(null) }
            } else kotlinx.coroutines.flow.flowOf(null)
          }
          .collectAsState(initial = null)
  val tabState = rememberSaveableStateHolder()
  BackHandler(onBack = onBack)
  Column(Modifier.fillMaxSize().safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.trip_details_back),
        )
      }
      Column(Modifier.weight(1f)) {
        val trip = loaded?.trip
        if (trip != null) {
          Text(trip.name, style = MaterialTheme.typography.headlineMedium)
          val locale = LocalConfiguration.current.locales[0]
          val formatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
          val start = trip.startDate.toDate().toInstant().atZone(zone).toLocalDate()
          val end = trip.endDate.toDate().toInstant().atZone(zone).toLocalDate()
          val subtitle =
              when (tab) {
                TripSection.Detail -> "${formatter.format(start)} – ${formatter.format(end)}"
                TripSection.Map -> formatter.format(LocalDate.now(zone))
                TripSection.Votes -> stringResource(R.string.votes_title)
                TripSection.Photos ->
                    photosCount?.let { count ->
                      val memberCount = trip.memberIds.distinct().size
                      stringResource(
                          R.string.trip_photo_summary,
                          pluralStringResource(R.plurals.trip_photo_count, count, count),
                          pluralStringResource(
                              R.plurals.trip_person_count,
                              memberCount,
                              memberCount,
                          ),
                      )
                    } ?: stringResource(R.string.photos_title)
              }
          Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
      }
      val share = rememberNotImplementedAction()
      if (tab == TripSection.Detail) {
        IconButton(onClick = share, modifier = Modifier.markNotImplemented(share)) {
          Icon(
              painter = painterResource(R.drawable.ic_trip_details_share),
              contentDescription = stringResource(R.string.trip_details_share),
          )
        }
      }
      if (tab != TripSection.Map && locationModel != null && locationState?.isSharing == true) {
        TextButton(onClick = locationModel::stopSharing) {
          Text(stringResource(R.string.trip_map_pause_sharing))
        }
      }
    }
    SecondaryTabRow(selectedTabIndex = tab.ordinal) {
      TripSection.entries.forEach { section ->
        Tab(
            selected = section == tab,
            onClick = {
              if (authRepository.getCurrentUser() == null) onAuthenticationRequired()
              else tab = section
            },
            text = { Text(stringResource(section.label)) },
        )
      }
    }
    Box(Modifier.weight(1f)) {
      tabState.SaveableStateProvider(tab.name) {
        when (tab) {
          TripSection.Detail ->
              TripDetailsRoute(
                  tripId = tripId,
                  tripRepository = tripRepository,
                  onBack = onBack,
                  onVotesClick = { tab = TripSection.Votes },
                  onTripMapClick = {
                    if (authRepository.getCurrentUser() == null) onAuthenticationRequired()
                    else tab = TripSection.Map
                  },
                  onPhotosClick = { tab = TripSection.Photos },
                  onMembersClick = notImplemented,
              )
          TripSection.Map -> {
            if (readOnly) {
              TripMapContent(
                  uiState = tripState,
                  isSharing = false,
                  onRetry = tripModel::retry,
                  onTurnOnSharing = {},
                  onPauseSharing = {},
                  readOnly = true,
              )
            } else {
              TripMapScreen(
                  tripId = tripId,
                  tripMapViewModelFactory = tripMapFactory,
                  locationViewModelFactory = requireNotNull(locationFactory),
              )
            }
          }
          TripSection.Votes ->
              TripVotes(
                  tripId,
                  loaded?.trip?.name.orEmpty(),
                  authRepository,
                  readOnly,
                  voteRepository,
                  onAuthenticationRequired,
                  onOpenVote,
                  onCreateVote,
              )
          TripSection.Photos -> {
            val connectivity =
                connectivityRepository
                    ?: remember(context) { DefaultConnectivityRepository(context) }
            PhotosRoute(
                viewModel =
                    viewModel {
                      PhotosViewModel(
                          photoRepository,
                          connectivity,
                          tripId,
                          userId,
                          createSavedStateHandle(),
                      )
                    },
                tripName = loaded?.trip?.name.orEmpty(),
                fileOf = photoRepository::fileOf,
                readOnly = readOnly,
            )
          }
        }
      }
    }
    // Global destinations leave the current trip.
    AppBottomBar(
        onTripsClick = onTripsClick,
        onMapClick = onMapClick,
        onFriendsClick = onFriendsClick,
        tripsSelected = false,
    )
  }
}

private enum class TripSection(val label: Int) {
  Detail(R.string.trip_details_tab),
  Map(R.string.trips_nav_map),
  Votes(R.string.trip_details_votes),
  Photos(R.string.trips_nav_photos),
}

@Composable
private fun TripVotes(
    tripId: String,
    tripName: String,
    auth: AuthRepository,
    readOnly: Boolean,
    repository: VoteRepository,
    onAuthenticationRequired: () -> Unit,
    onOpenVote: (String, Boolean) -> Unit,
    onCreateVote: () -> Unit,
) {
  var selectedVoteId by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmClose by rememberSaveable { mutableStateOf(false) }
  val userId = auth.getCurrentUser()?.uid
  val model = viewModel { VotesViewModel(repository, tripId, userId.orEmpty()) }
  VotesScreen(
      viewModel = model,
      tripName = tripName,
      onOpenVote = onOpenVote@{ voteId ->
            if (auth.getCurrentUser() == null) {
              onAuthenticationRequired()
              return@onOpenVote
            }
            confirmClose = false
            val ready = model.uiState.value as? VotesUiState.Ready
            if (!readOnly && ready?.liveVotes?.any { it.uid == voteId } == true) {
              selectedVoteId = voteId
            } else {
              onOpenVote(voteId, readOnly)
            }
          },
      onCloseVote =
          if (readOnly) null
          else
              { voteId ->
                confirmClose = true
                selectedVoteId = voteId
              },
      onCreateVote = { if (!readOnly) onCreateVote() },
      readOnly = readOnly,
  )
  val voteId = selectedVoteId
  if (voteId != null && userId != null) {
    if (confirmClose) {
      VoteCloseDialog(
          voteId,
          { VoteViewModel(repository, tripId, it, userId) },
          onDismiss = { selectedVoteId = null },
      )
    } else {
      VoteDialog(
          voteId,
          { VoteViewModel(repository, tripId, it, userId) },
          onDismiss = { selectedVoteId = null },
      )
    }
  }
}
