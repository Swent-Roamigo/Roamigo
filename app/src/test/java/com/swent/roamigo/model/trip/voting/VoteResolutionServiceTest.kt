// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.voting

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.*

class VoteResolutionServiceTest {
  @Test
  fun callablesSendOnlyTrustedIdentifiersAndPropagateFailures() = runTest {
    val functions = mock(FirebaseFunctions::class.java)
    val revote = mock(HttpsCallableReference::class.java)
    val owner = mock(HttpsCallableReference::class.java)
    val result = mock(HttpsCallableResult::class.java)
    val revoteData = mapOf("tripId" to "trip", "parentVoteId" to "parent", "voteId" to "child")
    val ownerData = mapOf("tripId" to "trip", "voteId" to "parent", "optionId" to "a")
    `when`(functions.getHttpsCallable("createRevote")).thenReturn(revote)
    `when`(functions.getHttpsCallable("resolveVoteByOwner")).thenReturn(owner)
    `when`(revote.call(revoteData)).thenReturn(Tasks.forResult(result))
    `when`(owner.call(ownerData)).thenReturn(Tasks.forResult(result))
    val service = VoteResolutionServiceFunctions { functions }
    service.createRevote("trip", "parent", "child")
    service.resolveByOwner("trip", "parent", "a")
    verify(revote).call(revoteData)
    verify(owner).call(ownerData)
    val failure = IllegalStateException("stale tie")
    `when`(owner.call(ownerData)).thenReturn(Tasks.forException(failure))
    assertSame(
        failure,
        runCatching { service.resolveByOwner("trip", "parent", "a") }.exceptionOrNull(),
    )
  }
}
