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
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import com.swent.roamigo.model.trip.inviting.UnavailableTripInvitationService
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.heatmap.VisitedCountryCounts

/**
 * [RoamigoApp] with fake repositories for the ones a test does not care about. Only tests use it:
 * the app itself must always be given real repositories.
 */
@Composable
fun RoamigoAppWithFakes(
    voteRepository: VoteRepository,
    friendRepository: FriendRepository,
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
    placeRepository: PlaceRepository? = null,
    countryCounts: VisitedCountryCounts = remember { VisitedCountryCounts { emptyMap() } },
    invitationService: TripInvitationService = UnavailableTripInvitationService,
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
