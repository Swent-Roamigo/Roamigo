// AI assistance was used for implementation support and code review.

package com.swent.roamigo.ui.trips

import androidx.lifecycle.ViewModel
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val location =
      TripLocation(
          latitude = 38.7223,
          longitude = -9.1393,
          name = "Lisbon",
      )

  private val alex =
      User(
          uid = "alex",
          displayName = "Alex Johnson",
          profilePictureUrl = null,
      )

  private val sarah =
      User(
          uid = "sarah",
          displayName = "Sarah",
          profilePictureUrl = null,
      )

  private val marc =
      User(
          uid = "marc",
          displayName = "Marc",
          profilePictureUrl = null,
      )

  private val unnamed =
      User(
          uid = "unnamed",
          displayName = "   ",
          profilePictureUrl = null,
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun selectMainTripPrefersActiveTrip() {
    val now = ZonedDateTime.parse("2026-10-08T14:00:00Z")

    val planned =
        trip(
            uid = "planned",
            name = "Planned trip",
            start = "2026-10-09T08:00:00Z",
            end = "2026-10-10T08:00:00Z",
            status = TripStatus.PLANNED,
        )

    val active =
        trip(
            uid = "active",
            name = "Active trip",
            start = "2026-10-07T08:00:00Z",
            end = "2026-10-10T08:00:00Z",
            status = TripStatus.ACTIVE,
        )

    assertEquals(active, selectMainTrip(listOf(planned, active), now))
  }

  @Test
  fun selectMainTripIncludesPlannedTripStartingToday() {
    val now = ZonedDateTime.parse("2026-10-08T14:00:00Z")

    // The trip started earlier today but is still today's planned trip.
    val today =
        trip(
            uid = "today",
            name = "Today's trip",
            start = "2026-10-08T08:00:00Z",
            end = "2026-10-09T20:00:00Z",
            status = TripStatus.PLANNED,
        )

    assertEquals(today, selectMainTrip(listOf(today), now))
  }

  @Test
  fun selectNextActivityReturnsClosestFutureAcceptedActivity() {
    val now = ZonedDateTime.parse("2026-10-08T10:00:00Z")

    val pastAccepted =
        activity(
            uid = "past",
            type = ActivityType.ACCEPTED,
            start = "2026-10-08T09:00:00Z",
        )

    val futureSuggestion =
        activity(
            uid = "suggestion",
            type = ActivityType.SUGGESTION,
            start = "2026-10-08T10:30:00Z",
        )

    val closestAccepted =
        activity(
            uid = "closest",
            type = ActivityType.ACCEPTED,
            start = "2026-10-08T11:00:00Z",
        )

    val laterAccepted =
        activity(
            uid = "later",
            type = ActivityType.ACCEPTED,
            start = "2026-10-08T15:00:00Z",
        )

    val result =
        selectNextActivity(
            listOf(pastAccepted, futureSuggestion, laterAccepted, closestAccepted),
            now,
        )

    assertEquals(closestAccepted, result)
  }

  @Test
  fun selectOtherTripsFiltersAndSortsTrips() {
    val now = ZonedDateTime.parse("2026-10-08T14:00:00Z")

    val main =
        trip(
            uid = "main",
            name = "Main",
            start = "2026-10-08T08:00:00Z",
            end = "2026-10-09T08:00:00Z",
            status = TripStatus.ACTIVE,
        )

    val tomorrow =
        trip(
            uid = "tomorrow",
            name = "Tomorrow",
            start = "2026-10-09T08:00:00Z",
            end = "2026-10-10T08:00:00Z",
            status = TripStatus.PLANNED,
        )

    val nextWeek =
        trip(
            uid = "next-week",
            name = "Next week",
            start = "2026-10-15T08:00:00Z",
            end = "2026-10-17T08:00:00Z",
            status = TripStatus.PLANNED,
        )

    val past =
        trip(
            uid = "past",
            name = "Past",
            start = "2026-10-07T08:00:00Z",
            end = "2026-10-07T20:00:00Z",
            status = TripStatus.PLANNED,
        )

    val completed =
        trip(
            uid = "completed",
            name = "Completed",
            start = "2026-10-09T08:00:00Z",
            end = "2026-10-10T08:00:00Z",
            status = TripStatus.COMPLETED,
        )

    val result =
        selectOtherTrips(
            trips = listOf(nextWeek, completed, main, tomorrow, past),
            mainTrip = main,
            now = now,
        )

    assertEquals(listOf(tomorrow, nextWeek), result)
  }

  @Test
  fun viewModelBuildsPresentationStateFromRepositories() = runTest {
    val now = Instant.parse("2026-10-08T10:00:00Z")

    val mainTrip =
        trip(
            uid = "lisbon",
            name = "Lisbon & Porto",
            start = "2026-10-08T00:00:00Z",
            end = "2026-10-10T23:00:00Z",
            status = TripStatus.ACTIVE,
        )

    val otherTrip =
        trip(
            uid = "berlin",
            name = "Berlin weekend",
            start = "2026-10-30T08:00:00Z",
            end = "2026-11-02T20:00:00Z",
            status = TripStatus.PLANNED,
        )

    val nextActivity =
        activity(
            uid = "belem",
            tripId = mainTrip.uid,
            name = "Belém Tower",
            type = ActivityType.ACCEPTED,
            start = "2026-10-09T09:30:00Z",
        )

    val secondAcceptedActivity =
        activity(
            uid = "museum",
            tripId = mainTrip.uid,
            name = "Museum",
            type = ActivityType.ACCEPTED,
            start = "2026-10-10T11:00:00Z",
        )

    val suggestion =
        activity(
            uid = "suggestion",
            tripId = mainTrip.uid,
            name = "Suggestion",
            type = ActivityType.SUGGESTION,
            start = "2026-10-08T12:00:00Z",
        )

    val repository =
        FakeTripRepository(
            trips = listOf(mainTrip, otherTrip),
            activitiesByTripId =
                mapOf(
                    mainTrip.uid to
                        listOf(
                            suggestion,
                            secondAcceptedActivity,
                            nextActivity,
                        )
                ),
            membersByTripId =
                mapOf(
                    mainTrip.uid to
                        listOf(
                            member(alex.uid),
                            member(sarah.uid),
                            member(marc.uid),
                            member(unnamed.uid),
                        ),
                    otherTrip.uid to
                        listOf(
                            member(alex.uid),
                            member(sarah.uid),
                        ),
                ),
        )

    val userRepository =
        FakeUserRepository(
            currentUser = alex,
            users = listOf(sarah, marc, unnamed),
        )

    val viewModel =
        TripsViewModel(
            tripRepository = repository,
            userRepository = userRepository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value

    assertEquals(false, state.isLoading)
    assertNull(state.errorMessage)

    assertEquals("Good morning", state.greeting)
    assertEquals("Alex", state.currentUserName)
    assertEquals("A", state.currentUserInitial)

    val main = state.mainTrip!!
    assertEquals("Lisbon & Porto", main.name)
    assertEquals("8 – 10 Oct", main.dateRange)
    assertEquals(3, main.durationDays)
    assertEquals(2, main.stopCount)
    assertEquals(listOf("A", "S", "M"), main.memberInitials)
    assertEquals("In progress", main.badge)

    val next = state.nextActivity!!
    assertEquals("Belém Tower", next.name)
    assertTrue(next.subtitle.startsWith("Day 2"))
    assertTrue(next.subtitle.endsWith("09:30"))

    assertEquals(1, state.otherTrips.size)
    assertEquals("Berlin weekend", state.otherTrips.single().name)
    assertEquals("30 Oct – 2 Nov", state.otherTrips.single().dateRange)
    assertEquals(2, state.otherTrips.single().memberCount)
  }

  @Test
  fun viewModelUsesTodayBadgeForPlannedTripStartingEarlierToday() = runTest {
    val now = Instant.parse("2026-10-08T14:00:00Z")

    val planned =
        trip(
            uid = "planned",
            name = "Lisbon",
            start = "2026-10-08T08:00:00Z",
            end = "2026-10-10T20:00:00Z",
            status = TripStatus.PLANNED,
        )

    val viewModel =
        TripsViewModel(
            tripRepository = FakeTripRepository(trips = listOf(planned)),
            userRepository = FakeUserRepository(currentUser = alex),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    advanceUntilIdle()

    assertEquals("Starts today", viewModel.uiState.value.mainTrip?.badge)
  }

  @Test
  fun viewModelUsesFallbackNameWhenDisplayNameIsBlank() = runTest {
    val blankUser =
        User(
            uid = "blank",
            displayName = "   ",
            profilePictureUrl = null,
        )

    val viewModel =
        TripsViewModel(
            tripRepository = FakeTripRepository(),
            userRepository = FakeUserRepository(currentUser = blankUser),
            clock =
                Clock.fixed(
                    Instant.parse("2026-10-08T20:00:00Z"),
                    ZoneOffset.UTC,
                ),
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value

    assertEquals("Traveler", state.currentUserName)
    assertEquals("T", state.currentUserInitial)
    assertEquals("Good evening", state.greeting)
    assertNull(state.mainTrip)
    assertNull(state.nextActivity)
    assertTrue(state.otherTrips.isEmpty())
  }

  @Test
  fun repositoryFailureProducesErrorState() = runTest {
    val viewModel =
        TripsViewModel(
            tripRepository =
                FakeTripRepository(
                    failure = IllegalStateException("Failure"),
                ),
            userRepository = FakeUserRepository(currentUser = alex),
            clock =
                Clock.fixed(
                    Instant.parse("2026-10-08T14:00:00Z"),
                    ZoneOffset.UTC,
                ),
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value

    assertEquals(false, state.isLoading)
    assertEquals("Unable to load trips.", state.errorMessage)
  }

  @Test
  fun factoryRejectsUnknownViewModelClass() {
    val factory =
        TripsViewModel.factory(
            tripRepository = FakeTripRepository(),
            userRepository = FakeUserRepository(currentUser = alex),
        )

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  private class OtherViewModel : ViewModel()

  private fun trip(
      uid: String,
      name: String,
      start: String,
      end: String,
      status: TripStatus,
  ): Trip =
      Trip(
          uid = uid,
          name = name,
          description = null,
          destinations = listOf(location),
          startDate = timestamp(start),
          endDate = timestamp(end),
          ownerId = alex.uid,
          status = status,
      )

  private fun activity(
      uid: String,
      tripId: String = "trip",
      name: String = uid,
      type: ActivityType,
      start: String,
  ): Activity =
      Activity(
          uid = uid,
          tripId = tripId,
          activityType = type,
          name = name,
          description = null,
          location = location,
          startTime = timestamp(start),
          endTime = null,
          createdByUserId = alex.uid,
          creationTime = timestamp("2026-10-01T10:00:00Z"),
      )

  private fun member(userId: String): TripMember =
      TripMember(
          userId = userId,
          role = TripMemberRole.EDITOR,
          joinTime = timestamp("2026-10-01T10:00:00Z"),
      )

  private fun timestamp(instant: String): Timestamp {
    val parsed = Instant.parse(instant)
    return Timestamp(parsed.epochSecond, parsed.nano)
  }
}
