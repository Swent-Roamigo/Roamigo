// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

/** A preview of an invitation to a trip, for tests; override only what the test is about. */
fun samplePreview(
    tripId: String = "trip-1",
    tripName: String = "Lisbon with friends",
    inviterName: String? = "Alex",
    alreadyMember: Boolean = false,
) = InvitationPreview(tripId, tripName, inviterName, alreadyMember)

/** In-memory invitations for tests: records calls and fails on demand. */
class FakeTripInvitationService(
    var link: InvitationLink = InvitationLink("fake-invitation-token"),
    var preview: InvitationPreview = samplePreview(tripId = "trip", tripName = "Lisbon"),
    var failure: Exception? = null,
) : TripInvitationService {
  val createdFor = mutableListOf<String>()
  val previewed = mutableListOf<String>()
  val accepted = mutableListOf<String>()
  val revokedFor = mutableListOf<String>()

  /** Suspends every call until it is completed, to observe the state while a call is running. */
  var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

  /** When set, each call returns the next link, so a new link can be told from the old one. */
  var links: Iterator<InvitationLink>? = null

  override suspend fun createLink(tripId: String): InvitationLink {
    createdFor += tripId
    enter()
    return links?.takeIf { it.hasNext() }?.next() ?: link
  }

  override suspend fun preview(token: String): InvitationPreview {
    previewed += token
    enter()
    return preview
  }

  override suspend fun accept(token: String): String {
    accepted += token
    enter()
    return preview.tripId
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
