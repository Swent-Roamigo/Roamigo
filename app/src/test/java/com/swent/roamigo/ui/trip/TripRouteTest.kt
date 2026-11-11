package com.swent.roamigo.ui.trip

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.voting.interactionVote
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripRouteTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

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
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = null,
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
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
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = FakeLocationRepository(),
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
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
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = locationRepository,
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
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
              userRepository = FakeUserRepository(User("member", "Member", null)),
              voteRepository = InMemoryVoteRepository(listOf(vote)),
              photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
              connectivityRepository = FakeConnectivityRepository(),
              locationRepository = FakeLocationRepository(),
              onBack = {},
              onAuthenticationRequired = {},
              onOpenVote = { _, _ -> },
              onCreateVote = {},
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

  private fun showRoute(fromHistory: Boolean, onCreateVote: () -> Unit = {}) {
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
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = FakeLocationRepository(),
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = onCreateVote,
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
