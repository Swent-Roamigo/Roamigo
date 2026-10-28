// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.swent.roamigo.model.location.LocationRepository
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PhotoRepository] that keeps photos in a [PhotoQueue] until [PhotoSyncer] uploads them.
 *
 * @param currentUserId the signed-in user, or null.
 * @param locationRepository tags photos with a position. It is never asked for the permission.
 * @param locationTimeout how long saving waits for a position before saving without one. A slow fix
 *   must not make the user wait, and a photo is worth more than its position.
 */
class DefaultPhotoRepository(
    private val queue: PhotoQueue,
    private val files: PhotoFileStore,
    private val scheduler: PhotoScheduler,
    private val locationRepository: LocationRepository,
    private val currentUserId: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val locationTimeout: Duration = 5.seconds,
) : PhotoRepository {

  override fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>> = queue.observe(tripId)

  override fun newPhotoFile(): File = files.newFile()

  override fun fileOf(photo: PendingPhoto): File = files.fileOf(photo)

  override suspend fun savePhoto(tripId: String, file: File) {
    val userId = checkNotNull(currentUserId()) { "A user must be signed in to save a photo" }
    check(file.length() > 0) { "The photo is empty" }
    val location =
        if (locationRepository.hasLocationPermission()) {
          withTimeoutOrNull(locationTimeout) { locationRepository.getCurrentLocation() }
        } else {
          null
        }
    queue.add(
        PendingPhoto(
            id = files.idOf(file),
            tripId = tripId,
            userId = userId,
            fileName = file.name,
            takenAtMillis = clock(),
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
    )
    scheduler.schedule()
  }

  override fun discard(file: File) = files.delete(file)

  override suspend fun retry(photoId: String) {
    queue.setStatus(photoId, PhotoUploadStatus.WAITING)
    scheduler.schedule()
  }
}
