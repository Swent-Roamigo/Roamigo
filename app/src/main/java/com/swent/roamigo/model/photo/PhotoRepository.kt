// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.Flow

/** What the screens need to take trip photos and follow their upload. */
interface PhotoRepository {
  /** Emits the trip's photos that are saved on this device and not uploaded yet. */
  fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>>

  /**
   * Emits the latest photos of the trip that the server has, newest first. It fails if they cannot
   * be read, e.g. because the user is not a member of the trip.
   */
  fun tripPhotos(tripId: String): Flow<List<TripPhoto>>

  /** An empty file in the app's photo folder, for the camera to write a new photo into. */
  fun newPhotoFile(): File

  /** The file of a waiting photo, e.g. to show it. */
  fun fileOf(photo: PendingPhoto): File

  /**
   * Keeps [file], which the camera wrote, as a photo of the trip and schedules its upload. The
   * photo is tagged with the current position if the location permission is already granted and a
   * position is available in time; it is saved without one otherwise.
   *
   * @throws IllegalStateException if no user is signed in, or [file] is empty.
   */
  suspend fun savePhoto(tripId: String, file: File)

  /** Deletes [file], e.g. a photo the user did not keep. */
  fun discard(file: File)

  /** Puts a failed photo back among the waiting ones and schedules its upload. */
  suspend fun retry(photoId: String)
}
