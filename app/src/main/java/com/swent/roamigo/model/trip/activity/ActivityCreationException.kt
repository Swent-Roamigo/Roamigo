// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

/**
 * A failure to create an activity.
 *
 * Every implementation of [ActivityRepository.createActivity] reports its failures through this
 * type, so the UI can map each case to a message without depending on Firebase.
 */
sealed class ActivityCreationException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The draft was rejected before anything was saved. */
  class Validation(val errors: List<ActivityDraftError>) :
      ActivityCreationException("Invalid activity draft: ${errors.joinToString()}")

  /** No user is signed in, so the activity has no author. */
  class NotAuthenticated(cause: Throwable? = null) :
      ActivityCreationException("A signed-in user is required to create an activity", cause)

  /** The data source refused to save the activity for the current user. */
  class PermissionDenied(cause: Throwable? = null) :
      ActivityCreationException("Permission denied while creating the activity", cause)

  /** The data source could not be reached. */
  class Network(cause: Throwable? = null) :
      ActivityCreationException("Network error while creating the activity", cause)

  /** Any other failure. */
  class Unknown(cause: Throwable?) :
      ActivityCreationException("Unexpected error while creating the activity", cause)
}
