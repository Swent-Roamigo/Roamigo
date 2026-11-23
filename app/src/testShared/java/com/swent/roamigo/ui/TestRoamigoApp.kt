// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.credentials.CredentialManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.place.PlaceRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.heatmap.VisitedCountryCounts

/**
 * [RoamigoApp] with in-memory data sources, so a test only passes the ones it is about. The fakes
 * live here, not in [RoamigoApp], so the production app can never fall back to sample data.
 */
@Composable
internal fun TestRoamigoApp(
    voteRepository: VoteRepository,
    friendRepository: FriendRepository,
    destinationRepository: DestinationRepository,
    tripRepository: TripRepository = remember { FakeTripRepository() },
    activityRepository: ActivityRepository = remember { FakeActivityRepository() },
    userRepository: UserRepository = remember { FakeUserRepository(User("alex", "Alex", null)) },
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = remember { AuthRepositoryFirebase() },
    credentialManager: CredentialManager? = null,
    photoRepository: PhotoRepository = remember { FakePhotoRepository() },
    connectivityRepository: ConnectivityRepository? = null,
    locationRepository: LocationRepository? = null,
    placeRepository: PlaceRepository? = null,
    countryCounts: VisitedCountryCounts = remember { VisitedCountryCounts { emptyMap() } },
    invitationService: TripInvitationService = remember { FakeTripInvitationService() },
) {
  RoamigoApp(
      voteRepository = voteRepository,
      friendRepository = friendRepository,
      tripRepository = tripRepository,
      activityRepository = activityRepository,
      userRepository = userRepository,
      navController = navController,
      authRepository = authRepository,
      credentialManager = credentialManager,
      destinationRepository = destinationRepository,
      photoRepository = photoRepository,
      connectivityRepository = connectivityRepository,
      locationRepository = locationRepository,
      placeRepository = placeRepository,
      countryCounts = countryCounts,
      invitationService = invitationService,
  )
}
