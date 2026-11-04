// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.RoamigoApplication
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.connectivity.ConnectivityRepository
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
import com.swent.roamigo.ui.authentication.AuthenticationRoute
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.friends.FriendsScreen
import com.swent.roamigo.ui.friends.FriendsUiState
import com.swent.roamigo.ui.map.TripOverviewMapRoute
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileRoute
import com.swent.roamigo.ui.trip.TripRoute
import com.swent.roamigo.ui.trip.creation.CreateTripRoute
import com.swent.roamigo.ui.trip.creation.TripReadyRoute
import com.swent.roamigo.ui.trip.history.TripHistoryRoute
import com.swent.roamigo.ui.trip.voting.CreateVoteRoute
import com.swent.roamigo.ui.trip.voting.VoteRoute
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
  val onMapClick: () -> Unit = { navigateRoot(navController, Route.TRIP_OVERVIEW_MAP) }
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
      AuthenticationRoute(
          authRepository = authRepository,
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
        VoteRoute(
            voteRepository = voteRepository,
            tripId = tripId,
            voteId = voteId,
            currentUserId = userId,
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
        CreateVoteRoute(
            voteRepository = voteRepository,
            tripId = tripId,
            currentUserId = userId,
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
      ProfileRoute(
          authRepository = authRepository,
          onSignedOut = {
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
          },
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
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
      val fromHistory = entry.arguments?.getString("readOnly") == "true"
      TripRoute(
          tripId = tripId,
          fromHistory = fromHistory,
          openMap = entry.arguments?.getString("initialTab") == "map",
          authRepository = authRepository,
          tripRepository = tripRepository,
          userRepository = userRepository,
          voteRepository = voteRepository,
          photoRepository = photoRepository,
          connectivityRepository = connectivityRepository,
          locationRepository = locationRepository,
          onBack = {
            if (fromHistory) navController.popBackStack()
            else navController.popBackStack(Route.TRIPS, inclusive = false)
          },
          onAuthenticationRequired = { redirectToAuthentication(navController) },
          onOpenVote = { voteId, readOnly ->
            navController.navigate(Route.vote(tripId, voteId, readOnly))
          },
          onCreateVote = { navController.navigate(Route.newVote(tripId)) },
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onMapClick = onMapClick,
          onFriendsClick = { navigateRoot(navController, Route.FRIENDS) },
      )
    }

    composable(Route.TRIP_READY) { entry ->
      val id = requireNotNull(entry.arguments?.getString("tripId"))
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
      TripReadyRoute(
          tripRepository = tripRepository,
          tripId = id,
          photo = photo,
          onInviteClick = onInviteClick,
          onSuggestClick = onSuggestClick,
          onShareLocationClick = { openTrip(openMap = true) },
          onOpenItineraryClick = { openTrip(openMap = false) },
          onShareTripClick = onShareTripClick,
      )
    }

    composable(Route.NEW_TRIP) { entry ->
      CreateTripRoute(
          tripRepository = tripRepository,
          destinationRepository = destinationRepository,
          savedStateHandle = entry.savedStateHandle,
          onClose = { navController.popBackStack() },
          onSignIn = {
            navController.navigate(Route.AUTHENTICATION) {
              popUpTo(Route.TRIPS) { inclusive = true }
              launchSingleTop = true
            }
          },
          onTripCreated = { tripId, photo ->
            navController.getBackStackEntry(Route.TRIPS).savedStateHandle["trip_created"] = true
            navController.navigate(Route.tripReady(tripId)) {
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
          },
      )
    }

    composable(Route.TRIP_OVERVIEW_MAP) {
      TripOverviewMapRoute(
          tripRepository = tripRepository,
          onMarkerClick = { tripId -> navController.navigate(Route.tripDetails(tripId)) },
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onMapClick = {},
          onFriendsClick = { navigateRoot(navController, Route.FRIENDS) },
      )
    }
  }
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
