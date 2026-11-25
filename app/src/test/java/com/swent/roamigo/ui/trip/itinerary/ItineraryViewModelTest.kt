// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.activity.ActivityDraft
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.initialSuggestionVoteId
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItineraryViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val subscribers = CoroutineScope(dispatcher + SupervisorJob())
  private val store = ViewModelStore()
  private val repository = ControlledActivityRepository()
  private val votes = InMemoryVoteRepository()
  private lateinit var viewModel: ItineraryViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    subscribers.cancel()
    store.clear()
    Dispatchers.resetMain()
  }

  private fun createViewModel(today: LocalDate = DAY_1) {
    val clock = Clock.fixed(today.atTime(12, 0).atZone(ZONE).toInstant(), ZONE)
    viewModel = ItineraryViewModel(TRIP_ID, repository, votes, clock)
    subscribers.launch { viewModel.uiState.collect() }
    store.put("itinerary", viewModel)
  }

  private fun ready() = viewModel.uiState.value as ItineraryUiState.Ready

  @Test
  fun listsOnlyAcceptedActivitiesOfTheSelectedDayInStartOrder() =
      runTest(dispatcher) {
        repository.activities.value =
            listOf(
                activity("late", DAY_1.atTime(18, 0)),
                activity("early", DAY_1.atTime(9, 30)),
                activity("idea", DAY_1.atTime(10, 0), ActivityType.SUGGESTION),
                activity("next day", DAY_2.atTime(8, 0)),
            )
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()

        val state = ready()
        assertEquals(DAY_1, state.selectedDay)
        assertEquals(listOf("early", "late"), state.activities.map { it.id })
        assertEquals(LocalTime.of(9, 30), state.activities.first().startTime)
        assertEquals("Lisbon", state.activities.first().locationName)
      }

  @Test
  fun offersEveryTripDayAndDaysOutsideTheTripThatHaveActivities() =
      runTest(dispatcher) {
        val outside = DAY_1.plusDays(5)
        repository.activities.value = listOf(activity("after", outside.atTime(10, 0)))
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()

        assertEquals(listOf(DAY_1, DAY_2, outside), ready().days)
      }

  @Test
  fun opensOnTodayDuringTheTripOtherwiseOnTheFirstPlannedDay() =
      runTest(dispatcher) {
        repository.activities.value = listOf(activity("museum", DAY_2.atTime(10, 0)))
        createViewModel(today = DAY_1.minusDays(30))
        viewModel.setTripDates(DAY_1, DAY_2.plusDays(1))
        runCurrent()
        assertEquals(DAY_2, ready().selectedDay)

        store.clear()
        createViewModel(today = DAY_2.plusDays(1))
        viewModel.setTripDates(DAY_1, DAY_2.plusDays(1))
        runCurrent()
        assertEquals(DAY_2.plusDays(1), ready().selectedDay)
      }

  @Test
  fun anEmptyTripOpensOnItsFirstDayAndSaysItHasNoActivity() =
      runTest(dispatcher) {
        createViewModel(today = DAY_1.minusDays(30))
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()

        val state = ready()
        assertEquals(DAY_1, state.selectedDay)
        assertFalse(state.hasActivities)
        assertTrue(state.activities.isEmpty())
      }

  @Test
  fun selectingADayShowsItsActivitiesAndAVanishedDayFallsBack() =
      runTest(dispatcher) {
        val outside = DAY_2.plusDays(3)
        repository.activities.value =
            listOf(activity("a", DAY_1.atTime(9, 0)), activity("b", outside.atTime(9, 0)))
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()

        viewModel.selectDay(outside)
        runCurrent()
        assertEquals(listOf("b"), ready().activities.map { it.id })

        // Removing the only activity outside the trip removes its day, so selection falls back.
        repository.activities.value = listOf(activity("a", DAY_1.atTime(9, 0)))
        runCurrent()
        assertEquals(DAY_1, ready().selectedDay)
        assertEquals(listOf(DAY_1, DAY_2), ready().days)
      }

  @Test
  fun followsLiveChangesIncludingAConvertedSuggestion() =
      runTest(dispatcher) {
        val idea = activity("idea", DAY_1.atTime(11, 0), ActivityType.SUGGESTION)
        repository.activities.value = listOf(idea)
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()
        assertTrue(ready().activities.isEmpty())

        repository.activities.value = listOf(idea.copy(activityType = ActivityType.ACCEPTED))
        runCurrent()
        assertEquals(listOf("idea"), ready().activities.map { it.id })

        repository.activities.value = emptyList()
        runCurrent()
        assertFalse(ready().hasActivities)
      }

  @Test
  fun groupsByLocalDayInTheClockZone() =
      runTest(dispatcher) {
        // 23:30 UTC on day 1 is already 00:30 on day 2 in Lisbon summer time (UTC+1).
        val lateUtc = DAY_1.atTime(23, 30).toInstant(ZoneOffset.UTC)
        repository.activities.value =
            listOf(activity("night", DAY_1.atTime(0, 0)).copy(startTime = timestamp(lateUtc)))
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        viewModel.selectDay(DAY_2)
        runCurrent()

        assertEquals(listOf("night"), ready().activities.map { it.id })
        assertEquals(LocalTime.of(0, 30), ready().activities.single().startTime)
      }

  @Test
  fun skipsAcceptedActivitiesWithoutAStartTime() =
      runTest(dispatcher) {
        repository.activities.value =
            listOf(
                activity("legacy", DAY_1.atTime(9, 0)).copy(startTime = null),
                activity("ok", DAY_1.atTime(10, 0)),
            )
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()

        assertEquals(listOf("ok"), ready().activities.map { it.id })
      }

  @Test
  fun aReversedTripRangeStillOffersItsFirstDay() =
      runTest(dispatcher) {
        createViewModel()
        viewModel.setTripDates(DAY_2, DAY_1)
        runCurrent()

        assertEquals(listOf(DAY_2), ready().days)
      }

  @Test
  fun aFailedFirstLoadShowsAnErrorAndRetryRecovers() =
      runTest(dispatcher) {
        repository.failNextObservation = true
        createViewModel()
        runCurrent()
        assertEquals(ItineraryUiState.Error, viewModel.uiState.value)

        repository.activities.value = listOf(activity("a", DAY_1.atTime(9, 0)))
        viewModel.retry()
        runCurrent()
        assertEquals(listOf("a"), ready().activities.map { it.id })
        assertEquals(2, repository.observations)
      }

  @Test
  fun aFailureAfterLoadingKeepsThePlanAndRetryClearsTheWarning() =
      runTest(dispatcher) {
        repository.activities.value = listOf(activity("a", DAY_1.atTime(9, 0)))
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()

        repository.failures.emit(IllegalStateException("listener lost"))
        runCurrent()
        assertTrue(ready().refreshFailed)
        assertEquals(listOf("a"), ready().activities.map { it.id })

        viewModel.retry()
        runCurrent()
        assertFalse(ready().refreshFailed)
      }

  @Test
  fun stopsObservingWhenNobodyIsSubscribed() =
      runTest(dispatcher) {
        createViewModel()
        runCurrent()
        assertEquals(1, repository.activeObservers)
        assertEquals(1, votes.activeObservers)

        subscribers.cancel()
        dispatcher.scheduler.advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, repository.activeObservers)
        assertEquals(0, votes.activeObservers)
      }

  @Test
  fun countsSuggestionsWhoseCurrentVoteRoundIsOpen() =
      runTest(dispatcher) {
        val revoted = vote(suggestionVoteId("revoted"), VoteStatus.CLOSED, nextRound = "round-2")
        votes.snapshots.value =
            Result.success(
                listOf(
                    vote(suggestionVoteId("open"), VoteStatus.OPEN),
                    vote(suggestionVoteId("decided"), VoteStatus.CLOSED),
                    revoted,
                    vote("round-2", VoteStatus.OPEN),
                    vote(suggestionVoteId("pending-round"), VoteStatus.CLOSED, nextRound = "late"),
                )
            )
        repository.activities.value =
            listOf("open", "decided", "revoted", "pending-round", "no-vote-yet").map {
              suggestion(it)
            } + suggestion("legacy").copy(linkedVoteId = null)
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()
        assertEquals(2, ready().waitingSuggestions)

        // Closing the second round settles the revoted suggestion.
        votes.snapshots.value =
            Result.success(
                votes.snapshots.value.getOrThrow().map {
                  if (it.uid == "round-2") it.copy(status = VoteStatus.CLOSED) else it
                }
            )
        runCurrent()
        assertEquals(1, ready().waitingSuggestions)

        // An accepted suggestion no longer waits, whatever its vote says.
        repository.activities.value =
            repository.activities.value.map {
              if (it.uid == "open") it.copy(activityType = ActivityType.ACCEPTED) else it
            }
        runCurrent()
        assertEquals(0, ready().waitingSuggestions)
      }

  @Test
  fun aVoteFailureHidesTheSuggestionCountButKeepsThePlan() =
      runTest(dispatcher) {
        votes.snapshots.value =
            Result.success(listOf(vote(suggestionVoteId("open"), VoteStatus.OPEN)))
        repository.activities.value = listOf(activity("a", DAY_1.atTime(9, 0)), suggestion("open"))
        createViewModel()
        viewModel.setTripDates(DAY_1, DAY_2)
        runCurrent()
        assertEquals(1, ready().waitingSuggestions)

        val loaded = votes.snapshots.value
        votes.snapshots.value = Result.failure(IllegalStateException("listener lost"))
        runCurrent()
        assertEquals(0, ready().waitingSuggestions)
        assertEquals(listOf("a"), ready().activities.map { it.id })
        assertFalse(ready().refreshFailed)

        votes.snapshots.value = loaded
        viewModel.retry()
        runCurrent()
        assertEquals(1, ready().waitingSuggestions)
      }

  private fun activity(
      id: String,
      start: LocalDateTime,
      type: ActivityType = ActivityType.ACCEPTED,
  ) =
      Activity(
          uid = id,
          tripId = TRIP_ID,
          activityType = type,
          name = id,
          description = null,
          location = TripLocation(38.7, -9.1, "Lisbon"),
          startTime = timestamp(start.atZone(ZONE).toInstant()),
          endTime = null,
          createdByUserId = "alex",
          creationTime = Timestamp(0, 0),
      )

  private fun suggestion(id: String) =
      activity(id, DAY_1.atTime(10, 0), ActivityType.SUGGESTION)
          .copy(linkedVoteId = suggestionVoteId(id))

  private fun suggestionVoteId(suggestionId: String) = initialSuggestionVoteId(suggestionId)

  private fun vote(id: String, status: VoteStatus, nextRound: String? = null) =
      Vote(
          uid = id,
          tripId = TRIP_ID,
          question = id,
          options = listOf(VoteOption("YES", "Yes"), VoteOption("NO", "No")),
          createdByUserId = "alex",
          status = status,
          creationTime = Timestamp(0, 0),
          closingTime = null,
          winningOptionId = null,
          nextRoundVoteId = nextRound,
      )

  private fun timestamp(instant: java.time.Instant) = Timestamp(instant.epochSecond, instant.nano)

  /** Emits [activities] live, and can fail the first observation or an ongoing one on demand. */
  private class ControlledActivityRepository : ActivityRepository {
    val activities = MutableStateFlow<List<Activity>>(emptyList())
    val failures = MutableSharedFlow<Exception>()
    var failNextObservation = false
    var observations = 0
    var activeObservers = 0

    override suspend fun createActivity(
        tripId: String,
        activityId: String,
        draft: ActivityDraft,
    ): Activity = throw UnsupportedOperationException()

    override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
      observations++
      if (failNextObservation) {
        failNextObservation = false
        throw IllegalStateException("offline")
      }
      activeObservers++
      try {
        emitAll(merge(activities, failures.map { throw it }))
      } finally {
        activeObservers--
      }
    }
  }

  private companion object {
    const val TRIP_ID = "trip"
    val ZONE: ZoneId = ZoneId.of("Europe/Lisbon")
    val DAY_1: LocalDate = LocalDate.of(2026, 7, 11)
    val DAY_2: LocalDate = DAY_1.plusDays(1)
  }
}
