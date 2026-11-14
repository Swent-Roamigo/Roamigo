// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.inviting.InvitationException
import com.swent.roamigo.model.trip.inviting.InvitationPreview
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
class JoinTripViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val preview = InvitationPreview("trip-1", "Lisbon with friends", "Alex", false)
  private val service = FakeTripInvitationService(preview = preview)

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun showsTheTripAndInviterBeforeJoining() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)
        assertEquals(JoinTripUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf("tok"), service.previewed)
        assertEquals(JoinTripUiState.Ready(preview), model.uiState.value)
        assertEquals(emptyList<String>(), service.accepted)
      }

  @Test
  fun joiningAcceptsTheInvitationAndReportsTheTrip() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()

        model.join()
        advanceUntilIdle()

        assertEquals(listOf("tok"), service.accepted)
        assertEquals(JoinTripUiState.Joined("trip-1"), model.uiState.value)
      }

  @Test
  fun aSecondTapWhileJoiningDoesNotJoinTwice() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()
        service.gate = CompletableDeferred()

        model.join()
        advanceUntilIdle()
        assertEquals(JoinTripUiState.Joining(preview), model.uiState.value)
        model.join()
        service.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, service.accepted.size)
      }

  @Test
  fun anInvalidInvitationIsExplained() =
      runTest(dispatcher) {
        service.failure = InvitationException.Invalid()
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Invalid, model.uiState.value)
      }

  @Test
  fun anInvitationThatExpiresWhileTheScreenIsOpenIsExplained() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()
        service.failure = InvitationException.Invalid()

        model.join()
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Invalid, model.uiState.value)
      }

  @Test
  fun aFullTripKeepsTheTripNameOnScreen() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()
        service.failure = InvitationException.Full()

        model.join()
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Full(preview), model.uiState.value)
      }

  @Test
  fun aFailedJoinCanBeTriedAgain() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()
        service.failure = IOException("offline")

        model.join()
        advanceUntilIdle()
        assertEquals(JoinTripUiState.Ready(preview, joinFailed = true), model.uiState.value)

        service.failure = null
        model.join()
        advanceUntilIdle()
        assertEquals(JoinTripUiState.Joined("trip-1"), model.uiState.value)
      }

  @Test
  fun aFailedCheckCanBeRetried() =
      runTest(dispatcher) {
        service.failure = IOException("offline")
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()
        assertEquals(JoinTripUiState.Failed, model.uiState.value)

        service.failure = null
        model.load()
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Ready(preview), model.uiState.value)
      }

  @Test
  fun joiningBeforeTheCheckFinishesDoesNothing() =
      runTest(dispatcher) {
        val model = JoinTripViewModel("tok", service)

        model.join()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), service.accepted)
      }

  @Test
  fun anExistingMemberIsToldTheyAreAlreadyOnTheTrip() =
      runTest(dispatcher) {
        val member = preview.copy(alreadyMember = true)
        service.preview = member
        val model = JoinTripViewModel("tok", service)
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Ready(member), model.uiState.value)
      }

  @Test
  fun theFactoryBuildsTheViewModelAndClosingItCancelsARunningCheck() =
      runTest(dispatcher) {
        service.gate = CompletableDeferred()
        val store = ViewModelStore()
        val model =
            ViewModelProvider(store, JoinTripViewModel.factory("tok", service))[
                JoinTripViewModel::class.java]
        advanceUntilIdle()
        assertEquals(JoinTripUiState.Loading, model.uiState.value)

        store.clear()
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Loading, model.uiState.value)
        assertEquals(listOf("tok"), service.previewed)
      }

  @Test
  fun closingTheViewModelCancelsAJoinInProgress() =
      runTest(dispatcher) {
        val store = ViewModelStore()
        val model =
            ViewModelProvider(store, JoinTripViewModel.factory("tok", service))[
                JoinTripViewModel::class.java]
        advanceUntilIdle()
        service.gate = CompletableDeferred()
        model.join()
        advanceUntilIdle()
        assertEquals(JoinTripUiState.Joining(preview), model.uiState.value)

        store.clear()
        advanceUntilIdle()

        assertEquals(JoinTripUiState.Joining(preview), model.uiState.value)
      }

  @Test
  fun theFactoryOnlyBuildsTheJoinViewModel() {
    val factory = JoinTripViewModel.factory("tok", service)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(InviteViewModel::class.java)
    }
  }
}
