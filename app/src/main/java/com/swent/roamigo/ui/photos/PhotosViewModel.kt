// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.photo.PHOTO_LOCATION_TIMEOUT
import com.swent.roamigo.model.photo.PhotoCoordinates
import com.swent.roamigo.model.photo.PhotoLocationResult
import com.swent.roamigo.model.photo.PhotoLocator
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes photos for one trip, shows the ones waiting to be uploaded, and the ones the trip already
 * has.
 *
 * The camera permission is asked in context, only once the user wants to take a photo, and the
 * screen is told what to do through [effects]: this class holds the steps but no Android types.
 *
 * A photo is tagged with the position read when the camera returns it, using only a location
 * permission that is already granted: this screen never asks for it.
 *
 * @param locator reads where a photo is taken.
 * @param clock returns the current time in epoch milliseconds.
 */
class PhotosViewModel(
    private val repository: PhotoRepository,
    connectivity: ConnectivityRepository,
    val tripId: String,
    private val locator: PhotoLocator,
    private val currentUserId: String? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private data class Local(
      val cameraStep: CameraStep = CameraStep.NONE,
      val preview: File? = null,
      val saving: Boolean = false,
      val saveFailed: Boolean = false,
      val location: PhotoLocationStatus? = null,
      val viewingId: String? = null,
  )

  private data class Gallery(
      val photos: List<TripPhoto> = emptyList(),
      val loading: Boolean = true,
      val failed: Boolean = false,
  )

  private val local = MutableStateFlow(Local())
  private val gallery = MutableStateFlow(Gallery())
  private var galleryJob: Job? = null

  // The file the camera is writing into, until it reports back. It is kept in the saved state
  // because Android may kill the app while the camera is open.
  private var captureFile: File?
    get() = savedStateHandle.get<String>(CAPTURE_FILE_KEY)?.let(::File)
    set(value) = savedStateHandle.set(CAPTURE_FILE_KEY, value?.path)

  private var cleared = false

  // When the camera returned the photo in the preview, and when its position fetch started. The
  // fetch start gives the part of the timeout that is left when the user saves, or after the
  // process was killed. Both are kept in the saved state along with the position.
  private var takenAtMillis = 0L
  private var fetchStartedAtMillis = 0L
  private var locationJob: Job? = null

  // Identifies the current fetch, so that a result arriving after a retake, a close or a timeout
  // cannot overwrite the state of the photo that replaced it.
  private var fetchGeneration = 0

  // Set while the user is in the app's settings, to resume the photo they asked for.
  private var awaitingSettings = false

  val uiState: StateFlow<PhotosUiState> =
      combine(repository.pendingPhotos(tripId), connectivity.isOnline, local, gallery) {
              photos,
              online,
              local,
              gallery ->
            PhotosUiState(
                isOnline = online,
                photos = photos,
                uploaded = gallery.photos,
                galleryLoading = gallery.loading,
                galleryFailed = gallery.failed,
                // A photo that is no longer in the gallery cannot stay open.
                viewing = gallery.photos.firstOrNull { it.id == local.viewingId },
                currentUserId = currentUserId,
                cameraStep = local.cameraStep,
                preview = local.preview,
                saving = local.saving,
                saveFailed = local.saveFailed,
                previewLocation = local.location,
            )
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, PhotosUiState(galleryLoading = true))

  init {
    // Photos left by a user who signed out are only uploaded once something starts a round.
    repository.resumeUploads()
  }

  private val _effects = Channel<PhotosEffect>(Channel.BUFFERED)
  val effects: Flow<PhotosEffect> = _effects.receiveAsFlow()

  init {
    observeGallery()
    restorePreview()
  }

  /** Reads the trip's photos again after they could not be read. */
  fun onGalleryRetry() {
    if (gallery.value.failed) observeGallery()
  }

  /** Opens an uploaded photo full screen. */
  fun onPhotoClick(photoId: String) = local.update { it.copy(viewingId = photoId) }

  fun onViewerClosed() = local.update { it.copy(viewingId = null) }

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
    awaitingSettings = true
    _effects.trySend(PhotosEffect.OpenSettings)
  }

  /**
   * The screen is back in front, e.g. after the user visited the app's settings. If the camera was
   * allowed there, the photo they asked for is taken without asking again.
   */
  fun onScreenResumed(permissionGranted: Boolean) {
    if (!awaitingSettings) return
    awaitingSettings = false
    if (permissionGranted) startCapture()
  }

  /** The camera could not be opened, e.g. because no camera app is installed. */
  fun onCameraUnavailable() {
    captureFile?.let(repository::discard)
    captureFile = null
  }

  /** The camera closed. [taken] is false if the user cancelled. */
  fun onPictureTaken(taken: Boolean) {
    val file = captureFile ?: return
    captureFile = null
    if (!taken) return repository.discard(file)
    takenAtMillis = clock()
    fetchStartedAtMillis = takenAtMillis
    local.update { it.copy(preview = file, saveFailed = false) }
    savedStateHandle[PREVIEW_FILE_KEY] = file.path
    savedStateHandle[TAKEN_AT_KEY] = takenAtMillis
    savedStateHandle[FETCH_STARTED_AT_KEY] = fetchStartedAtMillis
    setLocation(PhotoLocationStatus.Locating)
    startLocationFetch(PHOTO_LOCATION_TIMEOUT)
  }

  /**
   * Keeps the photo shown in the preview. If the screen is left while the position is still being
   * read, the photo is still saved, but without a position, because no new read is started once the
   * ViewModel is cleared.
   */
  fun onSave() {
    val current = local.value
    val file = current.preview ?: return
    if (current.saving) return
    local.update { it.copy(saving = true, saveFailed = false) }
    viewModelScope.launch {
      // Leaving the screen must not stop the save: the file is only safe once it is in the queue.
      withContext(NonCancellable) {
        try {
          val coordinates = awaitCoordinates()
          repository.savePhoto(tripId, file, takenAtMillis, coordinates)
          clearCapture()
          local.update { it.copy(preview = null, saving = false) }
        } catch (_: Exception) {
          // The photo stays in the preview so the user can try again, unless nobody is left to.
          if (cleared) repository.discard(file)
          else local.update { it.copy(saving = false, saveFailed = true) }
        }
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
    cleared = true
    captureFile?.let(repository::discard)
    // A photo being saved is the repository's now. Deleting it here could lose it.
    local.value.takeUnless { it.saving }?.preview?.let(repository::discard)
  }

  private fun observeGallery() {
    galleryJob?.cancel()
    gallery.update { it.copy(loading = true, failed = false) }
    galleryJob = viewModelScope.launch {
      try {
        repository.tripPhotos(tripId).collect { photos ->
          gallery.value = Gallery(photos, loading = false)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The photos read before stay on the screen, with a way to try again.
        gallery.update { it.copy(loading = false, failed = true) }
      }
    }
  }

  private fun startLocationFetch(budget: Duration) {
    locationJob?.cancel()
    val generation = ++fetchGeneration
    locationJob = viewModelScope.launch {
      val status =
          try {
            locator.locate(budget).toStatus()
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            // Safety net: the locator promises not to throw, but a photo is worth more than its
            // position.
            PhotoLocationStatus.Unavailable
          }
      // A cancelled fetch may still return a result, e.g. one it had already read.
      if (generation == fetchGeneration) setLocation(status)
    }
  }

  /**
   * The position to save the photo with, or null if there is none. Waits only for what is left of
   * the fetch's time budget, so the user never waits longer than the timeout in total.
   */
  private suspend fun awaitCoordinates(): PhotoCoordinates? {
    if (local.value.location == PhotoLocationStatus.Locating) {
      // A fetch cancelled with the view model also ends the join, and counts as no position.
      withTimeoutOrNull(remainingBudget()) { locationJob?.join() }
      locationJob?.cancel()
      fetchGeneration++
      if (local.value.location == PhotoLocationStatus.Locating) {
        setLocation(PhotoLocationStatus.Unavailable)
      }
    }
    return (local.value.location as? PhotoLocationStatus.Attached)?.let {
      PhotoCoordinates.orNull(it.latitude, it.longitude)
    }
  }

  private fun remainingBudget(): Duration =
      (PHOTO_LOCATION_TIMEOUT - (clock() - fetchStartedAtMillis).milliseconds).coerceIn(
          Duration.ZERO,
          PHOTO_LOCATION_TIMEOUT,
      )

  /** Shows [status] for the preview, if there still is one, and keeps it in the saved state. */
  private fun setLocation(status: PhotoLocationStatus) {
    if (local.value.preview == null) return
    local.update { it.copy(location = status) }
    savedStateHandle[LOCATION_STATUS_KEY] =
        when (status) {
          PhotoLocationStatus.Locating -> STATUS_LOCATING
          is PhotoLocationStatus.Attached -> STATUS_ATTACHED
          PhotoLocationStatus.Unavailable -> STATUS_UNAVAILABLE
          PhotoLocationStatus.Off -> STATUS_OFF
        }
    val attached = status as? PhotoLocationStatus.Attached
    savedStateHandle[LATITUDE_KEY] = attached?.latitude
    savedStateHandle[LONGITUDE_KEY] = attached?.longitude
  }

  /** Forgets the position and times of the photo that leaves the preview, and stops its fetch. */
  private fun clearCapture() {
    locationJob?.cancel()
    locationJob = null
    fetchGeneration++
    takenAtMillis = 0L
    fetchStartedAtMillis = 0L
    PREVIEW_KEYS.forEach { savedStateHandle.remove<Any>(it) }
    local.update { it.copy(location = null) }
  }

  /** Brings back the preview that was open when the process was killed, and its position. */
  private fun restorePreview() {
    val file = savedStateHandle.get<String>(PREVIEW_FILE_KEY)?.let(::File)
    if (file == null || !file.exists()) {
      // Without its photo the position belongs to nothing.
      PREVIEW_KEYS.forEach { savedStateHandle.remove<Any>(it) }
      return
    }
    takenAtMillis = savedStateHandle.get<Long>(TAKEN_AT_KEY) ?: clock()
    fetchStartedAtMillis = savedStateHandle.get<Long>(FETCH_STARTED_AT_KEY) ?: takenAtMillis
    local.update { it.copy(preview = file) }
    val latitude = savedStateHandle.get<Double>(LATITUDE_KEY)
    val longitude = savedStateHandle.get<Double>(LONGITUDE_KEY)
    when (savedStateHandle.get<String>(LOCATION_STATUS_KEY)) {
      STATUS_ATTACHED ->
          setLocation(
              // A corrupt saved pair must not reach awaitCoordinates, which would then throw.
              if (latitude != null && longitude != null) {
                PhotoCoordinates.orNull(latitude, longitude)?.let {
                  PhotoLocationStatus.Attached(it.latitude, it.longitude)
                } ?: PhotoLocationStatus.Unavailable
              } else PhotoLocationStatus.Unavailable
          )
      STATUS_OFF -> setLocation(PhotoLocationStatus.Off)
      STATUS_LOCATING -> {
        val budget = remainingBudget()
        if (budget > Duration.ZERO) {
          setLocation(PhotoLocationStatus.Locating)
          startLocationFetch(budget)
        } else setLocation(PhotoLocationStatus.Unavailable)
      }
      else -> setLocation(PhotoLocationStatus.Unavailable)
    }
  }

  private fun PhotoLocationResult.toStatus(): PhotoLocationStatus =
      when (this) {
        is PhotoLocationResult.Found ->
            PhotoLocationStatus.Attached(coordinates.latitude, coordinates.longitude)
        PhotoLocationResult.PermissionOff -> PhotoLocationStatus.Off
        PhotoLocationResult.Unavailable,
        PhotoLocationResult.TimedOut -> PhotoLocationStatus.Unavailable
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
    clearCapture()
    local.update { it.copy(preview = null, saveFailed = false) }
    return true
  }

  private companion object {
    const val CAPTURE_FILE_KEY = "capture_file"
    const val PREVIEW_FILE_KEY = "preview_file"
    const val LOCATION_STATUS_KEY = "preview_location_status"
    const val LATITUDE_KEY = "preview_latitude"
    const val LONGITUDE_KEY = "preview_longitude"
    const val TAKEN_AT_KEY = "preview_taken_at"
    const val FETCH_STARTED_AT_KEY = "preview_fetch_started_at"
    val PREVIEW_KEYS =
        listOf(
            PREVIEW_FILE_KEY,
            LOCATION_STATUS_KEY,
            LATITUDE_KEY,
            LONGITUDE_KEY,
            TAKEN_AT_KEY,
            FETCH_STARTED_AT_KEY,
        )
    const val STATUS_LOCATING = "locating"
    const val STATUS_ATTACHED = "attached"
    const val STATUS_UNAVAILABLE = "unavailable"
    const val STATUS_OFF = "off"
  }
}
