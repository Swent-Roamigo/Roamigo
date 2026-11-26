// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import com.swent.roamigo.model.photo.PendingPhoto
import com.swent.roamigo.model.photo.PhotoCoordinates
import com.swent.roamigo.model.photo.PhotoFileStore
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/** What [TestPhotoRepository.savePhoto] was given, besides the trip. */
data class SavedPhoto(
    val file: File,
    val takenAtMillis: Long,
    val coordinates: PhotoCoordinates?,
)

/** [PhotoRepository] for view model tests: it records every call and can fail or wait. */
class TestPhotoRepository(directory: File) : PhotoRepository {
  private val files = PhotoFileStore(directory)
  val photos = MutableStateFlow<List<PendingPhoto>>(emptyList())

  val saved = mutableListOf<Pair<String, File>>()
  val savedPhotos = mutableListOf<SavedPhoto>()
  val discarded = mutableListOf<File>()
  val retried = mutableListOf<String>()
  val created = mutableListOf<File>()
  var resumed = 0

  /** The photos the server has. */
  val uploaded = MutableStateFlow<List<TripPhoto>>(emptyList())

  /** Thrown by [tripPhotos] instead of emitting, when set. */
  var galleryError: Exception? = null

  /** Awaited by [tripPhotos] before it emits, when set. */
  var galleryGate: CompletableDeferred<Unit>? = null

  /** How many times the gallery was observed. */
  var galleryObservations = 0
    private set

  /**
   * Thrown by [pendingPhotos] in place of the next list, when set: before the first one, or after
   * the lists already read when it is set later and the photos change.
   */
  var pendingError: Exception? = null

  /** How many times the waiting photos were observed. */
  var pendingObservations = 0
    private set

  /** Thrown by [retry] instead of retrying, when set. */
  var retryError: Exception? = null

  /** Thrown by [savePhoto] instead of saving, when set. */
  var saveError: Exception? = null

  /** Awaited by [savePhoto] before it saves, when set. */
  var saveGate: CompletableDeferred<Unit>? = null

  override fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>> =
      photos
          .map { list -> list.filter { it.tripId == tripId } }
          .onStart { pendingObservations++ }
          .onEach { pendingError?.let { throw it } }

  override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> = flow {
    galleryObservations++
    galleryError?.let { throw it }
    galleryGate?.await()
    emitAll(uploaded)
  }

  override fun newPhotoFile(): File = files.newFile().also { created += it }

  override fun fileOf(photo: PendingPhoto): File = files.fileOf(photo)

  override suspend fun savePhoto(
      tripId: String,
      file: File,
      takenAtMillis: Long,
      coordinates: PhotoCoordinates?,
  ) {
    saveGate?.await()
    saveError?.let { throw it }
    saved += tripId to file
    savedPhotos += SavedPhoto(file, takenAtMillis, coordinates)
  }

  override fun discard(file: File) {
    discarded += file
    files.delete(file)
  }

  override suspend fun retry(photoId: String) {
    retryError?.let { throw it }
    retried += photoId
  }

  override fun resumeUploads() {
    resumed++
  }
}
