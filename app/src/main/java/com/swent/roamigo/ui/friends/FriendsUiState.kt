// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import com.swent.roamigo.model.users.User

/**
 * Represents an established friend displayed on the Friends screen.
 *
 * Travel statistics are presentation data supplied to the UI; this model does not calculate them.
 *
 * @property userId the identifier of the friend.
 * @property displayName the name displayed for the friend.
 * @property countriesVisited the number of countries, or null when unavailable.
 * @property tripsTogether the number of shared trips, or null when unavailable.
 * @property profilePictureUrl an optional avatar, with initials as the fallback.
 * @property friendshipId the relationship generation targeted by removal; null for display-only
 *   rows.
 * @property isProcessing whether an operation on this relationship is still completing.
 */
data class FriendUiModel(
    val userId: String,
    val displayName: String,
    val countriesVisited: Int? = null,
    val tripsTogether: Int? = null,
    val profilePictureUrl: String? = null,
    val friendshipId: String? = null,
    val isProcessing: Boolean = false,
)

/** An incoming request, including its generation so a stale selection cannot answer a newer one. */
data class IncomingFriendRequestUiModel(
    val requestId: String,
    val userId: String,
    val displayName: String,
    val profilePictureUrl: String? = null,
    val isProcessing: Boolean = false,
)

/**
 * Represents a pending friend request sent by the current user.
 *
 * @property userId the identifier of the user who received the request.
 * @property displayName the name displayed for that user.
 * @property countriesVisited the number of countries, or null when unavailable.
 * @property profilePictureUrl an optional avatar, with initials as the fallback.
 */
data class OutgoingFriendRequestUiModel(
    val userId: String,
    val displayName: String,
    val countriesVisited: Int? = null,
    val profilePictureUrl: String? = null,
)

/**
 * Represents the presentation state of the Friends list.
 *
 * @property friends established friendships displayed by the screen.
 * @property outgoingRequests pending requests sent by the current user.
 * @property isLoading whether initial loading or a retry is in progress.
 * @property loadError a load failure, with any previously loaded rows retained.
 * @property incomingRequests pending requests that the current user may answer.
 * @property actionFailure the latest failed mutation, acknowledged when its feedback is dismissed.
 */
data class FriendsUiState(
    val friends: List<FriendUiModel> = emptyList(),
    val outgoingRequests: List<OutgoingFriendRequestUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: FriendsLoadError? = null,
    val incomingRequests: List<IncomingFriendRequestUiModel> = emptyList(),
    val actionFailure: FriendsActionFailure? = null,
)

/** Missing profiles retain their rows; unavailable travel statistics are left absent. */
internal fun FriendsState.toUiState(
    currentUserId: String,
    unknownTraveler: String,
): FriendsUiState =
    FriendsUiState(
        friends =
            friends.map { friendship ->
              val otherUserId =
                  if (friendship.firstUserId == currentUserId) friendship.secondUserId
                  else friendship.firstUserId
              val profile = profiles[otherUserId]
              FriendUiModel(
                  userId = otherUserId,
                  displayName = profile.displayNameOr(unknownTraveler),
                  profilePictureUrl = profile?.profilePictureUrl,
                  friendshipId = friendship.uid,
                  isProcessing = friendship.uid in processingIds,
              )
            },
        outgoingRequests =
            outgoingRequests.map { request ->
              val profile = profiles[request.recipientUserId]
              OutgoingFriendRequestUiModel(
                  userId = request.recipientUserId,
                  displayName = profile.displayNameOr(unknownTraveler),
                  profilePictureUrl = profile?.profilePictureUrl,
              )
            },
        isLoading = isLoading,
        loadError = loadError,
        incomingRequests =
            incomingRequests.map { request ->
              val profile = profiles[request.senderUserId]
              IncomingFriendRequestUiModel(
                  requestId = request.uid,
                  userId = request.senderUserId,
                  displayName = profile.displayNameOr(unknownTraveler),
                  profilePictureUrl = profile?.profilePictureUrl,
                  isProcessing = request.uid in processingIds,
              )
            },
        actionFailure = actionFailure,
    )

private fun User?.displayNameOr(fallback: String): String =
    this?.displayName?.takeIf { it.isNotBlank() } ?: fallback
