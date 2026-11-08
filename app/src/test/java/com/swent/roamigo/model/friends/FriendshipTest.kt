// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Instant
import org.junit.Assert.assertEquals
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
  fun friendship_rejectsReservedAndSlashContainingIdentifiers() {
    for (invalid in listOf(".", "..", "alice/bob")) {
      assertThrows(IllegalArgumentException::class.java) {
        Friendship(invalid, "alice", "bob", acceptedAt)
      }
      assertThrows(IllegalArgumentException::class.java) {
        Friendship("request-1", invalid, "bob", acceptedAt)
      }
      assertThrows(IllegalArgumentException::class.java) {
        Friendship("request-1", "alice", invalid, acceptedAt)
      }
    }
  }

  @Test
  fun friendship_acceptsNonreservedIdentifiersWithoutChangingThem() {
    for (valid in listOf("...", "alice.bob", "alice_bob", "alice-bob", "二", " alice ")) {
      val result = Friendship(valid, valid, "bob", acceptedAt)
      assertEquals(valid, result.uid)
      assertEquals(valid, result.firstUserId)
      assertEquals(valid, Friendship("request-1", "alice", valid, acceptedAt).secondUserId)
    }
  }

  @Test
  fun friendship_acceptsDistinctParticipantsInEitherOrder() {
    Friendship("request-1", "alice", "bob", acceptedAt)
    Friendship("request-1", "bob", "alice", acceptedAt)
  }
}
