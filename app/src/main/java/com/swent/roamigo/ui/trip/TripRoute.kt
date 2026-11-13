// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.trip

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.RoamigoApplication
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.connectivity.DefaultConnectivityRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.isCompletedOn
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.map.LocationViewModel
import com.swent.roamigo.ui.map.TripMapRoute
import com.swent.roamigo.ui.map.TripMapScreen
import com.swent.roamigo.ui.map.TripMapUiState
import com.swent.roamigo.ui.map.TripMapViewModel
import com.swent.roamigo.ui.photos.PhotosRoute
import com.swent.roamigo.ui.trip.components.AddToTripSheet
import com.swent.roamigo.ui.trip.details.TripDetailsRoute
import com.swent.roamigo.ui.trip.voting.VoteCloseDialog
import com.swent.roamigo.ui.trip.voting.VoteDialog
import com.swent.roamigo.ui.trip.voting.VoteViewModel
import com.swent.roamigo.ui.trip.voting.VotesRoute
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
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
    activityRepository: ActivityRepository,
    userRepository: UserRepository,
    voteRepository: VoteRepository,
    photoRepository: PhotoRepository,
    connectivityRepository: ConnectivityRepository?,
    locationRepository: LocationRepository?,
    onBack: () -> Unit,
    onAuthenticationRequired: () -> Unit,
    onOpenVote: (String, Boolean) -> Unit,
    onCreateVote: () -> Unit,
    onMembersClick: () -> Unit,
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onFriendsClick: () -> Unit,
) {
  val context = LocalContext.current
  var tab by rememberSaveable {
    mutableStateOf(if (openMap) TripSection.Map else TripSection.Detail)
  }
  val notImplemented = rememberNotImplementedAction()
  var showAdd by rememberSaveable { mutableStateOf(false) }
  val userId = authRepository.getCurrentUser()?.uid
  if (userId == null) {
    LaunchedEffect(Unit) { onAuthenticationRequired() }
    return
  }
  val tripMapFactory =
      remember(tripId, tripRepository, activityRepository, userRepository) {
        TripMapViewModel.factory(tripId, tripRepository, activityRepository, userRepository)
      }
  val tripModel: TripMapViewModel = viewModel(key = "trip_map_$tripId", factory = tripMapFactory)
  val tripState by tripModel.uiState.collectAsStateWithLifecycle()
  val loaded = tripState as? TripMapUiState.Loaded
  val zone = ZoneId.systemDefault()
  val today by rememberLocalDate(zone)
  // Keep trip actions read-only until the trip has loaded and its end date is known.
  val readOnly = fromHistory || loaded?.trip?.isCompletedOn(today, zone) != false
  var hasOpenedMap by rememberSaveable { mutableStateOf(false) }
  if (tab == TripSection.Map && !readOnly) SideEffect { hasOpenedMap = true }
  val locationFactory =
      if ((tab == TripSection.Map || hasOpenedMap) && (!readOnly || hasOpenedMap)) {
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
  LaunchedEffect(tripId, loaded?.trip?.endDate, readOnly) {
    if (loaded?.trip != null && readOnly) {
      if (locationModel != null) {
        locationModel.onLeaveTrip()
      } else {
        val location =
            locationRepository
                ?: (context.applicationContext as RoamigoApplication).locationRepository
        runCatching { location.stopSharing(tripId) }
            .onFailure { Log.w("TripRoute", "Could not stop sharing for completed trip", it) }
      }
    }
  }
  val locationState = locationModel?.uiState?.collectAsStateWithLifecycle()?.value
  val photosCount by
      remember(tripId, tab) {
            if (tab == TripSection.Photos) {
              photoRepository.tripPhotos(tripId).map { it.size as Int? }.catch { emit(null) }
            } else kotlinx.coroutines.flow.flowOf(null)
          }
          .collectAsStateWithLifecycle(initialValue = null)
  val tabState = rememberSaveableStateHolder()
  BackHandler(onBack = onBack)
  if (showAdd) {
    AddToTripSheet(
        onDismiss = { showAdd = false },
        onActivity = notImplemented,
        onSuggestion = notImplemented,
        onVote = onCreateVote,
    )
  }
  TripScreen(
      trip =
          loaded?.trip?.let { trip ->
            TripHeaderUiState(
                name = trip.name,
                startDate = trip.startDate.toDate().toInstant().atZone(zone).toLocalDate(),
                endDate = trip.endDate.toDate().toInstant().atZone(zone).toLocalDate(),
                memberCount = trip.memberIds.distinct().size,
                members = trip.memberIds.distinct().mapNotNull { loaded.members[it] },
            )
          },
      selectedTab = tab,
      isSharing = locationState?.isSharing == true,
      onBack = onBack,
      onTabSelected = { section ->
        if (authRepository.getCurrentUser() == null) onAuthenticationRequired() else tab = section
      },
      onPauseSharing = { locationModel?.stopSharing() },
      onMembersClick = onMembersClick,
      onAddClick = if (readOnly) null else ({ showAdd = true }),
      showEndedBanner = readOnly && loaded?.trip != null,
      onTripsClick = onTripsClick,
      onMapClick = onMapClick,
      onFriendsClick = onFriendsClick,
  ) {
    tabState.SaveableStateProvider(tab.name) {
      when (tab) {
        TripSection.Detail ->
            TripDetailsRoute(
                tripId = tripId,
                tripRepository = tripRepository,
                onBack = onBack,
                onRetryTrip = tripModel::retryTrip,
                onVotesClick = { tab = TripSection.Votes },
                onTripMapClick = {
                  if (authRepository.getCurrentUser() == null) onAuthenticationRequired()
                  else tab = TripSection.Map
                },
                onPhotosClick = { tab = TripSection.Photos },
                onMembersClick = onMembersClick,
            )
        TripSection.Map -> {
          if (readOnly) {
            TripMapScreen(
                uiState = tripState,
                isSharing = false,
                onRetry = tripModel::retryTrip,
                onRetryActivities = tripModel::retryActivities,
                onTurnOnSharing = {},
                onPauseSharing = {},
                readOnly = true,
            )
          } else {
            TripMapRoute(
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
                tripRepository,
                onAuthenticationRequired,
                onOpenVote,
                onCreateVote,
            )
        TripSection.Photos -> {
          val connectivity =
              connectivityRepository ?: remember(context) { DefaultConnectivityRepository(context) }
          PhotosRoute(
              photoRepository = photoRepository,
              connectivityRepository = connectivity,
              tripId = tripId,
              currentUserId = userId,
              tripName = loaded?.trip?.name.orEmpty(),
              summary =
                  photosCount?.let { count ->
                    val memberCount = loaded?.trip?.memberIds?.distinct()?.size ?: 0
                    stringResource(
                        R.string.trip_photo_summary,
                        pluralStringResource(R.plurals.trip_photo_count, count, count),
                        pluralStringResource(R.plurals.trip_person_count, memberCount, memberCount),
                    )
                  },
              readOnly = readOnly,
          )
        }
      }
    }
  }
}

@Composable
internal fun rememberLocalDate(zone: ZoneId) =
    produceState(initialValue = LocalDate.now(zone), key1 = zone) {
      while (true) {
        val nextDate = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        val remainingMillis = Duration.between(Instant.now(), nextDate).toMillis().coerceAtLeast(1)
        delay(remainingMillis)
        value = LocalDate.now(zone)
      }
    }

@Composable
private fun TripVotes(
    tripId: String,
    tripName: String,
    auth: AuthRepository,
    readOnly: Boolean,
    repository: VoteRepository,
    tripRepository: TripRepository,
    onAuthenticationRequired: () -> Unit,
    onOpenVote: (String, Boolean) -> Unit,
    onCreateVote: () -> Unit,
) {
  var selectedVoteId by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmClose by rememberSaveable { mutableStateOf(false) }
  val userId = auth.getCurrentUser()?.uid
  val context = LocalContext.current
  var hintSeen by remember { mutableStateOf(votesHintSeen(context)) }
  VotesRoute(
      voteRepository = repository,
      tripRepository = tripRepository,
      tripId = tripId,
      currentUserId = userId.orEmpty(),
      tripName = tripName,
      onOpenVote = { voteId ->
        if (auth.getCurrentUser() == null) onAuthenticationRequired()
        else onOpenVote(voteId, readOnly)
      },
      onOpenLiveVote = { voteId ->
        if (auth.getCurrentUser() == null) onAuthenticationRequired()
        else {
          confirmClose = false
          selectedVoteId = voteId
        }
      },
      onCloseVote =
          if (readOnly) null
          else
              { voteId ->
                if (auth.getCurrentUser() == null) onAuthenticationRequired()
                else {
                  confirmClose = true
                  selectedVoteId = voteId
                }
              },
      onCreateVote = { if (!readOnly) onCreateVote() },
      readOnly = readOnly,
      showHint = !hintSeen,
      onDismissHint = {
        hintSeen = true
        markVotesHintSeen(context)
      },
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

private const val HINTS_PREFERENCES = "roamigo_hints"
private const val VOTES_HINT_SEEN = "votes_hint_seen"

// Losing this flag only shows the hint once more, so storage failures are not worth surfacing.
private fun votesHintSeen(context: Context): Boolean = runCatching {
  context
      .getSharedPreferences(HINTS_PREFERENCES, Context.MODE_PRIVATE)
      .getBoolean(VOTES_HINT_SEEN, false)
}
    .getOrDefault(false)

private fun markVotesHintSeen(context: Context) {
  runCatching {
    context.getSharedPreferences(HINTS_PREFERENCES, Context.MODE_PRIVATE).edit {
      putBoolean(VOTES_HINT_SEEN, true)
    }
  }
}
