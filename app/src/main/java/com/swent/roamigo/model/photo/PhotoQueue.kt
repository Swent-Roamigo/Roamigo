// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import kotlinx.coroutines.flow.Flow

/** The photos saved on this device and not uploaded yet, in the order they were saved. */
interface PhotoQueue {
  /** Emits the waiting photos of a trip, then again whenever one is added, changed or removed. */
  fun observe(tripId: String): Flow<List<PendingPhoto>>

  /** Every waiting photo, whatever the trip or the user. */
  suspend fun all(): List<PendingPhoto>

  /** Adds [photo], or replaces the photo with the same id. */
  suspend fun add(photo: PendingPhoto)

  /** Does nothing if no photo has this id. */
  suspend fun setStatus(id: String, status: PhotoUploadStatus)

  /** Does nothing if no photo has this id. */
  suspend fun remove(id: String)
}
