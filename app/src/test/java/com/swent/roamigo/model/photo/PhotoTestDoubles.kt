// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A [PendingPhoto] with the fields tests rarely care about filled in. */
fun pendingPhoto(
    id: String = "p1",
    tripId: String = "t1",
    userId: String = "u1",
    status: PhotoUploadStatus = PhotoUploadStatus.WAITING,
    takenAtMillis: Long = 1_000L,
    latitude: Double? = null,
    longitude: Double? = null,
) =
    PendingPhoto(
        id = id,
        tripId = tripId,
        userId = userId,
        fileName = "$id${PhotoFileStore.EXTENSION}",
        takenAtMillis = takenAtMillis,
        latitude = latitude,
        longitude = longitude,
        status = status,
    )

/** [PhotoQueue] in memory, which tests can read directly. */
class InMemoryPhotoQueue : PhotoQueue {
  private val photos = MutableStateFlow<List<PendingPhoto>>(emptyList())

  val current: List<PendingPhoto>
    get() = photos.value

  override fun observe(tripId: String): Flow<List<PendingPhoto>> = photos.map { list ->
    list.filter { it.tripId == tripId }
  }

  override suspend fun all(): List<PendingPhoto> = photos.value

  override suspend fun add(photo: PendingPhoto) {
    photos.value = photos.value.filterNot { it.id == photo.id } + photo
  }

  override suspend fun setStatus(id: String, status: PhotoUploadStatus) {
    photos.value = photos.value.map { if (it.id == id) it.copy(status = status) else it }
  }

  override suspend fun remove(id: String) {
    photos.value = photos.value.filterNot { it.id == id }
  }
}

/** [PhotoUploader] that records what it uploaded, and fails or waits when a test asks it to. */
class FakePhotoUploader : PhotoUploader {
  /** Ids of the photos uploaded, in order. */
  val uploaded = mutableListOf<String>()

  /** The exception to throw for a photo, or null to let it upload. */
  var failure: (PendingPhoto) -> Throwable? = { null }

  /** Runs when an upload starts, before it fails or succeeds. */
  var onUpload: suspend (PendingPhoto) -> Unit = {}

  override suspend fun upload(photo: PendingPhoto, file: File) {
    onUpload(photo)
    failure(photo)?.let { throw it }
    uploaded += photo.id
  }
}

class FakePhotoScheduler : PhotoScheduler {
  var calls = 0
    private set

  override fun schedule() {
    calls++
  }
}
