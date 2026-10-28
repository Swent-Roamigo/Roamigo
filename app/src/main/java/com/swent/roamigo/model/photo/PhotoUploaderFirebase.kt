// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [PhotoUploader] backed by Firebase Storage, at `trips/{tripId}/photos/{photoId}.jpg`. Who may
 * read and add photos is decided by `storage.rules`.
 *
 * @param storage looked up at the first upload, so Firebase is not touched before it is needed.
 */
class PhotoUploaderFirebase(private val storage: () -> FirebaseStorage = { Firebase.storage }) :
    PhotoUploader {

  override suspend fun upload(photo: PendingPhoto, file: File) {
    val metadata =
        StorageMetadata.Builder()
            .setContentType("image/jpeg")
            // The rules check the uploader against this, so nobody can add a photo in another name.
            .setCustomMetadata(UPLOADED_BY, photo.userId)
            .setCustomMetadata(TAKEN_AT, photo.takenAtMillis.toString())
            .apply {
              photo.latitude?.let { setCustomMetadata(LATITUDE, it.toString()) }
              photo.longitude?.let { setCustomMetadata(LONGITUDE, it.toString()) }
            }
            .build()
    try {
      storage()
          .reference
          .child("trips/${photo.tripId}/photos/${photo.id}${PhotoFileStore.EXTENSION}")
          .putFile(Uri.fromFile(file), metadata)
          .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: StorageException) {
      throw PhotoUploadException("Could not upload the photo", e, retryable = e.isRetryable())
    }
  }

  // The server refused the photo, or the project is not set up for it. Trying again cannot help.
  private fun StorageException.isRetryable(): Boolean =
      errorCode !in
          setOf(
              StorageException.ERROR_NOT_AUTHENTICATED,
              StorageException.ERROR_NOT_AUTHORIZED,
              StorageException.ERROR_BUCKET_NOT_FOUND,
              StorageException.ERROR_PROJECT_NOT_FOUND,
          )

  companion object {
    const val UPLOADED_BY = "uploadedBy"
    const val TAKEN_AT = "takenAt"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
  }
}
