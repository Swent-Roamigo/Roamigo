// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.users.UserRepository

/** Owns account-specific search state and delegates navigation to the app graph. */
@Composable
fun FriendsSearchRoute(
    userRepository: UserRepository,
    friendRepository: FriendRepository,
    authRepository: AuthRepository,
    currentUserId: String,
    onBack: () -> Unit,
    onSessionEnded: () -> Unit,
) {
  val model: FriendsSearchViewModel =
      viewModel(
          key = "friends_search_$currentUserId",
          factory =
              FriendsSearchViewModel.factory(
                  userRepository,
                  friendRepository,
                  currentUserId,
                  authRepository,
              ),
      )

  val state by model.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.sessionEnded) { if (state.sessionEnded) onSessionEnded() }

  FriendsSearchScreen(
      state = state,
      onQueryChange = model::updateQuery,
      onSendRequest = model::sendRequest,
      onRetrySearch = model::retrySearch,
      onRetryRelationships = model::retryRelationships,
      onDismissSendFailure = model::dismissSendFailure,
      onBack = onBack,
  )
}
