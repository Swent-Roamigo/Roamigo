// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.destination.DestinationRepository
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
import com.swent.roamigo.ui.map.TripOverviewMapRoute
import com.swent.roamigo.ui.map.TripOverviewMapViewModel
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileViewModel
import com.swent.roamigo.ui.trip.creation.CreateTripViewModel
import com.swent.roamigo.ui.trip.creation.DatesTravelersScreen
import com.swent.roamigo.ui.trip.creation.DestinationScreen
import com.swent.roamigo.ui.trip.creation.TripReadyScreen
import com.swent.roamigo.ui.trip.creation.TripReadyViewModel
import com.swent.roamigo.ui.trip.details.TripDetailsRoute
import com.swent.roamigo.ui.trip.details.TripDetailsViewModel
import com.swent.roamigo.ui.trip.history.TripHistoryRoute
import com.swent.roamigo.ui.trip.voting.CreateVoteScreen
import com.swent.roamigo.ui.trip.voting.CreateVoteViewModel
import com.swent.roamigo.ui.trip.voting.VoteScreen
import com.swent.roamigo.ui.trip.voting.VoteViewModel
import com.swent.roamigo.ui.trip.voting.VotesScreen
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
) {
  val context = LocalContext.current
  val manager = credentialManager ?: remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()
  val onMapClick: () -> Unit = {
    navController.navigate(Route.TRIP_OVERVIEW_MAP) { launchSingleTop = true }
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
          onNewTripClick = { navController.navigate(Route.NEW_TRIP) },
          onProfileClick = { navController.navigate(Route.PROFILE) },
          onFriendsClick = { navController.navigate(Route.FRIENDS) { launchSingleTop = true } },
          onHistoryClick = {
            navController.navigate(Route.TRIP_HISTORY) { launchSingleTop = true }
          },
          onOpenTrip = { tripId -> navController.navigate(Route.tripDetails(tripId)) },
      )
    }
    composable(Route.VOTES) { entry ->
      val tripId = requireNotNull(entry.arguments?.getString("tripId"))
      val tripName = entry.arguments?.getString("tripName").orEmpty()
      VotesScreen(
          viewModel = viewModel { VotesViewModel(voteRepository, tripId) },
          tripName = tripName,
          onOpenVote = { voteId -> navController.navigate(Route.vote(tripId, voteId)) },
          onCreateVote = { navController.navigate(Route.newVote(tripId)) },
          onOverviewClick = {
            navController.popBackStack(
                Route.tripDetails(tripId),
                inclusive = false,
            )
          },
          onMapClick = onMapClick,
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onFriendsClick = { navController.navigate(Route.FRIENDS) { launchSingleTop = true } },
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
          onPhotosClick = rememberNotImplementedAction(),
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
          onFriendsClick = { navController.navigate(Route.FRIENDS) { launchSingleTop = true } },
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
          onFriendsClick = { navController.navigate(Route.FRIENDS) { launchSingleTop = true } },
      )
    }

    composable(Route.TRIP_DETAILS) { entry ->
      val tripId = requireNotNull(entry.arguments?.getString("tripId"))
      // Share the route's keyed ViewModel so Votes receives the loaded trip name.
      val detailsViewModel =
          viewModel(key = tripId) { TripDetailsViewModel(tripId, tripRepository) }
      TripDetailsRoute(
          tripId = tripId,
          tripRepository = tripRepository,
          onBack = { navController.popBackStack() },
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onShareClick = rememberNotImplementedAction(),
          onInviteClick = rememberNotImplementedAction(),
          onItineraryClick = rememberNotImplementedAction(),
          onVotesClick = {
            val tripName = detailsViewModel.uiState.value.details?.name.orEmpty()
            navController.navigate(Route.votes(tripId, tripName))
          },
          onMapClick = onMapClick,
          onPhotosClick = rememberNotImplementedAction(),
      )
    }

    composable(Route.TRIP_READY) { entry ->
      val id = requireNotNull(entry.arguments?.getString("tripId"))
      val readyViewModel = viewModel { TripReadyViewModel(tripRepository, id) }
      val photo by
          entry.savedStateHandle.getStateFlow<Bundle?>("ready_photo", null).collectAsState()
      val onInviteClick = rememberNotImplementedAction()
      val onSuggestClick = rememberNotImplementedAction()
      val onShareLocationClick = rememberNotImplementedAction()
      val onOpenItineraryClick = rememberNotImplementedAction()
      val onShareTripClick = rememberNotImplementedAction()
      TripReadyScreen(
          viewModel = readyViewModel,
          photo = photo,
          onInviteClick = onInviteClick,
          onSuggestClick = onSuggestClick,
          onShareLocationClick = onShareLocationClick,
          onOpenItineraryClick = onOpenItineraryClick,
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
          onFriendsClick = { navController.navigate(Route.FRIENDS) { launchSingleTop = true } },
      )
    }
  }
}
