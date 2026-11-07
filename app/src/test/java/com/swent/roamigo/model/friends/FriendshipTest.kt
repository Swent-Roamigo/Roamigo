// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class FriendshipTest {
  private val acceptedAt = Instant.parse("2026-11-07T10:00:00Z")

  @Test
  fun friendship_rejectsBlankIdentifiersAndIdenticalParticipants() {
    listOf(
            Triple(" ", "alice", "bob"),
            Triple("request-1", "", "bob"),
            Triple("request-1", "alice", "\t"),
            Triple("request-1", "alice", "alice"),
        )
        .forEach { (uid, firstUserId, secondUserId) ->
          assertThrows(IllegalArgumentException::class.java) {
            Friendship(uid, firstUserId, secondUserId, acceptedAt)
          }
        }
  }

  @Test
  fun friendship_acceptsDistinctParticipantsInEitherOrder() {
    Friendship("request-1", "alice", "bob", acceptedAt)
    Friendship("request-1", "bob", "alice", acceptedAt)
  }
}
