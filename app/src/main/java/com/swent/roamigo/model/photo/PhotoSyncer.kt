// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What one round of uploads did.
 *
 * @param uploaded photos the server confirmed.
 * @param failed photos that stay on the device because their upload failed.
 * @param shouldRetry whether a failure may go away later, e.g. because the connection dropped.
 */
data class SyncResult(val uploaded: Int, val failed: Int, val shouldRetry: Boolean) {
  companion object {
    val NOTHING = SyncResult(uploaded = 0, failed = 0, shouldRetry = false)
  }
}

/**
 * Uploads the photos waiting on this device, one at a time.
 *
 * A photo is only removed from the device after the server confirmed it. A failure keeps it, marked
 * [PhotoUploadStatus.FAILED], so the next round tries it again.
 *
 * @param currentUserId the signed-in user, or null. Only that user's photos are uploaded: the
 *   others stay until their owner signs in again, because the server would refuse them.
 */
class PhotoSyncer(
    private val queue: PhotoQueue,
    private val files: PhotoFileStore,
    private val uploader: PhotoUploader,
    private val currentUserId: () -> String?,
) {
  // The app can ask for a round while another is running, e.g. a photo saved during an upload.
  private val mutex = Mutex()

  suspend fun sync(): SyncResult = mutex.withLock {
    val userId = currentUserId() ?: return SyncResult.NOTHING
    var uploaded = 0
    var failed = 0
    var shouldRetry = false
    for (photo in queue.all().filter { it.userId == userId }) {
      when (val outcome = upload(photo)) {
        Outcome.UPLOADED -> uploaded++
        Outcome.GONE -> Unit
        Outcome.FAILED,
        Outcome.FAILED_FOR_GOOD -> {
          failed++
          if (outcome == Outcome.FAILED) shouldRetry = true
        }
      }
    }
    SyncResult(uploaded, failed, shouldRetry)
  }

  /** Puts a failed photo back among the waiting ones, e.g. because the user asked to retry it. */
  suspend fun retry(photoId: String) = queue.setStatus(photoId, PhotoUploadStatus.WAITING)

  private enum class Outcome {
    UPLOADED,
    GONE,
    FAILED,
    FAILED_FOR_GOOD,
  }

  private suspend fun upload(photo: PendingPhoto): Outcome {
    val file = files.fileOf(photo)
    if (!file.exists()) {
      // Nothing is left to upload, e.g. the user cleared the app's storage.
      queue.remove(photo.id)
      return Outcome.GONE
    }
    queue.setStatus(photo.id, PhotoUploadStatus.UPLOADING)
    return try {
      uploader.upload(photo, file)
      queue.remove(photo.id)
      files.delete(file)
      Outcome.UPLOADED
    } catch (e: CancellationException) {
      // The work was stopped, e.g. the connection dropped. The photo is still to be uploaded.
      withContext(NonCancellable) { queue.setStatus(photo.id, PhotoUploadStatus.WAITING) }
      throw e
    } catch (e: PhotoUploadException) {
      fail(photo, retryable = e.retryable)
    } catch (_: IOException) {
      fail(photo, retryable = true)
    }
  }

  private suspend fun fail(photo: PendingPhoto, retryable: Boolean): Outcome {
    queue.setStatus(photo.id, PhotoUploadStatus.FAILED)
    return if (retryable) Outcome.FAILED else Outcome.FAILED_FOR_GOOD
  }
}
