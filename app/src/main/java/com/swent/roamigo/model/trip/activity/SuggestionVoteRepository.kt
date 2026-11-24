// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.validDocumentId
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.temporal.ChronoUnit

/**
 * A persisted suggestion and its initial vote, which may already have advanced to a later round.
 */
data class SuggestionWithVote(val activity: Activity, val vote: Vote)

interface SuggestionVoteRepository {
  /**
   * Creates both documents atomically for the signed-in trip member. Generate [suggestionId] once
   * per submission. A retry by the same author returns the stored pair regardless of [draft]. Begin
   * at the returned vote and follow [Vote.nextRoundVoteId] for the current round.
   *
   * @throws IllegalArgumentException for invalid Firestore IDs, including a suggestion ID too long
   *   to fit the reserved vote prefix.
   * @throws ActivityCreationException for validation, permission, persistence or pair conflicts.
   */
  suspend fun createSuggestionWithVote(
      tripId: String,
      suggestionId: String,
      draft: ActivityDraft,
  ): SuggestionWithVote
}

private const val SUGGESTION_VOTE_PREFIX = "suggestion-"
private val suggestionOptions = listOf(VoteOption("YES", "Yes"), VoteOption("NO", "No"))

/** The initial vote ID is the literal prefix `suggestion-` followed by the activity document ID. */
fun initialSuggestionVoteId(suggestionId: String): String =
    validateSuggestionDocumentId(
        SUGGESTION_VOTE_PREFIX + validateSuggestionDocumentId(suggestionId)
    )

internal fun validateSuggestionDocumentId(id: String): String {
  validDocumentId(id)
  require(
      id.toByteArray(Charsets.UTF_8).size <= 1500 && !(id.startsWith("__") && id.endsWith("__"))
  ) {
    "Invalid Firestore document ID"
  }
  require(Charsets.UTF_8.newEncoder().canEncode(id)) { "Document ID must be valid UTF-8" }
  return id
}

internal fun ActivityDraft.newSuggestionPair(
    tripId: String,
    suggestionId: String,
    voteId: String,
    authorId: String,
): SuggestionWithVote {
  val errors = validate(startRequired = false)
  if (errors.isNotEmpty()) throw ActivityCreationException.Validation(errors)
  val createdAt = Timestamp.now()
  // Firestore stores timestamps with microsecond precision.
  val activity =
      copy(
              startTime = startTime?.truncatedTo(ChronoUnit.MICROS),
              endTime = endTime?.truncatedTo(ChronoUnit.MICROS),
          )
          .toActivity(suggestionId, tripId, authorId, createdAt, ActivityType.SUGGESTION)
          .copy(linkedVoteId = voteId)
  val vote =
      Vote(
          uid = voteId,
          tripId = tripId,
          question = activity.name,
          options = suggestionOptions,
          createdByUserId = authorId,
          status = VoteStatus.OPEN,
          creationTime = createdAt,
          closingTime = null,
          winningOptionId = null,
          suggestionId = suggestionId,
      )
  return SuggestionWithVote(activity, vote)
}

internal fun storedSuggestionPair(
    activity: Activity?,
    vote: Vote?,
    tripId: String,
    suggestionId: String,
    voteId: String,
    authorId: String,
): SuggestionWithVote {
  if (
      (activity != null && activity.createdByUserId != authorId) ||
          (vote != null && vote.createdByUserId != authorId)
  ) {
    throw ActivityCreationException.PermissionDenied()
  }
  activity?.requireCreationMatch(authorId, ActivityType.SUGGESTION)
  if (
      activity == null ||
          vote == null ||
          activity.uid != suggestionId ||
          activity.tripId != tripId ||
          activity.linkedVoteId != voteId ||
          vote.uid != voteId ||
          vote.tripId != tripId ||
          vote.suggestionId != suggestionId ||
          vote.parentVoteId != null ||
          vote.round != 1 ||
          vote.question != activity.name ||
          vote.options != suggestionOptions ||
          vote.creationTime != activity.creationTime
  ) {
    throw ActivityCreationException.PairConflict()
  }
  return SuggestionWithVote(activity, vote)
}
