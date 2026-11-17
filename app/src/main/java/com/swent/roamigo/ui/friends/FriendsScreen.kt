// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
 * @param onDismissActionFailure acknowledges the latest action failure.
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
    onDismissActionFailure: () -> Unit = {},
) {
  var query by rememberSaveable { mutableStateOf("") }
  val snackbarHostState = remember { SnackbarHostState() }
  val actionMessage =
      uiState.actionFailure?.let { failure ->
        stringResource(
            when (failure.action) {
              FriendsAction.ACCEPT -> R.string.friends_accept_failed
              FriendsAction.DECLINE -> R.string.friends_decline_failed
              FriendsAction.REMOVE -> R.string.friends_remove_failed
            }
        )
      }
  LaunchedEffect(uiState.actionFailure) {
    if (actionMessage == null) {
      snackbarHostState.currentSnackbarData?.dismiss()
    } else {
      snackbarHostState.showSnackbar(
          message = actionMessage,
          withDismissAction = true,
          duration = SnackbarDuration.Indefinite,
      )
      onDismissActionFailure()
    }
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
      FriendsList(
          uiState = uiState,
          query = query,
          onQueryChange = { query = it },
          onRetry = onRetry,
          onViewMapClick = onViewMapClick,
          onAcceptRequest = onAcceptRequest,
          onDeclineRequest = onDeclineRequest,
          onRemoveFriend = onRemoveFriend,
          modifier = Modifier.weight(1f),
      )

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
    onRemoveFriend: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
  val loadingDescription = stringResource(R.string.friends_loading)
  val filtered = uiState.matching(query)
  val hasRelationships =
      uiState.friends.isNotEmpty() ||
          uiState.incomingRequests.isNotEmpty() ||
          uiState.outgoingRequests.isNotEmpty()
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
    if (uiState.isLoading) {
      item(key = "loading") {
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
    if (!hasRelationships && !uiState.isLoading && uiState.loadError == null) {
      item(key = "empty") { EmptyFriendsState() }
    }
    if (hasRelationships || query.isNotEmpty()) {
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
    if (filtered.incomingRequests.isNotEmpty()) {
      item(key = "incoming_title") { RelationshipSectionTitle(R.string.friends_incoming_title) }
    }
    items(items = filtered.incomingRequests, key = { "incoming:${it.requestId}" }) { request ->
      IncomingRequestRow(
          request,
          onAccept =
              onAcceptRequest?.let { { it(request.requestId) } } ?: rememberNotImplementedAction(),
          onDecline =
              onDeclineRequest?.let { { it(request.requestId) } } ?: rememberNotImplementedAction(),
      )
    }
    if (filtered.friends.isNotEmpty()) {
      item(key = "friends_title") { RelationshipSectionTitle(R.string.friends_established_title) }
    }
    items(
        items = filtered.friends,
        key = { friend -> "friend:${friend.userId}" },
    ) { friend ->
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

    if (filtered.outgoingRequests.isNotEmpty()) {
      item(key = "outgoing_title") { RelationshipSectionTitle(R.string.friends_outgoing_title) }
    }
    items(
        items = filtered.outgoingRequests,
        key = { request -> "request:${request.userId}" },
    ) { request ->
      OutgoingRequestRow(request)
    }
    if (
        hasRelationships &&
            query.isNotBlank() &&
            filtered.friends.isEmpty() &&
            filtered.incomingRequests.isEmpty() &&
            filtered.outgoingRequests.isEmpty()
    ) {
      item(key = "no_matches") { Text(stringResource(R.string.friends_no_matches)) }
    }
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
private fun OutgoingRequestRow(request: OutgoingFriendRequestUiModel) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_pending_request_row),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        modifier = Modifier.padding(Spacing.cardPadding),
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
  }
}

@Composable
private fun InitialAvatar(
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
