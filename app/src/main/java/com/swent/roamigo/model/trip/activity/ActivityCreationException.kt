// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.swent.roamigo.model.trip.ActivityType

/**
 * A failure to create an activity.
 *
 * [ActivityRepository] and [SuggestionVoteRepository] report persistence and validation failures
 * through this type so callers can handle them without depending on Firebase.
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

  /** The current user already owns a document with this ID and a different activity type. */
  class TypeConflict(val requested: ActivityType, val existing: ActivityType) :
      ActivityCreationException("Cannot create a $requested activity with an ID used by $existing")

  /** The requested IDs contain an incomplete or incompatible suggestion-vote pair. */
  class PairConflict(cause: Throwable? = null) :
      ActivityCreationException(
          "Existing documents do not form the requested suggestion-vote pair",
          cause,
      )

  /** The data source could not be reached. */
  class Network(cause: Throwable? = null) :
      ActivityCreationException("Network error while creating the activity", cause)

  /** Any other failure. */
  class Unknown(cause: Throwable?) :
      ActivityCreationException("Unexpected error while creating the activity", cause)
}
