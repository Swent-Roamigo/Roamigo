package com.swent.roamigo.ui.trip.creation

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripReadyViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun cleanup() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsTheRequestedTripWithItsOrderedRouteAndDateRange() =
      runTest(dispatcher) {
        val repo = FakeTripRepository()
        val zone = ZoneId.systemDefault()
        val date = LocalDate.of(2027, 1, 10)
        val draft =
            TripDraft(
                "Route",
                listOf(TripLocation(1.0, 2.0, "Lisbon"), TripLocation(3.0, 4.0, "Porto")),
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(7).atStartOfDay(zone).toInstant(),
                false,
                TravelMode.SOLO,
                1,
            )
        repo.createTrip("trip", draft)
        val vm = TripReadyViewModel(repo, "trip")
        assertEquals(TripReadyState.Loading, vm.state.value)
        advanceUntilIdle()
        val ready = vm.state.value as TripReadyState.Ready
        assertEquals("trip", ready.trip.uid)
        assertEquals(listOf("Lisbon", "Porto"), ready.trip.destinations.map { it.name })
        assertEquals(date, ready.trip.startDate.toDate().toInstant().atZone(zone).toLocalDate())
        assertEquals(
            date.plusDays(7),
            ready.trip.endDate.toDate().toInstant().atZone(zone).toLocalDate(),
        )
      }

  @Test
  fun missingTripProducesErrorAndCanRetry() =
      runTest(dispatcher) {
        val vm = TripReadyViewModel(FakeTripRepository(), "missing")
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
        vm.retry()
        assertEquals(TripReadyState.Loading, vm.state.value)
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
      }

  @Test
  fun invalidDataAndReadFailureDisplayErrorThenRetrySucceeds() =
      runTest(dispatcher) {
        val repo = FakeTripRepository()
        val date = LocalDate.of(2027, 1, 10).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val original =
            repo.createTrip(
                "trip",
                TripDraft(
                    "Route",
                    listOf(TripLocation(1.0, 2.0, "Lisbon")),
                    date,
                    date,
                    false,
                    TravelMode.SOLO,
                    1,
                ),
            )
        var result: Trip? = original.copy(destinations = emptyList())
        var fails = false
        val source =
            object : TripRepository by repo {
              override suspend fun getTrip(tripId: String): Trip? {
                if (fails) throw IllegalStateException("offline")
                return result
              }
            }
        val vm = TripReadyViewModel(source, "trip")
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
        result = original.copy(uid = "other")
        vm.retry()
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
        result = original.copy(destinations = listOf(TripLocation(1.0, 2.0, " ")))
        vm.retry()
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
        result = original.copy(endDate = com.google.firebase.Timestamp(date.epochSecond - 86400, 0))
        vm.retry()
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
        fails = true
        vm.retry()
        advanceUntilIdle()
        assertEquals(TripReadyState.Error, vm.state.value)
        fails = false
        result = original
        vm.retry()
        advanceUntilIdle()
        assertEquals(original.endDate, (vm.state.value as TripReadyState.Ready).trip.endDate)
      }

  @Test
  fun retryCancelsEarlierLoadAndUsesNewestResult() =
      runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repo = FakeTripRepository()
        val source =
            object : TripRepository by repo {
              override suspend fun getTrip(tripId: String): Trip? {
                calls++
                if (calls == 1) gate.await()
                return null
              }
            }
        val vm = TripReadyViewModel(source, "trip")
        runCurrent()
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(TripReadyState.Error, vm.state.value)
      }
}
