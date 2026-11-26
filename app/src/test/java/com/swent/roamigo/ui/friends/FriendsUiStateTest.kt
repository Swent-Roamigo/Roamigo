// AI assistance was used for implementation support.
package com.swent.roamigo.ui.friends

import com.swent.roamigo.model.friends.FriendRequest
import com.swent.roamigo.model.friends.FriendRequestStatus
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.User
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendsUiStateTest {
  private val fallback = "Unavailable profile name"
  private val participants = setOf("bob", "carol", "dana", "erin")
  private val relationships =
      FriendsState(
          friends =
              listOf(
                  Friendship("friend-bob", "alice", "bob", Instant.EPOCH),
                  Friendship("friend-carol", "carol", "alice", Instant.EPOCH),
              ),
          incomingRequests =
              listOf(
                  FriendRequest(
                      "incoming-dana",
                      "dana",
                      "alice",
                      FriendRequestStatus.PENDING,
                      Instant.EPOCH,
                  )
              ),
          outgoingRequests =
              listOf(
                  FriendRequest(
                      "outgoing-erin",
                      "alice",
                      "erin",
                      FriendRequestStatus.PENDING,
                      Instant.EPOCH,
                  )
              ),
          isLoading = false,
      )

  @Test
  fun missingProfilesUseTheSuppliedFallbackWithoutDroppingRelationships() {
    val ui = relationships.toUiState("alice", fallback)

    assertEquals(listOf(fallback, fallback), ui.friends.map { it.displayName })
    assertEquals(listOf("bob", "carol"), ui.friends.map { it.userId })
    assertEquals(listOf("friend-bob", "friend-carol"), ui.friends.map { it.friendshipId })
    assertEquals(listOf(fallback), ui.incomingRequests.map { it.displayName })
    assertEquals("incoming-dana", ui.incomingRequests.single().requestId)
    assertEquals(listOf(fallback), ui.outgoingRequests.map { it.displayName })
    assertEquals("erin", ui.outgoingRequests.single().userId)
  }

  @Test
  fun emptyAndWhitespaceNamesUseTheSameFallbackInEverySection() {
    for (blankName in listOf("", " ", "\t\n")) {
      val profiles = participants.associateWith { User(it, blankName, null) }
      val ui = relationships.copy(profiles = profiles).toUiState("alice", fallback)

      assertEquals(listOf(fallback, fallback), ui.friends.map { it.displayName })
      assertEquals(listOf(fallback), ui.incomingRequests.map { it.displayName })
      assertEquals(listOf(fallback), ui.outgoingRequests.map { it.displayName })
    }
  }

  @Test
  fun availableNamesAndPhotosArePreservedAlongsideProcessingAndErrors() {
    val names = mapOf("bob" to " Bob ", "carol" to "Zoë", "dana" to "Dana", "erin" to "Erin")
    val profiles = names.mapValues { (uid, name) ->
      User(uid, name, "https://example.com/$uid.png")
    }
    val failure = FriendsActionFailure("friend-bob", FriendsAction.REMOVE)
    val ui =
        relationships
            .copy(
                profiles = profiles,
                processingIds =
                    setOf(
                        "friend-bob",
                        "incoming-dana",
                        "outgoing-erin",
                    ),
                isLoading = true,
                loadError = FriendsLoadError.PROFILES,
                actionFailure = failure,
            )
            .toUiState("alice", fallback)

    assertEquals(listOf(" Bob ", "Zoë"), ui.friends.map { it.displayName })
    assertEquals(listOf("Dana"), ui.incomingRequests.map { it.displayName })
    assertEquals(listOf("Erin"), ui.outgoingRequests.map { it.displayName })
    assertEquals("https://example.com/bob.png", ui.friends.first().profilePictureUrl)
    assertEquals("https://example.com/dana.png", ui.incomingRequests.single().profilePictureUrl)
    assertEquals("https://example.com/erin.png", ui.outgoingRequests.single().profilePictureUrl)
    assertEquals(listOf(true, false), ui.friends.map { it.isProcessing })
    assertEquals(true, ui.incomingRequests.single().isProcessing)
    assertEquals(
        "outgoing-erin",
        ui.outgoingRequests.single().requestId,
    )
    assertEquals(
        true,
        ui.outgoingRequests.single().isProcessing,
    )
    assertEquals(true, ui.isLoading)
    assertEquals(FriendsLoadError.PROFILES, ui.loadError)
    assertEquals(failure, ui.actionFailure)
  }
}
