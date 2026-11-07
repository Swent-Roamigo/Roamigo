// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendFirestoreMappingTest {
  private val aliceHash = "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90"
  private val bobHash = "81b637d8fcd2c6da6359e6963113a1170de795e4b725b84d1e0b4cfd9ec58ce9"
  private val documentId = "${aliceHash}_$bobHash"
  private val createdAt = Timestamp(1_700_000_000, 123_456_789)
  private val respondedAt = Timestamp(1_700_000_001, 987_654_321)
  private val pending =
      mapOf<String, Any?>(
          "requestId" to "request-1",
          "senderUserId" to "alice",
          "recipientUserId" to "bob",
          "participantIds" to listOf("alice", "bob"),
          "status" to "PENDING",
          "createdAt" to createdAt,
          "respondedAt" to null,
      )

  @Test
  fun documentId_matchesKnownLowercaseSha256ValuesInBothDirections() {
    assertEquals(documentId, friendshipDocumentId("alice", "bob"))
    assertEquals(documentId, friendshipDocumentId("bob", "alice"))
    assertTrue(documentId.matches(Regex("[0-9a-f]{64}_[0-9a-f]{64}")))
  }

  @Test
  fun documentId_usesUtf8ForNonAsciiIdentifiers() {
    val expected = "084b42f6e95e4e6545e236975ade8e205383fefe1021c9d793f758c21f9377dc"
    assertEquals("${expected}_$aliceHash", friendshipDocumentId("alice", "二"))
  }

  @Test
  fun documentId_doesNotConfusePairsContainingSeparators() {
    assertNotEquals(friendshipDocumentId("a", "b:c"), friendshipDocumentId("a:b", "c"))
    assertNotEquals(friendshipDocumentId("a", "b_c"), friendshipDocumentId("a_b", "c"))
  }

  @Test
  fun documentId_rejectsInvalidParticipantsAndSelfRequests() {
    for (invalid in listOf("", " ", ".", "..", "a/b")) {
      assertThrows(IllegalArgumentException::class.java) { friendshipDocumentId(invalid, "bob") }
      assertThrows(IllegalArgumentException::class.java) { friendshipDocumentId("alice", invalid) }
    }
    assertThrows(IllegalArgumentException::class.java) { friendshipDocumentId("alice", "alice") }
  }

  @Test
  fun pendingRequest_preservesIdentityDirectionAndTimestampNanoseconds() {
    assertEquals(
        FriendRequest(
            "request-1",
            "alice",
            "bob",
            FriendRequestStatus.PENDING,
            Instant.parse("2023-11-14T22:13:20.123456789Z"),
        ),
        friendRequestFromFirestore(documentId, pending),
    )
  }

  @Test
  fun answeredRequests_preserveTheirStatusAndResponseNanoseconds() {
    for (status in listOf(FriendRequestStatus.ACCEPTED, FriendRequestStatus.DECLINED)) {
      val request =
          friendRequestFromFirestore(
              documentId,
              pending + mapOf("status" to status.name, "respondedAt" to respondedAt),
          )
      assertEquals(status, request.status)
      assertEquals(Instant.parse("2023-11-14T22:13:21.987654321Z"), request.respondedAt)
    }
  }

  @Test
  fun acceptedFriendship_usesTheRequestIdRatherThanTheDocumentName() {
    val data = pending + mapOf("status" to "ACCEPTED", "respondedAt" to respondedAt)

    assertEquals(
        Friendship("request-1", "alice", "bob", Instant.parse("2023-11-14T22:13:21.987654321Z")),
        friendshipFromFirestore(documentId, data),
    )
  }

  @Test
  fun friendship_rejectsPendingAndDeclinedRequests() {
    invalid { friendshipFromFirestore(documentId, pending) }
    invalid {
      friendshipFromFirestore(
          documentId,
          pending + mapOf("status" to "DECLINED", "respondedAt" to respondedAt),
      )
    }
  }

  @Test
  fun newRequest_canReverseDirectionAndIdentityAtTheSamePairDocument() {
    val data =
        pending +
            mapOf(
                "requestId" to "request-2",
                "senderUserId" to "bob",
                "recipientUserId" to "alice",
                "participantIds" to listOf("bob", "alice"),
            )
    val request = friendRequestFromFirestore(documentId, data)

    assertEquals("request-2", request.uid)
    assertEquals("bob", request.senderUserId)
    assertEquals("alice", request.recipientUserId)
  }

  @Test
  fun missingDataMissingFieldsAndUnexpectedFields_areInvalidData() {
    invalid { friendRequestFromFirestore(documentId, null) }
    pending.keys.forEach { field ->
      invalid { friendRequestFromFirestore(documentId, pending - field) }
    }
    invalid { friendRequestFromFirestore(documentId, pending + ("extra" to true)) }
  }

  @Test
  fun invalidIdentifiers_areReportedAsInvalidData() {
    for (field in listOf("requestId", "senderUserId", "recipientUserId")) {
      for (value in listOf(null, "", " ", ".", "..", "bad\n/id", 123)) {
        invalid { friendRequestFromFirestore(documentId, pending + (field to value)) }
      }
    }
    invalid {
      friendRequestFromFirestore(
          documentId,
          pending +
              mapOf("recipientUserId" to "alice", "participantIds" to listOf("alice", "alice")),
      )
    }
  }

  @Test
  fun participantMetadata_mustMatchExactlyAndPreserveDirection() {
    val invalidParticipants =
        listOf(
            null,
            "alice,bob",
            emptyList<String>(),
            listOf("alice"),
            listOf("bob", "alice"),
            listOf("alice", "alice"),
            listOf("alice", "charlie"),
            listOf("alice", "bob", "charlie"),
            listOf("alice", 1),
        )
    invalidParticipants.forEach { participants ->
      invalid {
        friendRequestFromFirestore(documentId, pending + ("participantIds" to participants))
      }
    }
  }

  @Test
  fun malformedStatusAndTimestamps_areInvalidData() {
    for (status in listOf(null, "", "pending", "UNKNOWN", 1)) {
      invalid { friendRequestFromFirestore(documentId, pending + ("status" to status)) }
    }
    for (timestamp in listOf(null, 1_700_000_000L, "2023-11-14T22:13:20Z")) {
      invalid { friendRequestFromFirestore(documentId, pending + ("createdAt" to timestamp)) }
    }
    for (timestamp in listOf(1_700_000_001L, "2023-11-14T22:13:21Z")) {
      invalid {
        friendRequestFromFirestore(
            documentId,
            pending + mapOf("status" to "ACCEPTED", "respondedAt" to timestamp),
        )
      }
    }
  }

  @Test
  fun responseTime_mustAgreeWithStatusAndCannotPrecedeCreation() {
    invalid { friendRequestFromFirestore(documentId, pending + ("respondedAt" to respondedAt)) }
    for (status in listOf("ACCEPTED", "DECLINED")) {
      invalid { friendRequestFromFirestore(documentId, pending + ("status" to status)) }
      invalid {
        friendRequestFromFirestore(
            documentId,
            pending +
                mapOf(
                    "status" to status,
                    "respondedAt" to Timestamp(createdAt.seconds, createdAt.nanoseconds - 1),
                ),
        )
      }
      assertEquals(
          Instant.parse("2023-11-14T22:13:20.123456789Z"),
          friendRequestFromFirestore(
                  documentId,
                  pending + mapOf("status" to status, "respondedAt" to createdAt),
              )
              .respondedAt,
      )
    }
  }

  @Test
  fun documentName_mustMatchTheParticipantsAndCanonicalCaseAndOrder() {
    val names =
        listOf(
            "",
            "unrelated",
            friendshipDocumentId("alice", "charlie"),
            documentId.uppercase(Locale.ROOT),
            "${bobHash}_$aliceHash",
        )
    names.forEach { name -> invalid { friendRequestFromFirestore(name, pending) } }
  }

  private fun invalid(action: () -> Unit) {
    val error = assertThrows(FriendRepositoryException.InvalidData::class.java) { action() }
    assertTrue(error.cause is IllegalArgumentException)
  }
}
