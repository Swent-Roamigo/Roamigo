// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

/**
 * Last known position of a trip member, as shared through Firebase Realtime Database at
 * `locations/{tripId}/{userId}`.
 *
 * @property userId unique identifier of the trip member.
 * @property latitude latitude in degrees, within [-90, 90].
 * @property longitude longitude in degrees, within [-180, 180].
 * @property updatedAt time of the last location update, in milliseconds since the epoch. Lets the
 *   UI show how old a position is and detect stale members.
 */
data class MemberLocation(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long,
) {
  companion object {
    internal const val LATITUDE = "latitude"
    internal const val LONGITUDE = "longitude"
    internal const val UPDATED_AT = "updatedAt"

    /**
     * Builds a [MemberLocation] from Realtime Database data.
     *
     * @param userId key of the entry, identifying the trip member.
     * @param data value of the entry, as returned by the database.
     * @return the member location, or null if the user ID is blank or a field is missing, has the
     *   wrong type or holds an invalid coordinate.
     */
    fun fromMap(userId: String, data: Map<*, *>): MemberLocation? {
      if (userId.isBlank()) return null
      // The database returns whole numbers as Long and others as Double, so accept any Number.
      val latitude = (data[LATITUDE] as? Number)?.toDouble() ?: return null
      val longitude = (data[LONGITUDE] as? Number)?.toDouble() ?: return null
      val updatedAt = (data[UPDATED_AT] as? Number)?.toLong() ?: return null
      if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
      return MemberLocation(userId, latitude, longitude, updatedAt)
    }
  }
}
