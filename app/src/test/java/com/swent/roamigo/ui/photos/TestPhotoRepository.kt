// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import com.swent.roamigo.model.photo.PendingPhoto
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

/** [PhotoRepository] for view model tests: it records every call and can fail or wait. */
class TestPhotoRepository(directory: File) : PhotoRepository {
  private val files = PhotoFileStore(directory)
  val photos = MutableStateFlow<List<PendingPhoto>>(emptyList())

  val saved = mutableListOf<Pair<String, File>>()
  val discarded = mutableListOf<File>()
  val retried = mutableListOf<String>()
  val created = mutableListOf<File>()

  /** The photos the server has. */
  val uploaded = MutableStateFlow<List<TripPhoto>>(emptyList())

  /** Thrown by [tripPhotos] instead of emitting, when set. */
  var galleryError: Exception? = null

  /** How many times the gallery was observed. */
  var galleryObservations = 0
    private set

  /** Thrown by [savePhoto] instead of saving, when set. */
  var saveError: Exception? = null

  /** Awaited by [savePhoto] before it saves, when set. */
  var saveGate: CompletableDeferred<Unit>? = null

  override fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>> = photos.map { list ->
    list.filter { it.tripId == tripId }
  }

  override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> = flow {
    galleryObservations++
    galleryError?.let { throw it }
    emitAll(uploaded)
  }

  override fun newPhotoFile(): File = files.newFile().also { created += it }

  override fun fileOf(photo: PendingPhoto): File = files.fileOf(photo)

  override suspend fun savePhoto(tripId: String, file: File) {
    saveGate?.await()
    saveError?.let { throw it }
    saved += tripId to file
  }

  override fun discard(file: File) {
    discarded += file
    files.delete(file)
  }

  override suspend fun retry(photoId: String) {
    retried += photoId
  }
}
