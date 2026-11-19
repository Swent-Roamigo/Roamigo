// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.swent.roamigo.model.FUNCTIONS_REGION
import java.time.Instant
import kotlinx.coroutines.tasks.await

/**
 * A link anyone can use to join a trip, until [expiresAt] or until the organiser revokes it.
 * [expiresAt] is null when the backend did not say.
 */
data class InvitationLink(val token: String, val expiresAt: Instant? = null)

/** Why an invitation operation failed, in terms the screens can explain. */
sealed class InvitationException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  /**
   * Unknown, revoked or expired. The server answers all three alike, so tokens cannot be probed.
   */
  class Invalid(cause: Throwable? = null) : InvitationException("Invitation not valid", cause)

  /** The trip already has the maximum number of members. */
  class Full(cause: Throwable? = null) : InvitationException("Trip is full", cause)

  /** Only the trip organiser may create or revoke invitations. */
  class NotAllowed(cause: Throwable? = null) : InvitationException("Not allowed", cause)

  /** The request did not get an answer, or failed for a reason the user cannot fix. */
  class Unavailable(cause: Throwable? = null) : InvitationException("Unavailable", cause)
}

/**
 * Invitations to a trip. They are created and revoked by trusted functions, because joining a trip
 * changes its members, which clients cannot write themselves.
 */
interface TripInvitationService {
  /** The organiser's current link for [tripId]; asking again returns it while it is valid. */
  suspend fun createLink(tripId: String): InvitationLink

  /** Revokes every link of [tripId]. Members who already joined stay on the trip. */
  suspend fun revokeLinks(tripId: String)
}

/**
 * The service when no backend is provided, for previews and tests that do not exercise invitations:
 * every call fails, and nothing reaches the network. Production passes
 * [TripInvitationServiceFunctions] explicitly.
 */
internal object UnavailableTripInvitationService : TripInvitationService {
  override suspend fun createLink(tripId: String): InvitationLink =
      throw InvitationException.Unavailable()

  override suspend fun revokeLinks(tripId: String): Unit = throw InvitationException.Unavailable()
}

/** Calls a Cloud Function by name and returns the data it answered with. */
internal fun interface CallableInvoker {
  suspend fun call(name: String, arguments: Map<String, String>): Any?
}

/** The callable functions of the real project, in the region the backend is deployed to. */
internal class FirebaseCallableInvoker(
    private val functions: () -> FirebaseFunctions = {
      FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    },
) : CallableInvoker {
  override suspend fun call(name: String, arguments: Map<String, String>): Any? =
      try {
        functions().getHttpsCallable(name).call(arguments).await().getData()
      } catch (exception: FirebaseFunctionsException) {
        throw invitationExceptionFor(exception.code, exception)
      }
}

/** The [TripInvitationService] that calls the trusted Cloud Functions. */
internal class TripInvitationServiceFunctions(
    private val invoker: CallableInvoker = FirebaseCallableInvoker(),
) : TripInvitationService {
  override suspend fun createLink(tripId: String): InvitationLink {
    val data = call("createTripInvitation", mapOf("tripId" to tripId))
    // An answer without a usable expiry still gives a working link; the screen just cannot date it.
    val expiresAt = (data["expirationTime"] as? Number)?.toLong()?.let(Instant::ofEpochMilli)
    return InvitationLink(token = data.string("token"), expiresAt = expiresAt)
  }

  override suspend fun revokeLinks(tripId: String) {
    call("revokeTripInvitations", mapOf("tripId" to tripId))
  }

  private suspend fun call(name: String, arguments: Map<String, String>): Map<*, *> =
      invoker.call(name, arguments) as? Map<*, *> ?: throw InvitationException.Unavailable()

  private fun Map<*, *>.string(key: String): String =
      (this[key] as? String)?.takeIf { it.isNotBlank() } ?: throw InvitationException.Unavailable()
}

/** Maps a Cloud Functions error code to the failure the screens explain. */
internal fun invitationExceptionFor(
    code: FirebaseFunctionsException.Code,
    cause: Throwable? = null,
): InvitationException =
    when (code) {
      // The server answers a malformed token with invalid-argument. It is as unusable as an unknown
      // one, and retrying it can never work.
      FirebaseFunctionsException.Code.NOT_FOUND,
      FirebaseFunctionsException.Code.INVALID_ARGUMENT -> InvitationException.Invalid(cause)
      FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> InvitationException.Full(cause)
      FirebaseFunctionsException.Code.PERMISSION_DENIED -> InvitationException.NotAllowed(cause)
      else -> InvitationException.Unavailable(cause)
    }
