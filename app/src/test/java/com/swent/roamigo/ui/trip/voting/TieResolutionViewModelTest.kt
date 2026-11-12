// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.*
import com.swent.roamigo.ui.trip.details.detailsTestTrip
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class TieResolutionViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = mock(VoteRepository::class.java)
  private val trips = mock(TripRepository::class.java)
  private val clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC)
  private val options =
      listOf(VoteOption("a", "Market"), VoteOption("b", "Restaurant"), VoteOption("c", "Pizza"))
  private val vote =
      Vote(
          "original",
          "trip",
          "Dinner?",
          options,
          "creator",
          VoteStatus.CLOSED,
          Timestamp(1, 0),
          null,
          null,
      )
  private val closed =
      VoteUiState.Closed(vote, VoteResult(mapOf("a" to 2, "b" to 2, "c" to 0), setOf("a", "b")))

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private suspend fun model(
      user: String = "alex",
      readOnly: Boolean = false,
  ): TieResolutionViewModel {
    `when`(trips.getTrip("trip"))
        .thenReturn(detailsTestTrip().copy(memberIds = listOf("alex", "creator", "member")))
    return TieResolutionViewModel(repository, trips, "trip", user, readOnly, clock)
  }

  private class ControlledTrips(repository: TripRepository, trip: Trip) :
      TripRepository by repository {
    var response = CompletableDeferred<Trip?>(trip)
    var reads = 0

    override suspend fun getTrip(tripId: String): Trip? {
      reads++
      return response.await()
    }
  }

  @Test
  fun initialLoadingThenBackgroundRefreshPreservesReadyActionsAndOwnerSelection() = runTest {
    val trip = detailsTestTrip()
    val controlled = ControlledTrips(trips, trip)
    controlled.response = CompletableDeferred()
    val vm = TieResolutionViewModel(repository, controlled, "trip", "alex", false, clock)
    runCurrent()
    assertEquals(ResolutionEligibility.Loading, vm.state.value.eligibility)
    assertTrue(vm.state.value.refreshing)
    assertFalse(vm.state.value.canResolve)
    controlled.response.complete(trip)
    advanceUntilIdle()
    val ready = vm.state.value.eligibility
    assertTrue(vm.state.value.canResolve)
    assertFalse(vm.state.value.refreshing)
    vm.startOwnerDecision(closed)
    vm.selectOwnerOption("a")
    controlled.response = CompletableDeferred()
    vm.refreshEligibility()
    vm.refreshEligibility()
    runCurrent()
    assertEquals(2, controlled.reads)
    assertEquals(ready, vm.state.value.eligibility)
    assertTrue(vm.state.value.refreshing)
    assertTrue(vm.state.value.canResolve)
    assertTrue(vm.state.value.isOwner)
    assertEquals("a", vm.state.value.selectedOptionId)
    controlled.response.complete(trip)
    advanceUntilIdle()
    assertEquals(ready, vm.state.value.eligibility)
    assertFalse(vm.state.value.refreshing)
    assertEquals("a", vm.state.value.selectedOptionId)
    verifyNoInteractions(repository)
  }

  @Test
  fun revoteCreationAwaitsSharedRefreshAndRetainsDraftAfterFailureUntilRetry() = runTest {
    val trip = detailsTestTrip()
    val controlled = ControlledTrips(trips, trip)
    val vm = TieResolutionViewModel(repository, controlled, "trip", "alex", false, clock)
    advanceUntilIdle()
    vm.startRevote(closed)
    val source = requireNotNull(vm.state.value.revoteSource)
    val written = mutableListOf<Vote>()
    val creationRepository =
        object : VoteRepository by repository {
          override suspend fun createRevote(vote: Vote) {
            written += vote
          }
        }
    val creation =
        CreateVoteViewModel(
            creationRepository,
            "trip",
            "alex",
            clock,
            source,
            beforeSubmit = { vm.validateRevoteSubmission().await() },
        )
    controlled.response = CompletableDeferred()
    vm.refreshEligibility()
    runCurrent()
    creation.submitVote()
    runCurrent()
    assertEquals(2, controlled.reads)
    assertTrue(vm.state.value.canResolve)
    assertTrue(vm.state.value.refreshing)
    assertTrue(creation.uiState.value.isSubmitting)
    verifyNoInteractions(repository)
    controlled.response.complete(null)
    advanceUntilIdle()
    assertEquals(ResolutionEligibility.Failed(missingTrip = true), vm.state.value.eligibility)
    assertFalse(vm.state.value.canResolve)
    assertFalse(vm.state.value.refreshing)
    assertFalse(creation.uiState.value.isSubmitting)
    assertEquals(source, vm.state.value.revoteSource)
    verifyNoInteractions(repository)
    controlled.response = CompletableDeferred(trip)
    vm.refreshEligibility()
    advanceUntilIdle()
    assertTrue(vm.state.value.canResolve)
    creation.submitVote()
    advanceUntilIdle()
    assertEquals(4, controlled.reads)
    assertEquals(1, written.size)
    assertEquals(written.single().uid, creation.uiState.value.createdVoteId)
    assertNotNull(creation.uiState.value.createdVoteId)
  }

  @Test
  fun ownerConfirmationAwaitsRefreshAndUsesFreshOwnershipMembershipAndCalendarEligibility() =
      runTest {
        val trip = detailsTestTrip()
        val cases =
            listOf(
                Triple(trip.copy(ownerId = "other"), true, false),
                Triple(trip.copy(memberIds = emptyList()), false, false),
                Triple(trip.copy(endDate = Timestamp(1, 0)), false, false),
                Triple(trip, true, true),
            )
        for ((freshTrip, canResolve, canWrite) in cases) {
          val writes = mock(VoteRepository::class.java)
          val controlled = ControlledTrips(trips, trip)
          val vm = TieResolutionViewModel(writes, controlled, "trip", "alex", false, clock)
          advanceUntilIdle()
          vm.startOwnerDecision(closed)
          vm.selectOwnerOption("a")
          controlled.response = CompletableDeferred()
          vm.refreshEligibility()
          runCurrent()
          vm.confirmOwnerDecision(closed)
          runCurrent()
          assertEquals(2, controlled.reads)
          assertTrue(vm.state.value.canResolve)
          assertTrue(vm.state.value.refreshing)
          assertTrue(vm.state.value.submitting)
          verifyNoInteractions(writes)
          controlled.response.complete(freshTrip)
          advanceUntilIdle()
          assertEquals(canResolve, vm.state.value.canResolve)
          assertFalse(vm.state.value.refreshing)
          assertFalse(vm.state.value.submitting)
          if (canWrite) verify(writes).resolveByOwner("trip", "original", "a", "alex")
          else verifyNoInteractions(writes)
        }
      }

  @Test
  fun cancellingAValidationDoesNotCancelSharedRefreshAndCancelledLoadsClearRefreshing() = runTest {
    val trip = detailsTestTrip()
    val controlled = ControlledTrips(trips, trip)
    val vm = TieResolutionViewModel(repository, controlled, "trip", "alex", false, clock)
    advanceUntilIdle()
    val ready = vm.state.value.eligibility
    controlled.response = CompletableDeferred()
    vm.refreshEligibility()
    val validation = vm.validateRevoteSubmission()
    runCurrent()
    assertEquals(2, controlled.reads)
    validation.cancel()
    runCurrent()
    assertTrue(validation.isCancelled)
    assertTrue(vm.state.value.refreshing)
    assertEquals(ready, vm.state.value.eligibility)
    controlled.response.completeExceptionally(CancellationException("left refresh"))
    advanceUntilIdle()
    assertEquals(ready, vm.state.value.eligibility)
    assertFalse(vm.state.value.refreshing)
    assertFalse(vm.state.value.failed)
    controlled.response = CompletableDeferred(trip.copy(memberIds = emptyList()))
    assertFalse(vm.validateRevoteSubmission().await())
    assertEquals(3, controlled.reads)
    assertFalse(vm.state.value.refreshing)
    verifyNoInteractions(repository)
  }

  @Test
  fun nonMemberCannotResolveEvenWhenTripIsWritable() = runTest {
    val vm = model("outsider")
    advanceUntilIdle()
    assertFalse(vm.state.value.canResolve)
    vm.startRevote(closed)
    vm.startOwnerDecision(closed)
    assertNull(vm.state.value.revoteSource)
    assertTrue(vm.state.value.ownerOptions.isEmpty())
    verifyNoInteractions(repository)
  }

  @Test
  fun revoteCarriesOnlyTiedOptionsAndDoesNotPersistUntilConfirmation() = runTest {
    val vm = model("member")
    advanceUntilIdle()
    vm.startRevote(closed)
    assertEquals(options.take(2), vm.state.value.revoteSource?.options)
    assertEquals(vote.question, vm.state.value.revoteSource?.question)
    verifyNoInteractions(repository)
    vm.dismiss()
    assertNull(vm.state.value.revoteSource)
  }

  @Test
  fun onlyTripOwnerCanChooseAndMustConfirmExactlyOneTiedOption() = runTest {
    for (user in listOf("creator", "member")) {
      val vm = model(user)
      advanceUntilIdle()
      vm.startOwnerDecision(closed)
      assertTrue(vm.state.value.ownerOptions.isEmpty())
    }
    val owner = model()
    advanceUntilIdle()
    owner.startOwnerDecision(closed)
    assertEquals(options.take(2), owner.state.value.ownerOptions)
    owner.selectOwnerOption("c")
    assertNull(owner.state.value.selectedOptionId)
    owner.selectOwnerOption("b")
    verifyNoInteractions(repository)
    owner.confirmOwnerDecision(closed)
    owner.confirmOwnerDecision(closed)
    advanceUntilIdle()
    verify(repository, times(1)).resolveByOwner("trip", "original", "b", "alex")
    assertEquals(mapOf("a" to 2, "b" to 2, "c" to 0), closed.result.counts)
  }

  @Test
  fun readOnlyEndedAndAlreadyResolvedVotesCannotStartResolution() = runTest {
    val readOnly = model(readOnly = true)
    advanceUntilIdle()
    readOnly.startRevote(closed)
    readOnly.startOwnerDecision(closed)
    assertNull(readOnly.state.value.revoteSource)
    assertTrue(readOnly.state.value.ownerOptions.isEmpty())
    val ended =
        TieResolutionViewModel(
            repository,
            trips,
            "trip",
            "alex",
            false,
            Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC),
        )
    advanceUntilIdle()
    ended.startRevote(closed)
    assertNull(ended.state.value.revoteSource)
    val vm = model()
    advanceUntilIdle()
    vm.startRevote(closed.copy(vote = vote.copy(nextRoundVoteId = "child")))
    vm.startOwnerDecision(closed.copy(vote = vote.copy(resolvedOptionId = "a")))
    assertNull(vm.state.value.revoteSource)
    assertTrue(vm.state.value.ownerOptions.isEmpty())
  }

  @Test
  fun permissionUsesViewerCalendarAcrossDstAndIgnoresNonAuthoritativeStatus() = runTest {
    val zone = ZoneId.of("Europe/Zurich")
    val end = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant()
    `when`(trips.getTrip("trip"))
        .thenReturn(
            detailsTestTrip()
                .copy(
                    endDate = Timestamp(end.epochSecond, 0),
                    status = TripStatus.COMPLETED,
                )
        )
    fun at(instant: String) =
        TieResolutionViewModel(
            repository,
            trips,
            "trip",
            "alex",
            false,
            Clock.fixed(Instant.parse(instant), zone),
        )
    val lastHour = at("2026-10-25T22:30:00Z")
    val followingDate = at("2026-10-25T23:00:00Z")
    advanceUntilIdle()
    assertTrue(lastHour.state.value.canResolve)
    assertFalse(followingDate.state.value.canResolve)
  }

  @Test
  fun missingOrFailedTripLookupNeverEnablesResolution() = runTest {
    `when`(trips.getTrip("trip")).thenReturn(null)
    val missing = TieResolutionViewModel(repository, trips, "trip", "alex", false, clock)
    advanceUntilIdle()
    assertFalse(missing.state.value.canResolve)
    `when`(trips.getTrip("trip")).thenThrow(IllegalStateException("offline"))
    val failed = TieResolutionViewModel(repository, trips, "trip", "alex", false, clock)
    advanceUntilIdle()
    assertEquals(ResolutionEligibility.Failed(), failed.state.value.eligibility)
    assertFalse(failed.state.value.failed)
    failed.startRevote(closed)
    failed.startOwnerDecision(closed)
    assertNull(failed.state.value.revoteSource)
    assertTrue(failed.state.value.ownerOptions.isEmpty())
    verifyNoInteractions(repository)
  }

  @Test
  fun failedOwnerDecisionRetainsDraftForRetryAndLocksEditsWhileSubmitting() = runTest {
    val vm = model()
    advanceUntilIdle()
    `when`(repository.resolveByOwner("trip", "original", "a", "alex")).thenAnswer {
      throw IllegalStateException("stale result")
    }
    vm.startOwnerDecision(closed)
    vm.confirmOwnerDecision(closed)
    verifyNoInteractions(repository)
    vm.selectOwnerOption("a")
    vm.confirmOwnerDecision(closed)
    vm.selectOwnerOption("b")
    vm.dismiss()
    vm.startRevote(closed)
    assertTrue(vm.state.value.submitting)
    assertEquals("a", vm.state.value.selectedOptionId)
    assertEquals(options.take(2), vm.state.value.ownerOptions)
    assertNull(vm.state.value.revoteSource)
    advanceUntilIdle()
    assertTrue(vm.state.value.failed)
    assertFalse(vm.state.value.submitting)
    assertEquals("a", vm.state.value.selectedOptionId)
    doAnswer { null }.`when`(repository).resolveByOwner("trip", "original", "a", "alex")
    vm.confirmOwnerDecision(closed)
    advanceUntilIdle()
    assertFalse(vm.state.value.failed)
    assertTrue(vm.state.value.ownerOptions.isEmpty())
    assertNull(vm.state.value.selectedOptionId)
    verify(repository, times(2)).resolveByOwner("trip", "original", "a", "alex")
  }

  @Test
  fun staleOwnerSelectionCannotResolveChangedResultOrDelegatedRound() = runTest {
    val vm = model()
    advanceUntilIdle()
    vm.startOwnerDecision(closed)
    vm.selectOwnerOption("a")
    vm.confirmOwnerDecision(closed.copy(vote = vote.copy(uid = "different")))
    vm.confirmOwnerDecision(
        closed.copy(result = VoteResult(mapOf("b" to 1, "c" to 1), setOf("b", "c")))
    )
    vm.confirmOwnerDecision(closed.copy(vote = vote.copy(nextRoundVoteId = "next")))
    vm.confirmOwnerDecision(closed.copy(vote = vote.copy(resolvedOptionId = "b")))
    vm.startRevote(closed.copy(vote = vote.copy(status = VoteStatus.OPEN)))
    vm.startRevote(closed.copy(result = VoteResult(mapOf("a" to 2), setOf("a"))))
    advanceUntilIdle()
    verifyNoInteractions(repository)
    assertNull(vm.state.value.revoteSource)
    vm.dismiss()
    assertNull(vm.state.value.selectedOptionId)
    assertTrue(vm.state.value.ownerOptions.isEmpty())
    val roundTwo = closed.copy(vote = vote.copy(uid = "round2", parentVoteId = vote.uid, round = 2))
    vm.startRevote(roundTwo)
    assertEquals(2, vm.state.value.revoteSource?.round)
    assertEquals("round2", vm.state.value.revoteSource?.uid)
    assertEquals(options.take(2), vm.state.value.revoteSource?.options)
  }

  @Test
  fun eligibilityFailuresRetryWithoutLosingOwnerDraftAndConcurrentLoadsAreCoalesced() = runTest {
    val vm = model()
    advanceUntilIdle()
    vm.startOwnerDecision(closed)
    vm.selectOwnerOption("a")
    `when`(trips.getTrip("trip")).thenReturn(null)
    vm.refreshEligibility()
    advanceUntilIdle()
    assertEquals(ResolutionEligibility.Failed(missingTrip = true), vm.state.value.eligibility)
    assertFalse(vm.state.value.refreshing)
    assertEquals("a", vm.state.value.selectedOptionId)
    assertFalse(vm.state.value.failed)
    `when`(trips.getTrip("trip")).thenThrow(IllegalStateException("offline"))
    vm.refreshEligibility()
    advanceUntilIdle()
    assertEquals(ResolutionEligibility.Failed(), vm.state.value.eligibility)
    val gate = CompletableDeferred<Unit>()
    var reads = 0
    val loadingTrips =
        object : TripRepository by trips {
          override suspend fun getTrip(tripId: String): Trip {
            reads++
            gate.await()
            return detailsTestTrip()
          }
        }
    val loading = TieResolutionViewModel(repository, loadingTrips, "trip", "alex", false, clock)
    loading.refreshEligibility()
    loading.refreshEligibility()
    runCurrent()
    assertEquals(1, reads)
    assertEquals(ResolutionEligibility.Loading, loading.state.value.eligibility)
    assertFalse(loading.state.value.canResolve)
    gate.complete(Unit)
    advanceUntilIdle()
    assertTrue(loading.state.value.canResolve)
    doReturn(detailsTestTrip()).`when`(trips).getTrip("trip")
    vm.refreshEligibility()
    advanceUntilIdle()
    assertTrue(vm.state.value.canResolve)
    assertEquals("a", vm.state.value.selectedOptionId)
    assertEquals(options.take(2), vm.state.value.ownerOptions)
  }

  @Test
  fun ownerConfirmationRechecksOwnershipMembershipAndLoadingErrorsBeforeWriting() = runTest {
    val vm = model()
    advanceUntilIdle()
    vm.startOwnerDecision(closed)
    vm.selectOwnerOption("a")
    for (trip in
        listOf(
            detailsTestTrip().copy(ownerId = "other"),
            detailsTestTrip().copy(memberIds = emptyList()),
        )) {
      `when`(trips.getTrip("trip")).thenReturn(trip)
      vm.confirmOwnerDecision(closed)
      advanceUntilIdle()
      verifyNoInteractions(repository)
      assertFalse(vm.state.value.submitting)
      doReturn(detailsTestTrip()).`when`(trips).getTrip("trip")
      vm.refreshEligibility()
      advanceUntilIdle()
    }
    `when`(trips.getTrip("trip")).thenThrow(IllegalStateException("offline"))
    vm.confirmOwnerDecision(closed)
    advanceUntilIdle()
    assertEquals(ResolutionEligibility.Failed(), vm.state.value.eligibility)
    assertFalse(vm.state.value.failed)
    assertEquals("a", vm.state.value.selectedOptionId)
    verifyNoInteractions(repository)
  }

  @Test
  fun activeViewModelRejectsOwnerAndRevoteWritesAfterFinalLocalDayAcrossDst() = runTest {
    val zone = ZoneId.of("Europe/Zurich")
    var instant = Instant.parse("2026-10-25T22:30:00Z")
    val movingClock =
        object : Clock() {
          override fun getZone() = zone

          override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant, zone)

          override fun instant() = instant
        }
    `when`(trips.getTrip("trip"))
        .thenReturn(
            detailsTestTrip()
                .copy(
                    endDate =
                        Timestamp(
                            LocalDate.of(2026, 10, 25).atStartOfDay(zone).toEpochSecond(),
                            0,
                        )
                )
        )
    val vm = TieResolutionViewModel(repository, trips, "trip", "alex", false, movingClock)
    advanceUntilIdle()
    assertTrue(vm.state.value.canResolve)
    vm.startRevote(closed)
    vm.startOwnerDecision(closed)
    vm.selectOwnerOption("a")
    instant = Instant.parse("2026-10-25T23:00:00Z")
    vm.confirmOwnerDecision(closed)
    advanceUntilIdle()
    assertEquals(
        ResolutionRestriction.ENDED,
        (vm.state.value.eligibility as ResolutionEligibility.Ready).restriction,
    )
    assertFalse(vm.validateRevoteSubmission().await())
    assertNotNull(vm.state.value.revoteSource)
    verifyNoInteractions(repository)
  }

  @Test
  fun cancelledEligibilityAndOwnerWriteDoNotBecomeSubmissionFailures() = runTest {
    `when`(trips.getTrip("trip")).thenThrow(CancellationException("left trip"))
    val cancelled = TieResolutionViewModel(repository, trips, "trip", "alex", false, clock)
    advanceUntilIdle()
    assertFalse(cancelled.state.value.failed)
    doReturn(detailsTestTrip()).`when`(trips).getTrip("trip")
    cancelled.refreshEligibility()
    advanceUntilIdle()
    cancelled.startOwnerDecision(closed)
    cancelled.selectOwnerOption("a")
    `when`(repository.resolveByOwner("trip", "original", "a", "alex"))
        .thenThrow(CancellationException("left decision"))
    cancelled.confirmOwnerDecision(closed)
    advanceUntilIdle()
    assertFalse(cancelled.state.value.submitting)
    assertFalse(cancelled.state.value.failed)
    cancelled.dismiss()
    assertNull(cancelled.state.value.ownerVoteId)
  }

  @Test
  fun linkedParentIsHiddenAndSecondTieRemainsUnresolved() {
    val parent = vote.copy(nextRoundVoteId = "child")
    val child = vote.copy(uid = "child", parentVoteId = vote.uid, round = 2)
    val state =
        VotesUiState.Ready(
            emptyList(),
            listOf(parent, child),
            finalSummaries = mapOf(parent.uid to closed.result, child.uid to closed.result),
        )
    assertEquals(listOf(child), state.unresolvedClosedVotes)
    assertTrue(state.decidedVotes.isEmpty())
    assertEquals(
        listOf(child.copy(resolvedOptionId = "a")),
        state.copy(closedVotes = listOf(parent, child.copy(resolvedOptionId = "a"))).decidedVotes,
    )
    assertTrue(
        state
            .copy(
                liveVotes = listOf(child.copy(status = VoteStatus.OPEN)),
                closedVotes = listOf(parent),
            )
            .unresolvedClosedVotes
            .isEmpty()
    )
  }
}
