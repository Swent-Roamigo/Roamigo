package com.swent.roamigo.ui.heatmap

import androidx.lifecycle.ViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TravelHeatmapViewModelTest {

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
  fun initialStateIsLoading() = runTest {
    val pending = CompletableDeferred<Map<String, Int>>()
    val vm = TravelHeatmapViewModel(VisitedCountryCounts { pending.await() })

    assertEquals(TravelHeatmapUiState.Loading, vm.uiState.value)
    runCurrent()
    assertEquals(TravelHeatmapUiState.Loading, vm.uiState.value)

    pending.complete(mapOf("PT" to 1))
    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("PT" to 1)),
        vm.uiState.value,
    )
  }

  @Test
  fun loadsRealCountryCounts() = runTest {
    val vm = TravelHeatmapViewModel(VisitedCountryCounts { mapOf("PT" to 3, "FR" to 2) })

    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("PT" to 3, "FR" to 2)),
        vm.uiState.value,
    )
  }

  @Test
  fun filtersInvalidCodesAndNonPositiveVisits() = runTest {
    val vm =
        TravelHeatmapViewModel(
            VisitedCountryCounts {
              mapOf(
                  "PT" to 3,
                  "FR" to 0,
                  "DE" to -1,
                  "pt" to 4,
                  "ABC" to 2,
                  "I" to 1,
              )
            }
        )

    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("PT" to 3)),
        vm.uiState.value,
    )
  }

  @Test
  fun emptyRepositoryReturnsSuccess() = runTest {
    val vm = TravelHeatmapViewModel(VisitedCountryCounts { emptyMap() })

    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(emptyMap()),
        vm.uiState.value,
    )
  }

  @Test
  fun repositoryFailureReturnsError() = runTest {
    val vm =
        TravelHeatmapViewModel(
            VisitedCountryCounts { throw IllegalStateException("Network unavailable") }
        )

    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Error,
        vm.uiState.value,
    )
  }

  @Test
  fun refreshReloadsUpdatedData() = runTest {
    var visits = 1
    val vm = TravelHeatmapViewModel(VisitedCountryCounts { mapOf("PT" to visits) })

    advanceUntilIdle()
    assertEquals(
        TravelHeatmapUiState.Success(mapOf("PT" to 1)),
        vm.uiState.value,
    )

    visits = 3
    vm.refresh()
    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("PT" to 3)),
        vm.uiState.value,
    )
  }

  @Test
  fun refreshAfterFailureRecovers() = runTest {
    var calls = 0
    val vm =
        TravelHeatmapViewModel(
            VisitedCountryCounts {
              calls++
              if (calls == 1) {
                throw IllegalStateException("Failed")
              }
              mapOf("IT" to 2)
            }
        )

    advanceUntilIdle()
    assertEquals(TravelHeatmapUiState.Error, vm.uiState.value)

    vm.refresh()
    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("IT" to 2)),
        vm.uiState.value,
    )
    assertEquals(2, calls)
  }

  @Test
  fun refreshCancelsPreviousLoad() = runTest {
    val pending = CompletableDeferred<Map<String, Int>>()
    var calls = 0

    val vm =
        TravelHeatmapViewModel(
            VisitedCountryCounts {
              calls++
              if (calls == 1) pending.await() else mapOf("ES" to 2)
            }
        )

    runCurrent()
    assertEquals(1, calls)

    vm.refresh()
    advanceUntilIdle()

    assertEquals(2, calls)
    assertEquals(
        TravelHeatmapUiState.Success(mapOf("ES" to 2)),
        vm.uiState.value,
    )

    pending.complete(mapOf("FR" to 9))
    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("ES" to 2)),
        vm.uiState.value,
    )
  }

  @Test
  fun factoryCreatesCorrectViewModel() = runTest {
    val factory = TravelHeatmapViewModel.factory(VisitedCountryCounts { mapOf("CH" to 2) })

    val vm = factory.create(TravelHeatmapViewModel::class.java)

    advanceUntilIdle()

    assertEquals(
        TravelHeatmapUiState.Success(mapOf("CH" to 2)),
        vm.uiState.value,
    )
  }

  @Test
  fun factoryRejectsUnexpectedViewModel() {
    class OtherViewModel : ViewModel()

    val factory = TravelHeatmapViewModel.factory(VisitedCountryCounts { emptyMap() })

    val result = runCatching { factory.create(OtherViewModel::class.java) }

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }
}
