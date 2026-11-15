// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

/** In-memory invitations for tests: records calls and fails on demand. */
class FakeTripInvitationService(
    var link: InvitationLink = InvitationLink("fake-invitation-token", 1_000L),
    var failure: Exception? = null,
) : TripInvitationService {
  val createdFor = mutableListOf<String>()
  val revokedFor = mutableListOf<String>()

  /** Suspends every call until it is completed, to observe the state while a call is running. */
  var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

  override suspend fun createLink(tripId: String): InvitationLink {
    createdFor += tripId
    enter()
    return link
  }

  override suspend fun revokeLinks(tripId: String) {
    revokedFor += tripId
    enter()
  }

  private suspend fun enter() {
    gate?.await()
    failure?.let { throw it }
  }
}
