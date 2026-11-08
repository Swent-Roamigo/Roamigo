// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FriendRequestTest {
  private val createdAt = Instant.parse("2026-11-07T10:00:00Z")

  private fun request(
      uid: String = "request-1",
      sender: String = "alice",
      recipient: String = "bob",
      status: FriendRequestStatus = FriendRequestStatus.PENDING,
      respondedAt: Instant? = null,
  ) = FriendRequest(uid, sender, recipient, status, createdAt, respondedAt)

  @Test
  fun request_rejectsBlankIdentifiersAndSelfRequests() {
    listOf<() -> FriendRequest>(
            { request(uid = " ") },
            { request(sender = "") },
            { request(recipient = "\t") },
            { request(sender = "alice", recipient = "alice") },
        )
        .forEach { invalidRequest ->
          assertThrows(IllegalArgumentException::class.java) { invalidRequest() }
        }
  }

  @Test
  fun request_rejectsReservedAndSlashContainingIdentifiers() {
    for (invalid in listOf(".", "..", "alice/bob")) {
      assertThrows(IllegalArgumentException::class.java) { request(uid = invalid) }
      assertThrows(IllegalArgumentException::class.java) { request(sender = invalid) }
      assertThrows(IllegalArgumentException::class.java) { request(recipient = invalid) }
    }
  }

  @Test
  fun request_acceptsNonreservedIdentifiersWithoutChangingThem() {
    for (valid in listOf("...", "alice.bob", "alice_bob", "alice-bob", "二", " alice ")) {
      val result = request(uid = valid, sender = valid)
      assertEquals(valid, result.uid)
      assertEquals(valid, result.senderUserId)
      assertEquals(valid, request(recipient = valid).recipientUserId)
    }
  }

  @Test
  fun pendingRequest_rejectsResponseTime() {
    assertThrows(IllegalArgumentException::class.java) { request(respondedAt = createdAt) }
  }

  @Test
  fun answeredRequest_requiresResponseTime() {
    listOf(FriendRequestStatus.ACCEPTED, FriendRequestStatus.DECLINED).forEach { status ->
      assertThrows(IllegalArgumentException::class.java) { request(status = status) }
    }
  }

  @Test
  fun answeredRequest_rejectsResponseBeforeCreation() {
    listOf(FriendRequestStatus.ACCEPTED, FriendRequestStatus.DECLINED).forEach { status ->
      assertThrows(IllegalArgumentException::class.java) {
        request(status = status, respondedAt = createdAt.minusNanos(1))
      }
    }
  }

  @Test
  fun request_acceptsPendingAndResponseAtOrAfterCreation() {
    request()
    listOf(FriendRequestStatus.ACCEPTED, FriendRequestStatus.DECLINED).forEach { status ->
      request(status = status, respondedAt = createdAt)
      request(status = status, respondedAt = createdAt.plusNanos(1))
    }
  }
}
