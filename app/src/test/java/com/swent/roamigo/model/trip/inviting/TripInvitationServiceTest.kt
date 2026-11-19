// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

import com.google.firebase.functions.FirebaseFunctionsException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    assertEquals(
        InvitationLink("tok-0123456789abcdef", Instant.ofEpochMilli(1_700_000_000_000L)),
        link,
    )
  }

  @Test
  fun aLinkWithoutAUsableExpiryStillWorksButHasNoDate() = runTest {
    answer = mapOf("token" to "tok-0123456789abcdef")
    assertEquals(null, service.createLink("trip-1").expiresAt)

    answer = mapOf("token" to "tok-0123456789abcdef", "expirationTime" to "soon")
    assertEquals(null, service.createLink("trip-1").expiresAt)
  }

  @Test
  fun aBlankOrNonTextTokenIsUnavailable() = runTest {
    answer = mapOf("token" to "   ")
    assertTrue(failureOf { service.createLink("trip-1") } is InvitationException.Unavailable)

    answer = mapOf("token" to 42)
    assertTrue(failureOf { service.createLink("trip-1") } is InvitationException.Unavailable)
  }

  @Test
  fun aLinkWithoutATokenIsUnavailable() = runTest {
    answer = mapOf("expirationTime" to 1L)

    assertTrue(failureOf { service.createLink("trip-1") } is InvitationException.Unavailable)
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

    assertTrue(failureOf { service.createLink("trip-1") } is InvitationException.Full)
  }

  @Test
  fun backendErrorCodesAreMappedToWhatTheScreensExplain() {
    val cause = RuntimeException("backend")

    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.NOT_FOUND, cause)
            is InvitationException.Invalid
    )
    assertTrue(
        invitationExceptionFor(FirebaseFunctionsException.Code.INVALID_ARGUMENT, cause)
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
            { unavailable.revokeLinks("t") },
        )
        .forEach { call -> assertTrue(failureOf { call() } is InvitationException.Unavailable) }
  }
}
