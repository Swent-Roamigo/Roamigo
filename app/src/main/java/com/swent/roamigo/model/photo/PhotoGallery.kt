// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import kotlinx.coroutines.flow.Flow

/** The photos of a trip that the server has. */
fun interface PhotoGallery {
  /**
   * Emits the latest photos of the trip, newest first, then again whenever one is added. It fails
   * if the photos cannot be read, e.g. because the user is not a member of the trip.
   */
  fun observe(tripId: String): Flow<List<TripPhoto>>
}
