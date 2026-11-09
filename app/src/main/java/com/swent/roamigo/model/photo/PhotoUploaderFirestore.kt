// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.google.firebase.Firebase
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * [PhotoUploader] that stores the photo in Firestore, at `trips/{tripId}/photos/{photoId}`, because
 * Cloud Storage needs a paid Firebase plan. The photo is shrunk first by [compressor] to fit the 1
 * MiB of a document. Who may read and add photos is decided by `firestore.rules`.
 *
 * The document is named after the photo, so uploading the same photo twice, e.g. after a first
 * attempt whose answer was lost, writes the same document again instead of adding a second one.
 *
 * A write made without a connection only completes once the server has it, so waiting for it is
 * safe: the background job that calls this stops if the connection is lost.
 *
 * @param firestore looked up at the first upload, so Firebase is not touched before it is needed.
 * @param dispatcher where the photo is shrunk, which is heavy work.
 */
class PhotoUploaderFirestore(
    private val firestore: () -> FirebaseFirestore = { Firebase.firestore },
    private val compressor: PhotoCompressor = BitmapPhotoCompressor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PhotoUploader {

  override suspend fun upload(photo: PendingPhoto, file: File) {
    val image =
        try {
          withContext(dispatcher) { compressor.compress(file) }
        } catch (e: IOException) {
          // The same file will not get any better, so there is no point in trying again.
          throw PhotoUploadException(
              "The photo cannot be prepared for upload",
              e,
              retryable = false,
          )
        }
    val data = buildMap {
      put(UPLOADED_BY, photo.userId)
      put(TAKEN_AT, photo.takenAtMillis)
      put(IMAGE, Blob.fromBytes(image))
      // The position is one value: both coordinates, or none.
      if (photo.latitude != null && photo.longitude != null) {
        put(LATITUDE, photo.latitude)
        put(LONGITUDE, photo.longitude)
      }
    }
    try {
      firestore()
          .collection("trips")
          .document(photo.tripId)
          .collection("photos")
          .document(photo.id)
          .set(data)
          .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseFirestoreException) {
      throw PhotoUploadException("Could not upload the photo", e, retryable = e.isRetryable())
    }
  }

  // The server refused the photo, so trying again cannot help.
  private fun FirebaseFirestoreException.isRetryable(): Boolean =
      code !in
          setOf(
              FirebaseFirestoreException.Code.PERMISSION_DENIED,
              FirebaseFirestoreException.Code.UNAUTHENTICATED,
              FirebaseFirestoreException.Code.INVALID_ARGUMENT,
              FirebaseFirestoreException.Code.FAILED_PRECONDITION,
          )

  companion object {
    const val UPLOADED_BY = "uploadedBy"
    const val TAKEN_AT = "takenAt"
    const val IMAGE = "image"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
  }
}
