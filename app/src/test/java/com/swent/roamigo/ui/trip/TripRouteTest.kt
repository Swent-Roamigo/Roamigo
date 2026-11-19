// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.PhotoCoordinates
import com.swent.roamigo.model.photo.PhotoModule
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormTrip
import com.swent.roamigo.ui.trip.voting.interactionVote
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Takes every picture successfully and answers permission requests with a grant. */
private class RecordingRegistry : ActivityResultRegistry() {
  val launched = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()

  @Suppress("UNCHECKED_CAST")
  override fun <I, O> onLaunch(
      requestCode: Int,
      contract: ActivityResultContract<I, O>,
      input: I,
      options: ActivityOptionsCompat?,
  ) {
    launched += contract to input
    when (contract) {
      is ActivityResultContracts.RequestPermission -> dispatchResult(requestCode, true as O)
      is ActivityResultContracts.TakePicture -> dispatchResult(requestCode, true as O)
    }
  }
}

@RunWith(AndroidJUnit4::class)
class TripRouteTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  /** FileProvider keeps the first test's data directory otherwise, and rejects the next one's. */
  @Before
  fun forgetCachedFileProviderRoots() {
    val cache = FileProvider::class.java.getDeclaredField("sCache")
    cache.isAccessible = true
    (cache.get(null) as MutableMap<*, *>).clear()
  }

  @Test
  fun staysReadOnlyAcrossTabsUntilTripLoadSucceeds() {
    val auth =
        FakeAuthRepository(
            AuthenticatedUser("member", "Member", null, null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        TripRoute(
            tripId = "unavailable-trip",
            fromHistory = false,
            openMap = false,
            authRepository = auth,
            tripRepository = FakeTripRepository(failure = IOException("backend detail")),
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = FakeLocationRepository(),
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
            onCreateActivity = {},
            onMembersClick = {},
            onTripsClick = {},
            onMapClick = {},
            onFriendsClick = {},
        )
      }
    }

    composeRule.onNodeWithText("Unable to load trip details.").assertIsDisplayed()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithText("Photos").performClick()
    composeRule.onNodeWithText("Take a photo").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
  }

  @Test
  fun tripPhotosAreRequestedOnlyOnceTheMapTabIsOpened() {
    val trip = trip()
    val photoRequests = mutableListOf<String>()
    val photos =
        object : PhotoRepository by FakePhotoRepository(composeRule.activity.cacheDir) {
          override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> {
            photoRequests += tripId
            return flowOf(emptyList())
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) {
          TripRoute(
              tripId = trip.uid,
              fromHistory = false,
              openMap = false,
              authRepository = signedInAuth(),
              tripRepository = FakeTripRepository(trips = listOf(trip)),
              activityRepository = FakeActivityRepository(),
              userRepository = FakeUserRepository(User("member", "Member", null)),
              voteRepository = InMemoryVoteRepository(),
              photoRepository = photos,
              connectivityRepository = FakeConnectivityRepository(),
              locationRepository = FakeLocationRepository(),
              onBack = {},
              onAuthenticationRequired = {},
              onOpenVote = { _, _ -> },
              onCreateVote = {},
              onCreateActivity = {},
              onMembersClick = {},
              onTripsClick = {},
              onMapClick = {},
              onFriendsClick = {},
          )
        }
      }
    }

    composeRule.onNodeWithText(trip.name).assertIsDisplayed()
    composeRule.runOnIdle { assertTrue(photoRequests.isEmpty()) }

    // The trip's tab row comes before the app's bottom bar, which also has a Map item.
    composeRule
        .onAllNodesWithText(composeRule.activity.getString(R.string.trips_nav_map))[0]
        .performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(listOf(trip.uid), photoRequests) }

    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_tab))
        .performClick()
    composeRule
        .onAllNodesWithText(composeRule.activity.getString(R.string.trips_nav_map))[0]
        .performClick()
    composeRule.runOnIdle { assertEquals(listOf(trip.uid), photoRequests) }
  }

  @Test
  fun slowActivitiesDoNotDelayTheHeaderTripActionsOrLocationObservation() {
    val trip = trip()
    val photoRequests = mutableListOf<String>()
    val photos =
        object : PhotoRepository by FakePhotoRepository(composeRule.activity.cacheDir) {
          override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> {
            photoRequests += tripId
            return flowOf(emptyList())
          }
        }
    val pendingActivities = CompletableDeferred<List<Activity>>()
    val activities =
        object : ActivityRepository by FakeActivityRepository() {
          override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
            emit(pendingActivities.await())
          }
        }
    val locations = FakeLocationRepository()
    composeRule.setContent {
      RoamigoTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) {
          TripRoute(
              tripId = trip.uid,
              fromHistory = false,
              openMap = true,
              authRepository = signedInAuth(),
              tripRepository = FakeTripRepository(trips = listOf(trip)),
              activityRepository = activities,
              userRepository = FakeUserRepository(User("member", "Member", null)),
              voteRepository = InMemoryVoteRepository(),
              photoRepository = photos,
              connectivityRepository = FakeConnectivityRepository(),
              locationRepository = locations,
              onBack = {},
              onAuthenticationRequired = {},
              onOpenVote = { _, _ -> },
              onCreateVote = {},
              onCreateActivity = {},
              onMembersClick = {},
              onTripsClick = {},
              onMapClick = {},
              onFriendsClick = {},
          )
        }
      }
    }

    composeRule.onNodeWithText(trip.name).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_loading).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(1, locations.observeMemberLocationsCalls)
      assertEquals(listOf(trip.uid), photoRequests)
    }

    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trip_details_tab))
        .performClick()
    composeRule.onNodeWithTag(C.Tag.trip_add_button).assertIsDisplayed()
  }

  @Test
  fun offLocationPillInThePhotoPreviewOpensTheMapTabOfTheSameTrip() {
    val registry = RecordingRegistry()
    val locations = FakeLocationRepository()
    showOffLocationPhotoPreview(signedInAuth(), registry, locations) {}

    composeRule.onNodeWithTag(C.Tag.photo_preview_location).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_preview).assertDoesNotExist()
    assertTrue(
        registry.launched.none { it.first is ActivityResultContracts.RequestMultiplePermissions }
    )
    assertTrue(
        registry.launched
            .filter { it.first is ActivityResultContracts.RequestPermission }
            .all { it.second == Manifest.permission.CAMERA }
    )
    assertTrue(locations.startSharingCalls.isEmpty())
  }

  @Test
  fun photoPreviewIsKeptAndSaysLocationAppliesToTheNextPhotoAfterGrantingItOnTheMap() {
    val registry = RecordingRegistry()
    val locations = FakeLocationRepository()
    showOffLocationPhotoPreview(signedInAuth(), registry, locations) {}
    val picturesTaken = {
      registry.launched.count { it.first is ActivityResultContracts.TakePicture }
    }
    assertEquals(1, picturesTaken())

    composeRule.onNodeWithTag(C.Tag.photo_preview_location).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()
    locations.hasPermission = true
    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trips_nav_photos))
        .performClick()

    composeRule.onNodeWithTag(C.Tag.photo_preview).assertIsDisplayed()
    composeRule.waitUntil(5_000) {
      runCatching {
        composeRule
            .onNodeWithTag(C.Tag.photo_preview_location)
            .assertTextEquals("Location is on for your next photo · Retake to add it")
      }
          .isSuccess
    }
    assertEquals(1, picturesTaken())
    assertTrue(locations.startSharingCalls.isEmpty())
  }

  @Test
  fun offLocationPillAsksASignedOutUserToSignInInsteadOfOpeningTheMap() {
    val auth = signedInAuth()
    var authenticationRequests = 0
    showOffLocationPhotoPreview(auth, RecordingRegistry(), FakeLocationRepository()) {
      authenticationRequests++
    }

    auth.signOut()
    composeRule.onNodeWithTag(C.Tag.photo_preview_location).performClick()

    assertEquals(1, authenticationRequests)
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photo_preview).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp-land")
  fun photoPreviewFitsInsideTheTripScreenInALandscapePhoneWithEveryMessage() {
    val failingSaves =
        object :
            PhotoRepository by FakePhotoRepository(
                File(composeRule.activity.filesDir, PhotoModule.PHOTO_DIRECTORY)
            ) {
          override suspend fun savePhoto(
              tripId: String,
              file: File,
              takenAtMillis: Long,
              coordinates: PhotoCoordinates?,
          ) = throw IOException("disk full")
        }
    showOffLocationPhotoPreview(
        signedInAuth(),
        RecordingRegistry(),
        FakeLocationRepository(),
        connectivity = FakeConnectivityRepository(isOnlineInitially = false),
        photos = failingSaves,
        onAuthenticationRequired = {},
    )
    composeRule.onNodeWithTag(C.Tag.photo_preview_save).performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(C.Tag.photo_preview_error).fetchSemanticsNodes().isNotEmpty()
    }

    val tags =
        listOf(
            C.Tag.photo_preview_location,
            C.Tag.photo_preview_offline,
            C.Tag.photo_preview_error,
            C.Tag.photo_preview_close,
            C.Tag.photo_preview_save,
            C.Tag.photo_preview_retake,
        )
    tags.forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }
    val root = composeRule.onRoot().getUnclippedBoundsInRoot()
    val bounds = tags.associateWith { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
    bounds.forEach { (tag, box) ->
      assertTrue(
          "$tag $box is not inside the root $root",
          box.left >= root.left &&
              box.top >= root.top &&
              box.right <= root.right &&
              box.bottom <= root.bottom,
      )
    }
    tags.forEachIndexed { i, first ->
      tags.drop(i + 1).forEach { second ->
        val a = bounds.getValue(first)
        val b = bounds.getValue(second)
        val overlaps = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
        assertTrue("$first $a overlaps $second $b", !overlaps)
      }
    }
  }

  private fun showOffLocationPhotoPreview(
      auth: FakeAuthRepository,
      registry: RecordingRegistry,
      locations: FakeLocationRepository,
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
      photos: PhotoRepository =
          FakePhotoRepository(File(composeRule.activity.filesDir, PhotoModule.PHOTO_DIRECTORY)),
      onAuthenticationRequired: () -> Unit,
  ) {
    val trip = trip()
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    shadowOf(composeRule.activity.application).grantPermissions(Manifest.permission.CAMERA)
    composeRule.setContent {
      RoamigoTheme {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalActivityResultRegistryOwner provides owner,
        ) {
          TripRoute(
              tripId = trip.uid,
              fromHistory = false,
              openMap = false,
              authRepository = auth,
              tripRepository = FakeTripRepository(trips = listOf(trip)),
              activityRepository = FakeActivityRepository(),
              userRepository = FakeUserRepository(User("member", "Member", null)),
              voteRepository = InMemoryVoteRepository(),
              photoRepository = photos,
              connectivityRepository = connectivity,
              locationRepository = locations,
              onBack = {},
              onAuthenticationRequired = onAuthenticationRequired,
              onOpenVote = { _, _ -> },
              onCreateVote = {},
              onCreateActivity = {},
              onMembersClick = {},
              onTripsClick = {},
              onMapClick = {},
              onFriendsClick = {},
          )
        }
      }
    }

    composeRule
        .onNodeWithText(composeRule.activity.getString(R.string.trips_nav_photos))
        .performClick()
    composeRule.onNodeWithTag(C.Tag.photos_take_photo).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_preview).assertIsDisplayed()
    composeRule.waitUntil(5_000) {
      runCatching {
        composeRule
            .onNodeWithTag(C.Tag.photo_preview_location)
            .assertTextEquals("Location off · Turn on in Map")
      }
          .isSuccess
    }
  }

  @Test
  fun detailRetryRefreshesTheSharedTripStateAndRestoresEditing() {
    val trip = trip()
    val delegate = FakeTripRepository(trips = listOf(trip))
    var failLoads = true
    val repository =
        object : TripRepository by delegate {
          override suspend fun getTrips(): List<Trip> {
            if (failLoads) throw IOException("temporary failure")
            return delegate.getTrips()
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        TripRoute(
            tripId = trip.uid,
            fromHistory = false,
            openMap = false,
            authRepository = signedInAuth(),
            tripRepository = repository,
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = FakeLocationRepository(),
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
            onCreateActivity = {},
            onMembersClick = {},
            onTripsClick = {},
            onMapClick = {},
            onFriendsClick = {},
        )
      }
    }

    composeRule.onNodeWithText("Unable to load trip details.").assertIsDisplayed()
    composeRule.runOnUiThread { failLoads = false }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText(trip.name).assertIsDisplayed()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Start a vote").assertIsDisplayed()
    composeRule.onNodeWithText("Photos").performClick()
    composeRule.onNodeWithText("Take a photo").assertIsDisplayed()
  }

  @Test
  fun openingACompletedTripStopsAnyRestoredLocationShare() {
    val trip = trip(endDate = LocalDate.now(ZoneId.systemDefault()).minusDays(1))
    val locationRepository = FakeLocationRepository(hasPermission = true)
    runBlocking { locationRepository.startSharing(trip.uid) }

    composeRule.setContent {
      RoamigoTheme {
        TripRoute(
            tripId = trip.uid,
            fromHistory = false,
            openMap = false,
            authRepository = signedInAuth(),
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = locationRepository,
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
            onCreateActivity = {},
            onMembersClick = {},
            onTripsClick = {},
            onMapClick = {},
            onFriendsClick = {},
        )
      }
    }

    composeRule.waitUntil { locationRepository.stopSharingCalls.contains(trip.uid) }
    assertTrue(locationRepository.sharedTripIds.isEmpty())
  }

  @Test
  fun votesHintIsShownOnceAndStaysDismissedOnALaterVisit() {
    val hintPreferences =
        composeRule.activity.getSharedPreferences("roamigo_hints", Context.MODE_PRIVATE)
    hintPreferences.edit().clear().commit()
    val vote = interactionVote().copy(tripId = "trip-id")

    fun showVotes() {
      composeRule.setContent {
        RoamigoTheme {
          TripRoute(
              tripId = "trip-id",
              fromHistory = false,
              openMap = false,
              authRepository = signedInAuth(),
              tripRepository = FakeTripRepository(trips = listOf(trip())),
              activityRepository = FakeActivityRepository(),
              userRepository = FakeUserRepository(User("member", "Member", null)),
              voteRepository = InMemoryVoteRepository(listOf(vote)),
              photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
              connectivityRepository = FakeConnectivityRepository(),
              locationRepository = FakeLocationRepository(),
              onBack = {},
              onAuthenticationRequired = {},
              onOpenVote = { _, _ -> },
              onCreateVote = {},
              onCreateActivity = {},
              onMembersClick = {},
              onTripsClick = {},
              onMapClick = {},
              onFriendsClick = {},
          )
        }
      }
      composeRule.onNodeWithText("Votes").performClick()
    }

    showVotes()
    composeRule.onNodeWithTag(C.Tag.votes_hint).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.votes_hint_dismiss).performClick()
    composeRule.onNodeWithTag(C.Tag.votes_hint).assertDoesNotExist()
    assertTrue(hintPreferences.getBoolean("votes_hint_seen", false))

    // A new composition stands for a later visit: only the stored flag can keep the hint hidden.
    composeRule.activityRule.scenario.recreate()
    showVotes()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.votes_hint).assertDoesNotExist()
  }

  private fun showRoute(
      fromHistory: Boolean,
      onCreateVote: () -> Unit = {},
      onCreateActivity: (ActivityFormTrip) -> Unit = {},
  ) {
    val trip =
        trip(
            endDate =
                if (fromHistory) LocalDate.now(ZoneId.systemDefault()).minusDays(1)
                else LocalDate.now(ZoneId.systemDefault()).plusDays(1)
        )
    composeRule.setContent {
      RoamigoTheme {
        TripRoute(
            tripId = trip.uid,
            fromHistory = fromHistory,
            openMap = false,
            authRepository = signedInAuth(),
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = FakeLocationRepository(),
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = onCreateVote,
            onCreateActivity = onCreateActivity,
            onMembersClick = {},
            onTripsClick = {},
            onMapClick = {},
            onFriendsClick = {},
        )
      }
    }
  }

  @Test
  fun addSheetOffersThreeActionsAndStartsAVote() {
    var createVoteClicks = 0
    showRoute(fromHistory = false, onCreateVote = { createVoteClicks++ })

    composeRule.onNodeWithTag(C.Tag.trip_add_button).performClick()
    composeRule.onNodeWithText("Add an activity").assertIsDisplayed()
    composeRule.onNodeWithText("Suggest a place").assertIsDisplayed()
    composeRule.onNodeWithText("Start a vote").performClick()

    composeRule.runOnIdle { assertEquals(1, createVoteClicks) }
  }

  @Test
  fun addingAnActivityOpensTheFormForTheLoadedTrip() {
    val opened = mutableListOf<ActivityFormTrip>()
    showRoute(fromHistory = false, onCreateActivity = { opened += it })

    composeRule.onNodeWithTag(C.Tag.trip_add_button).performClick()
    composeRule.onNodeWithText("Add an activity").performClick()

    val today = LocalDate.now(ZoneId.systemDefault())
    composeRule.runOnIdle {
      assertEquals(
          listOf(ActivityFormTrip("Lisbon & Porto", today.minusDays(1), today.plusDays(1))),
          opened,
      )
    }
  }

  @Test
  fun addSheetHasNoPhotoShortcutUntilItCanTakeAPhotoDirectly() {
    showRoute(fromHistory = false)

    composeRule.onNodeWithTag(C.Tag.trip_add_button).performClick()

    // A shortcut that only switched tabs added little, so it is not offered for now.
    composeRule.onNodeWithTag(C.Tag.trip_add_sheet).assertIsDisplayed()
    composeRule.onNodeWithText("Take a photo").assertDoesNotExist()
  }

  @Test
  fun addButtonIsHiddenOnAPastTrip() {
    showRoute(fromHistory = true)

    composeRule.onAllNodesWithTag(C.Tag.trip_add_button).assertCountEquals(0)
  }

  private fun signedInAuth() =
      FakeAuthRepository(
          AuthenticatedUser("member", "Member", null, null),
          initiallySignedIn = true,
      )

  private fun trip(endDate: LocalDate = LocalDate.now(ZoneId.systemDefault()).plusDays(1)): Trip {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
    val end = endDate.atStartOfDay(zone).toInstant()
    return Trip(
        uid = "trip-id",
        name = "Lisbon & Porto",
        description = null,
        destinations = listOf(TripLocation(38.7, -9.1, "Lisbon")),
        startDate = Timestamp(start.epochSecond, start.nano),
        endDate = Timestamp(end.epochSecond, end.nano),
        flexibleDates = false,
        travelMode = TravelMode.GROUP,
        travelerCount = 2,
        ownerId = "member",
        memberIds = listOf("member"),
        status = TripStatus.ACTIVE,
        createdAt = Timestamp(Instant.EPOCH.epochSecond, 0),
    )
  }
}
