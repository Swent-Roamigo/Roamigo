// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

/**
 * Thrown when sharing or observing trip locations fails, e.g. because the user is not a trip
 * member, the device is offline or no position is available. Keeps callers independent of the
 * backend.
 */
class LocationSharingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
