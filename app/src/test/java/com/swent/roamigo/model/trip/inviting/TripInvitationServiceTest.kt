// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripInvitationServiceTest {
  private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
      try {
        block()
        null
      } catch (error: Exception) {
        error
      }

  private val calls = mutableListOf<Pair<String, Map<String, String>>>()
  private var answer: Any? = emptyMap<String, Any?>()
  private var failure: Exception? = null

  private val service = TripInvitationServiceFunctions { name, arguments ->
    calls += name to arguments
    failure?.let { throw it }
    answer
  }

  @Test
  fun createLinkCallsTheFunctionForTheTripAndReadsTheLink() = runTest {
    answer = mapOf("token" to "tok-0123456789abcdef", "expirationTime" to 1_700_000_000_000L)

    val link = service.createLink("trip-1")

    assertEquals(listOf("createTripInvitation" to mapOf("tripId" to "trip-1")), calls)
    assertEquals(InvitationLink("tok-0123456789abcdef", 1_700_000_000_000L), link)
  }

  @Test
  fun aLinkWithoutAnExpiryIsStillUsable() = runTest {
    answer = mapOf("token" to "tok-0123456789abcdef", "expirationTime" to null)

    assertNull(service.createLink("trip-1").expiresAtMillis)
  }

  @Test
  fun aLinkWithoutATokenIsUnavailable() = runTest {
    answer = mapOf("expirationTime" to 1L)

    assertTrue(failureOf { service.createLink("trip-1") } is InvitationException.Unavailable)
  }

  @Test
  fun previewReadsTheTripInviterAndMembership() = runTest {
    answer =
        mapOf(
            "tripId" to "trip-1",
            "tripName" to "Lisbon",
            "inviterName" to "Alex",
            "alreadyMember" to true,
        )

    val preview = service.preview("tok")

    assertEquals(listOf("previewTripInvitation" to mapOf("token" to "tok")), calls)
    assertEquals(InvitationPreview("trip-1", "Lisbon", "Alex", true), preview)
  }

  @Test
  fun aMissingOrBlankInviterNameBecomesNull() = runTest {
    answer = mapOf("tripId" to "t", "tripName" to "Lisbon", "inviterName" to "  ")
    assertNull(service.preview("tok").inviterName)

    answer = mapOf("tripId" to "t", "tripName" to "Lisbon")
    val preview = service.preview("tok")
    assertNull(preview.inviterName)
    assertEquals(false, preview.alreadyMember)
  }

  @Test
  fun aPreviewWithoutATripNameIsUnavailable() = runTest {
    answer = mapOf("tripId" to "t")

    assertTrue(failureOf { service.preview("tok") } is InvitationException.Unavailable)
  }

  @Test
  fun acceptReturnsTheTripJoined() = runTest {
    answer = mapOf("tripId" to "trip-9")

    assertEquals("trip-9", service.accept("tok"))
    assertEquals(listOf("acceptTripInvitation" to mapOf("token" to "tok")), calls)
  }

  @Test
  fun revokeCallsTheFunctionForTheTrip() = runTest {
    answer = mapOf("revoked" to 1)

    service.revokeLinks("trip-1")

    assertEquals(listOf("revokeTripInvitations" to mapOf("tripId" to "trip-1")), calls)
  }

  @Test
  fun anAnswerThatIsNotAMapIsUnavailable() = runTest {
    answer = "oops"

    assertTrue(failureOf { service.revokeLinks("trip-1") } is InvitationException.Unavailable)
  }

  @Test
  fun anInvitationFailureFromTheBackendReachesTheCaller() = runTest {
    failure = InvitationException.Full()

    assertTrue(failureOf { service.accept("tok") } is InvitationException.Full)
  }

  @Test
  fun backendErrorCodesAreMappedToWhatTheScreensExplain() {
    val cause = RuntimeException("backend")

    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.NOT_FOUND, cause)
            is InvitationException.Invalid
    )
    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED, cause)
            is InvitationException.Full
    )
    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.PERMISSION_DENIED, cause)
            is InvitationException.NotAllowed
    )
    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.UNAVAILABLE, cause)
            is InvitationException.Unavailable
    )
    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.UNAUTHENTICATED)
            is InvitationException.Unavailable
    )
    assertEquals(
        cause,
        invitationExceptionFor(FirebaseFunctionsException.Code.NOT_FOUND, cause).cause,
    )
  }

  @Test
  fun theDefaultServiceFailsEveryCallWithoutTouchingTheNetwork() = runTest {
    val unavailable = UnavailableTripInvitationService

    listOf<suspend () -> Unit>(
            { unavailable.createLink("t") },
            { unavailable.preview("tok") },
            { unavailable.accept("tok") },
            { unavailable.revokeLinks("t") },
        )
        .forEach { call -> assertTrue(failureOf { call() } is InvitationException.Unavailable) }
  }
}
