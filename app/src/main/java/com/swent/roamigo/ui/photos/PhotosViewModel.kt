// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.photo.PhotoRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Takes photos for one trip and shows the ones waiting to be uploaded.
 *
 * The camera permission is asked in context, only once the user wants to take a photo, and the
 * screen is told what to do through [effects]: this class holds the steps but no Android types.
 */
class PhotosViewModel(
    private val repository: PhotoRepository,
    connectivity: ConnectivityRepository,
    val tripId: String,
) : ViewModel() {

  private data class Local(
      val cameraStep: CameraStep = CameraStep.NONE,
      val preview: File? = null,
      val saving: Boolean = false,
      val saveFailed: Boolean = false,
  )

  private val local = MutableStateFlow(Local())

  // The file the camera is writing into, until it reports back.
  private var captureFile: File? = null

  val uiState: StateFlow<PhotosUiState> =
      combine(repository.pendingPhotos(tripId), connectivity.isOnline, local) {
              photos,
              online,
              local ->
            PhotosUiState(
                isOnline = online,
                photos = photos,
                cameraStep = local.cameraStep,
                preview = local.preview,
                saving = local.saving,
                saveFailed = local.saveFailed,
            )
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, PhotosUiState())

  init {
    // Photos left by a user who signed out are only uploaded once something starts a round.
    repository.resumeUploads()
  }

  private val _effects = Channel<PhotosEffect>(Channel.BUFFERED)
  val effects: Flow<PhotosEffect> = _effects.receiveAsFlow()

  /** The user wants a photo. [permissionGranted] tells whether the camera is already allowed. */
  fun onTakePhotoClick(permissionGranted: Boolean) {
    if (permissionGranted) startCapture() else showStep(CameraStep.RATIONALE)
  }

  /** The user agreed to be asked for the camera permission. */
  fun onRationaleContinue() {
    showStep(CameraStep.NONE)
    _effects.trySend(PhotosEffect.RequestCameraPermission)
  }

  /** Closes the open permission sheet without doing anything. */
  fun onStepDismissed() = showStep(CameraStep.NONE)

  /**
   * The system answered the permission request.
   *
   * @param shouldShowRationale what the system says after a refusal: true if it will ask again,
   *   false if it never will, i.e. the permission is blocked.
   */
  fun onCameraPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
    when {
      granted -> startCapture()
      shouldShowRationale -> showStep(CameraStep.DENIED)
      else -> showStep(CameraStep.BLOCKED)
    }
  }

  fun onOpenSettings() {
    showStep(CameraStep.NONE)
    _effects.trySend(PhotosEffect.OpenSettings)
  }

  /** The camera closed. [taken] is false if the user cancelled. */
  fun onPictureTaken(taken: Boolean) {
    val file = captureFile ?: return
    captureFile = null
    if (taken) local.update { it.copy(preview = file, saveFailed = false) }
    else repository.discard(file)
  }

  /** Keeps the photo shown in the preview. */
  fun onSave() {
    val current = local.value
    val file = current.preview ?: return
    if (current.saving) return
    local.update { it.copy(saving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        repository.savePhoto(tripId, file)
        local.update { it.copy(preview = null, saving = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The photo stays in the preview, so the user can try again instead of losing it.
        local.update { it.copy(saving = false, saveFailed = true) }
      }
    }
  }

  /** Throws the photo in the preview away and opens the camera again. */
  fun onRetake() {
    if (closePreview()) startCapture()
  }

  /** Throws the photo in the preview away. */
  fun onPreviewClosed() {
    closePreview()
  }

  fun onRetry(photoId: String) {
    viewModelScope.launch { repository.retry(photoId) }
  }

  override fun onCleared() {
    captureFile?.let(repository::discard)
    // A photo being saved is the repository's now. Deleting it here could lose it.
    local.value.takeUnless { it.saving }?.preview?.let(repository::discard)
  }

  private fun showStep(step: CameraStep) = local.update { it.copy(cameraStep = step) }

  private fun startCapture() {
    captureFile?.let(repository::discard)
    val file = repository.newPhotoFile()
    captureFile = file
    _effects.trySend(PhotosEffect.LaunchCamera(file))
  }

  /** Returns whether a photo was thrown away; nothing happens while it is being saved. */
  private fun closePreview(): Boolean {
    val current = local.value
    val file = current.preview ?: return false
    if (current.saving) return false
    repository.discard(file)
    local.update { it.copy(preview = null, saveFailed = false) }
    return true
  }
}
