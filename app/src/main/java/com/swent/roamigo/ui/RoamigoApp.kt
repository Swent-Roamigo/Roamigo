// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileViewModel
import com.swent.roamigo.ui.trip.creation.CreateTripViewModel
import com.swent.roamigo.ui.trip.creation.DestinationScreen
import com.swent.roamigo.ui.trips.TripsRoute

/**
 * The app's navigation graph: permissions, then the trips home, which opens the profile (through
 * the avatar) and the create-trip flow.
 *
 * The repositories default to in-memory placeholders until the real data sources are wired in.
 */
@Composable
fun RoamigoApp(
    tripRepository: TripRepository = remember { FakeTripRepository() },
    userRepository: UserRepository = remember { FakeUserRepository(User("alex", "Alex", null)) },
    navController: NavHostController = rememberNavController(),
) {
  NavHost(navController = navController, startDestination = Route.PERMISSIONS) {
    composable(Route.PERMISSIONS) {
      PermissionsScreen(
          onContinue = {
            // The explainer is shown once, so Back from the trips home must leave the app.
            navController.navigate(Route.TRIPS) { popUpTo(Route.PERMISSIONS) { inclusive = true } }
          }
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
      ProfileScreen(state)
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
