// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Instant

/**
 * A mutual friendship established by accepting a request.
 *
 * [uid] is the accepted request's identifier, preserved for the lifetime of this friendship.
 * Neither participant has a privileged role; their ordering has no meaning. A new friendship after
 * removal must use a new identifier.
 */
data class Friendship(
    val uid: String,
    val firstUserId: String,
    val secondUserId: String,
    val acceptedAt: Instant,
) {
  init {
    require(uid.isNotBlank()) { "Friendship identifier must not be blank" }
    require(firstUserId.isNotBlank() && secondUserId.isNotBlank()) {
      "Friendship participants must not be blank"
    }
    require(firstUserId != secondUserId) { "A friendship requires two distinct users" }
  }
}
