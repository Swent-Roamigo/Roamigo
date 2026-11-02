// AI assistance was used for implementation support.
package com.swent.roamigo.model.authentication

/** Sign-in failures that callers can distinguish without depending on a provider SDK. */
sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {

  /** The authentication service could not be reached. */
  class Network(cause: Throwable) : AuthException("Authentication network failure", cause)

  /** The supplied credential is unsupported, malformed, or rejected. */
  class InvalidCredential(cause: Throwable? = null) :
      AuthException("Invalid authentication credential", cause)

  /** An unclassified failure, including a successful response without a user. */
  class Unknown(cause: Throwable? = null) :
      AuthException("Unexpected authentication failure", cause)
}
