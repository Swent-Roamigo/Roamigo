// AI assistance was used for implementation support.
package com.swent.roamigo.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.friends.FriendRepository
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.ui.components.rememberNotImplementedAction

/** Connects the existing Friends list to one authenticated account's live relationships. */
@Composable
fun FriendsRoute(
    friendRepository: FriendRepository,
    userRepository: UserRepository,
    authRepository: AuthRepository,
    currentUserId: String,
    onSessionEnded: () -> Unit,
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onAddFriendClick: () -> Unit = rememberNotImplementedAction(),
    onViewMapClick: ((String) -> Unit)? = null,
) {
  val model: FriendsViewModel =
      viewModel(
          key = "friends_$currentUserId",
          factory =
              FriendsViewModel.factory(
                  friendRepository,
                  userRepository,
                  authRepository,
                  currentUserId,
              ),
      )
  val state by model.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.loadError) {
    if (state.loadError == FriendsLoadError.SESSION) onSessionEnded()
  }
  FriendsScreen(
      uiState = state.toUiState(currentUserId, stringResource(R.string.friends_unknown_traveler)),
      onRetry = model::retry,
      onTripsClick = onTripsClick,
      onMapClick = onMapClick,
      onAddFriendClick = onAddFriendClick,
      onViewMapClick = onViewMapClick,
      onAcceptRequest = model::acceptRequest,
      onDeclineRequest = model::declineRequest,
      onRemoveFriend = model::removeFriend,
      onDismissActionFailure = model::dismissActionFailure,
  )
}
