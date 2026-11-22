// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.FriendAvatarBlue
import com.swent.roamigo.ui.theme.FriendAvatarGreen
import com.swent.roamigo.ui.theme.FriendAvatarRose
import com.swent.roamigo.ui.theme.Paper
import com.swent.roamigo.ui.theme.Spacing

/** Colors used for friend avatars to provide visual variety. */
private val friendAvatarColors = listOf(FriendAvatarBlue, FriendAvatarRose, FriendAvatarGreen)

/**
 * Renders the Friends list from presentation-ready state and forwards user actions to the caller.
 *
 * Friendship data, navigation, and persistence are intentionally handled outside this screen.
 *
 * @param uiState the state displayed by the screen.
 * @param modifier the modifier applied to the screen.
 * @param onAddFriendClick invoked when the user selects the add-friend action.
 * @param onViewMapClick invoked with a friend's user ID when the user selects their map action.
 * @param onTripsClick invoked when the Trips destination is selected.
 * @param onMapClick invoked when the Map destination is selected.
 * @param onRetry invoked when a relationship or profile load should be retried.
 * @param onAcceptRequest invoked with the incoming request ID to accept.
 * @param onDeclineRequest invoked with the incoming request ID to decline.
 * @param onRemoveFriend invoked with the friendship ID to remove.
 * @param onDismissActionFailure reports the displayed failure when dismissed or the screen leaves.
 */
@Composable
fun FriendsScreen(
    uiState: FriendsUiState,
    modifier: Modifier = Modifier,
    onAddFriendClick: () -> Unit = rememberNotImplementedAction(),
    onViewMapClick: ((String) -> Unit)? = null,
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onRetry: () -> Unit = rememberNotImplementedAction(),
    onAcceptRequest: ((String) -> Unit)? = null,
    onDeclineRequest: ((String) -> Unit)? = null,
    onRemoveFriend: ((String) -> Unit)? = null,
    onDismissActionFailure: (FriendsActionFailure) -> Unit = {},
    onCancelRequest: ((String) -> Unit)? = null,
) {
  var query by rememberSaveable { mutableStateOf("") }
  var pendingRemoval by remember { mutableStateOf<FriendUiModel?>(null) }

  val confirmableFriend = pendingRemoval?.let { selected ->
    uiState.friends.find {
      it.friendshipId == selected.friendshipId &&
          it.userId == selected.userId &&
          it.friendshipId != null &&
          !it.isProcessing
    }
  }
  LaunchedEffect(pendingRemoval, confirmableFriend) {
    if (pendingRemoval != null && confirmableFriend == null) {
      pendingRemoval = null
    }
  }
  val activity = LocalActivity.current
  val snackbarHostState = remember { SnackbarHostState() }
  val actionFailure = uiState.actionFailure
  val currentOnDismissActionFailure by rememberUpdatedState(onDismissActionFailure)
  if (actionFailure != null) {
    val actionMessage =
        stringResource(
            when (actionFailure.action) {
              FriendsAction.ACCEPT -> R.string.friends_accept_failed
              FriendsAction.DECLINE -> R.string.friends_decline_failed
              FriendsAction.CANCEL ->
                  when (actionFailure.reason) {
                    FriendsActionFailureReason.NETWORK -> R.string.friends_cancel_request_network
                    FriendsActionFailureReason.CONFLICT -> R.string.friends_cancel_request_conflict
                    FriendsActionFailureReason.PERMISSION ->
                        R.string.friends_cancel_request_permission
                    FriendsActionFailureReason.OTHER -> R.string.friends_cancel_request_failed
                  }
              FriendsAction.REMOVE -> R.string.friends_remove_failed
            }
        )
    LaunchedEffect(actionFailure) {
      try {
        snackbarHostState.showSnackbar(
            message = actionMessage,
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
      } finally {
        // Recreation retains the ViewModel, so the next screen must still show this failure.
        if (activity?.isChangingConfigurations != true) {
          currentOnDismissActionFailure(actionFailure)
        }
      }
    }
  }
  if (confirmableFriend != null) {
    AlertDialog(
        onDismissRequest = { pendingRemoval = null },
        modifier = Modifier.testTag(C.Tag.friends_remove_confirm_dialog),
        title = { Text(stringResource(R.string.friends_remove_confirm_title)) },
        text = {
          val unknown = stringResource(R.string.friends_unknown_traveler)

          Text(
              if (confirmableFriend.displayName == unknown) {
                stringResource(
                    R.string.friends_remove_unknown_message,
                    confirmableFriend.userId,
                )
              } else {
                stringResource(
                    R.string.friends_remove_confirm_message,
                    confirmableFriend.displayName,
                )
              }
          )
        },
        confirmButton = {
          Button(
              onClick = {
                val target = confirmableFriend.friendshipId
                pendingRemoval = null

                if (target != null) {
                  onRemoveFriend?.invoke(target)
                }
              },
              modifier = Modifier.testTag(C.Tag.friends_remove_confirm_action),
          ) {
            Text(stringResource(R.string.friends_remove))
          }
        },
        dismissButton = {
          TextButton(
              onClick = { pendingRemoval = null },
              modifier = Modifier.testTag(C.Tag.friends_remove_dismiss_action),
          ) {
            Text(stringResource(R.string.friends_remove_confirm_cancel))
          }
        },
    )
  }

  Scaffold(
      modifier = modifier.fillMaxSize().testTag(C.Tag.friends_screen),
      snackbarHost = {
        SnackbarHost(snackbarHostState, Modifier.testTag(C.Tag.friends_action_error))
      },
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            tripsSelected = false,
            onFriendsClick = {},
            friendsSelected = true,
        )
      },
      containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.screenPadding)
    ) {
      BoxWithConstraints(
          modifier = Modifier.fillMaxWidth().weight(1f),
      ) {
        // Center the empty state when enough vertical space is available.
        // On shorter screens, keep it inside the scrollable list.
        val centerEmptyState =
            !uiState.hasRelationships &&
                !uiState.isLoading &&
                uiState.loadError == null &&
                query.isBlank() &&
                maxHeight >= 300.dp

        FriendsList(
            uiState = uiState,
            query = query,
            onQueryChange = { query = it },
            onRetry = onRetry,
            onViewMapClick = onViewMapClick,
            onAcceptRequest = onAcceptRequest,
            onDeclineRequest = onDeclineRequest,
            onCancelRequest = onCancelRequest,
            onRemoveFriend =
                onRemoveFriend?.let {
                  { id ->
                    uiState.friends
                        .find { it.friendshipId == id }
                        ?.let { friend -> pendingRemoval = friend }
                    Unit
                  }
                },
            modifier = Modifier.fillMaxSize(),
            showEmptyInline = !centerEmptyState,
        )

        if (centerEmptyState) {
          EmptyFriendsState(
              modifier = Modifier.align(Alignment.Center),
          )
        }
      }

      OutlinedButton(
          onClick = onAddFriendClick,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(bottom = Spacing.large)
                  .testTag(C.Tag.friends_add_friend)
                  .markNotImplemented(onAddFriendClick),
      ) {
        Text(stringResource(R.string.friends_add_friend))
      }
    }
  }
}

private fun FriendsUiState.matching(query: String): FriendsUiState {
  val needle = query.trim()
  if (needle.isEmpty()) return this
  return copy(
      friends = friends.filter { it.displayName.contains(needle, ignoreCase = true) },
      incomingRequests =
          incomingRequests.filter { it.displayName.contains(needle, ignoreCase = true) },
      outgoingRequests =
          outgoingRequests.filter { it.displayName.contains(needle, ignoreCase = true) },
  )
}

@Composable
private fun FriendsHeader(friendCount: Int) {
  Column(
      modifier = Modifier.padding(top = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
        text = stringResource(R.string.friends_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )

    if (friendCount > 0) {
      Text(
          text = pluralStringResource(R.plurals.friends_count, friendCount, friendCount),
          style = MaterialTheme.typography.titleSmall,
      )
    }

    Text(
        text = stringResource(R.string.friends_privacy_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun FriendsList(
    uiState: FriendsUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onViewMapClick: ((String) -> Unit)?,
    onAcceptRequest: ((String) -> Unit)?,
    onDeclineRequest: ((String) -> Unit)?,
    onCancelRequest: ((String) -> Unit)?,
    onRemoveFriend: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    showEmptyInline: Boolean = true,
) {
  val filtered = uiState.matching(query)
  LazyColumn(
      modifier = modifier.testTag(C.Tag.friends_list),
      contentPadding =
          PaddingValues(
              top = Spacing.extraLarge,
              bottom = Spacing.large,
          ),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    item(key = "header") { FriendsHeader(friendCount = uiState.friends.size) }
    statusItems(uiState, onRetry, showEmptyInline)
    if (uiState.hasRelationships || query.isNotEmpty()) {
      searchItem(query, onQueryChange)
    }
    incomingSection(filtered.incomingRequests, onAcceptRequest, onDeclineRequest)
    friendsSection(filtered.friends, onViewMapClick, onRemoveFriend)
    outgoingSection(filtered.outgoingRequests, onCancelRequest)
    if (uiState.hasRelationships && query.isNotBlank() && !filtered.hasRelationships) {
      item(key = "no_matches") { Text(stringResource(R.string.friends_no_matches)) }
    }
  }
}

private fun LazyListScope.statusItems(
    uiState: FriendsUiState,
    onRetry: () -> Unit,
    showEmptyInline: Boolean,
) {
  if (uiState.isLoading) {
    item(key = "loading") {
      val loadingDescription = stringResource(R.string.friends_loading)
      LinearProgressIndicator(
          modifier =
              Modifier.fillMaxWidth().testTag(C.Tag.friends_loading).semantics {
                contentDescription = loadingDescription
              },
      )
    }
  }
  uiState.loadError?.let { error ->
    item(key = "error") {
      Text(
          stringResource(
              when (error) {
                FriendsLoadError.RELATIONSHIPS -> R.string.friends_load_failed
                FriendsLoadError.PROFILES -> R.string.friends_profiles_failed
                FriendsLoadError.SESSION -> R.string.friends_session_ended
              }
          ),
          modifier = Modifier.testTag(C.Tag.friends_load_error),
      )
      if (error != FriendsLoadError.SESSION) {
        TextButton(onClick = onRetry, modifier = Modifier.testTag(C.Tag.friends_retry)) {
          Text(stringResource(R.string.friends_retry))
        }
      }
    }
  }
  if (
      showEmptyInline &&
          !uiState.hasRelationships &&
          !uiState.isLoading &&
          uiState.loadError == null
  ) {
    item(key = "empty") { EmptyFriendsState() }
  }
}

private fun LazyListScope.searchItem(query: String, onQueryChange: (String) -> Unit) {
  item(key = "search") {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_search),
        placeholder = { Text(stringResource(R.string.friends_search)) },
        singleLine = true,
    )
  }
}

private fun LazyListScope.incomingSection(
    requests: List<IncomingFriendRequestUiModel>,
    onAcceptRequest: ((String) -> Unit)?,
    onDeclineRequest: ((String) -> Unit)?,
) {
  if (requests.isNotEmpty()) {
    item(key = "incoming_title") { RelationshipSectionTitle(R.string.friends_incoming_title) }
  }
  items(items = requests, key = { "incoming:${it.requestId}" }) { request ->
    IncomingRequestRow(
        request,
        onAccept =
            onAcceptRequest?.let { { it(request.requestId) } } ?: rememberNotImplementedAction(),
        onDecline =
            onDeclineRequest?.let { { it(request.requestId) } } ?: rememberNotImplementedAction(),
    )
  }
}

private fun LazyListScope.friendsSection(
    friends: List<FriendUiModel>,
    onViewMapClick: ((String) -> Unit)?,
    onRemoveFriend: ((String) -> Unit)?,
) {
  if (friends.isNotEmpty()) {
    item(key = "friends_title") { RelationshipSectionTitle(R.string.friends_established_title) }
  }
  items(items = friends, key = { friend -> "friend:${friend.userId}" }) { friend ->
    FriendRow(
        friend = friend,
        onViewMapClick =
            onViewMapClick?.let { callback -> { callback(friend.userId) } }
                ?: rememberNotImplementedAction(),
        onRemove =
            onRemoveFriend?.let { callback -> { friend.friendshipId?.let(callback) } }
                ?: rememberNotImplementedAction(),
    )
  }
}

private fun LazyListScope.outgoingSection(
    requests: List<OutgoingFriendRequestUiModel>,
    onCancelRequest: ((String) -> Unit)?,
) {
  if (requests.isNotEmpty()) {
    item(key = "outgoing_title") { RelationshipSectionTitle(R.string.friends_outgoing_title) }
  }

  items(
      items = requests,
      key = { request -> "request:${request.userId}" },
  ) { request ->
    OutgoingRequestRow(
        request = request,
        onCancel =
            onCancelRequest?.let { callback ->
              {
                request.requestId?.let(callback)
                Unit
              }
            },
    )
  }
}

@Composable
private fun RelationshipSectionTitle(@StringRes title: Int) {
  Text(
      stringResource(title),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.semantics { heading() },
  )
}

@Composable
private fun IncomingRequestRow(
    request: IncomingFriendRequestUiModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .testTag(C.Tag.friends_incoming_request_prefix + request.requestId),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(Modifier.padding(Spacing.cardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        InitialAvatar(request.displayName, request.userId, request.profilePictureUrl)
        Spacer(Modifier.width(Spacing.medium))
        Text(
            request.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
      }
      if (request.isProcessing) {
        Text(
            stringResource(R.string.friends_updating),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
      val acceptDescription =
          stringResource(R.string.friends_accept_accessibility, request.displayName)
      val declineDescription =
          stringResource(R.string.friends_decline_accessibility, request.displayName)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Button(
            onClick = onAccept,
            enabled = !request.isProcessing,
            modifier =
                Modifier.weight(1f)
                    .testTag(C.Tag.friends_accept_prefix + request.requestId)
                    .markNotImplemented(onAccept)
                    .semantics { contentDescription = acceptDescription },
        ) {
          Text(stringResource(R.string.friends_accept))
        }
        OutlinedButton(
            onClick = onDecline,
            enabled = !request.isProcessing,
            modifier =
                Modifier.weight(1f)
                    .testTag(C.Tag.friends_decline_prefix + request.requestId)
                    .markNotImplemented(onDecline)
                    .semantics { contentDescription = declineDescription },
        ) {
          Text(stringResource(R.string.friends_decline))
        }
      }
    }
  }
}

@Composable
private fun FriendRow(
    friend: FriendUiModel,
    onViewMapClick: () -> Unit,
    onRemove: () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_friend_row),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(Modifier.padding(Spacing.cardPadding)) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
      ) {
        InitialAvatar(
            displayName = friend.displayName,
            userId = friend.userId,
            profilePictureUrl = friend.profilePictureUrl,
        )

        Spacer(modifier = Modifier.width(Spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = friend.displayName,
              style = MaterialTheme.typography.titleMedium,
          )

          friendMetadata(friend.countriesVisited, friend.tripsTogether)?.let { metadata ->
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        val viewMapDescription =
            stringResource(
                R.string.friends_view_map_accessibility,
                friend.displayName,
            )

        Surface(
            onClick = onViewMapClick,
            modifier =
                Modifier.testTag(C.Tag.friends_view_map_prefix + friend.userId)
                    .markNotImplemented(onViewMapClick)
                    .semantics { contentDescription = viewMapDescription },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
              text = stringResource(R.string.friends_view_map),
              modifier =
                  Modifier.padding(
                      horizontal = Spacing.medium,
                      vertical = Spacing.small,
                  ),
              style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      if (friend.friendshipId != null) {
        val removeDescription =
            stringResource(R.string.friends_remove_accessibility, friend.displayName)
        TextButton(
            onClick = onRemove,
            enabled = !friend.isProcessing,
            modifier =
                Modifier.align(Alignment.End)
                    .testTag(C.Tag.friends_remove_prefix + friend.friendshipId)
                    .markNotImplemented(onRemove)
                    .semantics { contentDescription = removeDescription },
        ) {
          Text(
              stringResource(
                  if (friend.isProcessing) R.string.friends_updating else R.string.friends_remove
              )
          )
        }
      }
    }
  }
}

@Composable
private fun OutgoingRequestRow(
    request: OutgoingFriendRequestUiModel,
    onCancel: (() -> Unit)?,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_pending_request_row),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
      ) {
        InitialAvatar(
            displayName = request.displayName,
            userId = request.userId,
            profilePictureUrl = request.profilePictureUrl,
        )

        Spacer(modifier = Modifier.width(Spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = request.displayName,
              style = MaterialTheme.typography.titleMedium,
          )

          Text(
              text = outgoingRequestMetadata(request.countriesVisited),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
              text = stringResource(R.string.friends_pending),
              modifier =
                  Modifier.padding(
                      horizontal = Spacing.medium,
                      vertical = Spacing.small,
                  ),
              style = MaterialTheme.typography.bodySmall,
          )
        }
      }

      if (request.requestId != null && onCancel != null) {
        val cancelDescription =
            stringResource(
                R.string.friends_cancel_request_accessibility,
                request.displayName,
            )

        TextButton(
            onClick = onCancel,
            enabled = !request.isProcessing,
            modifier =
                Modifier.align(Alignment.End)
                    .testTag(C.Tag.friends_cancel_request_prefix + request.requestId)
                    .semantics { contentDescription = cancelDescription },
        ) {
          Text(
              stringResource(
                  if (request.isProcessing) R.string.friends_updating
                  else R.string.friends_cancel_request
              )
          )
        }
      }
    }
  }
}

@Composable
internal fun InitialAvatar(
    displayName: String,
    userId: String,
    profilePictureUrl: String? = null,
) {
  val initial = displayName.trim().firstOrNull()?.uppercase() ?: "?"

  val avatarColor = friendAvatarColors[(userId.hashCode() ushr 2) % friendAvatarColors.size]

  Box(
      modifier =
          Modifier.size(48.dp)
              .background(
                  color = avatarColor,
                  shape = CircleShape,
              )
              .testTag(C.Tag.friends_avatar_prefix + userId),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = initial,
        style = MaterialTheme.typography.titleMedium,
        color = Paper,
    )
    if (profilePictureUrl != null) {
      AsyncImage(
          model = profilePictureUrl,
          contentDescription = null,
          modifier =
              Modifier.matchParentSize()
                  .clip(CircleShape)
                  .testTag(C.Tag.friends_photo_prefix + userId),
          contentScale = ContentScale.Crop,
      )
    }
  }
}

@Composable
private fun EmptyFriendsState(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(C.Tag.friends_empty_state),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = stringResource(R.string.friends_empty_title),
        style = MaterialTheme.typography.titleLarge,
    )

    Text(
        text = stringResource(R.string.friends_empty_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun friendMetadata(
    countriesVisited: Int?,
    tripsTogether: Int?,
): String? {
  if (countriesVisited == null && tripsTogether == null) return null
  val countries = countriesVisited?.let {
    pluralStringResource(
        R.plurals.friends_country_count,
        countriesVisited,
        countriesVisited,
    )
  }

  val trips = tripsTogether?.let {
    pluralStringResource(
        R.plurals.friends_trip_together_count,
        tripsTogether,
        tripsTogether,
    )
  }

  if (countries == null) return trips
  if (trips == null) return countries

  return stringResource(
      R.string.friends_metadata,
      countries,
      trips,
  )
}

@Composable
private fun outgoingRequestMetadata(countriesVisited: Int?): String {
  if (countriesVisited == null) return stringResource(R.string.friends_request_sent)
  val countries =
      pluralStringResource(
          R.plurals.friends_country_count,
          countriesVisited,
          countriesVisited,
      )

  return stringResource(
      R.string.friends_request_metadata,
      countries,
      stringResource(R.string.friends_request_sent),
  )
}
