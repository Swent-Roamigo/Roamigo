// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

sealed class UserRepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

  class NotAuthenticated(cause: Throwable? = null) :
      UserRepositoryException("Authentication required", cause)

  class PermissionDenied(cause: Throwable? = null) :
      UserRepositoryException("Directory access denied", cause)

  class Network(cause: Throwable? = null) : UserRepositoryException("Directory unavailable", cause)

  class InvalidData(cause: Throwable? = null) :
      UserRepositoryException("Invalid directory data", cause)

  class Unknown(cause: Throwable? = null) :
      UserRepositoryException("Directory operation failed", cause)
}
