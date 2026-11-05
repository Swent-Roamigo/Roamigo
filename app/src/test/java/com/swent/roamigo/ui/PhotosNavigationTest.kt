// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.photos.TestPhotoRepository
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotosNavigationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val tripId = "trip #1"
  private val tripName = "Lisbon & Porto / 2026?"
  private val connectivity = FakeConnectivityRepository()
  private lateinit var navController: NavHostController

  private fun openPhotos(
      photoRepository: PhotoRepository = FakePhotoRepository(),
      memberIds: List<String> = listOf("owner", "member"),
  ) {
    val now = Instant.now().epochSecond
    val trip =
        Trip(
            tripId,
            tripName,
            null,
            listOf(TripLocation(38.7, -9.1, "Lisbon")),
            Timestamp(now - 3600, 0),
            Timestamp(now + 86400, 0),
            false,
            TravelMode.GROUP,
            2,
            "owner",
            memberIds,
            TripStatus.ACTIVE,
            Timestamp(now - 7200, 0),
        )
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(emptyList()),
            destinationRepository = sampleDestinationRepository(),
            tripRepository =
                FakeTripRepository(
                    trips =
                        listOf(
                            trip.copy(
                                uid = "other",
                                name = "Other trip",
                                startDate = Timestamp(now + 86400, 0),
                                endDate = Timestamp(now + 172800, 0),
                                status = TripStatus.PLANNED,
                            ),
                            trip,
                        )
                ),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("member", "Member", null, null),
                    initiallySignedIn = true,
                ),
            photoRepository = photoRepository,
            connectivityRepository = connectivity,
            navController = navController,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Photos").performClick()
  }

  @Test
  fun thePhotosTabOfATripOpensItsPhotos() {
    openPhotos()

    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    composeRule.onNodeWithText(tripName).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, navController.currentBackStackEntry?.destination?.route)
      assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getString("tripId"))
    }
  }

  @Test
  fun followsTheConnectionInjectedIntoTheApp() {
    connectivity.online.value = false
    openPhotos()

    composeRule.onNodeWithTag(C.Tag.photos_banner).assertIsDisplayed()
  }

  @Test
  fun galleryFailureKeepsTheTripHeaderVisible() {
    val repository =
        TestPhotoRepository(composeRule.activity.cacheDir).apply {
          galleryError = IOException("Gallery unavailable")
        }
    openPhotos(repository)

    composeRule.onNodeWithTag(C.Tag.photos_gallery_error).assertIsDisplayed()
    composeRule.onNodeWithText(tripName).assertIsDisplayed()
    composeRule.onNodeWithText("Trip photos").assertIsDisplayed()
  }

  @Test
  fun photoSummaryTracksGalleryUpdatesAndCountsDistinctMembers() {
    val repository = FakePhotoRepository()
    openPhotos(repository, memberIds = listOf("owner", "member", "member"))

    composeRule.onNodeWithText("0 photos · 2 people").assertIsDisplayed()

    repository.setUploaded(listOf(TripPhoto("photo-1", "member", 0, null, null, byteArrayOf())))

    composeRule.onNodeWithText("1 photo · 2 people").assertIsDisplayed()
  }

  @Test
  fun switchingBackAcrossTripTabsKeepsTheTripAndBackButtonReturnsToTrips() {
    openPhotos()

    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Plan").performClick()
    composeRule.onNodeWithText("Photos").performClick()
    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    composeRule.onNodeWithText(tripName).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, navController.currentBackStackEntry?.destination?.route)
      assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getString("tripId"))
    }

    composeRule
        .onNodeWithContentDescription(
            composeRule.activity.getString(com.swent.roamigo.R.string.trip_details_back)
        )
        .performClick()

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun backFromPhotosReturnsDirectlyToTrips() {
    openPhotos()

    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }
}
