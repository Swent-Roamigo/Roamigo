// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlacePickerViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val candidate = PlaceCandidate("cafe", "Café Romand", "Lausanne", "Provider notice")
  private val original = TripLocation(0.0, 0.0, "Original")
  private val stores = mutableListOf<ViewModelStore>()

  @Before
  fun setup() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun cleanup() {
    stores.forEach { it.clear() }
    Dispatchers.resetMain()
  }

  private fun picker(
      repo: PlaceRepository = FakePlaceRepository(),
      handle: SavedStateHandle = SavedStateHandle(),
      initial: TripLocation? = null,
      proximity: PlaceProximity? = null,
  ): PlacePickerViewModel {
    val store = ViewModelStore().also { stores += it }
    return ViewModelProvider(
        store,
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
              PlacePickerViewModel(repo, handle, initial, proximity) as T
        },
    )[PlacePickerViewModel::class.java]
  }

  private fun repository() = FakePlaceRepository(search = { _, _ -> listOf(candidate) })

  @Test
  fun queryIsLimitedAtProviderBoundary() =
      runTest(dispatcher) {
        val repo = repository()
        val vm = picker(repo)
        val boundary = "a".repeat(MAX_PLACE_QUERY_LENGTH)
        vm.updateQuery(boundary)
        advanceUntilIdle()
        assertEquals(boundary, vm.uiState.value.query)
        vm.updateQuery(boundary + "b")
        advanceUntilIdle()
        assertEquals(boundary, vm.uiState.value.query)
        assertEquals(1, repo.searchRequests.size)
        assertEquals(boundary, repo.searchRequests.single().first)
      }

  @Test
  fun shortQueriesSkipRepositoryAndDebounceIsExactly300Millis() =
      runTest(dispatcher) {
        val repo = repository()
        val vm = picker(repo)
        listOf("", " ", "a", " ab ").forEach {
          vm.updateQuery(it)
          advanceUntilIdle()
        }
        assertTrue(repo.searchRequests.isEmpty())
        vm.updateQuery("  café  ")
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertEquals(PlaceSearchStatus.LOADING, vm.uiState.value.searchStatus)
        assertTrue(repo.searchRequests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("café" to null), repo.searchRequests)
        assertEquals(listOf(candidate), vm.uiState.value.candidates)
        vm.updateQuery("café ")
        advanceUntilIdle()
        assertEquals(1, repo.searchRequests.size)
        assertEquals("Provider notice", vm.uiState.value.attribution)
      }

  @Test
  fun emptyAndMalformedCandidatesAreNotSelectable() =
      runTest(dispatcher) {
        val repo =
            FakePlaceRepository(
                search = { _, _ ->
                  listOf(PlaceCandidate("", "Bad", null), PlaceCandidate("x", " ", null))
                }
            )
        val vm = picker(repo)
        vm.updateQuery("café")
        advanceUntilIdle()
        assertEquals(PlaceSearchStatus.EMPTY, vm.uiState.value.searchStatus)
        vm.selectCandidate(candidate)
        advanceUntilIdle()
        assertTrue(repo.resolveRequests.isEmpty())
        repo.search = { _, _ -> emptyList() }
        vm.retry()
        advanceUntilIdle()
        assertEquals(PlaceSearchStatus.EMPTY, vm.uiState.value.searchStatus)
      }

  @Test
  fun allTypedSearchAndResolutionErrorsPreserveSelectionAndRetryFreshSearch() =
      runTest(dispatcher) {
        val failures =
            listOf(
                PlaceSearchException.Configuration() to PlacePickerError.CONFIGURATION,
                PlaceSearchException.InvalidToken() to PlacePickerError.INVALID_TOKEN,
                PlaceSearchException.QuotaExceeded() to PlacePickerError.QUOTA,
                PlaceSearchException.InvalidPlace() to PlacePickerError.INVALID_PLACE,
                PlaceSearchException.Unavailable("secret") to PlacePickerError.UNAVAILABLE,
                IllegalStateException("secret") to PlacePickerError.UNAVAILABLE,
            )
        failures.forEach { (failure, expected) ->
          val repo = repository()
          repo.search = { _, _ -> throw failure }
          val vm = picker(repo, initial = original)
          vm.updateQuery("café")
          advanceUntilIdle()
          assertEquals(expected, vm.uiState.value.searchError)
          assertEquals(original, vm.uiState.value.selectedLocation)
          repo.search = { _, _ -> listOf(candidate) }
          vm.retry()
          runCurrent()
          assertEquals(listOf(candidate), vm.uiState.value.candidates)
          repo.resolve = { throw failure }
          vm.selectCandidate(candidate)
          runCurrent()
          assertEquals(expected, vm.uiState.value.resolutionError)
          assertEquals(original, vm.uiState.value.selectedLocation)
          assertTrue(vm.uiState.value.candidates.isEmpty())
          vm.selectCandidate(candidate)
          runCurrent()
          assertEquals(1, repo.resolveRequests.size)
          vm.retry()
          runCurrent()
          assertEquals(3, repo.searchRequests.size)
          repo.resolve = { Place("Chosen", null, 90.0, -180.0) }
          vm.selectCandidate(candidate)
          runCurrent()
          assertEquals("Chosen", vm.uiState.value.selectedLocation?.name)
          assertNull(vm.uiState.value.resolutionError)
        }
      }

  @Test
  fun resolvesOnceThenConfirmsTrimmedLocationExactlyOnce() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<Place>()
        val repo = repository()
        repo.resolve = { answer.await() }
        val vm = picker(repo, proximity = PlaceProximity(46.0, 6.0))
        vm.confirm()
        assertNull(vm.consumeResult())
        vm.updateQuery("café")
        advanceUntilIdle()
        assertEquals(PlaceProximity(46.0, 6.0), repo.searchRequests.single().second)
        vm.selectCandidate(candidate)
        vm.selectCandidate(candidate)
        runCurrent()
        assertEquals(candidate.id, vm.uiState.value.resolvingCandidateId)
        assertFalse(vm.uiState.value.canConfirm)
        vm.retry()
        runCurrent()
        assertEquals(1, repo.resolveRequests.size)
        answer.complete(Place("  Café  ", null, -90.0, 180.0, "CH", "Resolved notice"))
        runCurrent()
        assertTrue(vm.uiState.value.canConfirm)
        assertEquals("Resolved notice", vm.uiState.value.attribution)
        vm.confirm()
        vm.confirm()
        assertEquals(TripLocation(-90.0, 180.0, "Café", "CH"), vm.consumeResult())
        assertNull(vm.consumeResult())
        assertFalse(vm.cancel())
        vm.updateQuery("ignored")
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, repo.searchRequests.size)
      }

  @Test
  fun rejectsInvalidProviderNamesCoordinatesAndCountryCodes() =
      runTest(dispatcher) {
        val invalid =
            listOf(
                Place(" ", null, 0.0, 0.0),
                Place("x", null, Double.NaN, 0.0),
                Place("x", null, 0.0, Double.NaN),
                Place("x", null, Double.POSITIVE_INFINITY, 0.0),
                Place("x", null, 0.0, Double.NEGATIVE_INFINITY),
                Place("x", null, 90.1, 0.0),
                Place("x", null, 0.0, -180.1),
                Place("x", null, 0.0, 0.0, "ch"),
            )
        invalid.forEach { place ->
          val repo = repository()
          repo.resolve = { place }
          val vm = picker(repo)
          vm.updateQuery("café")
          advanceUntilIdle()
          vm.selectCandidate(candidate)
          runCurrent()
          assertEquals(PlacePickerError.INVALID_PLACE, vm.uiState.value.resolutionError)
          assertNull(vm.uiState.value.selectedLocation)
          vm.confirm()
          assertNull(vm.consumeResult())
        }
      }

  @Test
  fun repeatedQueryAndRetryRacesCannotPublishStaleResultsOrOverlapRequests() =
      runTest(dispatcher) {
        val first = CompletableDeferred<List<PlaceCandidate>>()
        var active = 0
        var maximum = 0
        var calls = 0
        val repo =
            FakePlaceRepository(
                search = { _, _ ->
                  active++
                  maximum = maxOf(maximum, active)
                  try {
                    if (++calls == 1) withContext(NonCancellable) { first.await() }
                    else listOf(candidate)
                  } finally {
                    active--
                  }
                }
            )
        val vm = picker(repo)
        vm.updateQuery("AAA")
        advanceTimeBy(300)
        runCurrent()
        vm.updateQuery("BBB")
        advanceTimeBy(300)
        runCurrent()
        vm.updateQuery("AAA")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, repo.searchRequests.size)
        first.complete(listOf(PlaceCandidate("stale", "Stale", null)))
        runCurrent()
        assertEquals(listOf("AAA", "AAA"), repo.searchRequests.map { it.first })
        assertEquals(listOf(candidate), vm.uiState.value.candidates)
        assertEquals(1, maximum)
        val retryAnswer = CompletableDeferred<List<PlaceCandidate>>()
        repo.search = { _, _ -> withContext(NonCancellable) { retryAnswer.await() } }
        vm.retry()
        runCurrent()
        vm.retry()
        runCurrent()
        retryAnswer.complete(listOf(PlaceCandidate("old-retry", "Old", null)))
        repo.search = { _, _ -> listOf(candidate) }
        runCurrent()
        assertEquals(listOf(candidate), vm.uiState.value.candidates)
      }

  @Test
  fun lateResolutionCannotReplacePreviousSelectionAndNewSearchWaitsForIt() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<Place>()
        val repo = repository()
        repo.resolve = { withContext(NonCancellable) { answer.await() } }
        val vm = picker(repo, initial = original)
        vm.updateQuery("café")
        advanceUntilIdle()
        vm.selectCandidate(candidate)
        runCurrent()
        vm.updateQuery("other")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, repo.searchRequests.size)
        answer.complete(Place("Late", null, 1.0, 2.0))
        runCurrent()
        assertEquals(original, vm.uiState.value.selectedLocation)
        assertEquals(2, repo.searchRequests.size)
        assertNull(vm.uiState.value.resolutionError)
      }

  @Test
  fun sharedRepositorySerializesPickersAndInvalidatesTheirOldCandidates() =
      runTest(dispatcher) {
        val repo = repository()
        val first = picker(repo)
        val second = picker(repo)
        first.updateQuery("first")
        advanceUntilIdle()
        second.updateQuery("second")
        advanceUntilIdle()
        first.selectCandidate(candidate)
        runCurrent()
        assertTrue(repo.resolveRequests.isEmpty())
        assertEquals(3, repo.searchRequests.size)
        first.selectCandidate(candidate)
        runCurrent()
        assertEquals(1, repo.resolveRequests.size)
        second.selectCandidate(candidate)
        runCurrent()
        assertEquals(1, repo.resolveRequests.size)
        assertEquals(4, repo.searchRequests.size)
      }

  @Test
  fun queuedResolutionRefreshesCandidatesIfAnotherPickerUsedTheSessionFirst() =
      runTest(dispatcher) {
        val repo = repository()
        val first = picker(repo)
        first.updateQuery("first")
        advanceUntilIdle()
        val second = picker(repo)
        second.updateQuery("second")
        second.retry()
        first.selectCandidate(candidate)
        runCurrent()
        assertTrue(repo.resolveRequests.isEmpty())
        assertEquals(listOf("first", "second", "first"), repo.searchRequests.map { it.first })
        assertEquals(listOf(candidate), first.uiState.value.candidates)
      }

  @Test
  fun staleFailuresAndCancelledQueriesNeverReplaceCurrentSearchState() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<Unit>()
        val repo = repository()
        repo.search = { _, _ ->
          withContext(NonCancellable) {
            answer.await()
            throw PlaceSearchException.Unavailable("old failure")
          }
        }
        val vm = picker(repo, initial = original)
        vm.updateQuery("first")
        advanceTimeBy(300)
        runCurrent()
        vm.updateQuery("second")
        advanceTimeBy(300)
        runCurrent()
        repo.search = { _, _ -> listOf(candidate) }
        answer.complete(Unit)
        runCurrent()
        assertEquals(PlaceSearchStatus.RESULTS, vm.uiState.value.searchStatus)
        assertNull(vm.uiState.value.searchError)
        vm.updateQuery("a")
        runCurrent()
        assertEquals(PlaceSearchStatus.INITIAL, vm.uiState.value.searchStatus)
        assertEquals(original, vm.uiState.value.selectedLocation)
      }

  @Test
  fun cancelStopsWorkWithoutPublishingErrorsOrChangingCallerSelection() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<Place>()
        val repo = repository()
        repo.resolve = { withContext(NonCancellable) { answer.await() } }
        val vm = picker(repo, initial = original)
        vm.updateQuery("café")
        advanceUntilIdle()
        vm.selectCandidate(candidate)
        runCurrent()
        assertTrue(vm.cancel())
        assertFalse(vm.cancel())
        vm.confirm()
        answer.complete(Place("Late", null, 1.0, 2.0))
        runCurrent()
        assertEquals(original, vm.uiState.value.selectedLocation)
        assertNull(vm.consumeResult())
        assertNull(vm.uiState.value.resolutionError)
      }

  @Test
  fun savedPrimitivesRestoreSelectionButNeverCandidatesOrInFlightRetrieval() =
      runTest(dispatcher) {
        val handle = SavedStateHandle()
        val repo = repository()
        val vm = picker(repo, handle, original)
        vm.updateQuery("café")
        advanceUntilIdle()
        vm.selectCandidate(candidate)
        runCurrent()
        val restored = picker(repo, handle, TripLocation(1.0, 1.0, "Ignored"))
        assertEquals(vm.uiState.value.selectedLocation, restored.uiState.value.selectedLocation)
        assertEquals("café", restored.uiState.value.query)
        assertTrue(restored.uiState.value.candidates.isEmpty())
        assertNull(restored.uiState.value.resolvingCandidateId)
        assertEquals(PlaceSearchStatus.INITIAL, restored.uiState.value.searchStatus)
        assertEquals(1, repo.resolveRequests.size)
        restored.confirm()
        val awaitingDelivery = picker(repo, handle)
        assertEquals(restored.uiState.value.selectedLocation, awaitingDelivery.consumeResult())
        assertNull(awaitingDelivery.consumeResult())
        restored.consumeResult()
        val completed = picker(repo, handle)
        assertFalse(completed.uiState.value.canConfirm)
        assertNull(completed.consumeResult())
        val reopened = picker(repo)
        assertNull(reopened.uiState.value.selectedLocation)
        assertFalse(reopened.uiState.value.finished)
      }

  @Test
  fun processRestorationRefreshesActiveSearchWithoutSavingProviderCandidates() =
      runTest(dispatcher) {
        val handle = SavedStateHandle()
        val old = picker(repository(), handle, original)
        old.updateQuery("café")
        advanceUntilIdle()
        stores.last().clear()
        val freshCandidate = candidate.copy(id = "fresh-session")
        val repo = FakePlaceRepository(search = { _, _ -> listOf(freshCandidate) })
        val restored = picker(repo, handle)
        assertEquals(PlaceSearchStatus.LOADING, restored.uiState.value.searchStatus)
        assertTrue(restored.uiState.value.candidates.isEmpty())
        assertEquals(original, restored.uiState.value.selectedLocation)
        assertFalse(restored.uiState.value.canConfirm)
        restored.confirm()
        assertNull(restored.consumeResult())
        runCurrent()
        assertEquals(listOf(freshCandidate), restored.uiState.value.candidates)
        restored.selectCandidate(candidate)
        runCurrent()
        assertTrue(repo.resolveRequests.isEmpty())
        assertEquals(listOf("café" to null), repo.searchRequests)
      }

  @Test
  fun interruptedSearchOrRetrievalRestoresAsFreshSearchAndNeverRepeatsRetrieve() =
      runTest(dispatcher) {
        listOf(false, true).forEach { resolving ->
          val handle = SavedStateHandle()
          val pending = CompletableDeferred<List<PlaceCandidate>>()
          val oldRepo = repository()
          if (!resolving) oldRepo.search = { _, _ -> pending.await() }
          else oldRepo.resolve = { awaitCancellation() }
          val old = picker(oldRepo, handle, original)
          old.updateQuery("café")
          advanceTimeBy(300)
          runCurrent()
          if (resolving) {
            old.selectCandidate(candidate)
            runCurrent()
          }
          stores.last().clear()
          val repo = repository()
          val restored = picker(repo, handle)
          assertNull(restored.uiState.value.resolvingCandidateId)
          runCurrent()
          assertEquals(listOf(candidate), restored.uiState.value.candidates)
          assertEquals(original, restored.uiState.value.selectedLocation)
          assertEquals(1, repo.searchRequests.size)
          assertTrue(repo.resolveRequests.isEmpty())
        }
      }

  @Test
  fun clearingOwnerInvalidatesNonCooperativeSearchAndStopsItPublishing() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<List<PlaceCandidate>>()
        val repo =
            FakePlaceRepository(search = { _, _ -> withContext(NonCancellable) { answer.await() } })
        val vm = picker(repo)
        vm.updateQuery("café")
        advanceTimeBy(300)
        runCurrent()
        stores.last().clear()
        answer.complete(listOf(candidate))
        runCurrent()
        assertTrue(vm.uiState.value.candidates.isEmpty())
        assertEquals(PlaceSearchStatus.INITIAL, vm.uiState.value.searchStatus)
      }

  @Test
  fun finishedRestoredPickerNeverRestartsEvenIfSearchFlagWasSaved() =
      runTest(dispatcher) {
        val repo = repository()
        val vm =
            picker(
                repo,
                SavedStateHandle(
                    mapOf("searchActive" to true, "finished" to true, "query" to "café")
                ),
            )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        assertTrue(repo.searchRequests.isEmpty())
      }

  @Test
  fun invalidRestoredOrInjectedLocationsAndProximityAreRejected() =
      runTest(dispatcher) {
        val invalidStates =
            listOf(
                mapOf("name" to " ", "latitude" to 0.0, "longitude" to 0.0),
                mapOf("name" to "x", "latitude" to Double.NaN, "longitude" to 0.0),
                mapOf("name" to "x", "latitude" to 0.0, "longitude" to 0.0, "country" to "bad"),
                mapOf("name" to "x"),
                mapOf("name" to "x", "latitude" to 0.0),
                emptyMap(),
            )
        invalidStates.forEach { fields ->
          val vm = picker(handle = SavedStateHandle(fields + ("initialized" to true)))
          assertNull(vm.uiState.value.selectedLocation)
        }
        assertNull(
            picker(initial = TripLocation(91.0, 0.0, "Invalid")).uiState.value.selectedLocation
        )
        assertNull(picker(initial = TripLocation(0.0, 0.0, " ")).uiState.value.selectedLocation)
        val repo = repository()
        val vm = picker(repo, proximity = PlaceProximity(Double.NaN, 0.0))
        vm.updateQuery("café")
        advanceUntilIdle()
        assertNull(repo.searchRequests.single().second)
        vm.stop()
        assertTrue(vm.uiState.value.candidates.isEmpty())
        vm.updateQuery("another")
        runCurrent()
        vm.stop()
        advanceUntilIdle()
        assertEquals(PlaceSearchStatus.INITIAL, vm.uiState.value.searchStatus)
      }

  @Test
  fun mapStartsFromSelectionThenProximityAndNeverUsesZeroAsMissing() =
      runTest(dispatcher) {
        val nearby = PlaceProximity(46.0, 6.0)
        val vm = picker(initial = original.copy(countryCode = "CH"), proximity = nearby)
        vm.enterMap()
        assertTrue(vm.uiState.value.isMapMode)
        assertEquals(original.copy(countryCode = "CH"), vm.uiState.value.manualDraft.location)
        assertEquals(0.0, vm.uiState.value.mapInitialCenter!!.latitude, 0.0)
        assertTrue(vm.uiState.value.canConfirm)
        val empty = picker(proximity = nearby)
        empty.enterMap()
        assertNull(empty.uiState.value.manualDraft.point)
        assertEquals(46.0, empty.uiState.value.mapInitialCenter!!.latitude, 0.0)
        assertEquals(6.0, empty.uiState.value.mapInitialCenter!!.longitude, 0.0)
        assertFalse(empty.uiState.value.canConfirm)
        val overview = picker(proximity = PlaceProximity(Double.NaN, 0.0))
        overview.enterMap()
        assertNull(overview.uiState.value.mapInitialCenter)
      }

  @Test
  fun manualPointAndNameAreBothRequiredAndReplacingPointRequiresNewName() =
      runTest(dispatcher) {
        val vm = picker()
        vm.selectMapPoint(1.0, 2.0)
        vm.updateMapName("Ignored outside map")
        assertNull(vm.uiState.value.manualDraft.point)
        vm.enterMap()
        vm.updateMapName("Name without point")
        assertEquals("", vm.uiState.value.manualDraft.name)
        assertFalse(vm.uiState.value.canConfirm)
        vm.confirm()
        assertNull(vm.consumeResult())
        vm.selectMapPoint(0.0, 0.0)
        assertEquals("", vm.uiState.value.manualDraft.name)
        listOf("", "  ", "\t\n").forEach {
          vm.updateMapName(it)
          assertFalse(vm.uiState.value.canConfirm)
          vm.confirm()
          assertNull(vm.consumeResult())
        }
        vm.updateMapName("  Meeting point  ")
        assertTrue(vm.uiState.value.canConfirm)
        vm.selectMapPoint(0.0, 0.0)
        assertEquals("  Meeting point  ", vm.uiState.value.manualDraft.name)
        vm.selectMapPoint(1.0, 2.0)
        assertEquals(1.0, vm.uiState.value.manualDraft.point!!.latitude, 0.0)
        assertEquals(2.0, vm.uiState.value.manualDraft.point!!.longitude, 0.0)
        assertEquals("", vm.uiState.value.manualDraft.name)
        assertFalse(vm.uiState.value.canConfirm)
        vm.updateMapName("  New point  ")
        vm.confirm()
        vm.confirm()
        assertEquals(TripLocation(1.0, 2.0, "New point"), vm.consumeResult())
        assertNull(vm.consumeResult())
        vm.selectMapPoint(3.0, 4.0)
        vm.updateMapName("Too late")
        vm.enterMap()
        vm.backToSearch()
        assertEquals("  New point  ", vm.uiState.value.manualDraft.name)
        assertTrue(vm.uiState.value.isMapMode)
      }

  @Test
  fun manualCoordinatesRejectNonFiniteAndOutOfRangeValuesButAcceptBoundaries() =
      runTest(dispatcher) {
        val vm = picker(initial = original)
        vm.enterMap()
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -91.0, 91.0)
            .forEach { vm.selectMapPoint(it, 0.0) }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -181.0, 181.0)
            .forEach { vm.selectMapPoint(0.0, it) }
        assertEquals(original, vm.uiState.value.manualDraft.location)
        listOf(-90.0 to -180.0, 90.0 to 180.0, 0.0 to 0.0).forEach { (lat, lon) ->
          vm.selectMapPoint(lat, lon)
          vm.updateMapName("Boundary")
          assertEquals(TripLocation(lat, lon, "Boundary"), vm.uiState.value.manualDraft.location)
          assertTrue(vm.uiState.value.canConfirm)
        }
      }

  @Test
  fun resolvedPlaceKeepsCountryUntilMarkerMovesAndBackDiscardsManualChanges() =
      runTest(dispatcher) {
        val repo = repository().apply { resolve = { Place("Resolved", null, 46.0, 6.0, "CH") } }
        val vm = picker(repo, initial = original)
        vm.updateQuery("café")
        advanceUntilIdle()
        vm.selectCandidate(candidate)
        advanceUntilIdle()
        val selected = TripLocation(46.0, 6.0, "Resolved", "CH")
        vm.enterMap()
        vm.enterMap()
        assertEquals(selected, vm.uiState.value.manualDraft.location)
        vm.selectMapPoint(46.0, 6.0)
        assertEquals("CH", vm.uiState.value.manualDraft.countryCode)
        vm.selectMapPoint(46.0, 6.1)
        assertNull(vm.uiState.value.manualDraft.countryCode)
        assertEquals("", vm.uiState.value.manualDraft.name)
        vm.updateMapName("Elsewhere")
        vm.backToSearch()
        assertFalse(vm.uiState.value.isMapMode)
        assertEquals("café", vm.uiState.value.query)
        assertEquals(selected, vm.uiState.value.selectedLocation)
        assertNull(vm.consumeResult())
        vm.enterMap()
        assertEquals(selected, vm.uiState.value.manualDraft.location)
        vm.confirm()
        assertEquals(selected, vm.consumeResult())
      }

  @Test
  fun enteringMapInvalidatesLateSearchSuccessAndFailureAndRejectsSearchActions() =
      runTest(dispatcher) {
        listOf(false, true).forEach { fail ->
          val answer = CompletableDeferred<List<PlaceCandidate>>()
          val repo =
              FakePlaceRepository(
                  search = { _, _ -> withContext(NonCancellable) { answer.await() } }
              )
          val vm = picker(repo, initial = original)
          vm.updateQuery("café")
          advanceTimeBy(300)
          runCurrent()
          vm.enterMap()
          vm.selectMapPoint(1.0, 2.0)
          vm.updateMapName("Manual")
          vm.updateQuery("Ignored")
          vm.retry()
          vm.selectCandidate(candidate)
          if (fail) answer.completeExceptionally(PlaceSearchException.Unavailable("Late"))
          else answer.complete(listOf(candidate))
          advanceUntilIdle()
          assertTrue(vm.uiState.value.candidates.isEmpty())
          assertNull(vm.uiState.value.searchError)
          assertEquals("café", vm.uiState.value.query)
          assertEquals(TripLocation(1.0, 2.0, "Manual"), vm.uiState.value.manualDraft.location)
          assertEquals(original, vm.uiState.value.selectedLocation)
          assertEquals(1, repo.searchRequests.size)
          assertTrue(repo.resolveRequests.isEmpty())
        }
      }

  @Test
  fun enteringMapInvalidatesLateResolutionSuccessAndFailure() =
      runTest(dispatcher) {
        listOf(false, true).forEach { fail ->
          val answer = CompletableDeferred<Place>()
          val repo =
              repository().apply { resolve = { withContext(NonCancellable) { answer.await() } } }
          val vm = picker(repo, initial = original)
          vm.updateQuery("café")
          advanceUntilIdle()
          vm.selectCandidate(candidate)
          runCurrent()
          vm.enterMap()
          vm.selectMapPoint(1.0, 2.0)
          vm.updateMapName("Manual")
          if (fail) answer.completeExceptionally(PlaceSearchException.InvalidPlace())
          else answer.complete(Place("Late", null, 3.0, 4.0))
          advanceUntilIdle()
          assertEquals(original, vm.uiState.value.selectedLocation)
          assertEquals(TripLocation(1.0, 2.0, "Manual"), vm.uiState.value.manualDraft.location)
          assertNull(vm.uiState.value.resolutionError)
          assertNull(vm.uiState.value.resolvingCandidateId)
        }
      }

  @Test
  fun manualDraftAndModeRestoreWithoutProviderCallsAndResultRestoresOnce() =
      runTest(dispatcher) {
        val repo = repository()
        val handle = SavedStateHandle()
        val vm = picker(repo, handle, original)
        vm.updateQuery("prior query")
        vm.enterMap()
        vm.selectMapPoint(0.0, 180.0)
        vm.updateMapName("  Draft  ")
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = picker(repo, restoredHandle)
        assertTrue(restored.uiState.value.isMapMode)
        assertEquals(vm.uiState.value.manualDraft, restored.uiState.value.manualDraft)
        assertEquals("prior query", restored.uiState.value.query)
        assertEquals(original, restored.uiState.value.selectedLocation)
        advanceUntilIdle()
        assertTrue(repo.searchRequests.isEmpty())
        restored.confirm()
        val pending = picker(repo, restoredHandle)
        assertEquals(TripLocation(0.0, 180.0, "Draft"), pending.consumeResult())
        assertNull(pending.consumeResult())
        val reopened = picker(repo)
        assertFalse(reopened.uiState.value.isMapMode)
        assertNull(reopened.uiState.value.manualDraft.point)
      }

  @Test
  fun invalidSavedManualCoordinatesAreDroppedAndMapFailureNeverDiscardsDraft() =
      runTest(dispatcher) {
        listOf(
                mapOf("mapLatitude" to Double.NaN, "mapLongitude" to 1.0),
                mapOf("mapLatitude" to 1.0),
                mapOf("mapLongitude" to 1.0),
                emptyMap(),
            )
            .forEach { fields ->
              val vm =
                  picker(
                      handle =
                          SavedStateHandle(fields + mapOf("mapMode" to true, "mapName" to "Draft"))
                  )
              assertFalse(vm.uiState.value.canConfirm)
              assertNull(vm.uiState.value.manualDraft.point)
            }
        val vm = picker(initial = original)
        vm.enterMap()
        vm.mapLoadingFailed()
        assertTrue(vm.uiState.value.mapError)
        assertEquals(original, vm.uiState.value.manualDraft.location)
        vm.retryMap()
        assertFalse(vm.uiState.value.mapError)
        assertEquals(1, vm.uiState.value.mapLoadAttempt)
        vm.mapLoadingFailed()
        vm.mapLoaded()
        assertFalse(vm.uiState.value.mapError)
        vm.backToSearch()
        assertEquals(original, vm.uiState.value.selectedLocation)
        assertTrue(vm.cancel())
        assertNull(vm.consumeResult())
      }

  @Test
  fun mapCallbacksAndRetriesIgnoreClosedAndCompletedModes() =
      runTest(dispatcher) {
        val vm = picker(initial = original)
        val searchState = vm.uiState.value
        vm.mapLoadingFailed()
        vm.mapLoaded()
        vm.retryMap()
        assertEquals(searchState, vm.uiState.value)
        vm.enterMap()
        vm.mapLoadingFailed()
        vm.backToSearch()
        val returnedState = vm.uiState.value
        vm.mapLoaded()
        vm.mapLoadingFailed()
        vm.retryMap()
        assertEquals(returnedState, vm.uiState.value)
        vm.enterMap()
        vm.mapLoadingFailed()
        vm.confirm()
        val finishedState = vm.uiState.value
        vm.mapLoaded()
        vm.mapLoadingFailed()
        vm.retryMap()
        assertEquals(finishedState, vm.uiState.value)
        assertEquals(original, vm.consumeResult())
        assertNull(vm.consumeResult())
      }

  @Test
  fun incompleteRestoredMapDraftFramesExistingSelectionBeforeProximity() =
      runTest(dispatcher) {
        val handle =
            SavedStateHandle(
                mapOf(
                    "initialized" to true,
                    "name" to "Original",
                    "latitude" to 0.0,
                    "longitude" to 0.0,
                    "mapMode" to true,
                    "mapLatitude" to Double.NaN,
                    "mapLongitude" to 1.0,
                    "mapName" to "Unconfirmed name",
                )
            )
        val vm = picker(handle = handle, proximity = PlaceProximity(46.0, 6.0))
        val state = vm.uiState.value
        assertTrue(state.isMapMode)
        assertEquals(original, state.selectedLocation)
        assertNull(state.manualDraft.point)
        assertEquals("Unconfirmed name", state.manualDraft.name)
        assertEquals(0.0, state.mapInitialCenter!!.latitude, 0.0)
        assertEquals(0.0, state.mapInitialCenter.longitude, 0.0)
        assertFalse(state.canConfirm)
        vm.backToSearch()
        assertEquals(original, vm.uiState.value.selectedLocation)
      }
}
