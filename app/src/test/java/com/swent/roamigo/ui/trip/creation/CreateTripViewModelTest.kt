// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.model.destination.destinationFixture
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripCreationException
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTripViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialStateHasNoSelectedDestinations() {
    val viewModel = CreateTripViewModel(destinationRepository = FakeDestinationRepository())

    assertTrue(viewModel.uiState.value.selectedDestinations.isEmpty())
    assertEquals(DestinationSearchStatus.INITIAL, viewModel.uiState.value.destinationSearchStatus)
  }

  @Test
  fun addingAndRemovingStructuredDestinationsKeepsIdentityAndRouteOrder() {
    val viewModel = CreateTripViewModel(destinationRepository = FakeDestinationRepository())
    val lisbon = destinationFixture("geonames:2267057", "Lisbon", "Portugal", "PT", 2267057)
    val porto = destinationFixture("geonames:2735943", "Porto", "Portugal", "PT", 2735943)
    val paris = destinationFixture("geonames:2988507", "Paris", "France", "FR", 2988507)

    viewModel.toggleDestination(lisbon)
    viewModel.toggleDestination(porto)
    viewModel.toggleDestination(paris)
    viewModel.removeDestination(porto)

    assertEquals(listOf(lisbon, paris), viewModel.uiState.value.selectedDestinations)
    viewModel.toggleDestination(lisbon)
    assertEquals(listOf(paris), viewModel.uiState.value.selectedDestinations)
  }

  @Test
  fun creationMapsOrderedSuggestionsDatesAndOptionsAndPersistsAuthenticatedOwner() = runTest {
    val repository = RecordingRepository()
    val zone = ZoneId.of("Europe/Zurich")
    val viewModel = CreateTripViewModel(repository, zone, FakeDestinationRepository())
    val start = LocalDate.of(LocalDate.now().year + 1, 3, 27)
    val end = start.plusDays(4)
    listOf("Prague", "Lisbon", "Barcelona", "Porto")
        .map(::sampleDestination)
        .forEach(viewModel::toggleDestination)
    viewModel.selectDate(start)
    viewModel.selectDate(end)
    viewModel.setFlexibleDates(true)
    viewModel.incrementTravelers()

    viewModel.createTrip()
    advanceUntilIdle()

    val (id, draft) = repository.requests.single()
    assertEquals("Prague → Lisbon → Barcelona → Porto", draft.name)
    assertEquals(
        listOf(
            TripLocation(50.0755, 14.4378, "Prague"),
            TripLocation(38.7223, -9.1393, "Lisbon"),
            TripLocation(41.3874, 2.1686, "Barcelona"),
            TripLocation(41.1579, -8.6291, "Porto"),
        ),
        draft.destinations,
    )
    assertEquals(start.atStartOfDay(zone).toInstant(), draft.startDate)
    assertEquals(end.atStartOfDay(zone).toInstant(), draft.endDate)
    assertTrue(draft.flexibleDates)
    assertEquals(TravelMode.GROUP, draft.travelMode)
    assertEquals(4, draft.travelerCount)
    assertTrue(draft.validate().isEmpty())
    assertEquals(id, viewModel.uiState.value.createdTripId)
    assertFalse(viewModel.uiState.value.isSubmitting)
    assertNull(viewModel.uiState.value.creationError)
    assertFalse(viewModel.uiState.value.canCreateTrip)
    assertEquals("owner", repository.getTrips().single().ownerId)
    assertEquals(listOf("owner"), repository.getTrips().single().memberIds)
    viewModel.createTrip()
    advanceUntilIdle()
    assertEquals(1, repository.requests.size)
  }

  @Test
  fun soloDraftAndInvalidDestinationsAreHandledBeforeSaving() = runTest {
    val repository = RecordingRepository()
    val viewModel =
        CreateTripViewModel(repository, destinationRepository = FakeDestinationRepository())
    viewModel.toggleDestination(sampleDestination("Lisbon").copy(name = ""))
    viewModel.createTrip()
    advanceUntilIdle()
    assertTrue(repository.requests.isEmpty())
    assertTrue(viewModel.uiState.value.selectedDestinations.isEmpty())

    viewModel.toggleDestination(sampleDestination("Porto"))
    viewModel.selectTravelMode(TravelMode.SOLO)
    viewModel.createTrip()
    advanceUntilIdle()
    assertEquals("Porto", repository.requests.single().second.name)
    assertEquals(TravelMode.SOLO, repository.requests.single().second.travelMode)
    assertEquals(1, repository.requests.single().second.travelerCount)
  }

  @Test
  fun pendingSubmissionIgnoresRepeatedTapsAndFreezesDraft() = runTest {
    val repository = RecordingRepository().apply { gate = CompletableDeferred() }
    val viewModel =
        CreateTripViewModel(repository, destinationRepository = FakeDestinationRepository())
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.createTrip()
    viewModel.createTrip()
    runCurrent()
    assertTrue(viewModel.uiState.value.isSubmitting)
    assertFalse(viewModel.uiState.value.canCreateTrip)
    val pendingState = viewModel.uiState.value
    viewModel.toggleDestination(sampleDestination("Porto"))
    viewModel.removeDestination(sampleDestination("Lisbon"))
    viewModel.updateDestinationQuery("Porto")
    viewModel.selectDate(LocalDate.now().plusDays(10))
    viewModel.setFlexibleDates(true)
    viewModel.selectTravelMode(TravelMode.SOLO)
    viewModel.incrementTravelers()
    viewModel.decrementTravelers()
    viewModel.showNextMonth()
    viewModel.showPreviousMonth()
    viewModel.createTrip()
    assertEquals(pendingState, viewModel.uiState.value)
    assertEquals(1, repository.requests.size)
    repository.gate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(1, repository.getTrips().size)
  }

  @Test
  fun typedFailurePreservesDraftAndRetryReusesIdAndPayload() = runTest {
    val repository = RecordingRepository().apply { failure = TripCreationException.Network() }
    val viewModel =
        CreateTripViewModel(repository, destinationRepository = FakeDestinationRepository())
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.createTrip()
    advanceUntilIdle()
    assertSame(repository.failure, viewModel.uiState.value.creationError)
    assertEquals(listOf(sampleDestination("Lisbon")), viewModel.uiState.value.selectedDestinations)
    assertFalse(viewModel.uiState.value.isSubmitting)
    assertTrue(viewModel.uiState.value.canCreateTrip)
    repository.failure = null
    viewModel.createTrip()
    advanceUntilIdle()
    assertEquals(repository.requests[0], repository.requests[1])
    assertEquals(1, repository.getTrips().size)
  }

  @Test
  fun editingAfterFailureCreatesANewSubmission() = runTest {
    val repository = RecordingRepository().apply { failure = TripCreationException.Network() }
    val viewModel =
        CreateTripViewModel(repository, destinationRepository = FakeDestinationRepository())
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.createTrip()
    advanceUntilIdle()
    viewModel.toggleDestination(sampleDestination("Porto"))
    repository.failure = null
    viewModel.createTrip()
    advanceUntilIdle()
    assertNotEquals(repository.requests[0].first, repository.requests[1].first)
    assertEquals("Lisbon → Porto", repository.requests[1].second.name)
  }

  @Test
  fun authenticationAndPermissionFailuresNeverReportSuccess() = runTest {
    listOf(TripCreationException.NotAuthenticated(), TripCreationException.PermissionDenied())
        .forEach { failure ->
          val repository = RecordingRepository().apply { this.failure = failure }
          val viewModel =
              CreateTripViewModel(repository, destinationRepository = FakeDestinationRepository())
          viewModel.toggleDestination(sampleDestination("Lisbon"))
          viewModel.createTrip()
          advanceUntilIdle()
          assertSame(failure, viewModel.uiState.value.creationError)
          assertNull(viewModel.uiState.value.createdTripId)
          assertFalse(viewModel.uiState.value.isSubmitting)
          assertTrue(repository.getTrips().isEmpty())
        }
  }

  @Test
  fun unexpectedFailureIsWrappedButCancellationIsNotShownAsAnError() = runTest {
    val repository = RecordingRepository().apply { failure = IllegalStateException("unexpected") }
    val viewModel =
        CreateTripViewModel(repository, destinationRepository = FakeDestinationRepository())
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.createTrip()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.creationError is TripCreationException.Unknown)
    assertSame(repository.failure, viewModel.uiState.value.creationError?.cause)
    repository.failure = CancellationException("cancelled")
    viewModel.createTrip()
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.creationError)
    assertNull(viewModel.uiState.value.createdTripId)
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  private class RecordingRepository(
      private val delegate: TripRepository = FakeTripRepository(currentUserId = "owner")
  ) : TripRepository by delegate {
    val requests = mutableListOf<Pair<String, TripDraft>>()
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun createTrip(tripId: String, draft: TripDraft): Trip {
      requests += tripId to draft
      gate?.await()
      failure?.let { throw it }
      return delegate.createTrip(tripId, draft)
    }
  }
}
