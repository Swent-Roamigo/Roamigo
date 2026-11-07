// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory friendship data for JVM and device tests, with no Firebase or network calls. Pass the
 * same [store] to different users' repositories to model their shared backend. [clock] controls
 * request and response timestamps. [failure] simulates a service failure before a write.
 */
class FakeFriendRepository(
    initialUserId: String? = null,
    private val store: Store = Store(),
    private val clock: Clock = Clock.systemUTC(),
) : FriendRepository {

  /** Shared storage; operations use one lock and observations receive immutable snapshots. */
  class Store {
    internal val mutex = Mutex()
    internal val requests = MutableStateFlow<Map<Set<String>, FriendRequest>>(emptyMap())
    internal var nextRequestNumber = 0L
  }

  // A revision detects sign-out and sign-in even if StateFlow conflates both session changes.
  private data class Session(val userId: String?, val revision: Long)

  private val sessions = MutableStateFlow(Session(initialUserId, 0))
  private val failures = MutableStateFlow<FriendRepositoryException?>(null)
  private val observerCount = AtomicInteger()

  /** Changes the simulated account; existing observations end instead of switching accounts. */
  var currentUserId: String?
    get() = sessions.value.userId
    set(value) {
      sessions.update { if (it.userId == value) it else Session(value, it.revision + 1) }
    }

  /** A failure for this client, applied to reads, writes, and active observations until cleared. */
  var failure: FriendRepositoryException?
    get() = failures.value
    set(value) {
      failures.value = value
    }

  /** Allows tests to verify that observation subscriptions are released. */
  val activeObservers: Int
    get() = observerCount.get()

  override suspend fun getFriends(): List<Friendship> = access { userId ->
    friendships(store.requests.value.values, userId)
  }

  override fun observeFriends(): Flow<List<Friendship>> = flow {
    val session = authenticatedSession()
    val userId = requireNotNull(session.userId)
    observerCount.incrementAndGet()
    try {
      combine(sessions, store.requests, failures) { _, requests, failure ->
            if (sessions.value != session) null
            else {
              failure?.let { throw it }
              friendships(requests.values, userId)
            }
          }
          .takeWhile { it != null && sessions.value == session }
          .filterNotNull()
          .distinctUntilChanged()
          .collect { emit(it) }
    } finally {
      observerCount.decrementAndGet()
    }
  }

  override suspend fun getIncomingRequests(): List<FriendRequest> = access { userId ->
    store.requests.value.values.filter {
      it.status == FriendRequestStatus.PENDING && it.recipientUserId == userId
    }
  }

  override suspend fun getOutgoingRequests(): List<FriendRequest> = access { userId ->
    store.requests.value.values.filter {
      it.status == FriendRequestStatus.PENDING && it.senderUserId == userId
    }
  }

  override suspend fun sendFriendRequest(otherUserId: String): FriendRequest = access { userId ->
    val pair = pair(userId, otherUserId)
    val existing = store.requests.value[pair]
    val request =
        when (existing?.status) {
          null,
          FriendRequestStatus.DECLINED ->
              FriendRequest(
                  uid = "request-${++store.nextRequestNumber}",
                  senderUserId = userId,
                  recipientUserId = otherUserId,
                  status = FriendRequestStatus.PENDING,
                  createdAt = clock.instant(),
              )
          FriendRequestStatus.PENDING ->
              if (existing.senderUserId == userId) existing
              else
                  existing.copy(
                      status = FriendRequestStatus.ACCEPTED,
                      respondedAt = clock.instant(),
                  )
          FriendRequestStatus.ACCEPTED -> existing
        }
    store.requests.value = store.requests.value + (pair to request)
    request
  }

  override suspend fun acceptFriendRequest(otherUserId: String, requestId: String): Friendship =
      respond(otherUserId, requestId, FriendRequestStatus.ACCEPTED).toFriendship()

  override suspend fun declineFriendRequest(otherUserId: String, requestId: String) {
    respond(otherUserId, requestId, FriendRequestStatus.DECLINED)
  }

  override suspend fun removeFriend(otherUserId: String, friendshipId: String) {
    access { userId ->
      requireIdentifier(friendshipId)
      val pair = pair(userId, otherUserId)
      val request = store.requests.value[pair] ?: return@access
      if (request.uid != friendshipId || request.status != FriendRequestStatus.ACCEPTED) {
        throw FriendRepositoryException.Conflict()
      }
      store.requests.value = store.requests.value.filterKeys { it != pair }
    }
  }

  private suspend fun respond(
      otherUserId: String,
      requestId: String,
      status: FriendRequestStatus,
  ): FriendRequest = access { userId ->
    requireIdentifier(requestId)
    val pair = pair(userId, otherUserId)
    val request = store.requests.value[pair] ?: throw FriendRepositoryException.Conflict()
    if (request.uid != requestId) throw FriendRepositoryException.Conflict()
    if (request.recipientUserId != userId) throw FriendRepositoryException.PermissionDenied()
    if (request.status != FriendRequestStatus.PENDING && request.status != status) {
      throw FriendRepositoryException.Conflict()
    }
    val updated =
        if (request.status == status) request
        else request.copy(status = status, respondedAt = clock.instant())
    store.requests.value = store.requests.value + (pair to updated)
    updated
  }

  private suspend fun <T> access(action: (String) -> T): T =
      store.mutex.withLock {
        currentCoroutineContext().ensureActive()
        val userId = requireNotNull(authenticatedSession().userId)
        failure?.let { throw it }
        action(userId)
      }

  private fun authenticatedSession(): Session =
      sessions.value.also {
        if (it.userId == null) throw FriendRepositoryException.NotAuthenticated()
      }

  private fun pair(userId: String, otherUserId: String): Set<String> {
    requireIdentifier(otherUserId)
    require(userId != otherUserId) { "A relationship requires two distinct users" }
    return setOf(userId, otherUserId)
  }

  private fun requireIdentifier(value: String) {
    require(value.isNotBlank() && '/' !in value && value != "." && value != "..") {
      "Invalid relationship identifier"
    }
  }

  private fun friendships(requests: Collection<FriendRequest>, userId: String): List<Friendship> =
      requests
          .filter {
            it.status == FriendRequestStatus.ACCEPTED &&
                (it.senderUserId == userId || it.recipientUserId == userId)
          }
          .map { it.toFriendship() }

  private fun FriendRequest.toFriendship(): Friendship =
      Friendship(uid, senderUserId, recipientUserId, requireNotNull(respondedAt))
}
