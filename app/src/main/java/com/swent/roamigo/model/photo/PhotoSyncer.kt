// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

  /**
   * Runs one round.
   *
   * @return whether a failure may go away later, e.g. because the connection dropped, so another
   *   round is worth trying. A refusal by the server is not.
   */
  suspend fun sync(): Boolean = mutex.withLock {
    val userId = currentUserId() ?: return false
    // Every photo is tried, even after a failure.
    queue.all().filter { it.userId == userId }.map { upload(it) }.any { it }
  }

  /** Returns whether the photo failed in a way that may go away later. */
  private suspend fun upload(photo: PendingPhoto): Boolean {
    val file = files.fileOf(photo)
    if (!file.exists()) {
      // Nothing is left to upload, e.g. the user cleared the app's storage.
      queue.remove(photo.id)
      return false
    }
    queue.setStatus(photo.id, PhotoUploadStatus.UPLOADING)
    return try {
      uploader.upload(photo, file)
      queue.remove(photo.id)
      files.delete(file)
      false
    } catch (e: CancellationException) {
      // The work was stopped, e.g. the connection dropped. The photo is still to be uploaded.
      withContext(NonCancellable) { queue.setStatus(photo.id, PhotoUploadStatus.WAITING) }
      throw e
    } catch (e: IOException) {
      queue.setStatus(photo.id, PhotoUploadStatus.FAILED)
      (e as? PhotoUploadException)?.retryable ?: true
    }
  }
}
