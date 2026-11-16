// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [PhotoRepository] that keeps photos in a [PhotoQueue] until [PhotoSyncer] uploads them.
 *
 * @param gallery the photos of a trip that the server has.
 * @param currentUserId the signed-in user, or null.
 */
class DefaultPhotoRepository(
    private val queue: PhotoQueue,
    private val files: PhotoFileStore,
    private val scheduler: PhotoScheduler,
    private val gallery: PhotoGallery,
    private val currentUserId: () -> String?,
) : PhotoRepository {

  // The queue belongs to the device, so photos saved by another account stay out of sight.
  override fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>> =
      queue.observe(tripId).map { photos -> photos.filter { it.userId == currentUserId() } }

  override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> = gallery.observe(tripId)

  override fun newPhotoFile(): File = files.newFile()

  override fun fileOf(photo: PendingPhoto): File = files.fileOf(photo)

  override suspend fun savePhoto(
      tripId: String,
      file: File,
      takenAtMillis: Long,
      coordinates: PhotoCoordinates?,
  ) {
    val userId = checkNotNull(currentUserId()) { "A user must be signed in to save a photo" }
    check(file.length() > 0) { "The photo is empty" }
    queue.add(
        PendingPhoto(
            id = files.idOf(file),
            tripId = tripId,
            userId = userId,
            fileName = file.name,
            takenAtMillis = takenAtMillis,
            latitude = coordinates?.latitude,
            longitude = coordinates?.longitude,
        )
    )
    scheduler.schedule()
  }

  override fun discard(file: File) = files.delete(file)

  override suspend fun retry(photoId: String) {
    queue.setStatus(photoId, PhotoUploadStatus.WAITING)
    scheduler.schedule()
  }

  override fun resumeUploads() = scheduler.schedule()
}
