// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.roamigo.model.toFirebaseTimestamp
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.activityFromFirestore
import com.swent.roamigo.model.trip.requireData
import com.swent.roamigo.model.trip.toFirestoreData
import com.swent.roamigo.model.trip.validDocumentId
import com.swent.roamigo.model.trip.voting.toFirestoreData as toVoteFirestoreData
import com.swent.roamigo.model.trip.voting.toVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [ActivityRepository] and [SuggestionVoteRepository], following
 * `model/database_structure.txt`.
 *
 * @param db the Firestore instance holding the trips.
 * @param currentUserId returns the ID of the signed-in user, or `null` when signed out.
 */
class ActivityRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val currentUserId: () -> String?,
) : ActivityRepository, SuggestionVoteRepository {

  override suspend fun createActivity(
      tripId: String,
      activityId: String,
      draft: ActivityDraft,
  ): Activity {
    val type = ActivityType.ACCEPTED
    val authorId = currentUserId() ?: throw ActivityCreationException.NotAuthenticated()
    // A malformed ID is a caller bug, so it is thrown as is instead of as an unknown failure.
    val document = activities(tripId).document(validDocumentId(activityId))

    val outcome = mapCreationFailures {
      // A transaction makes the existence check and the write atomic, so a retry after an
      // ambiguous failure cannot overwrite the first attempt.
      db.runTransaction { transaction ->
            val existing = transaction.get(document)
            if (existing.exists()) {
              return@runTransaction CreationOutcome.Stored(
                  activityFromFirestore(existing.id, tripId, existing.requireData())
              )
            }
            val errors = draft.validate(startRequired = true)
            if (errors.isNotEmpty()) return@runTransaction CreationOutcome.InvalidDraft(errors)

            val created = draft.toActivity(activityId, tripId, authorId, Timestamp.now(), type)
            transaction.set(document, created.toFirestoreData())
            CreationOutcome.Stored(created)
          }
          .await()
    }

    // Deciding outside the transaction keeps the exception types independent of how Firestore
    // reports failures thrown from inside a transaction function.
    return when (outcome) {
      is CreationOutcome.Stored -> outcome.activity.requireCreationMatch(authorId, type)
      is CreationOutcome.InvalidDraft -> throw ActivityCreationException.Validation(outcome.errors)
    }
  }

  override suspend fun createSuggestionWithVote(
      tripId: String,
      suggestionId: String,
      draft: ActivityDraft,
  ): SuggestionWithVote {
    validateSuggestionDocumentId(tripId)
    val voteId = initialSuggestionVoteId(suggestionId)
    val authorId = currentUserId() ?: throw ActivityCreationException.NotAuthenticated()
    val trip = db.collection("trips").document(tripId)
    val activityRef = trip.collection("activities").document(suggestionId)
    val voteRef = trip.collection("votes").document(voteId)

    val outcome = mapCreationFailures {
      db.runTransaction { transaction ->
            val activity = transaction.get(activityRef)
            val vote = transaction.get(voteRef)
            if (activity.exists() || vote.exists()) {
              return@runTransaction readStoredPair(
                  activity,
                  vote,
                  tripId,
                  suggestionId,
                  voteId,
                  authorId,
              )
            }
            val pair =
                try {
                  draft.newSuggestionPair(tripId, suggestionId, voteId, authorId)
                } catch (error: ActivityCreationException.Validation) {
                  return@runTransaction PairCreationOutcome(Result.failure(error))
                }
            transaction.set(activityRef, pair.activity.toFirestoreData())
            transaction.set(voteRef, pair.vote.toVoteFirestoreData())
            PairCreationOutcome(Result.success(pair))
          }
          .await()
    }
    return outcome.result.getOrThrow()
  }

  private fun readStoredPair(
      activity: DocumentSnapshot,
      vote: DocumentSnapshot,
      tripId: String,
      suggestionId: String,
      voteId: String,
      authorId: String,
  ): PairCreationOutcome =
      try {
        val storedActivity =
            if (activity.exists())
                activityFromFirestore(activity.id, tripId, activity.requireData())
            else null
        val storedVote =
            if (vote.exists()) {
              val data = vote.requireData()
              if (data["uid"] != voteId || data["tripId"] != tripId) {
                throw ActivityCreationException.PairConflict()
              }
              vote.toVote(tripId)
            } else null
        PairCreationOutcome(
            Result.success(
                storedSuggestionPair(
                    storedActivity,
                    storedVote,
                    tripId,
                    suggestionId,
                    voteId,
                    authorId,
                )
            )
        )
      } catch (error: ActivityCreationException) {
        PairCreationOutcome(Result.failure(error))
      } catch (error: IllegalArgumentException) {
        PairCreationOutcome(Result.failure(ActivityCreationException.PairConflict(error)))
      }

  override fun observeActivities(tripId: String): Flow<List<Activity>> = callbackFlow {
    val registration =
        activities(tripId).addSnapshotListener { snapshot, error ->
          if (error != null) {
            close(error)
            return@addSnapshotListener
          }
          val updates =
              try {
                requireNotNull(snapshot) { "Missing activities snapshot" }
                    .documents
                    .map { activityFromFirestore(it.id, tripId, it.requireData()) }
              } catch (mappingError: Exception) {
                close(mappingError)
                return@addSnapshotListener
              }
          trySend(updates)
        }
    awaitClose { registration.remove() }
  }

  private fun activities(tripId: String): CollectionReference =
      db.collection("trips").document(validDocumentId(tripId)).collection("activities")
}

private data class PairCreationOutcome(val result: Result<SuggestionWithVote>)

private inline fun <T> mapCreationFailures(block: () -> T): T =
    try {
      block()
    } catch (error: CancellationException) {
      throw error
    } catch (error: FirebaseFirestoreException) {
      throw error.toActivityCreationException()
    } catch (error: Exception) {
      throw ActivityCreationException.Unknown(error)
    }

private sealed interface CreationOutcome {
  data class Stored(val activity: Activity) : CreationOutcome

  data class InvalidDraft(val errors: List<ActivityDraftError>) : CreationOutcome
}

private fun FirebaseFirestoreException.toActivityCreationException(): ActivityCreationException =
    when (code) {
      FirebaseFirestoreException.Code.UNAUTHENTICATED ->
          ActivityCreationException.NotAuthenticated(this)
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
          ActivityCreationException.PermissionDenied(this)
      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ActivityCreationException.Network(this)
      else -> ActivityCreationException.Unknown(this)
    }

internal fun ActivityDraft.toActivity(
    activityId: String,
    tripId: String,
    authorId: String,
    createdAt: Timestamp,
    type: ActivityType = ActivityType.ACCEPTED,
): Activity =
    Activity(
        uid = activityId,
        tripId = tripId,
        activityType = type,
        name = name,
        description = description,
        location = location,
        startTime = startTime?.toFirebaseTimestamp(),
        endTime = endTime?.toFirebaseTimestamp(),
        createdByUserId = authorId,
        creationTime = createdAt,
    )
