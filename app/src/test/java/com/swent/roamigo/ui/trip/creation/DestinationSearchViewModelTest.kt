package com.swent.roamigo.ui.trip.creation

import com.swent.roamigo.R
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationConfigurationException
import com.swent.roamigo.model.destination.DestinationKind
import com.swent.roamigo.model.destination.FakeDestinationRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationSearchViewModelTest {
  @Test
  fun missingDestinationConfigurationUsesLocalizableError() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository =
          FakeDestinationRepository(search = { throw DestinationConfigurationException() })
      val viewModel =
          CreateTripViewModel(
              destinationRepository = repository,
              requestDispatcher = StandardTestDispatcher(testScheduler),
          )

      viewModel.updateDestinationQuery("Paris")
      advanceUntilIdle()

      assertEquals(DestinationSearchStatus.ERROR, viewModel.uiState.value.destinationSearchStatus)
      assertEquals(
          R.string.destination_search_not_configured,
          viewModel.uiState.value.destinationSearchErrorRes,
      )
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun shortTrimmedQueriesDoNotCallRepositoryAndValidQueryWaitsForDebounce() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeDestinationRepository()
      val viewModel =
          CreateTripViewModel(
              destinationRepository = repository,
              requestDispatcher = StandardTestDispatcher(testScheduler),
          )

      viewModel.updateDestinationQuery("  a ")
      runCurrent()
      advanceTimeBy(1_000)
      runCurrent()
      assertTrue(repository.searchQueries.isEmpty())

      viewModel.updateDestinationQuery("Pa")
      runCurrent()
      advanceTimeBy(299)
      runCurrent()
      assertTrue(repository.searchQueries.isEmpty())
      advanceTimeBy(1)
      runCurrent()

      assertEquals(listOf("Pa"), repository.searchQueries)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun staleSearchCannotReplaceNewerResultsAndResultsAreCappedAtTwenty() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val oldSearch = CompletableDeferred<List<Destination>>()
      val newSearch = CompletableDeferred<List<Destination>>()
      val repository =
          FakeDestinationRepository(
              search = { query -> if (query == "Old") oldSearch.await() else newSearch.await() },
          )
      val viewModel =
          CreateTripViewModel(
              destinationRepository = repository,
              requestDispatcher = StandardTestDispatcher(testScheduler),
          )
      val oldResults = listOf(destination("old-result"))
      val newResults = (1..25).map { destination("new-$it") }

      viewModel.updateDestinationQuery("Old")
      advanceTimeBy(300)
      runCurrent()
      viewModel.updateDestinationQuery("New")
      advanceTimeBy(300)
      runCurrent()
      newSearch.complete(newResults)
      runCurrent()
      oldSearch.complete(oldResults)
      advanceUntilIdle()

      assertEquals(listOf("Old", "New"), repository.searchQueries)
      assertEquals(newResults.take(20), viewModel.uiState.value.destinations)
      assertEquals(DestinationSearchStatus.RESULTS, viewModel.uiState.value.destinationSearchStatus)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun emptyAndErrorStatesPreserveOrderedSelectionAndRetryUsesCurrentQuery() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      var shouldFail = false
      val repository =
          FakeDestinationRepository(
              search = { query ->
                if (shouldFail) throw IOException("offline")
                if (query == "None") emptyList() else listOf(destination("result"))
              },
          )
      val viewModel =
          CreateTripViewModel(
              destinationRepository = repository,
              requestDispatcher = StandardTestDispatcher(testScheduler),
          )
      val first = destination("same-city-fr")
      val second = destination("same-city-us")
      viewModel.toggleDestination(first)
      viewModel.toggleDestination(second)

      viewModel.updateDestinationQuery("None")
      advanceUntilIdle()
      assertEquals(DestinationSearchStatus.EMPTY, viewModel.uiState.value.destinationSearchStatus)
      assertEquals(listOf(first, second), viewModel.uiState.value.selectedDestinations)

      shouldFail = true
      viewModel.updateDestinationQuery("Paris")
      advanceUntilIdle()
      assertEquals(DestinationSearchStatus.ERROR, viewModel.uiState.value.destinationSearchStatus)
      assertEquals(
          R.string.destination_search_error,
          viewModel.uiState.value.destinationSearchErrorRes,
      )
      assertEquals(listOf(first, second), viewModel.uiState.value.selectedDestinations)

      shouldFail = false
      viewModel.retryDestinationSearch()
      advanceUntilIdle()
      assertEquals(listOf("None", "Paris", "Paris"), repository.searchQueries)
      assertEquals(DestinationSearchStatus.RESULTS, viewModel.uiState.value.destinationSearchStatus)
      assertNull(viewModel.uiState.value.destinationSearchErrorRes)
      assertEquals(listOf(first, second), viewModel.uiState.value.selectedDestinations)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun duplicateDisplayNamesHaveDistinctIdentityAndSelectionOrderIsStable() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeDestinationRepository()
      val viewModel =
          CreateTripViewModel(
              destinationRepository = repository,
              requestDispatcher = StandardTestDispatcher(testScheduler),
          )
      val parisFrance = destination("geonames:2988507", "Paris", "France", "FR", 2988507)
      val parisTexas = destination("geonames:4717560", "Paris", "United States", "US", 4717560)
      val lisbon = destination("geonames:2267057", "Lisbon", "Portugal", "PT", 2267057)

      viewModel.toggleDestination(parisFrance)
      viewModel.toggleDestination(parisTexas)
      viewModel.toggleDestination(lisbon)
      viewModel.removeDestination(parisTexas)

      assertEquals(listOf(parisFrance, lisbon), viewModel.uiState.value.selectedDestinations)
      assertFalse(
          viewModel.uiState.value.selectedDestinations.any { it.stableId == parisTexas.stableId }
      )
      assertTrue(parisFrance.stableId != parisTexas.stableId)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun searchResultsPreserveCityMetadata() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val zurich =
          destination("geonames:2657896", "Zurich", "Switzerland", "CH", 2657896)
              .copy(wikidataId = "Q72")
      val repository = FakeDestinationRepository(search = { listOf(zurich) })
      val viewModel =
          CreateTripViewModel(
              destinationRepository = repository,
              requestDispatcher = StandardTestDispatcher(testScheduler),
          )

      viewModel.updateDestinationQuery("Zurich")
      advanceUntilIdle()

      assertEquals(listOf(zurich), viewModel.uiState.value.destinations)
      assertEquals(DestinationKind.CITY, viewModel.uiState.value.destinations[0].kind)
      assertEquals("CH", viewModel.uiState.value.destinations[0].countryCode)
      assertEquals(2657896, viewModel.uiState.value.destinations[0].geonameId)
      assertEquals("Q72", viewModel.uiState.value.destinations[0].wikidataId)
    } finally {
      Dispatchers.resetMain()
    }
  }

  private fun destination(
      stableId: String,
      name: String = "Sample City",
      countryName: String = "Sample Country",
      countryCode: String = "SC",
      geonameId: Int = 1,
  ) =
      Destination(
          stableId = stableId,
          kind = DestinationKind.CITY,
          name = name,
          countryName = countryName,
          countryCode = countryCode,
          latitude = 10.0,
          longitude = 20.0,
          geonameId = geonameId,
          wikidataId = null,
      )
}
