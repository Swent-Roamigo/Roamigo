// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
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
 * @param firestore looked up when the first trip is observed.
 */
class PhotoGalleryFirestore(
    private val firestore: () -> FirebaseFirestore = { Firebase.firestore },
    private val limit: Long = LIMIT,
) : PhotoGallery {

  override fun observe(tripId: String): Flow<List<TripPhoto>> = callbackFlow {
    val registration =
        firestore()
            .collection("trips")
            .document(tripId)
            .collection("photos")
            .orderBy(PhotoUploaderFirestore.TAKEN_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
              if (error != null) {
                close(error)
              } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toTripPhoto() })
              }
            }
    awaitClose { registration.remove() }
  }

  /** The photo in this document, or null if the document is not one the app wrote. */
  private fun DocumentSnapshot.toTripPhoto(): TripPhoto? {
    val image = getBlob(PhotoUploaderFirestore.IMAGE)?.toBytes() ?: return null
    return TripPhoto(
        id = id,
        uploadedBy = getString(PhotoUploaderFirestore.UPLOADED_BY) ?: return null,
        takenAtMillis = getLong(PhotoUploaderFirestore.TAKEN_AT) ?: return null,
        latitude = getDouble(PhotoUploaderFirestore.LATITUDE),
        longitude = getDouble(PhotoUploaderFirestore.LONGITUDE),
        image = image,
    )
  }

  companion object {
    /** Enough for a trip's worth of photos at a glance, and a bounded download. */
    const val LIMIT = 50L
  }
}
