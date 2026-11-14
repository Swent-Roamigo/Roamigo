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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
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
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.inviting.InvitationLinks
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import com.swent.roamigo.model.trip.inviting.UnavailableTripInvitationService
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.authentication.AuthenticationRoute
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.friends.FriendsScreen
import com.swent.roamigo.ui.friends.FriendsUiState
import com.swent.roamigo.ui.heatmap.ExploredWorldRoute
import com.swent.roamigo.ui.heatmap.VisitedCountryCounts
import com.swent.roamigo.ui.map.TripOverviewMapRoute
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.navigation.isReadOnly
import com.swent.roamigo.ui.navigation.opensMap
import com.swent.roamigo.ui.navigation.token
import com.swent.roamigo.ui.navigation.tripId
import com.swent.roamigo.ui.navigation.voteId
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileRoute
import com.swent.roamigo.ui.trip.TripRoute
import com.swent.roamigo.ui.trip.creation.CreateTripRoute
import com.swent.roamigo.ui.trip.creation.TripReadyRoute
import com.swent.roamigo.ui.trip.history.TripHistoryRoute
import com.swent.roamigo.ui.trip.inviting.InviteRoute
import com.swent.roamigo.ui.trip.inviting.JoinTripRoute
import com.swent.roamigo.ui.trip.inviting.JoinWithCodeDialog
import com.swent.roamigo.ui.trip.members.TripMembersRoute
import com.swent.roamigo.ui.trip.voting.CreateVoteRoute
import com.swent.roamigo.ui.trip.voting.VoteRoute
import com.swent.roamigo.ui.trips.TripsRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * New sign-ins continue through the permissions explainer to trips; existing sessions open trips.
 *
 * Repository defaults provide placeholders for previews and tests; MainActivity supplies real
 * trips, activities, and votes.
 *
 * @param activityRepository Provides live activity and suggestion updates for the trip map.
 * @param voteRepository Shared backend for trip vote discovery, creation, and individual ballots.
 * @param photoRepository Takes trip photos and keeps them until they are uploaded.
 * @param connectivityRepository Tells whether the device is online; the device's own by default.
 * @param invitationService Creates, previews and accepts the links that invite people to a trip.
 *   Every call fails by default, so only production wiring talks to the backend.
 * @param locationRepository Shares the user's location on a trip map; the app-wide instance of
 *   [RoamigoApplication] by default, since sharing must go through a single instance.
 */
@Composable
fun RoamigoApp(
    voteRepository: VoteRepository,
    tripRepository: TripRepository = remember { FakeTripRepository() },
    activityRepository: ActivityRepository = remember { FakeActivityRepository() },
    userRepository: UserRepository = remember { FakeUserRepository(User("alex", "Alex", null)) },
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = remember { AuthRepositoryFirebase() },
    credentialManager: CredentialManager? = null,
    destinationRepository: DestinationRepository,
    photoRepository: PhotoRepository = remember { FakePhotoRepository() },
    connectivityRepository: ConnectivityRepository? = null,
    locationRepository: LocationRepository? = null,
    countryCounts: VisitedCountryCounts = remember { VisitedCountryCounts { emptyMap() } },
    invitationService: TripInvitationService = UnavailableTripInvitationService,
) {
  val context = LocalContext.current
  val manager = credentialManager ?: remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()
  // The trip whose invitation sheet is open, and whether the "join with a code" dialog is.
  var inviteTripId by rememberSaveable { mutableStateOf<String?>(null) }
  var joinCodeOpen by rememberSaveable { mutableStateOf(false) }
  // An invitation opened while signed out is kept, and followed once the user has signed in.
  var pendingJoinToken by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(authRepository, userRepository) {
    if (authRepository.getCurrentUser() != null) {
      publishProfileSafely(userRepository)
    }
  }
  // Bottom-bar destinations are defined once so every screen switches tabs the same way.
  val onMapClick: () -> Unit = { navigateRoot(navController, Route.TRIP_OVERVIEW_MAP) }
  val onFriendsClick: () -> Unit = { navigateRoot(navController, Route.FRIENDS) }
  val onTripsClick: () -> Unit = { navController.popBackStack(Route.TRIPS, inclusive = false) }
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
            pendingJoinToken?.let { token ->
              pendingJoinToken = null
              navController.navigate(Route.join(token))
            }
          }
      )
    }
    composable(Route.AUTHENTICATION) {
      AuthenticationRoute(
          authRepository = authRepository,
          credentialManager = credentialManager,
          onSignedIn = {
            scope.launch { publishProfileSafely(userRepository) }

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
          onInviteTrip = { tripId -> inviteTripId = tripId },
          onJoinClick = { joinCodeOpen = true },
          onNewTripClick = { navController.navigate(Route.NEW_TRIP) },
          onProfileClick = { navController.navigate(Route.PROFILE) },
          onFriendsClick = onFriendsClick,
          onHistoryClick = {
            navController.navigate(Route.TRIP_HISTORY) { launchSingleTop = true }
          },
          onOpenTrip = { tripId -> navController.navigate(Route.tripDetails(tripId)) },
      )
    }
    composable(Route.VOTE) { entry ->
      val tripId = entry.tripId()
      val voteId = entry.voteId()
      SignedIn(authRepository, navController) { userId ->
        VoteRoute(
            voteRepository = voteRepository,
            tripId = tripId,
            voteId = voteId,
            currentUserId = userId,
            onBack = { navController.popBackStack() },
            readOnly = entry.isReadOnly(),
        )
      }
    }
    composable(Route.NEW_VOTE) { entry ->
      val tripId = entry.tripId()
      SignedIn(authRepository, navController) { userId ->
        CreateVoteRoute(
            voteRepository = voteRepository,
            tripId = tripId,
            currentUserId = userId,
            onBack = { navController.popBackStack() },
            onVoteCreated = { navController.popBackStack() },
        )
      }
    }
    composable(Route.MEMBERS) { entry ->
      val tripId = entry.tripId()
      SignedIn(authRepository, navController) { userId ->
        TripMembersRoute(
            tripId = tripId,
            currentUserId = userId,
            tripRepository = tripRepository,
            userRepository = userRepository,
            onBack = { navController.popBackStack() },
            // The profile source may not know the signed-in user; their account name does.
            currentUserName = authRepository.getCurrentUser()?.displayName,
        )
      }
    }
    composable(Route.FRIENDS) {
      FriendsScreen(
          uiState = FriendsUiState(),
          onTripsClick = onTripsClick,
          onMapClick = onMapClick,
      )
    }
    composable(Route.PROFILE) {
      ProfileRoute(
          authRepository = authRepository,
          onSignedOut = {
            pendingJoinToken = null
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
          onTripsClick = onTripsClick,
          onMapClick = onMapClick,
          onFriendsClick = onFriendsClick,
      )
    }

    composable(Route.TRIP_HISTORY) { entry ->
      TripHistoryRoute(
          tripRepository = tripRepository,
          countryCounts = countryCounts,
          sharedOwner = entry,
          onBackClick = { navController.popBackStack() },
          onMapClick = onMapClick,
          onTripsClick = onTripsClick,
          onOpenTrip = { tripId ->
            navController.navigate(Route.tripDetails(tripId, readOnly = true))
          },
          onOpenWorldMapClick = {
            navController.navigate(Route.EXPLORED_WORLD) { launchSingleTop = true }
          },
          onFriendsClick = onFriendsClick,
      )
    }

    composable(Route.EXPLORED_WORLD) { entry ->
      val historyEntry = remember(entry) { navController.getBackStackEntry(Route.TRIP_HISTORY) }

      ExploredWorldRoute(
          tripRepository = tripRepository,
          countryCounts = countryCounts,
          sharedOwner = historyEntry,
          onBackClick = { navController.popBackStack() },
      )
    }

    composable(Route.TRIP_DETAILS) { entry ->
      val tripId = entry.tripId()
      val fromHistory = entry.isReadOnly()
      TripRoute(
          tripId = tripId,
          fromHistory = fromHistory,
          openMap = entry.opensMap(),
          authRepository = authRepository,
          tripRepository = tripRepository,
          activityRepository = activityRepository,
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
          onMembersClick = { navController.navigate(Route.members(tripId)) },
          onTripsClick = onTripsClick,
          onMapClick = onMapClick,
          onFriendsClick = onFriendsClick,
      )
    }

    composable(Route.TRIP_READY) { entry ->
      val id = entry.tripId()
      val photo by
          entry.savedStateHandle.getStateFlow<Bundle?>("ready_photo", null).collectAsState()
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
          onInviteClick = { inviteTripId = id },
          onSuggestClick = onSuggestClick,
          onShareLocationClick = { openTrip(openMap = true) },
          onOpenItineraryClick = { openTrip(openMap = false) },
          onShareTripClick = onShareTripClick,
      )
    }

    composable(
        Route.JOIN,
        deepLinks =
            listOf(
                navDeepLink {
                  uriPattern = "${InvitationLinks.SCHEME}://${InvitationLinks.HOST}/{token}"
                }
            ),
    ) { entry ->
      val token = entry.token()
      if (authRepository.getCurrentUser() == null) {
        LaunchedEffect(token) {
          pendingJoinToken = token
          redirectToAuthentication(navController)
        }
      } else {
        JoinTripRoute(
            token = token,
            service = invitationService,
            onJoined = { tripId ->
              navController.navigate(Route.tripDetails(tripId)) {
                popUpTo(Route.TRIPS)
                launchSingleTop = true
              }
            },
            onClose = {
              if (!navController.popBackStack()) {
                navController.navigate(Route.TRIPS) { launchSingleTop = true }
              }
            },
        )
      }
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
          onMarkerClick = { tripId ->
            navController.navigate(Route.tripDetails(tripId, openMap = true))
          },
          onTripsClick = onTripsClick,
          onMapClick = {},
          onFriendsClick = onFriendsClick,
      )
    }
  }
  inviteTripId?.let { tripId ->
    InviteRoute(tripId, invitationService, onDismiss = { inviteTripId = null })
  }
  if (joinCodeOpen) {
    JoinWithCodeDialog(
        onSubmit = { token ->
          joinCodeOpen = false
          navController.navigate(Route.join(token))
        },
        onDismiss = { joinCodeOpen = false },
    )
  }
}

/**
 * Shows [content] for the signed-in user, or sends the user to authentication when the session is
 * gone, so routes that need an account do not each repeat the check.
 */
@Composable
private fun SignedIn(
    authRepository: AuthRepository,
    navController: NavHostController,
    content: @Composable (userId: String) -> Unit,
) {
  val userId = authRepository.getCurrentUser()?.uid
  if (userId == null) {
    LaunchedEffect(Unit) { redirectToAuthentication(navController) }
  } else {
    content(userId)
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

private suspend fun publishProfileSafely(userRepository: UserRepository) {
  try {
    userRepository.publishCurrentUser()
  } catch (error: CancellationException) {
    throw error
  } catch (error: Exception) {
    Log.w("RoamigoApp", "Could not publish user directory profile", error)
  }
}
