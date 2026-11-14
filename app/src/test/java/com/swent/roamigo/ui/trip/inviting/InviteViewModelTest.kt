// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.inviting.InvitationException
import com.swent.roamigo.model.trip.inviting.InvitationLink
import java.io.IOException
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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val service =
      FakeTripInvitationService(link = InvitationLink("token-0123456789abcd", 5_000L))

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun createsTheLinkForTheTripAndShowsIt() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        assertEquals(InviteUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf("trip"), service.createdFor)
        assertEquals(
            InviteUiState.Ready(
                "roamigo://invite/token-0123456789abcd",
                "token-0123456789abcd",
                5_000L,
            ),
            model.uiState.value,
        )
      }

  @Test
  fun aNetworkFailureCanBeRetried() =
      runTest(dispatcher) {
        service.failure = IOException("offline")
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()
        assertEquals(InviteUiState.Failed(notAllowed = false), model.uiState.value)

        service.failure = null
        model.load()
        advanceUntilIdle()

        assertEquals(2, service.createdFor.size)
        assertEquals(
            "token-0123456789abcd",
            (model.uiState.value as InviteUiState.Ready).token,
        )
      }

  @Test
  fun aMemberWhoIsNotTheOrganiserIsToldSo() =
      runTest(dispatcher) {
        service.failure = InvitationException.NotAllowed()
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()

        assertEquals(InviteUiState.Failed(notAllowed = true), model.uiState.value)
      }

  @Test
  fun stoppingTheLinkRevokesItsTrip() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()

        model.stopSharing()
        advanceUntilIdle()

        assertEquals(listOf("trip"), service.revokedFor)
        assertEquals(InviteUiState.Stopped, model.uiState.value)
      }

  @Test
  fun aFailedStopLeavesAnErrorToRetry() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()
        service.failure = IOException("offline")

        model.stopSharing()
        advanceUntilIdle()

        assertEquals(InviteUiState.Failed(notAllowed = false), model.uiState.value)
      }

  @Test
  fun aNewLinkCanBeCreatedAfterStopping() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()
        model.stopSharing()
        advanceUntilIdle()

        model.load()
        advanceUntilIdle()

        assertEquals(2, service.createdFor.size)
        assertEquals(true, model.uiState.value is InviteUiState.Ready)
      }

  @Test
  fun theSheetShowsLoadingWhileTheLinkIsBeingCreated() =
      runTest(dispatcher) {
        service.gate = CompletableDeferred()
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()

        assertEquals(InviteUiState.Loading, model.uiState.value)

        service.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(true, model.uiState.value is InviteUiState.Ready)
      }

  @Test
  fun stoppingShowsProgressAndASecondTapRevokesOnlyOnce() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()
        service.gate = CompletableDeferred()

        model.stopSharing()
        advanceUntilIdle()
        assertEquals(InviteUiState.Loading, model.uiState.value)

        model.stopSharing()
        service.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("trip"), service.revokedFor)
        assertEquals(InviteUiState.Stopped, model.uiState.value)
      }

  @Test
  fun aLinkIsNotCreatedWhileTheTripIsBeingRevoked() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()
        service.gate = CompletableDeferred()
        model.stopSharing()
        advanceUntilIdle()

        model.load()
        service.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, service.createdFor.size)
        assertEquals(InviteUiState.Stopped, model.uiState.value)
      }

  @Test
  fun aFailedStopByAMemberWhoIsNotTheOrganiserIsExplained() =
      runTest(dispatcher) {
        val model = InviteViewModel("trip", service)
        advanceUntilIdle()
        service.failure = InvitationException.NotAllowed()

        model.stopSharing()
        advanceUntilIdle()

        assertEquals(InviteUiState.Failed(notAllowed = true), model.uiState.value)
      }

  @Test
  fun theFactoryBuildsTheViewModelAndClosingItCancelsARunningRequest() =
      runTest(dispatcher) {
        service.gate = CompletableDeferred()
        val store = ViewModelStore()
        val model =
            ViewModelProvider(store, InviteViewModel.factory("trip", service))[
                InviteViewModel::class.java]
        advanceUntilIdle()
        assertEquals(InviteUiState.Loading, model.uiState.value)

        store.clear()
        advanceUntilIdle()

        // The cancelled request neither failed nor succeeded.
        assertEquals(InviteUiState.Loading, model.uiState.value)
        assertEquals(listOf("trip"), service.createdFor)
      }

  @Test
  fun closingTheViewModelCancelsARunningRevoke() =
      runTest(dispatcher) {
        val store = ViewModelStore()
        val model =
            ViewModelProvider(store, InviteViewModel.factory("trip", service))[
                InviteViewModel::class.java]
        advanceUntilIdle()
        service.gate = CompletableDeferred()
        model.stopSharing()
        advanceUntilIdle()

        store.clear()
        advanceUntilIdle()

        assertEquals(InviteUiState.Loading, model.uiState.value)
        assertEquals(listOf("trip"), service.revokedFor)
      }

  @Test
  fun theFactoryOnlyBuildsTheInviteViewModel() {
    val factory = InviteViewModel.factory("trip", service)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(JoinTripViewModel::class.java)
    }
  }
}
