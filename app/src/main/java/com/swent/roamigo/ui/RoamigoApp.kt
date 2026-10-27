// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui

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
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.authentication.AuthenticationScreen
import com.swent.roamigo.ui.authentication.AuthenticationViewModel
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileViewModel
import com.swent.roamigo.ui.trip.creation.CreateTripViewModel
import com.swent.roamigo.ui.trip.creation.DatesTravelersScreen
import com.swent.roamigo.ui.trip.creation.DestinationScreen
import com.swent.roamigo.ui.trip.history.TripHistoryRoute
import com.swent.roamigo.ui.trips.TripsRoute
import kotlinx.coroutines.launch

/**
 * New sign-ins continue through the permissions explainer to trips; existing sessions open trips.
 *
 * Repository defaults provide placeholders for previews and tests; MainActivity supplies real
 * trips.
 */
@Composable
fun RoamigoApp(
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
          tripRepository = tripRepository,
          userRepository = userRepository,
          onNewTripClick = { navController.navigate(Route.NEW_TRIP) },
          onProfileClick = { navController.navigate(Route.PROFILE) },
          onHistoryClick = {
            navController.navigate(Route.TRIP_HISTORY) { launchSingleTop = true }
          },
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
      )
    }

    composable(Route.TRIP_HISTORY) {
      TripHistoryRoute(
          tripRepository = tripRepository,
          onBackClick = { navController.popBackStack() },
          onTripsClick = {
            navController.popBackStack(
                Route.TRIPS,
                inclusive = false,
            )
          },
      )
    }

    composable(Route.NEW_TRIP) {
      val creationViewModel = viewModel {
        CreateTripViewModel(
            repository = tripRepository,
            destinationRepository = destinationRepository,
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
          navController.popBackStack(Route.TRIPS, inclusive = false)
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
  }
}
