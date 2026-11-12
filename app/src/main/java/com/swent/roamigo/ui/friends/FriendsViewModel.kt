// AI assistance was used for implementation support.
package com.swent.roamigo.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.friends.FriendRepositoryException
import com.swent.roamigo.model.friends.FriendRequest
import com.swent.roamigo.model.friends.Friendship
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.users.UserRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FriendsLoadError {
  RELATIONSHIPS,
  PROFILES,
  SESSION,
}

enum class FriendsAction {
  ACCEPT,
  DECLINE,
  REMOVE,
}

data class FriendsActionFailure(val relationshipId: String, val action: FriendsAction)

/**
 * Live relationship data, separate from the existing screen's travel-statistic presentation models.
 * Missing entries in [profiles] mean the profile is unavailable; the relationship is still present.
 * Errors are categories for the UI to translate with resources, never backend diagnostic strings.
 */
data class FriendsState(
    val friends: List<Friendship> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val profiles: Map<String, User> = emptyMap(),
    val isLoading: Boolean = true,
    val loadError: FriendsLoadError? = null,
    val processingIds: Set<String> = emptySet(),
    val actionFailure: FriendsActionFailure? = null,
)

/**
 * Manages friends for one account. Recreate this ViewModel when [currentUserId] changes. Repository
 * observations own the relationship state; successful actions do not optimistically remove requests
 * or add friendships before those observations confirm the change.
 */
class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository,
    private val currentUserId: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow(FriendsState())
  val uiState: StateFlow<FriendsState> = _uiState.asStateFlow()
  private var observationJob: Job? = null
  private val actionJobs = mutableMapOf<String, Job>()

  init {
    observe()
  }

  /** Restarts failed observation/profile loading. An ended session needs a new ViewModel. */
  fun retry() {
    if (
        _uiState.value.loadError in setOf(FriendsLoadError.RELATIONSHIPS, FriendsLoadError.PROFILES)
    ) {
      observe()
    }
  }

  fun acceptRequest(requestId: String) = respond(requestId, FriendsAction.ACCEPT)

  fun declineRequest(requestId: String) = respond(requestId, FriendsAction.DECLINE)

  private fun respond(requestId: String, action: FriendsAction) {
    val request =
        _uiState.value.incomingRequests.find {
          it.uid == requestId && it.recipientUserId == currentUserId
        } ?: return
    performAction(requestId, action) {
      if (action == FriendsAction.ACCEPT) {
        friendRepository.acceptFriendRequest(request.senderUserId, request.uid)
      } else {
        friendRepository.declineFriendRequest(request.senderUserId, request.uid)
      }
    }
  }

  fun removeFriend(friendshipId: String) {
    val friendship = _uiState.value.friends.find { it.uid == friendshipId } ?: return
    val otherUserId =
        when (currentUserId) {
          friendship.firstUserId -> friendship.secondUserId
          friendship.secondUserId -> friendship.firstUserId
          else -> return
        }
    performAction(friendshipId, FriendsAction.REMOVE) {
      friendRepository.removeFriend(otherUserId, friendship.uid)
    }
  }

  fun dismissActionFailure() {
    _uiState.update { it.copy(actionFailure = null) }
  }

  private fun observe() {
    val previous = observationJob
    previous?.cancel()
    _uiState.update { it.copy(isLoading = true, loadError = null) }
    observationJob = viewModelScope.launch {
      previous?.join()
      try {
        ensureCurrentUser()
        combine(
                friendRepository.observeFriends().requireSession(),
                friendRepository.observeIncomingRequests().requireSession(),
                friendRepository.observeOutgoingRequests().requireSession(),
            ) { friends, incoming, outgoing ->
              Triple(friends, incoming, outgoing)
            }
            .collectLatest { (friends, incoming, outgoing) ->
              val ids =
                  (friends.flatMap { listOf(it.firstUserId, it.secondUserId) } +
                          incoming.map { it.senderUserId } +
                          outgoing.map { it.recipientUserId })
                      .toSet() - currentUserId
              _uiState.update {
                it.copy(
                    friends = friends,
                    incomingRequests = incoming,
                    outgoingRequests = outgoing,
                    profiles = it.profiles.filterKeys { id -> id in ids },
                    isLoading = false,
                    loadError = null,
                )
              }
              try {
                val profiles =
                    if (ids.isEmpty()) emptyMap()
                    else
                        userRepository.getUsers(ids).filter { it.uid in ids }.associateBy { it.uid }
                _uiState.update { it.copy(profiles = profiles) }
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (error: Exception) {
                if (error.isSessionFailure()) throw error
                _uiState.update { it.copy(loadError = FriendsLoadError.PROFILES) }
              }
            }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (error.isSessionFailure()) endSession()
        else
            _uiState.update {
              it.copy(isLoading = false, loadError = FriendsLoadError.RELATIONSHIPS)
            }
      }
    }
  }

  private fun performAction(id: String, action: FriendsAction, operation: suspend () -> Unit) {
    val state = _uiState.value
    if (state.loadError == FriendsLoadError.SESSION || id in state.processingIds) return
    _uiState.update { it.copy(processingIds = it.processingIds + id, actionFailure = null) }
    // Register before starting: a repository may finish synchronously on Main.immediate.
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          try {
            ensureCurrentUser()
            operation()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            if (error.isSessionFailure()) endSession()
            else _uiState.update { it.copy(actionFailure = FriendsActionFailure(id, action)) }
          } finally {
            actionJobs.remove(id)
            _uiState.update { it.copy(processingIds = it.processingIds - id) }
          }
        }
    actionJobs[id] = job
    job.start()
  }

  private suspend fun ensureCurrentUser() {
    if (userRepository.getCurrentUser().uid != currentUserId) {
      throw FriendRepositoryException.NotAuthenticated()
    }
  }

  // These long-lived flows finish normally only when their authenticated session ends.
  private fun <T> Flow<T>.requireSession(): Flow<T> = onCompletion {
    if (it == null) throw FriendRepositoryException.NotAuthenticated()
  }

  private fun Exception.isSessionFailure() =
      this is FriendRepositoryException.NotAuthenticated ||
          this is UserRepositoryException.NotAuthenticated

  private fun endSession() {
    observationJob?.cancel()
    actionJobs.values.toList().forEach { it.cancel() }
    _uiState.value = FriendsState(isLoading = false, loadError = FriendsLoadError.SESSION)
  }

  companion object {
    fun factory(
        friendRepository: FriendRepository,
        userRepository: UserRepository,
        currentUserId: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { FriendsViewModel(friendRepository, userRepository, currentUserId) }
    }
  }
}
