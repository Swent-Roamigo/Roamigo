// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Trusted tie validation and atomic persistence; callers never supply counts or owner identity. */
interface VoteResolutionService {
  suspend fun createRevote(tripId: String, parentVoteId: String, voteId: String)

  suspend fun resolveByOwner(tripId: String, voteId: String, optionId: String)
}

internal class VoteResolutionServiceFunctions(
    private val functions: () -> FirebaseFunctions = {
      FirebaseFunctions.getInstance("europe-west1")
    },
) : VoteResolutionService {
  override suspend fun createRevote(tripId: String, parentVoteId: String, voteId: String) {
    functions()
        .getHttpsCallable("createRevote")
        .call(mapOf("tripId" to tripId, "parentVoteId" to parentVoteId, "voteId" to voteId))
        .await()
  }

  override suspend fun resolveByOwner(tripId: String, voteId: String, optionId: String) {
    functions()
        .getHttpsCallable("resolveVoteByOwner")
        .call(mapOf("tripId" to tripId, "voteId" to voteId, "optionId" to optionId))
        .await()
  }
}
