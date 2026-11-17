// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.friends.FriendRequest
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.users.UserRepositoryException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion

/**
 * A reconciled snapshot of the three relationship streams.
 *
 * A friendship takes precedence over a pending request with the same generation ID. Newer requests
 * with different IDs are preserved.
 */
internal data class RelationshipSnapshot(
    val friends: List<Friendship>,
    val incoming: List<FriendRequest>,
    val outgoing: List<FriendRequest>,
)

/** Ensures operations never use data belonging to another signed-in account. */
internal suspend fun UserRepository.ensureCurrentUser(expectedUserId: String) {
  if (getCurrentUser().uid != expectedUserId) {
    throw UserRepositoryException.NotAuthenticated()
  }
}

/**
 * A relationship or authentication observer ending normally is treated as an ended session, not as
 * a successful empty result.
 *
 * Cancellation and upstream failures keep their original cause.
 */
internal fun <T> Flow<T>.requireSession(): Flow<T> = onCompletion { cause ->
  if (cause == null) {
    throw FriendRepositoryException.NotAuthenticated()
  }
}

/** Identifies authentication errors without depending on Firebase types. */
internal fun Exception.isSessionFailure(): Boolean =
    this is FriendRepositoryException.NotAuthenticated ||
        this is UserRepositoryException.NotAuthenticated

/**
 * Combines the three relationship observers and reconciles overlapping generations without changing
 * the underlying repository data.
 */
internal fun FriendRepository.observeRelationshipSnapshots(): Flow<RelationshipSnapshot> =
    combine(
        observeFriends().requireSession(),
        observeIncomingRequests().requireSession(),
        observeOutgoingRequests().requireSession(),
    ) { friends, incoming, outgoing ->
      val establishedIds = friends.mapTo(hashSetOf()) { it.uid }

      RelationshipSnapshot(
          friends = friends,
          incoming = incoming.filterNot { it.uid in establishedIds },
          outgoing = outgoing.filterNot { it.uid in establishedIds },
      )
    }
