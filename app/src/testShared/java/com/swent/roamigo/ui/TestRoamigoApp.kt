// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.credentials.CredentialManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [RoamigoApp] with in-memory data sources, so a test only passes the ones it is about. The fakes
 * live here, not in [RoamigoApp], so the production app can never fall back to sample data.
 * [authRepository] has no default so that no test can reach the real Firebase Authentication.
 */
@Composable
internal fun TestRoamigoApp(
    voteRepository: VoteRepository,
    friendRepository: FriendRepository,
    destinationRepository: DestinationRepository,
    authRepository: AuthRepository,
    tripRepository: TripRepository = remember { FakeTripRepository() },
    activityRepository: ActivityRepository = remember { FakeActivityRepository() },
    userRepository: UserRepository = remember { FakeUserRepository(User("alex", "Alex", null)) },
    navController: NavHostController = rememberNavController(),
    credentialManager: CredentialManager? = null,
    photoRepository: PhotoRepository = remember { FakePhotoRepository() },
    connectivityRepository: ConnectivityRepository? = null,
    locationRepository: LocationRepository? = null,
    placeRepository: PlaceRepository? = null,
    countryCounts: VisitedCountryCounts = remember { VisitedCountryCounts { emptyMap() } },
    invitationService: TripInvitationService = remember { FakeTripInvitationService() },
    notificationRepository: NotificationRepository = EmptyNotificationRepository,
) {
  ProvidePendingFriendRequests(authRepository, friendRepository) {
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
        notificationRepository = notificationRepository,
    )
  }
}

private object EmptyNotificationRepository : NotificationRepository {
  private val inbox = MutableStateFlow(emptyList<Notification>())

  override fun observeNotifications(): Flow<List<Notification>> = inbox

  override fun observeUnreadCount(): Flow<Int> = flowOf(0)

  override suspend fun markAsRead(notificationId: String): Unit =
      error("The empty inbox has no notifications")
}
