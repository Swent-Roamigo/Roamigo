// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import java.io.IOException

/** Sends a photo saved on the device to the server. */
interface PhotoUploader {
  /**
   * Returns once the server has confirmed the photo.
   *
   * @throws PhotoUploadException if the photo could not be uploaded.
   */
  suspend fun upload(photo: PendingPhoto, file: File)
}

/**
 * An upload that failed.
 *
 * @param retryable whether trying again later can succeed. It is false when the server refused the
 *   photo, e.g. because the user is not a member of the trip.
 */
class PhotoUploadException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = true,
) : IOException(message, cause)
