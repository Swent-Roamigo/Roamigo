// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import kotlinx.coroutines.flow.Flow

/**
 * Friendship data for the authenticated user, independent of the backend implementation.
 *
 * The actor comes from the authenticated session, never from a caller-supplied user ID. All
 * operations require authentication and must be safe to call on the main thread. Failures use
 * [FriendRepositoryException]; coroutine cancellation propagates normally. Invalid arguments,
 * including self-requests, throw [IllegalArgumentException]. Identifiers must be nonblank, contain
 * no slash, and differ from "." and "..". Lists have unspecified ordering.
 *
 * State changes are atomic and successful writes require backend confirmation. There is at most one
 * current relationship per unordered user pair. Only participants may access their data. A
 * committed acceptance needs no retry: refreshing retrieves the friendship. Duplicate calls are
 * handled as documented below, without requiring an automatic retry mechanism.
 */
interface FriendRepository {

  /** Returns established friendships involving the current user, or an empty list if none exist. */
  suspend fun getFriends(): List<Friendship>

  /**
   * Observes the initial friendships and subsequent additions and removals, including empty lists.
   * Each collection owns its subscription and releases it when collection ends. Failures terminate
   * collection without emitting partial lists. Collection ends on sign-out or account change;
   * callers must collect again for a new session. A flow must never switch users silently.
   */
  fun observeFriends(): Flow<List<Friendship>>

  /** Returns pending requests whose recipient is the current user. */
  suspend fun getIncomingRequests(): List<FriendRequest>

  /**
   * Observes initial and subsequent pending incoming requests, including empty lists. Accepted and
   * declined requests disappear from this list. Uses the session, failure, and cleanup contract of
   * [observeFriends].
   */
  fun observeIncomingRequests(): Flow<List<FriendRequest>>

  /** Returns pending requests whose sender is the current user. */
  suspend fun getOutgoingRequests(): List<FriendRequest>

  /**
   * Observes initial and subsequent pending outgoing requests, including empty lists. Accepted and
   * declined requests disappear from this list. Uses the session, failure, and cleanup contract of
   * [observeFriends].
   */
  fun observeOutgoingRequests(): Flow<List<FriendRequest>>

  /**
   * Sends a request to [otherUserId] and returns its resulting state.
   *
   * An outgoing pending request is returned unchanged. An incoming pending request is accepted
   * automatically by its recipient, preserving the request ID and original direction. If already
   * friends, the accepted request is returned unchanged. Thus the result may be pending or
   * accepted. After decline or removal, either user may send a fresh request immediately, using a
   * new ID with no cooldown. Concurrent opposite sends establish one friendship when both succeed;
   * duplicate sends in the same direction do not create additional requests.
   */
  suspend fun sendFriendRequest(otherUserId: String): FriendRequest

  /**
   * Accepts [requestId] with [otherUserId] and returns the mutual friendship.
   *
   * Only the stored recipient may accept. Repeating acceptance of the same accepted request returns
   * the existing friendship. A missing, declined, or replaced request fails with
   * [FriendRepositoryException.Conflict], without restoring a removed friendship or accepting a
   * newer request. The returned friendship's ID equals [requestId].
   */
  suspend fun acceptFriendRequest(otherUserId: String, requestId: String): Friendship

  /**
   * Declines [requestId] with [otherUserId]. Only the stored recipient may decline. Repeating
   * decline of that same declined request succeeds without changes. A missing, accepted, or
   * replaced request fails with [FriendRepositoryException.Conflict].
   */
  suspend fun declineFriendRequest(otherUserId: String, requestId: String)

  /**
   * Removes [friendshipId] with [otherUserId] for both participants. Either friend may remove it.
   * If no relationship remains, succeeds without changes. If a different request or friendship
   * exists, fails with [FriendRepositoryException.Conflict] instead of changing the newer state.
   * Pending and declined requests cannot be removed through this operation.
   */
  suspend fun removeFriend(otherUserId: String, friendshipId: String)
}
