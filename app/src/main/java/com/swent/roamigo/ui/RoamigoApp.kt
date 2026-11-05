// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.R
import com.swent.roamigo.RoamigoApplication
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.connectivity.DefaultConnectivityRepository
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.authentication.AuthenticationScreen
import com.swent.roamigo.ui.authentication.AuthenticationViewModel
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.friends.FriendsScreen
import com.swent.roamigo.ui.friends.FriendsUiState
import com.swent.roamigo.ui.map.LocationViewModel
import com.swent.roamigo.ui.map.TripMapScreen
import com.swent.roamigo.ui.map.TripMapUiState
import com.swent.roamigo.ui.map.TripMapViewModel
import com.swent.roamigo.ui.map.TripOverviewMapRoute
import com.swent.roamigo.ui.map.TripOverviewMapViewModel
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.photos.PhotosRoute
import com.swent.roamigo.ui.photos.PhotosViewModel
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileViewModel
import com.swent.roamigo.ui.trip.creation.CreateTripViewModel
import com.swent.roamigo.ui.trip.creation.DatesTravelersScreen
import com.swent.roamigo.ui.trip.creation.DestinationScreen
import com.swent.roamigo.ui.trip.creation.TripReadyScreen
import com.swent.roamigo.ui.trip.creation.TripReadyViewModel
import com.swent.roamigo.ui.trip.details.TripDetailsRoute
import com.swent.roamigo.ui.trip.history.TripHistoryRoute
import com.swent.roamigo.ui.trip.voting.CreateVoteScreen
import com.swent.roamigo.ui.trip.voting.CreateVoteViewModel
import com.swent.roamigo.ui.trip.voting.VoteCloseDialog
import com.swent.roamigo.ui.trip.voting.VoteDialog
import com.swent.roamigo.ui.trip.voting.VoteScreen
import com.swent.roamigo.ui.trip.voting.VoteViewModel
import com.swent.roamigo.ui.trip.voting.VotesScreen
import com.swent.roamigo.ui.trip.voting.VotesUiState
import com.swent.roamigo.ui.trip.voting.VotesViewModel
import com.swent.roamigo.ui.trips.TripsRoute
import kotlinx.coroutines.launch

/**
 * New sign-ins continue through the permissions explainer to trips; existing sessions open trips.
 *
 * Repository defaults provide placeholders for previews and tests; MainActivity supplies real trips
 * and votes.
 *
 * @param voteRepository Shared backend for trip vote discovery, creation, and individual ballots.
 * @param photoRepository Takes trip photos and keeps them until they are uploaded.
 * @param connectivityRepository Tells whether the device is online; the device's own by default.
 * @param locationRepository Shares the user's location on a trip map; the app-wide instance of
 *   [RoamigoApplication] by default, since sharing must go through a single instance.
 */
@Composable
fun RoamigoApp(
    voteRepository: VoteRepository,
    tripRepository: TripRepository = remember { FakeTripRepository() },
    userRepository: UserRepository = remember { FakeUserRepository(User("alex", "Alex", null)) },
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = remember { AuthRepositoryFirebase() },
    credentialManager: CredentialManager? = null,
    destinationRepository: DestinationRepository,
    photoRepository: PhotoRepository = remember { FakePhotoRepository() },
    connectivityRepository: ConnectivityRepository? = null,
    locationRepository: LocationRepository? = null,
) {
  val context = LocalContext.current
  val manager = credentialManager ?: remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()
  val onMapClick: () -> Unit = {
    navigateRoot(navController, Route.TRIP_OVERVIEW_MAP)
  }
  val startDestination =
      remember(authRepository) {
        if (authRepository.getCurrentUser() == null) Route.AUTHENTICATION else Route.TRIPS
      }
  NavHost(navController = navController, startDestination = startDestination) {
    composable(Route.PERMISSIONS) {
      PermissionsScreen(
          onContinue = {
            navController.navigate(Route.TRIPS) {
              popUpTo(Route.PERMISSIONS) { inclusive = true }
              launchSingleTop = true
            }
          }
      )
    }
    composable(Route.AUTHENTICATION) {
      AuthenticationScreen(
          authViewModel = viewModel { AuthenticationViewModel(authRepository) },
          credentialManager = credentialManager,
          onSignedIn = {
            // Back from trips must not return to authentication or permissions.
            navController.navigate(Route.PERMISSIONS) {
              popUpTo(Route.AUTHENTICATION) { inclusive = true }
              launchSingleTop = true
            }
          },
      )
    }
    composable(Route.TRIPS) { entry ->
      val refreshRequested by
          entry.savedStateHandle.getStateFlow("trip_created", false).collectAsState()
      TripsRoute(
          refreshRequested = refreshRequested,
          onRefreshConsumed = { entry.savedStateHandle["trip_created"] = false },
          onMapClick = onMapClick,
          tripRepository = tripRepository,
          userRepository = userRepository,
          destinationRepository = destinationRepository,
          authRepository = authRepository,
          onNewTripClick = { navController.navigate(Route.NEW_TRIP) },
          onProfileClick = { navController.navigate(Route.PROFILE) },
          onFriendsClick = { navigateRoot(navController, Route.FRIENDS) },
          onHistoryClick = {
            navController.navigate(Route.TRIP_HISTORY) { launchSingleTop = true }
          },
          onOpenTrip = { tripId -> navController.navigate(Route.tripDetails(tripId)) },
      )
    }
    composable(Route.VOTE) { entry ->
      val tripId = requireNotNull(entry.arguments?.getString("tripId"))
      val voteId = requireNotNull(entry.arguments?.getString("voteId"))
      val userId = authRepository.getCurrentUser()?.uid
      if (userId == null) {
        LaunchedEffect(Unit) {
          navController.navigate(Route.AUTHENTICATION) { popUpTo(navController.graph.id) }
        }
      } else {
        VoteScreen(
            viewModel = viewModel { VoteViewModel(voteRepository, tripId, voteId, userId) },
            onBack = { navController.popBackStack() },
            readOnly = entry.arguments?.getString("readOnly") == "true",
        )
      }
    }
    composable(Route.NEW_VOTE) { entry ->
      val tripId = requireNotNull(entry.arguments?.getString("tripId"))
      val userId = authRepository.getCurrentUser()?.uid
      if (userId == null) {
        LaunchedEffect(Unit) {
          navController.navigate(Route.AUTHENTICATION) { popUpTo(navController.graph.id) }
        }
      } else {
        val creationViewModel = viewModel { CreateVoteViewModel(voteRepository, tripId, userId) }
        val state by creationViewModel.uiState.collectAsState()
        BackHandler(enabled = state.isSubmitting) { /* Wait for the pending write. */ }
        CreateVoteScreen(
            viewModel = creationViewModel,
            onBack = { navController.popBackStack() },
            onVoteCreated = { navController.popBackStack() },
        )
      }
    }
    composable(Route.FRIENDS) {
      FriendsScreen(
          uiState = FriendsUiState(),
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onMapClick = onMapClick,
      )
    }
    composable(Route.PROFILE) {
      val profileViewModel = viewModel { ProfileViewModel(authRepository) }
      val state by profileViewModel.uiState.collectAsState()
      LaunchedEffect(state.signedOut) {
        if (state.signedOut) {
          scope.launch {
            try {
              manager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: ClearCredentialException) {
              // The repository session is already gone, so still return to authentication.
              Log.w("RoamigoApp", "Could not clear credential state", e)
            }
            navController.navigate(Route.AUTHENTICATION) {
              popUpTo(navController.graph.id)
              launchSingleTop = true
            }
          }
        }
      }
      ProfileScreen(
          state,
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onSignOutClick = profileViewModel::signOut,
          onMapClick = onMapClick,
          onFriendsClick = { navigateRoot(navController, Route.FRIENDS) },
      )
    }

    composable(Route.TRIP_HISTORY) {
      TripHistoryRoute(
          tripRepository = tripRepository,
          onBackClick = { navController.popBackStack() },
          onMapClick = onMapClick,
          onTripsClick = {
            navController.popBackStack(
                Route.TRIPS,
                inclusive = false,
            )
          },
          onOpenTrip = { tripId ->
            navController.navigate(Route.tripDetails(tripId, readOnly = true))
          },
          onFriendsClick = { navigateRoot(navController, Route.FRIENDS) },
      )
    }

    composable(Route.TRIP_DETAILS) { entry ->
      val tripId = requireNotNull(entry.arguments?.getString("tripId"))
      val readOnly = entry.arguments?.getString("readOnly") == "true"
      var tab by rememberSaveable {
        mutableStateOf(
            if (entry.arguments?.getString("initialTab") == "map") TripSection.Map
            else TripSection.Detail
        )
      }
      var membersOpen by rememberSaveable { mutableStateOf(false) }
      val back: () -> Unit = {
        if (readOnly) navController.popBackStack()
        else navController.popBackStack(Route.TRIPS, false)
      }
      val tripMapFactory =
          remember(tripId, tripRepository, userRepository) {
            TripMapViewModel.factory(tripId, tripRepository, userRepository)
          }
      val tripModel: TripMapViewModel =
          viewModel(key = "trip_map_$tripId", factory = tripMapFactory)
      val tripState by tripModel.uiState.collectAsState()
      val loaded = tripState as? TripMapUiState.Loaded
      val userId = authRepository.getCurrentUser()?.uid
      if (userId == null) {
        LaunchedEffect(Unit) { redirectToAuthentication(navController) }
        return@composable
      }
      val tabState = rememberSaveableStateHolder()
      BackHandler(onBack = back)
      Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          TextButton(onClick = back) { Text(stringResource(R.string.trip_details_back)) }
          TextButton(onClick = { membersOpen = true }) {
            Icon(Icons.Outlined.People, contentDescription = null)
            Text(stringResource(R.string.trip_details_members))
          }
        }
        SecondaryTabRow(selectedTabIndex = tab.ordinal) {
          TripSection.entries.forEach { section ->
            Tab(
                selected = section == tab,
                onClick = {
                  if (authRepository.getCurrentUser() == null)
                      redirectToAuthentication(navController)
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
                      onBack = back,
                      onVotesClick = { tab = TripSection.Votes },
                      onTripMapClick = {
                        if (authRepository.getCurrentUser() == null)
                            redirectToAuthentication(navController)
                        else tab = TripSection.Map
                      },
                      onPhotosClick = { tab = TripSection.Photos },
                      onMembersClick = { membersOpen = true },
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
                  val location =
                      locationRepository
                          ?: (context.applicationContext as RoamigoApplication).locationRepository
                  val connectivity =
                      connectivityRepository
                          ?: remember(context) {
                            DefaultConnectivityRepository(context)
                          }
                  TripMapScreen(
                      tripId = tripId,
                      tripMapViewModelFactory = tripMapFactory,
                      locationViewModelFactory =
                          LocationViewModel.factory(tripId, userId, location, connectivity),
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
                      navController,
                  )
              TripSection.Photos -> {
                val connectivity =
                    connectivityRepository
                        ?: remember(context) {
                          DefaultConnectivityRepository(context)
                        }
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
                    onTripsClick = back,
                    readOnly = readOnly,
                )
              }
            }
          }
        }
      }
      if (membersOpen) {
        AlertDialog(
            onDismissRequest = { membersOpen = false },
            title = { Text(stringResource(R.string.trip_details_members)) },
            text = {
              when (tripState) {
                TripMapUiState.Loading -> CircularProgressIndicator()
                is TripMapUiState.Error ->
                    Column {
                      Text(stringResource(R.string.trip_details_load_failed))
                      TextButton(onClick = tripModel::retry) {
                        Text(stringResource(R.string.trip_details_retry))
                      }
                    }
                is TripMapUiState.Loaded ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                      items(loaded?.members?.values.orEmpty().toList()) { member ->
                        Text(member.displayName)
                      }
                    }
              }
            },
            confirmButton = {
              TextButton(onClick = { membersOpen = false }) {
                Text(stringResource(R.string.trip_details_back))
              }
            },
        )
      }
    }

    composable(Route.TRIP_READY) { entry ->
      val id = requireNotNull(entry.arguments?.getString("tripId"))
      val readyViewModel = viewModel { TripReadyViewModel(tripRepository, id) }
      val photo by
          entry.savedStateHandle.getStateFlow<Bundle?>("ready_photo", null).collectAsState()
      val onInviteClick = rememberNotImplementedAction()
      val onSuggestClick = rememberNotImplementedAction()
      fun openTrip(openMap: Boolean) {
        navController.navigate(Route.tripDetails(id, openMap = openMap)) {
          popUpTo(Route.TRIP_READY) { inclusive = true }
          launchSingleTop = true
        }
      }
      val onShareTripClick = rememberNotImplementedAction()
      TripReadyScreen(
          viewModel = readyViewModel,
          photo = photo,
          onInviteClick = onInviteClick,
          onSuggestClick = onSuggestClick,
          onShareLocationClick = { openTrip(openMap = true) },
          onOpenItineraryClick = { openTrip(openMap = false) },
          onShareTripClick = onShareTripClick,
      )
    }

    composable(Route.NEW_TRIP) { entry ->
      val creationViewModel = viewModel {
        CreateTripViewModel(
            repository = tripRepository,
            destinationRepository = destinationRepository,
            savedStateHandle = entry.savedStateHandle,
        )
      }
      val state by creationViewModel.uiState.collectAsState()
      var datesStep by rememberSaveable { mutableStateOf(false) }
      val back: () -> Unit = {
        if (!state.isSubmitting) {
          if (datesStep) datesStep = false else navController.popBackStack()
        }
      }
      BackHandler(onBack = back)
      LaunchedEffect(state.createdTripId) {
        if (state.createdTripId != null) {
          navController.getBackStackEntry(Route.TRIPS).savedStateHandle["trip_created"] = true
          val first = state.selectedDestinations.firstOrNull()
          val photo = first?.let { state.photosByDestinationId[it.stableId] }
          navController.navigate(Route.tripReady(state.createdTripId!!)) {
            popUpTo(Route.NEW_TRIP) { inclusive = true }
            launchSingleTop = true
          }
          if (photo != null) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(
                    "ready_photo",
                    Bundle().apply {
                      putString("url", photo.imageUrl)
                      putString("creator", photo.creator)
                      putString("source", photo.sourceUrl)
                      putString("license", photo.license)
                      putString("licenseUrl", photo.licenseUrl)
                      putString("title", photo.title)
                      putString("credit", photo.credit)
                    },
                )
          }
        }
      }
      if (datesStep) {
        DatesTravelersScreen(
            createTripViewModel = creationViewModel,
            onBack = back,
            onClose = { if (!state.isSubmitting) navController.popBackStack() },
            onCreateTrip = creationViewModel::createTrip,
            onSignIn = {
              navController.navigate(Route.AUTHENTICATION) {
                popUpTo(Route.TRIPS) { inclusive = true }
                launchSingleTop = true
              }
            },
        )
      } else {
        DestinationScreen(
            createTripViewModel = creationViewModel,
            onBack = back,
            onClose = { navController.popBackStack() },
            onContinue = { datesStep = true },
        )
      }
    }

    composable(Route.TRIP_OVERVIEW_MAP) {
      TripOverviewMapRoute(
          viewModel = viewModel { TripOverviewMapViewModel(tripRepository) },
          onMarkerClick = { tripId -> navController.navigate(Route.tripDetails(tripId)) },
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onMapClick = {},
          onFriendsClick = { navigateRoot(navController, Route.FRIENDS) },
      )
    }
  }
}

private enum class TripSection(val label: Int) {
  Detail(R.string.trip_details_tab),
  Map(R.string.trips_nav_map),
  Votes(R.string.trip_details_votes),
  Photos(R.string.trips_nav_photos),
}

private fun redirectToAuthentication(navController: NavHostController) {
  navController.navigate(Route.AUTHENTICATION) {
    popUpTo(navController.graph.id)
    launchSingleTop = true
  }
}

private fun navigateRoot(navController: NavHostController, route: String) {
  navController.navigate(route) {
    popUpTo(Route.TRIPS)
    launchSingleTop = true
  }
}

@Composable
private fun TripVotes(
    tripId: String,
    tripName: String,
    auth: AuthRepository,
    readOnly: Boolean,
    repository: VoteRepository,
    navController: NavHostController,
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
              redirectToAuthentication(navController)
              return@onOpenVote
            }
            confirmClose = false
            val ready = model.uiState.value as? VotesUiState.Ready
            if (!readOnly && ready?.liveVotes?.any { it.uid == voteId } == true) {
              selectedVoteId = voteId
            } else {
              navController.navigate(Route.vote(tripId, voteId, readOnly))
            }
          },
      onCloseVote =
          if (readOnly) null
          else
              { voteId ->
                confirmClose = true
                selectedVoteId = voteId
              },
      onCreateVote = { if (!readOnly) navController.navigate(Route.newVote(tripId)) },
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
