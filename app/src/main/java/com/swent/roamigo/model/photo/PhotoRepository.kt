// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.Flow

/** What the screens need to take trip photos and follow their upload. */
interface PhotoRepository {
  /**
   * Emits the signed-in user's photos of the trip that are saved on this device and not uploaded
   * yet.
   */
  fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>>

  /**
   * Emits the trip's bounded gallery list, newest first, and updates as it changes. Locally written
   * photos can appear before the server confirms them; pending queue entries are not included
   * unless they have been written to the gallery. The limit applies before callers filter for
   * usable coordinates.
   *
   * It fails if the photos cannot be read, e.g. because the user is not a member of the trip.
   * Collect again to retry.
   */
  fun tripPhotos(tripId: String): Flow<List<TripPhoto>>

  /** An empty file in the app's photo folder, for the camera to write a new photo into. */
  fun newPhotoFile(): File

  /** The file of a waiting photo, e.g. to show it. */
  fun fileOf(photo: PendingPhoto): File

  /**
   * Keeps [file], which the camera wrote, as a photo of the trip and schedules its upload. Saving
   * the same file again replaces the queued photo instead of adding a second one, so it is safe to
   * retry, for example after process death.
   *
   * @param takenAtMillis when the camera returned the photo, in epoch milliseconds.
   * @param coordinates where the photo was taken, or null if the position is unknown. It is stored
   *   as missing, never as a made-up position.
   * @throws IllegalStateException if no user is signed in, or [file] is empty.
   */
  suspend fun savePhoto(
      tripId: String,
      file: File,
      takenAtMillis: Long,
      coordinates: PhotoCoordinates?,
  )

  /** Deletes [file], e.g. a photo the user did not keep. */
  fun discard(file: File)

  /** Puts a failed photo back among the waiting ones and schedules its upload. */
  suspend fun retry(photoId: String)

  /**
   * Asks for the waiting photos to be uploaded. Needed because a round skips the photos of a user
   * who is not signed in, and nothing else starts one when that user signs in again.
   */
  fun resumeUploads()
}
