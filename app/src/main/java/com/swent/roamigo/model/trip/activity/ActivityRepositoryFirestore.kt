// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.activityFromFirestore
import com.swent.roamigo.model.trip.requireData
import com.swent.roamigo.model.trip.toFirebaseTimestamp
import com.swent.roamigo.model.trip.toFirestoreData
import com.swent.roamigo.model.trip.validDocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [ActivityRepository], following `model/database_structure.txt`.
 *
 * @param db the Firestore instance holding the trips.
 * @param currentUserId returns the ID of the signed-in user, or `null` when signed out.
 */
class ActivityRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val currentUserId: () -> String?,
) : ActivityRepository {

  override suspend fun createActivity(
      tripId: String,
      activityId: String,
      draft: ActivityDraft,
  ): Activity {
    val authorId = currentUserId() ?: throw ActivityCreationException.NotAuthenticated()
    val errors = draft.validate()
    if (errors.isNotEmpty()) throw ActivityCreationException.Validation(errors)

    val activity =
        try {
          val document = activities(tripId).document(validDocumentId(activityId))
          // A transaction makes the existence check and the write atomic, so a retry after an
          // ambiguous failure cannot overwrite the first attempt.
          db.runTransaction { transaction ->
                val existing = transaction.get(document)
                if (existing.exists()) {
                  activityFromFirestore(existing.id, tripId, existing.requireData())
                } else {
                  val created = draft.toActivity(activityId, tripId, authorId, Timestamp.now())
                  transaction.set(document, created.toFirestoreData())
                  created
                }
              }
              .await()
        } catch (error: CancellationException) {
          throw error
        } catch (error: FirebaseFirestoreException) {
          throw error.toActivityCreationException()
        } catch (error: Exception) {
          throw ActivityCreationException.Unknown(error)
        }

    // Deciding outside the transaction keeps the exception types independent of how Firestore
    // reports failures thrown from inside a transaction function.
    if (activity.createdByUserId != authorId) throw ActivityCreationException.PermissionDenied()
    return activity
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
              } catch (error: Exception) {
                close(error)
                return@addSnapshotListener
              }
          trySend(updates)
        }
    awaitClose { registration.remove() }
  }

  private fun activities(tripId: String): CollectionReference =
      db.collection("trips").document(validDocumentId(tripId)).collection("activities")
}

private fun FirebaseFirestoreException.toActivityCreationException(): ActivityCreationException =
    when (code) {
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
): Activity =
    Activity(
        uid = activityId,
        tripId = tripId,
        activityType = ActivityType.ACCEPTED,
        name = name,
        description = description,
        location = location,
        startTime = startTime.toFirebaseTimestamp(),
        endTime = endTime?.toFirebaseTimestamp(),
        createdByUserId = authorId,
        creationTime = createdAt,
    )
