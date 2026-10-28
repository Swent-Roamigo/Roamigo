// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [PhotoRepository] for previews and tests: photos are kept for the process only and
 * nothing is uploaded.
 *
 * @param directory where the camera writes new photos, and where saved photos stay.
 * @param initialPhotos photos already waiting, e.g. a failed one for a preview.
 */
class FakePhotoRepository(
    private val directory: File = File(System.getProperty("java.io.tmpdir"), "roamigo-photos"),
    private val userId: String = "alex",
    initialPhotos: List<PendingPhoto> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
) : PhotoRepository {
  private val files = PhotoFileStore(directory)
  private val photos = MutableStateFlow(initialPhotos)

  /** The photos that count as uploaded, newest first. Tests and previews can set them. */
  val uploaded = MutableStateFlow<List<TripPhoto>>(emptyList())

  override fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>> = photos.map { list ->
    list.filter { it.tripId == tripId && it.userId == userId }
  }

  override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> = uploaded

  override fun newPhotoFile(): File = files.newFile()

  override fun fileOf(photo: PendingPhoto): File = files.fileOf(photo)

  override suspend fun savePhoto(tripId: String, file: File) {
    photos.value +=
        PendingPhoto(files.idOf(file), tripId, userId, file.name, takenAtMillis = clock())
  }

  override fun discard(file: File) = files.delete(file)

  override suspend fun retry(photoId: String) {
    photos.value =
        photos.value.map {
          if (it.id == photoId) it.copy(status = PhotoUploadStatus.WAITING) else it
        }
  }

  override fun resumeUploads() = Unit
}
