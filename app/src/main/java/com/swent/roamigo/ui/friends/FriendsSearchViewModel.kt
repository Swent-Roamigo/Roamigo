package com.swent.roamigo.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.friends.FriendRequest
import com.swent.roamigo.model.friends.FriendRequestStatus
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.users.UserRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UNKNOWN means relationship information is not reliable; sending must be disabled. */
enum class SearchRelationship {
  UNKNOWN,
  AVAILABLE,
  OUTGOING_PENDING,
  INCOMING_PENDING,
  FRIEND,
}

enum class SearchFailure {
  INVALID_QUERY,
  DIRECTORY,
}

enum class SendFailure {
  CONFLICT,
  NETWORK,
  PERMISSION,
  OTHER,
}

data class SendRequestFailure(
    val userId: String,
    val reason: SendFailure,
)

data class UserSearchMatch(
    val user: User,
    val relationship: SearchRelationship,
)

/** No Firebase types or backend diagnostic messages escape into the presentation layer. */
data class FriendsSearchState(
    val query: String = "",
    val results: List<UserSearchMatch> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailure: SearchFailure? = null,
    val relationshipsLoading: Boolean = true,
    val relationshipsFailed: Boolean = false,
    val sendingIds: Set<String> = emptySet(),
    val sendFailure: SendRequestFailure? = null,
    val sessionEnded: Boolean = false,
)

/** Search and request creation only; Accept/Decline/Remove belong to FriendsViewModel (#309). */
class FriendsSearchViewModel(
    private val users: UserRepository,
    private val friends: FriendRepository,
    private val currentUserId: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(FriendsSearchState())
  val uiState: StateFlow<FriendsSearchState> = _uiState.asStateFlow()

  private var relationships: Relationships? = null
  private var relationshipJob: Job? = null
  private var searchJob: Job? = null
  private val sendJobs = mutableMapOf<String, Job>()
  private var searchVersion = 0L

  init {
    observeRelationships()
  }

  fun updateQuery(query: String) {
    if (_uiState.value.sessionEnded || query == _uiState.value.query) return
    search(query, clearResults = true)
  }

  fun retrySearch() {
    val state = _uiState.value
    if (!state.sessionEnded && state.searchFailure == SearchFailure.DIRECTORY) {
      search(state.query, clearResults = false)
    }
  }

  fun retryRelationships() {
    if (_uiState.value.relationshipsFailed && !_uiState.value.sessionEnded) {
      observeRelationships()
    }
  }

  fun dismissSendFailure() {
    _uiState.update { it.copy(sendFailure = null) }
  }

  /** Only a displayed user with no known relationship can receive a new request. */
  fun sendRequest(userId: String) {
    if (!canSendRequest(userId, _uiState.value)) return

    _uiState.update {
      it.copy(
          sendingIds = it.sendingIds + userId,
          sendFailure = null,
      )
    }

    // Register before starting so fast requests cannot race job bookkeeping.
    val job = viewModelScope.launch(start = CoroutineStart.LAZY) { performSend(userId) }

    sendJobs[userId] = job
    job.start()
  }

  private fun canSendRequest(
      userId: String,
      state: FriendsSearchState,
  ): Boolean {
    if (state.sessionEnded || state.relationshipsLoading || state.relationshipsFailed) {
      return false
    }

    if (userId == currentUserId || userId in state.sendingIds) {
      return false
    }

    return state.results.any {
      it.user.uid == userId && it.relationship == SearchRelationship.AVAILABLE
    }
  }

  private suspend fun performSend(userId: String) {
    try {
      ensureAccount()

      val request = friends.sendFriendRequest(userId)

      ensureAccount()
      applyConfirmedRequest(userId, request)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      handleSendFailure(userId, error)
    } finally {
      sendJobs.remove(userId)

      _uiState.update { it.copy(sendingIds = it.sendingIds - userId) }
    }
  }

  private fun applyConfirmedRequest(
      userId: String,
      request: FriendRequest,
  ) {
    val confirmed = request.relationshipFor(currentUserId)

    _uiState.update { state ->
      if (state.sessionEnded) return@update state

      state.copy(
          results =
              state.results.map { match ->
                if (
                    match.user.uid == userId && match.relationship == SearchRelationship.AVAILABLE
                ) {
                  match.copy(relationship = confirmed)
                } else {
                  match
                }
              }
      )
    }
  }

  private fun handleSendFailure(
      userId: String,
      error: Exception,
  ) {
    if (error.isSessionError()) {
      endSession()
      return
    }

    _uiState.update { state ->
      val status = state.results.firstOrNull { it.user.uid == userId }?.relationship

      // Firestore may have committed the request even if its acknowledgement was lost.
      if (state.sessionEnded || status == null || status.isConfirmed()) {
        state
      } else {
        state.copy(sendFailure = SendRequestFailure(userId, error.toSendFailure()))
      }
    }
  }

  private fun search(
      query: String,
      clearResults: Boolean,
  ) {
    searchJob?.cancel()

    val version = ++searchVersion
    val normalized = query.trim()
    val maxLength = if ('@' in normalized) 254 else 120
    val invalid = normalized.length > maxLength

    _uiState.update {
      it.copy(
          query = query,
          results = if (clearResults) emptyList() else it.results,
          isSearching = normalized.isNotEmpty() && !invalid,
          searchFailure = if (invalid) SearchFailure.INVALID_QUERY else null,
          sendFailure = if (clearResults) null else it.sendFailure,
      )
    }

    if (invalid || normalized.isEmpty()) return

    searchJob = viewModelScope.launch { performSearch(normalized, version, clearResults) }
  }

  private suspend fun performSearch(
      query: String,
      version: Long,
      debounce: Boolean,
  ) {
    try {
      if (debounce) {
        delay(SEARCH_DEBOUNCE_MS)
      }

      ensureAccount()

      val matches =
          users
              .searchUsers(query)
              .asSequence()
              .filter(::isValidResult)
              .distinctBy(User::uid)
              .take(MAX_SEARCH_RESULTS)
              .toList()

      ensureAccount()
      publishResults(version, matches)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      handleSearchFailure(version, error)
    }
  }

  private fun isValidResult(user: User): Boolean {
    if (user.uid == currentUserId || user.uid.isBlank()) {
      return false
    }

    return '/' !in user.uid && user.uid != "." && user.uid != ".."
  }

  private fun publishResults(
      version: Long,
      usersFound: List<User>,
  ) {
    if (version != searchVersion) return

    _uiState.update { state ->
      if (state.sessionEnded) return@update state

      state.copy(
          results =
              usersFound.map { user ->
                UserSearchMatch(
                    user,
                    relationships?.statusOf(user.uid) ?: SearchRelationship.UNKNOWN,
                )
              },
          isSearching = false,
          searchFailure = null,
      )
    }
  }

  private fun handleSearchFailure(
      version: Long,
      error: Exception,
  ) {
    if (error.isSessionError()) {
      endSession()
      return
    }

    if (version != searchVersion) return

    _uiState.update {
      it.copy(
          isSearching = false,
          searchFailure = SearchFailure.DIRECTORY,
      )
    }
  }

  private fun observeRelationships() {
    val previous = relationshipJob
    previous?.cancel()

    relationships = null

    _uiState.update { state ->
      state.copy(
          relationshipsLoading = true,
          relationshipsFailed = false,
          results = state.results.map { it.copy(relationship = SearchRelationship.UNKNOWN) },
      )
    }

    relationshipJob = viewModelScope.launch {
      previous?.join()

      try {
        ensureAccount()

        combine(
                friends.observeFriends().requireSession(),
                friends.observeIncomingRequests().requireSession(),
                friends.observeOutgoingRequests().requireSession(),
            ) { established, incoming, outgoing ->
              Relationships.from(
                  currentUserId,
                  established,
                  incoming,
                  outgoing,
              )
            }
            .collect { latest ->
              relationships = latest

              _uiState.update { state ->
                state.copy(
                    relationshipsLoading = false,
                    relationshipsFailed = false,
                    results =
                        state.results.map { it.copy(relationship = latest.statusOf(it.user.uid)) },
                    // Clear an old error if Firestore has now confirmed the request.
                    sendFailure =
                        state.sendFailure?.takeIf {
                          latest.statusOf(it.userId) == SearchRelationship.AVAILABLE
                        },
                )
              }
            }

        // Normal completion means the observed account has ended.
        endSession()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (error.isSessionError()) {
          endSession()
        } else {
          relationships = null

          _uiState.update { state ->
            state.copy(
                relationshipsLoading = false,
                relationshipsFailed = true,
                results = state.results.map { it.copy(relationship = SearchRelationship.UNKNOWN) },
            )
          }
        }
      }
    }
  }

  private suspend fun ensureAccount() {
    if (users.getCurrentUser().uid != currentUserId) {
      throw UserRepositoryException.NotAuthenticated()
    }
  }

  private fun <T> Flow<T>.requireSession(): Flow<T> = onCompletion {
    if (it == null) {
      throw FriendRepositoryException.NotAuthenticated()
    }
  }

  private fun Exception.isSessionError(): Boolean =
      this is UserRepositoryException.NotAuthenticated ||
          this is FriendRepositoryException.NotAuthenticated

  private fun Exception.toSendFailure(): SendFailure =
      when (this) {
        is FriendRepositoryException.Conflict -> SendFailure.CONFLICT
        is FriendRepositoryException.Network -> SendFailure.NETWORK
        is FriendRepositoryException.PermissionDenied -> SendFailure.PERMISSION
        else -> SendFailure.OTHER
      }

  private fun endSession() {
    if (_uiState.value.sessionEnded) return

    ++searchVersion

    _uiState.value =
        FriendsSearchState(
            relationshipsLoading = false,
            sessionEnded = true,
        )

    relationships = null
    searchJob?.cancel()
    relationshipJob?.cancel()
    sendJobs.values.toList().forEach { it.cancel() }
  }

  companion object {
    private const val SEARCH_DEBOUNCE_MS = 300L
    private const val MAX_SEARCH_RESULTS = 20

    fun factory(
        users: UserRepository,
        friends: FriendRepository,
        currentUserId: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { FriendsSearchViewModel(users, friends, currentUserId) }
    }
  }
}

private data class Relationships(
    val friends: Set<String>,
    val incoming: Set<String>,
    val outgoing: Set<String>,
) {
  fun statusOf(uid: String): SearchRelationship =
      when (uid) {
        in friends -> SearchRelationship.FRIEND
        in incoming -> SearchRelationship.INCOMING_PENDING
        in outgoing -> SearchRelationship.OUTGOING_PENDING
        else -> SearchRelationship.AVAILABLE
      }

  companion object {
    fun from(
        actor: String,
        friends: List<Friendship>,
        incoming: List<FriendRequest>,
        outgoing: List<FriendRequest>,
    ): Relationships =
        Relationships(
            friends =
                friends
                    .mapNotNull { friendship ->
                      when (actor) {
                        friendship.firstUserId -> friendship.secondUserId
                        friendship.secondUserId -> friendship.firstUserId
                        else -> null
                      }
                    }
                    .toSet(),
            incoming =
                incoming.filter { it.recipientUserId == actor }.map { it.senderUserId }.toSet(),
            outgoing =
                outgoing.filter { it.senderUserId == actor }.map { it.recipientUserId }.toSet(),
        )
  }
}

private fun FriendRequest.relationshipFor(
    currentUserId: String,
): SearchRelationship =
    when (status) {
      FriendRequestStatus.ACCEPTED -> SearchRelationship.FRIEND

      FriendRequestStatus.PENDING ->
          if (senderUserId == currentUserId) {
            SearchRelationship.OUTGOING_PENDING
          } else {
            SearchRelationship.INCOMING_PENDING
          }

      FriendRequestStatus.DECLINED -> SearchRelationship.UNKNOWN
    }

private fun SearchRelationship.isConfirmed(): Boolean =
    this == SearchRelationship.FRIEND ||
        this == SearchRelationship.INCOMING_PENDING ||
        this == SearchRelationship.OUTGOING_PENDING
