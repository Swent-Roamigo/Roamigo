// AI assistance was used for implementation support and code review.
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

  /** Does nothing unless this is a currently displayed, eligible user. */
  fun sendRequest(userId: String) {
    val state = _uiState.value

    if (
        state.sessionEnded ||
            state.relationshipsLoading ||
            state.relationshipsFailed ||
            userId == currentUserId ||
            userId in state.sendingIds ||
            state.results.none {
              it.user.uid == userId && it.relationship == SearchRelationship.AVAILABLE
            }
    ) {
      return
    }

    _uiState.update {
      it.copy(
          sendingIds = it.sendingIds + userId,
          sendFailure = null,
      )
    }

    // LAZY prevents a synchronously finishing repository from racing registration.
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          try {
            ensureAccount()

            val request = friends.sendFriendRequest(userId)

            ensureAccount()

            // Only mark a relationship after backend confirmation.
            // The observers remain authoritative.
            val confirmed =
                when (request.status) {
                  FriendRequestStatus.ACCEPTED -> SearchRelationship.FRIEND

                  FriendRequestStatus.PENDING ->
                      if (request.senderUserId == currentUserId) {
                        SearchRelationship.OUTGOING_PENDING
                      } else {
                        SearchRelationship.INCOMING_PENDING
                      }

                  FriendRequestStatus.DECLINED -> SearchRelationship.UNKNOWN
                }

            _uiState.update { state ->
              if (state.sessionEnded) {
                state
              } else {
                state.copy(
                    results =
                        state.results.map { match ->
                          if (
                              match.user.uid == userId &&
                                  match.relationship == SearchRelationship.AVAILABLE
                          ) {
                            match.copy(relationship = confirmed)
                          } else {
                            match
                          }
                        }
                )
              }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            if (error.isSessionError()) {
              endSession()
            } else {
              _uiState.update { state ->
                val currentStatus =
                    state.results.firstOrNull { it.user.uid == userId }?.relationship

                // A write can commit even when its acknowledgement is lost.
                // Do not report a failure if observation confirmed the relationship.
                if (
                    state.sessionEnded ||
                        currentStatus == null ||
                        currentStatus == SearchRelationship.FRIEND ||
                        currentStatus == SearchRelationship.INCOMING_PENDING ||
                        currentStatus == SearchRelationship.OUTGOING_PENDING
                ) {
                  state
                } else {
                  state.copy(sendFailure = SendRequestFailure(userId, error.toSendFailure()))
                }
              }
            }
          } finally {
            sendJobs.remove(userId)
            _uiState.update { it.copy(sendingIds = it.sendingIds - userId) }
          }
        }

    sendJobs[userId] = job
    job.start()
  }

  private fun search(query: String, clearResults: Boolean) {
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

    searchJob = viewModelScope.launch {
      try {
        if (clearResults) {
          delay(300) // Avoid a server query on every keystroke.
        }

        ensureAccount()

        val found =
            users
                .searchUsers(normalized)
                .asSequence()
                .filter {
                  it.uid != currentUserId &&
                      it.uid.isNotBlank() &&
                      '/' !in it.uid &&
                      it.uid != "." &&
                      it.uid != ".."
                }
                .distinctBy { it.uid }
                .take(20)
                .toList()

        ensureAccount()

        if (version == searchVersion) {
          _uiState.update { state ->
            if (state.sessionEnded) {
              state
            } else {
              state.copy(
                  results =
                      found.map {
                        UserSearchMatch(
                            it,
                            relationships?.statusOf(it.uid) ?: SearchRelationship.UNKNOWN,
                        )
                      },
                  isSearching = false,
                  searchFailure = null,
              )
            }
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (error.isSessionError()) {
          endSession()
        } else if (version == searchVersion) {
          _uiState.update {
            it.copy(
                isSearching = false,
                searchFailure = SearchFailure.DIRECTORY,
            )
          }
        }
      }
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
