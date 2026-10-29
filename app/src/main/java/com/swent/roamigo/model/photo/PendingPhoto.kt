// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

/** Where a photo saved on this device is in its way to the server. */
enum class PhotoUploadStatus {
  /** Saved on the device, not uploaded yet. */
  WAITING,

  /** Being uploaded right now. */
  UPLOADING,

  /** The last upload failed. The photo stays on the device until it is uploaded. */
  FAILED,
}

/**
 * A trip photo saved on this device that the server has not confirmed yet.
 *
 * @param id identifies the photo, and names its file on the device and on the server.
 * @param fileName the photo's file inside the device's photo folder.
 * @param takenAtMillis when the photo was saved, in epoch milliseconds.
 * @param latitude where the photo was taken, or null if the position was not available.
 * @param longitude see [latitude].
 */
data class PendingPhoto(
    val id: String,
    val tripId: String,
    val userId: String,
    val fileName: String,
    val takenAtMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: PhotoUploadStatus = PhotoUploadStatus.WAITING,
)
