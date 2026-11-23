// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [PhotoGallery] reading `trips/{tripId}/photos`, where [PhotoUploaderFirestore] writes. Each
 * document holds its whole image, so only the [limit] latest photos are read.
 *
 * A photo uploaded from this device shows up at once, before the server confirms it, and the photos
 * read before stay available without a connection.
 *
 * Snapshots are decoded on [dispatcher], not on the main thread, since each one copies up to
 * [limit] images.
 *
 * @param firestore looked up when the first trip is observed.
 * @param dispatcher runs the listener. It must run its tasks one at a time, so that snapshots are
 *   delivered in order.
 */
class PhotoGalleryFirestore(
    private val firestore: () -> FirebaseFirestore = { Firebase.firestore },
    private val limit: Long = LIMIT,
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : PhotoGallery {

  override fun observe(tripId: String): Flow<List<TripPhoto>> = callbackFlow {
    val registration =
        firestore()
            .collection("trips")
            .document(tripId)
            .collection("photos")
            .orderBy(PhotoUploaderFirestore.TAKEN_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener(dispatcher.asExecutor()) { snapshot, error ->
              if (error != null) {
                close(error)
              } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toTripPhotoOrNull() })
              }
            }
    awaitClose { registration.remove() }
  }

  // Firestore throws when a field holds another type than the one asked for. One such document
  // must not stop the others from showing, nor end the listener.
  private fun DocumentSnapshot.toTripPhotoOrNull(): TripPhoto? =
      try {
        toTripPhoto()
      } catch (e: RuntimeException) {
        Log.w(TAG, "Skipping malformed photo document $id", e)
        null
      }

  /** The photo in this document, or null if the document is not one the app wrote. */
  private fun DocumentSnapshot.toTripPhoto(): TripPhoto? {
    val image = getBlob(PhotoUploaderFirestore.IMAGE)?.toBytes() ?: return null
    val latitude = getDouble(PhotoUploaderFirestore.LATITUDE)
    val longitude = getDouble(PhotoUploaderFirestore.LONGITUDE)
    // The position is one valid value: both coordinates, or none.
    val position =
        if (latitude != null && longitude != null) PhotoCoordinates.orNull(latitude, longitude)
        else null
    return TripPhoto(
        id = id,
        uploadedBy = getString(PhotoUploaderFirestore.UPLOADED_BY) ?: return null,
        takenAtMillis = getLong(PhotoUploaderFirestore.TAKEN_AT) ?: return null,
        latitude = position?.latitude,
        longitude = position?.longitude,
        image = image,
    )
  }

  companion object {
    private const val TAG = "PhotoGalleryFirestore"

    /** Enough for a trip's worth of photos at a glance, and a bounded download. */
    const val LIMIT = 50L
  }
}
