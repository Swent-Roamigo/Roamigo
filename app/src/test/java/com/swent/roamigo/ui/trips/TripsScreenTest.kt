// AI assistance was used for implementation support and code review.

package com.swent.roamigo.ui.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripMember
import com.swent.roamigo.model.trip.TripMemberRole
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class TripsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val location =
      TripLocation(
          latitude = 38.7223,
          longitude = -9.1393,
          name = "Lisbon",
      )

  @Test
  fun tripsRouteDisplaysRepositoryContentAndInviteAction() {
    val now = Instant.parse("2026-10-08T12:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)

    val alex =
        User(
            uid = "alex",
            displayName = "Alex",
            profilePictureUrl = null,
        )

    val sarah =
        User(
            uid = "sarah",
            displayName = "Sarah",
            profilePictureUrl = null,
        )

    val marc =
        User(
            uid = "marc",
            displayName = "Marc",
            profilePictureUrl = null,
        )

    val mainTrip =
        trip(
            uid = "main",
            name = "Lisbon & Porto",
            start = now.minusSeconds(24 * 60 * 60),
            end = now.plusSeconds(3 * 24 * 60 * 60),
            status = TripStatus.ACTIVE,
            ownerId = alex.uid,
        )

    val otherTrip =
        trip(
            uid = "other",
            name = "Berlin weekend",
            start = now.plusSeconds(10 * 24 * 60 * 60),
            end = now.plusSeconds(12 * 24 * 60 * 60),
            status = TripStatus.PLANNED,
            ownerId = alex.uid,
        )

    val nextActivity =
        Activity(
            uid = "activity",
            tripId = mainTrip.uid,
            activityType = ActivityType.ACCEPTED,
            name = "Belém Tower",
            description = null,
            location = location,
            startTime = timestamp(now.plusSeconds(60 * 60)),
            endTime = null,
            createdByUserId = alex.uid,
            creationTime = timestamp(now.minusSeconds(60 * 60)),
        )

    val tripRepository =
        FakeTripRepository(
            trips = listOf(mainTrip, otherTrip),
            activitiesByTripId =
                mapOf(
                    mainTrip.uid to listOf(nextActivity),
                ),
            membersByTripId =
                mapOf(
                    mainTrip.uid to
                        listOf(
                            member(alex.uid, now),
                            member(sarah.uid, now),
                            member(marc.uid, now),
                        ),
                    otherTrip.uid to
                        listOf(
                            member(alex.uid, now),
                            member(sarah.uid, now),
                        ),
                ),
        )

    val userRepository =
        FakeUserRepository(
            currentUser = alex,
            users = listOf(sarah, marc),
        )

    var inviteClicks = 0

    composeTestRule.setContent {
      MaterialTheme {
        TripsRoute(
            tripRepository = tripRepository,
            userRepository = userRepository,
            onInviteClick = { inviteClicks++ },
            clock = clock,
        )
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.trips_main_trip_card)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule.onNodeWithTag(C.Tag.trips_main_trip_card).assertIsDisplayed()

    composeTestRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()

    composeTestRule.onNodeWithText("Next up").assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.trips_next_activity_card).assertIsDisplayed()

    composeTestRule.onNodeWithText("Belém Tower").assertIsDisplayed()

    composeTestRule.onNodeWithText("Other trips").assertIsDisplayed()

    composeTestRule.onNodeWithText("Berlin weekend").assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.trips_invite_button).performClick()

    assertEquals(1, inviteClicks)
  }

  @Test
  fun newTripAndBottomNavigationCallbacksAreForwarded() {
    var newTripClicks = 0
    var tripsClicks = 0
    var mapClicks = 0
    var photosClicks = 0

    composeTestRule.setContent {
      MaterialTheme {
        TripsScreen(
            uiState =
                TripsUiState(
                    isLoading = false,
                ),
            onNewTripClick = { newTripClicks++ },
            onTripsClick = { tripsClicks++ },
            onMapClick = { mapClicks++ },
            onPhotosClick = { photosClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.trips_new_trip_button).assertIsDisplayed().performClick()

    composeTestRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()

    composeTestRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()

    composeTestRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(1, newTripClicks)
    assertEquals(1, tripsClicks)
    assertEquals(1, mapClicks)
    assertEquals(1, photosClicks)
  }

  @Test
  fun singularCountsUseSingularLabels() {
    composeTestRule.setContent {
      MaterialTheme {
        TripsScreen(
            uiState =
                TripsUiState(
                    isLoading = false,
                    mainTrip =
                        MainTripUiModel(
                            id = "main",
                            name = "Day trip",
                            dateRange = "8 Oct",
                            durationDays = 1,
                            stopCount = 1,
                            memberInitials = emptyList(),
                            badge = null,
                        ),
                    otherTrips =
                        listOf(
                            OtherTripUiModel(
                                id = "other",
                                name = "Geneva",
                                dateRange = "10 Oct",
                                memberCount = 1,
                            )
                        ),
                )
        )
      }
    }

    composeTestRule.onNodeWithText("8 Oct · 1 day · 1 stop").assertIsDisplayed()
    composeTestRule.onNodeWithText("10 Oct · 1 traveler").assertIsDisplayed()
  }

  @Test
  fun errorStateDisplaysErrorAndHidesTripContent() {
    composeTestRule.setContent {
      MaterialTheme {
        TripsScreen(
            uiState =
                TripsUiState(
                    isLoading = false,
                    errorMessage = "Unable to load trips.",
                )
        )
      }
    }

    composeTestRule.onNodeWithText("Unable to load trips.").assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.trips_main_trip_card).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.trips_next_activity_card).assertDoesNotExist()
  }

  @Test
  fun loadingStateDoesNotDisplayTripCards() {
    composeTestRule.setContent {
      MaterialTheme {
        TripsScreen(
            uiState = TripsUiState(),
        )
      }
    }

    composeTestRule.onNodeWithText("Your trips").assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.trips_main_trip_card).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.trips_next_activity_card).assertDoesNotExist()
  }

  private fun trip(
      uid: String,
      name: String,
      start: Instant,
      end: Instant,
      status: TripStatus,
      ownerId: String,
  ): Trip =
      Trip(
          uid = uid,
          name = name,
          description = null,
          destinations = listOf(location),
          startDate = timestamp(start),
          endDate = timestamp(end),
          ownerId = ownerId,
          status = status,
      )

  private fun member(
      userId: String,
      now: Instant,
  ): TripMember =
      TripMember(
          userId = userId,
          role = TripMemberRole.EDITOR,
          joinTime = timestamp(now),
      )

  private fun timestamp(instant: Instant): Timestamp =
      Timestamp(
          instant.epochSecond,
          instant.nano,
      )
}
