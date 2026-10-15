// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteRepositoryFirestore
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.authentication.AuthenticationScreen
import com.swent.roamigo.ui.authentication.AuthenticationViewModel
import com.swent.roamigo.ui.friends.FriendsScreen
import com.swent.roamigo.ui.friends.FriendsUiState
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileViewModel
import com.swent.roamigo.ui.trip.creation.CreateTripViewModel
import com.swent.roamigo.ui.trip.creation.DatesTravelersScreen
import com.swent.roamigo.ui.trip.creation.DestinationScreen
import com.swent.roamigo.ui.trip.voting.CreateVoteScreen
import com.swent.roamigo.ui.trip.voting.CreateVoteViewModel
import com.swent.roamigo.ui.trip.voting.VoteScreen
import com.swent.roamigo.ui.trip.voting.VoteViewModel
import com.swent.roamigo.ui.trips.TripsRoute

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
    // Lazy so that opening the app does not touch Firestore before a vote screen needs it.
    voteRepository: Lazy<VoteRepository> = lazy { VoteRepositoryFirestore(Firebase.firestore) },
) {
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
      )
    }
    composable(Route.PROFILE) {
      val state by viewModel<ProfileViewModel>().uiState.collectAsState()
      ProfileScreen(
          state,
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
          onFriendsClick = { navController.navigate(Route.FRIENDS) },
      )
    }
    composable(Route.FRIENDS) {
      // There is no friends data source yet, so the screen shows its empty state.
      FriendsScreen(
          FriendsUiState(),
          onTripsClick = { navController.popBackStack(Route.TRIPS, inclusive = false) },
      )
    }
    composable(Route.NEW_TRIP) {
      val creationViewModel = viewModel { CreateTripViewModel(tripRepository) }
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
    composable(
        Route.CREATE_VOTE,
        arguments = listOf(navArgument(Route.TRIP_ID_ARG) { type = NavType.StringType }),
    ) { entry ->
      val tripId = entry.arguments?.getString(Route.TRIP_ID_ARG).orEmpty()
      val userId = authRepository.getCurrentUser()?.uid.orEmpty()
      CreateVoteScreen(
          viewModel = viewModel { CreateVoteViewModel(voteRepository.value, tripId, userId) },
          onBack = { navController.popBackStack() },
          onVoteCreated = { voteId ->
            // Back from the new vote returns to where creation started, not to the finished form.
            navController.navigate(Route.vote(tripId, voteId)) {
              popUpTo(Route.CREATE_VOTE) { inclusive = true }
            }
          },
      )
    }
    composable(
        Route.VOTE,
        arguments =
            listOf(
                navArgument(Route.TRIP_ID_ARG) { type = NavType.StringType },
                navArgument(Route.VOTE_ID_ARG) { type = NavType.StringType },
            ),
    ) { entry ->
      val tripId = entry.arguments?.getString(Route.TRIP_ID_ARG).orEmpty()
      val voteId = entry.arguments?.getString(Route.VOTE_ID_ARG).orEmpty()
      val userId = authRepository.getCurrentUser()?.uid.orEmpty()
      VoteScreen(
          viewModel = viewModel { VoteViewModel(voteRepository.value, tripId, voteId, userId) },
          onBack = { navController.popBackStack() },
      )
    }
  }
}
