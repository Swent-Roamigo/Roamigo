// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import com.swent.roamigo.model.photo.PendingPhoto
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

/**
 * @param isOnline whether the device can reach the internet.
 * @param photos the trip's photos saved on this device and not uploaded yet.
 * @param preview the photo just taken, waiting for the user to keep it or retake it.
 * @param saving whether [preview] is being saved.
 * @param saveFailed whether the last attempt to save [preview] failed.
 */
data class PhotosUiState(
    val isOnline: Boolean = true,
    val photos: List<PendingPhoto> = emptyList(),
    val cameraStep: CameraStep = CameraStep.NONE,
    val preview: File? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** One-off requests to the screen, which owns the Android parts: permission, camera, settings. */
sealed interface PhotosEffect {
  /** Show the system dialog asking for the camera permission. */
  data object RequestCameraPermission : PhotosEffect

  /** Open the camera, which writes the photo into [file]. */
  data class LaunchCamera(val file: File) : PhotosEffect

  /** Open the app's page in the system settings. */
  data object OpenSettings : PhotosEffect
}
