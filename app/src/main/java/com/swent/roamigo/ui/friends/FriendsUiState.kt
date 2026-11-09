// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

/**
 * Represents an established friend displayed on the Friends screen.
 *
 * Travel statistics are presentation data supplied to the UI; this model does not calculate them.
 *
 * @property userId the identifier of the friend.
 * @property displayName the name displayed for the friend.
 * @property countriesVisited the number of countries displayed for the friend.
 * @property tripsTogether the number of trips shared with the current user.
 */
data class FriendUiModel(
    val userId: String,
    val displayName: String,
    val countriesVisited: Int,
    val tripsTogether: Int,
)

/**
 * Represents a pending friend request sent by the current user.
 *
 * @property userId the identifier of the user who received the request.
 * @property displayName the name displayed for that user.
 * @property countriesVisited the number of countries displayed for that user.
 */
data class OutgoingFriendRequestUiModel(
    val userId: String,
    val displayName: String,
    val countriesVisited: Int,
)

/**
 * Represents the presentation state of the Friends list.
 *
 * @property friends established friendships displayed by the screen.
 * @property outgoingRequests pending requests sent by the current user.
 */
data class FriendsUiState(
    val friends: List<FriendUiModel> = emptyList(),
    val outgoingRequests: List<OutgoingFriendRequestUiModel> = emptyList(),
)
