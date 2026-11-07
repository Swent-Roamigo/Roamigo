// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

/**
 * Friendship failures that callers can distinguish without depending on a backend SDK. Messages are
 * diagnostic context; user-facing text must come from Android XML resources. Implementations
 * preserve the original failure as [cause] when one is available.
 */
sealed class FriendRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** No user is authenticated for the operation. */
  class NotAuthenticated(cause: Throwable? = null) :
      FriendRepositoryException("A signed-in user is required", cause)

  /** The current user is not allowed to read or change the relationship. */
  class PermissionDenied(cause: Throwable? = null) :
      FriendRepositoryException("Permission denied for friendship operation", cause)

  /** The request, friendship, or state no longer matches the intended operation. */
  class Conflict(cause: Throwable? = null) :
      FriendRepositoryException("Friendship state conflicts with the operation", cause)

  /** The service could not be reached; a write's outcome may be unconfirmed. */
  class Network(cause: Throwable? = null) :
      FriendRepositoryException("Friendship service could not be reached", cause)

  /** Stored data cannot be represented as a valid request or friendship. */
  class InvalidData(cause: Throwable? = null) :
      FriendRepositoryException("Invalid stored friendship data", cause)

  /** A failure that does not match the other categories. */
  class Unknown(cause: Throwable? = null) :
      FriendRepositoryException("Unexpected friendship operation failure", cause)
}
