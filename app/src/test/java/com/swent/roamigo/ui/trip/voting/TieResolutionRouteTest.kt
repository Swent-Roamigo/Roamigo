// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.voting.*
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.details.detailsTestTrip
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

@RunWith(AndroidJUnit4::class)
class TieResolutionRouteTest {
  @get:Rule val composeRule = createComposeRule()
  private val original =
      interactionVote()
          .copy(
              status = VoteStatus.CLOSED,
              options = interactionVote().options + VoteOption("c", "Pizza"),
          )
  private val backend =
      InMemoryVoteRepository(listOf(original)).also {
        it.emitBallots(
            "trip",
            "vote",
            listOf(
                VoteBallot("one", "a", Timestamp(1, 0)),
                VoteBallot("two", "b", Timestamp(1, 0)),
            ),
        )
      }
  private val trips =
      mock(TripRepository::class.java).also {
        runBlocking {
          `when`(it.getTrip("trip"))
              .thenReturn(
                  detailsTestTrip()
                      .copy(
                          endDate = Timestamp(4102444800L, 0),
                          memberIds = listOf("alex", "member"),
                      )
              )
        }
      }

  private lateinit var resolutionModel: TieResolutionViewModel

  private fun controlledList(
      tripRepository: TripRepository = trips
  ): MutableStateFlow<VotesUiState> {
    val result =
        calculateVoteResult(
            original.options,
            listOf(
                VoteBallot("one", "a", Timestamp(1, 0)),
                VoteBallot("two", "b", Timestamp(1, 0)),
            ),
        )
    val state =
        MutableStateFlow<VotesUiState>(
            VotesUiState.Ready(
                emptyList(),
                listOf(original),
                finalSummaries = mapOf(original.uid to result),
            )
        )
    val model = mock(VotesViewModel::class.java)
    `when`(model.uiState).thenReturn(state)
    composeRule.setContent {
      RoamigoTheme {
        VotesRoute(
            backend,
            "trip",
            "alex",
            "Lisbon",
            {},
            {},
            {},
            tripRepository = tripRepository,
            votesViewModelFactory = viewModelFactory { initializer { model } },
            resolutionViewModelFactory =
                viewModelFactory {
                  initializer {
                    TieResolutionViewModel(backend, tripRepository, "trip", "alex", false).also {
                      resolutionModel = it
                    }
                  }
                },
        )
      }
    }
    return state
  }

  @Test
  fun routineRefreshKeepsTieActionsVisibleWithoutCheckingText() {
    val trip = detailsTestTrip().copy(endDate = Timestamp(4102444800L, 0))
    val gate = CompletableDeferred<Trip?>()
    var refreshing = false
    val repository =
        object : TripRepository by trips {
          override suspend fun getTrip(tripId: String): Trip? =
              if (refreshing) gate.await() else trip
        }
    controlledList(repository)
    composeRule.onNodeWithText("Revote").assertIsDisplayed()
    composeRule.onNodeWithText("Owner decides").assertIsDisplayed()
    composeRule.runOnIdle {
      refreshing = true
      resolutionModel.refreshEligibility()
    }
    composeRule.onNodeWithText("Revote").assertIsDisplayed()
    composeRule.onNodeWithText("Owner decides").assertIsDisplayed()
    composeRule
        .onNodeWithText("Checking vote-resolution permissions", substring = true)
        .assertDoesNotExist()
    composeRule.runOnIdle {
      assertTrue(resolutionModel.state.value.refreshing)
      assertTrue(resolutionModel.state.value.canResolve)
      gate.complete(trip)
    }
    composeRule.onNodeWithText("Revote").assertIsDisplayed()
    composeRule.onNodeWithText("Owner decides").assertIsDisplayed()
    composeRule.runOnIdle { assertFalse(resolutionModel.state.value.refreshing) }
  }

  @Test
  fun ownerSelectionSurvivesLoadingMissingSummariesAndRecoverableListErrors() {
    val state = controlledList()
    val initial = state.value as VotesUiState.Ready
    composeRule.onNodeWithText("Owner decides").performClick()
    composeRule.onNode(hasText("Market") and isSelectable()).performClick()
    for (transient in
        listOf(
            VotesUiState.Loading,
            initial.copy(finalSummaries = emptyMap()),
            initial.copy(
                liveVotes = listOf(original.copy(status = VoteStatus.OPEN)),
                closedVotes = emptyList(),
                finalSummaries = emptyMap(),
            ),
            VotesUiState.Error,
            initial.copy(syncState = VotesSyncState.FAILED),
            initial.copy(syncState = VotesSyncState.RETRYING),
        )) {
      composeRule.runOnIdle { state.value = transient }
      composeRule.onNode(hasText("Market") and isSelectable()).assertIsSelected()
      composeRule.onNodeWithText("Confirm decision").assertIsNotEnabled()
      composeRule.onNodeWithText("Cancel").assertIsEnabled()
      composeRule.runOnIdle { state.value = initial }
      composeRule.onNode(hasText("Market") and isSelectable()).assertIsSelected()
      composeRule.onNodeWithText("Confirm decision").assertIsEnabled()
    }
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithText("Confirm decision").assertDoesNotExist()
  }

  @Test
  fun ownerDraftAndCancelSurviveEligibilityRefreshFailureUntilRetryRestoresConfirmation() {
    controlledList()
    composeRule.onNodeWithText("Owner decides").performClick()
    composeRule.onNode(hasText("Market") and isSelectable()).performClick()
    composeRule.runOnIdle {
      runBlocking { `when`(trips.getTrip("trip")).thenThrow(IllegalStateException("offline")) }
      resolutionModel.refreshEligibility()
    }
    composeRule
        .onNode(
            hasText("Unable to check vote-resolution permissions. Please retry.") and
                hasAnyAncestor(isDialog())
        )
        .assertIsDisplayed()
    composeRule.onNodeWithText("Confirm decision").assertIsNotEnabled()
    composeRule.onNodeWithText("Cancel").assertIsEnabled()
    composeRule.onNode(hasText("Market") and isSelectable()).assertIsSelected()
    composeRule.runOnIdle {
      runBlocking {
        doReturn(detailsTestTrip().copy(endDate = Timestamp(4102444800L, 0)))
            .`when`(trips)
            .getTrip("trip")
      }
    }
    composeRule.onNode(hasText("Retry") and hasAnyAncestor(isDialog())).performClick()
    composeRule.onNodeWithText("Confirm decision").assertIsEnabled()
    composeRule.onNode(hasText("Market") and isSelectable()).assertIsSelected()
    composeRule.onNodeWithText("Cancel").performClick()
  }

  @Test
  fun confirmedExternalResolutionDelegationOrChangedOutcomeDismissesOwnerDialog() {
    val state = controlledList()
    val initial = state.value as VotesUiState.Ready
    for (external in
        listOf(
            initial.copy(closedVotes = listOf(original.copy(resolvedOptionId = "b"))),
            initial.copy(closedVotes = listOf(original.copy(nextRoundVoteId = "round2"))),
            initial.copy(
                finalSummaries =
                    mapOf(
                        original.uid to VoteResult(mapOf("a" to 2, "b" to 1, "c" to 0), setOf("a"))
                    )
            ),
        )) {
      composeRule.runOnIdle { state.value = initial }
      composeRule.onNodeWithText("Owner decides").performClick()
      composeRule.onNodeWithText("Confirm decision").assertIsNotEnabled()
      composeRule.onNode(hasText("Market") and isSelectable()).performClick()
      composeRule.runOnIdle { state.value = external }
      composeRule.onNodeWithText("Confirm decision").assertDoesNotExist()
    }
  }

  @Test
  fun eligibilityFailureIsVisibleAndRetryRestoresActionsWithoutBlockingVoteViewing() {
    runBlocking { `when`(trips.getTrip("trip")).thenReturn(null) }
    val opened = mutableListOf<String>()
    composeRule.setContent {
      RoamigoTheme {
        VotesRoute(backend, "trip", "alex", "Lisbon", opened::add, {}, {}, tripRepository = trips)
      }
    }
    composeRule
        .onNodeWithText("Trip unavailable. Unable to check vote-resolution permissions.")
        .assertIsDisplayed()
    composeRule.onNodeWithText("Revote").assertDoesNotExist()
    composeRule.onNodeWithText("Dinner?").performClick()
    composeRule.runOnIdle {
      assertEquals(listOf("vote"), opened)
      runBlocking { `when`(trips.getTrip("trip")).thenThrow(IllegalStateException("offline")) }
    }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule
        .onNodeWithText("Unable to check vote-resolution permissions. Please retry.")
        .assertIsDisplayed()
    composeRule.runOnIdle {
      runBlocking {
        doReturn(detailsTestTrip().copy(endDate = Timestamp(4102444800L, 0)))
            .`when`(trips)
            .getTrip("trip")
      }
    }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Revote").assertIsDisplayed()
    composeRule.onNodeWithText("Owner decides").assertIsDisplayed()
  }

  @Test
  fun cancellingRevoteRestoresScrolledListAndReopeningGetsAFreshDraft() {
    val preceding =
        (0..10).map {
          original.copy(uid = "live-$it", question = "Earlier vote $it", status = VoteStatus.OPEN)
        }
    backend.snapshots.value = Result.success(preceding + original)
    val calls = mutableListOf<Vote>()
    val opened = mutableListOf<String>()
    var fail = true
    val gate = CompletableDeferred<Unit>()
    lateinit var backDispatcher: OnBackPressedDispatcher
    val repository =
        object : VoteRepository by backend {
          override suspend fun createRevote(vote: Vote) {
            calls += vote
            if (fail) throw IllegalStateException("offline")
            gate.await()
            backend.createRevote(vote)
          }
        }
    composeRule.setContent {
      backDispatcher =
          checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
      RoamigoTheme {
        VotesRoute(
            repository,
            "trip",
            "member",
            "Lisbon",
            {},
            opened::add,
            {},
            tripRepository = trips,
        )
      }
    }
    composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Revote"))
    composeRule.onNodeWithText("Revote").performClick()
    composeRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeRule.onNodeWithText("Revote").assertIsDisplayed()
    composeRule.onNodeWithText("Earlier vote 0").assertDoesNotExist()
    composeRule.runOnIdle { assertTrue(calls.isEmpty()) }
    composeRule.onNodeWithText("Revote").performClick()
    composeRule.onNodeWithText("Start vote").performClick()
    composeRule.onNodeWithText("Unable to create the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithText("Revote").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Unable to create the vote. Please try again.").assertDoesNotExist()
    composeRule.runOnIdle { fail = false }
    composeRule.onNodeWithText("Start vote").performClick()
    composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    composeRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
    composeRule.runOnIdle { gate.complete(Unit) }
    composeRule.runOnIdle {
      assertEquals(2, calls.size)
      assertNotEquals(calls[0].uid, calls[1].uid)
      assertEquals(listOf(calls[1].uid), opened)
    }
    composeRule.onNodeWithText("Start vote").assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, opened.size) }
  }

  private fun assertResolutionUnavailable(readOnly: Boolean, endDate: Timestamp) {
    runBlocking {
      `when`(trips.getTrip("trip"))
          .thenReturn(
              detailsTestTrip().copy(endDate = endDate, memberIds = listOf("alex", "member"))
          )
    }
    composeRule.setContent {
      RoamigoTheme {
        VotesRoute(
            backend,
            "trip",
            "alex",
            "Lisbon",
            {},
            {},
            {},
            readOnly = readOnly,
            tripRepository = trips,
        )
      }
    }
    composeRule.onNodeWithText("Tie · no final decision").assertIsDisplayed()
    composeRule.onNodeWithText("Revote").assertDoesNotExist()
    composeRule.onNodeWithText("Owner decides").assertDoesNotExist()
  }

  @Test
  fun readOnlyListNeverOffersResolution() =
      assertResolutionUnavailable(true, Timestamp(4102444800L, 0))

  @Test
  fun endedTripListNeverOffersResolution() = assertResolutionUnavailable(false, Timestamp(1, 0))

  @Test
  fun revoteOpensPrefilledCreateFlowAndPersistsOnlyAfterStart() {
    val created = mutableListOf<Vote>()
    val opened = mutableListOf<String>()
    val repository =
        object : VoteRepository by backend {
          override suspend fun createRevote(vote: Vote) {
            created += vote
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        VotesRoute(
            repository,
            "trip",
            "member",
            "Lisbon",
            {},
            opened::add,
            {},
            tripRepository = trips,
        )
      }
    }
    composeRule.onNodeWithText("Owner decides").assertDoesNotExist()
    composeRule.onNodeWithText("Revote").performClick()
    composeRule.onNodeWithText("Dinner?").assertIsNotEnabled()
    composeRule.onNodeWithText("Market").assertIsNotEnabled()
    composeRule.onNodeWithText("Restaurant").assertIsNotEnabled()
    composeRule.onNodeWithText("Pizza").assertDoesNotExist()
    composeRule.runOnIdle { assertTrue(created.isEmpty()) }
    composeRule.onNodeWithText("Start vote").performClick()
    composeRule.runOnIdle {
      val next = created.single()
      assertEquals("vote", next.parentVoteId)
      assertEquals(original.options.take(2), next.options)
      assertNotEquals(original.uid, next.uid)
      assertEquals(listOf(next.uid), opened)
    }
  }

  @Test
  fun ownerSelectsTiedOptionAndConfirmsWithoutChangingBallotCounts() {
    val calls = mutableListOf<String>()
    val repository =
        object : VoteRepository by backend {
          override suspend fun resolveByOwner(
              tripId: String,
              voteId: String,
              optionId: String,
              userId: String,
          ) {
            calls += optionId
            backend.snapshots.value =
                Result.success(
                    listOf(original.copy(resolvedOptionId = optionId, resolvedByUserId = userId))
                )
          }
        }
    composeRule.setContent {
      RoamigoTheme {
        VotesRoute(repository, "trip", "alex", "Lisbon", {}, {}, {}, tripRepository = trips)
      }
    }
    composeRule.onNodeWithText("Revote").assertIsDisplayed()
    composeRule.onNodeWithText("Owner decides").performClick()
    composeRule.onNodeWithText("Confirm decision").assertIsNotEnabled()
    composeRule.onNode(hasText("Pizza") and isSelectable()).assertDoesNotExist()
    // The modal shows only tied choices; selecting does not write.
    composeRule.onNode(hasText("Market") and isSelectable()).performClick()
    composeRule.runOnIdle { assertTrue(calls.isEmpty()) }
    composeRule.onNodeWithText("Confirm decision").assertIsEnabled().performClick()
    composeRule.onNodeWithText("Owner decided").assertIsDisplayed()
    composeRule.onNodeWithText("Decided · Market").assertIsDisplayed()
    composeRule.onNodeWithText("Revote").assertDoesNotExist()
    composeRule.onNodeWithText("Owner decides").assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(
          listOf("a", "b"),
          kotlinx.coroutines
              .runBlocking { backend.observeFinalBallots("trip", "vote").first() }
              .map { it.optionId },
      )
    }
    composeRule.runOnIdle { assertEquals(listOf("a"), calls) }
  }
}
