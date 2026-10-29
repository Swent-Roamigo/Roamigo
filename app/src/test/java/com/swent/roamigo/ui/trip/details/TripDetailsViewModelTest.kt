// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripMember
import com.swent.roamigo.model.trip.TripMemberRole
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailsViewModelTest {
  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsOnlySelectedTripAndKeepsPlannedTravelersSeparateFromMembers() = runTest {
    val trip = detailsTestTrip()
    val member = TripMember("alex", TripMemberRole.OWNER, trip.createdAt)
    val location = TripLocation(38.7, -9.1, "Lisbon")
    val suggestion =
        Activity(
            "suggestion",
            trip.uid,
            ActivityType.SUGGESTION,
            "Tower",
            null,
            location,
            trip.startDate,
            null,
            "alex",
            trip.createdAt,
        )
    val repository =
        FakeTripRepository(
            trips = listOf(trip.copy(uid = "other", name = "Other"), trip),
            membersByTripId = mapOf(trip.uid to listOf(member)),
            activitiesByTripId =
                mapOf(
                    trip.uid to
                        listOf(
                            suggestion,
                            suggestion.copy(uid = "planned", activityType = ActivityType.ACCEPTED),
                        )
                ),
        )
    val model = TripDetailsViewModel(trip.uid, repository, ZoneId.of("UTC"))
    assertTrue(model.uiState.value.isLoading)
    advanceUntilIdle()
    val state = model.uiState.value
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(
        TripDetails(
            "Lisbon & Porto",
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 19),
            3,
            1,
            1,
        ),
        state.details,
    )
  }

  @Test
  fun missingTripDoesNotLoadSubcollections() = runTest {
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getMembersForTrip(tripId: String): List<TripMember> =
              error("Should not read members")

          override suspend fun getActivitiesForTrip(tripId: String): List<Activity> =
              error("Should not read activities")
        }
    val model = TripDetailsViewModel("missing", repository)
    advanceUntilIdle()
    assertEquals(TripDetailsError.NOT_FOUND, model.uiState.value.error)
    assertNull(model.uiState.value.details)
  }

  @Test
  fun datesUseTheSameLocalCalendarAsTripCreation() = runTest {
    val localMidnight = Instant.parse("2026-10-11T22:00:00Z")
    val trip = detailsTestTrip().copy(startDate = Timestamp(localMidnight.epochSecond, 0))
    val model =
        TripDetailsViewModel(
            "trip",
            FakeTripRepository(trips = listOf(trip)),
            ZoneId.of("Europe/Zurich"),
        )
    advanceUntilIdle()
    assertEquals(LocalDate.of(2026, 10, 12), model.uiState.value.details?.startDate)
  }

  @Test
  fun repositoryFailureCanBeRetried() = runTest {
    var fails = true
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            if (fails) error("Private backend detail")
            return listOf(detailsTestTrip())
          }
        }
    val model = TripDetailsViewModel("trip", repository)
    advanceUntilIdle()
    assertEquals(TripDetailsError.LOAD_FAILED, model.uiState.value.error)
    fails = false
    model.refresh()
    advanceUntilIdle()
    assertNull(model.uiState.value.error)
    assertEquals(0, model.uiState.value.details?.memberCount)
    assertEquals(0, model.uiState.value.details?.suggestedPlaceCount)
  }

  @Test
  fun failedMemberReadDoesNotPresentPartialDataAsComplete() = runTest {
    val repository =
        object : TripRepository by FakeTripRepository(trips = listOf(detailsTestTrip())) {
          override suspend fun getMembersForTrip(tripId: String): List<TripMember> = error("Denied")
        }
    val model = TripDetailsViewModel("trip", repository)
    advanceUntilIdle()
    assertEquals(TripDetailsError.LOAD_FAILED, model.uiState.value.error)
    assertNull(model.uiState.value.details)
  }

  @Test
  fun retryCancelsAnOlderLoad() = runTest {
    val waiting = CompletableDeferred<List<Trip>>()
    var reads = 0
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> =
              if (++reads == 1) waiting.await() else listOf(detailsTestTrip())
        }
    val model = TripDetailsViewModel("trip", repository)
    advanceUntilIdle()
    assertTrue(model.uiState.value.isLoading)
    model.refresh()
    advanceUntilIdle()
    waiting.complete(emptyList())
    advanceUntilIdle()
    assertEquals("Lisbon & Porto", model.uiState.value.details?.name)
    assertNull(model.uiState.value.error)
  }
}

internal fun detailsTestTrip(id: String = "trip"): Trip {
  val start = Instant.parse("2026-10-12T00:00:00Z")
  val end = Instant.parse("2026-10-19T00:00:00Z")
  return Trip(
      id,
      "Lisbon & Porto",
      null,
      emptyList(),
      Timestamp(start.epochSecond, 0),
      Timestamp(end.epochSecond, 0),
      false,
      TravelMode.GROUP,
      3,
      "alex",
      listOf("alex"),
      TripStatus.PLANNED,
      Timestamp(start.epochSecond, 0),
  )
}
