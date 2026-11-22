// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import com.swent.roamigo.model.photo.PendingPhoto
import com.swent.roamigo.model.photo.TripPhoto
import java.io.File

/** The sheet explaining the camera permission, if one is open. */
enum class CameraStep {
  NONE,

  /** Why the camera is needed, shown before the system asks. */
  RATIONALE,

  /** The user refused. The camera stays off, and the system asks again next time. */
  DENIED,

  /** The system no longer asks, so only the app's settings can allow the camera. */
  BLOCKED,
}

/** Whether the photo in the preview has a position, as far as the user can tell. */
sealed interface PhotoLocationStatus {
  /** The position is still being read. */
  data object Locating : PhotoLocationStatus

  /** The photo is tagged with this position. */
  data class Attached(val latitude: Double, val longitude: Double) : PhotoLocationStatus

  /** The permission is granted, but no position could be read in time. */
  data object Unavailable : PhotoLocationStatus

  /** The location permission is off, so the photo has no position. */
  data object Off : PhotoLocationStatus

  /**
   * The permission has been granted since this photo was taken, so the photo itself still has no
   * position: only the next one will.
   */
  data object NextPhotoOnly : PhotoLocationStatus
}

/**
 * @param isOnline whether the device can reach the internet.
 * @param photos the trip's photos saved on this device and not uploaded yet.
 * @param uploaded the latest photos of the trip that the server has, newest first.
 * @param galleryLoading whether the first read of [uploaded] is still to come, so that an empty
 *   list is not mistaken for a trip without photos.
 * @param galleryFailed whether [uploaded] could not be read.
 * @param pendingFailed whether [photos] could not be read from the device.
 * @param viewing the uploaded photo shown full screen, if any.
 * @param currentUserId the signed-in user, to tell their photos from the others.
 * @param preview the photo just taken, waiting for the user to keep it or retake it.
 * @param saving whether [preview] is being saved.
 * @param saveFailed whether the last attempt to save [preview] failed.
 * @param previewLocation whether [preview] has a position, or null when there is no preview.
 */
data class PhotosUiState(
    val isOnline: Boolean = true,
    val photos: List<PendingPhoto> = emptyList(),
    val uploaded: List<TripPhoto> = emptyList(),
    val galleryLoading: Boolean = false,
    val galleryFailed: Boolean = false,
    val pendingFailed: Boolean = false,
    val viewing: TripPhoto? = null,
    val currentUserId: String? = null,
    val cameraStep: CameraStep = CameraStep.NONE,
    val preview: File? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val previewLocation: PhotoLocationStatus? = null,
)

/** One-off requests to the screen, which owns the Android parts: permission, camera, settings. */
sealed interface PhotosEffect {
  /** Show the system dialog asking for the camera permission. */
  data object RequestCameraPermission : PhotosEffect

  /** Open the camera, which writes the photo into [file]. */
  data class LaunchCamera(val file: File) : PhotosEffect

  /** Open the app's page in the system settings. */
  data object OpenSettings : PhotosEffect

  /** A photo could not be queued for another try, e.g. because the device storage failed. */
  data object RetryFailed : PhotosEffect
}
