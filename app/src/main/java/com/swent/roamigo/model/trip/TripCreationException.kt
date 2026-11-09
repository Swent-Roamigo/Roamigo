// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

/**
 * A failure to create a trip.
 *
 * Every implementation of `TripRepository.createTrip` reports its failures through this type, so
 * the UI can map each case to a message without depending on Firebase or any other data source.
 */
sealed class TripCreationException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The draft was rejected before anything was saved. */
  class Validation(val errors: List<TripDraftError>) :
      TripCreationException("Invalid trip draft: ${errors.joinToString()}")

  /** No user is signed in, so the trip has no owner. */
  class NotAuthenticated(cause: Throwable? = null) :
      TripCreationException("A signed-in user is required to create a trip", cause)

  /** The data source refused to save the trip for the current user. */
  class PermissionDenied(cause: Throwable? = null) :
      TripCreationException("Permission denied while creating the trip", cause)

  /** The data source could not be reached. */
  class Network(cause: Throwable? = null) :
      TripCreationException("Network error while creating the trip", cause)

  /** Any other failure. */
  class Unknown(cause: Throwable?) :
      TripCreationException("Unexpected error while creating the trip", cause)
}
