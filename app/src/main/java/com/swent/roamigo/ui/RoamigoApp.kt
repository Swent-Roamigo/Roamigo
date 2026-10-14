// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
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
import com.swent.roamigo.ui.trip.creation.DestinationScreen
import com.swent.roamigo.ui.trips.TripsRoute

/**
 * The app's navigation graph: permissions, authentication, then trips, profile, and trip creation.
 *
 * Trip and user repositories default to in-memory placeholders until real data sources are wired
 * in.
 */
@Composable
fun RoamigoApp(
    tripRepository: TripRepository = remember { FakeTripRepository() },
    userRepository: UserRepository = remember { FakeUserRepository(User("alex", "Alex", null)) },
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = remember { AuthRepositoryFirebase() },
    credentialManager: CredentialManager? = null,
) {
  NavHost(navController = navController, startDestination = Route.PERMISSIONS) {
    composable(Route.PERMISSIONS) {
      PermissionsScreen(
          onContinue = {
            navController.navigate(Route.AUTHENTICATION) {
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
            navController.navigate(Route.TRIPS) {
              popUpTo(Route.AUTHENTICATION) { inclusive = true }
              launchSingleTop = true
            }
          },
      )
    }
    composable(Route.TRIPS) {
      TripsRoute(
          tripRepository = tripRepository,
          userRepository = userRepository,
          onNewTripClick = { navController.navigate(Route.NEW_TRIP) },
          onProfileClick = { navController.navigate(Route.PROFILE) },
      )
    }
    composable(Route.PROFILE) {
      val state by viewModel<ProfileViewModel>().uiState.collectAsState()
      ProfileScreen(
          state,
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
      )
    }
    composable(Route.NEW_TRIP) {
      DestinationScreen(
          createTripViewModel = viewModel<CreateTripViewModel>(),
          onBack = { navController.popBackStack() },
          onClose = { navController.popBackStack() },
          // The next step of the flow does not exist yet.
          onContinue = {},
      )
    }
  }
}
