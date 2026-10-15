// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
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
 * @param onPhotosClick invoked when the Photos destination is selected.
 */
@Composable
fun FriendsScreen(
    uiState: FriendsUiState,
    modifier: Modifier = Modifier,
    onAddFriendClick: () -> Unit = rememberNotImplementedAction(),
    onViewMapClick: ((String) -> Unit)? = null,
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
    onPhotosClick: () -> Unit = rememberNotImplementedAction(),
) {
  val notImplemented = rememberNotImplementedAction()
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(C.Tag.friends_screen),
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onPhotosClick = onPhotosClick,
            tripsSelected = false,
        )
      },
      containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.screenPadding),
    ) {
      FriendsHeader()

      if (uiState.friends.isEmpty() && uiState.outgoingRequests.isEmpty()) {
        EmptyFriendsState(modifier = Modifier.weight(1f))
      } else {
        FriendsList(
            uiState = uiState,
            onViewMapClick = onViewMapClick ?: { notImplemented() },
            modifier = Modifier.weight(1f),
        )
      }

      OutlinedButton(
          onClick = onAddFriendClick,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(bottom = Spacing.large)
                  .testTag(C.Tag.friends_add_friend),
      ) {
        Text(stringResource(R.string.friends_add_friend))
      }
    }
  }
}

@Composable
private fun FriendsHeader() {
  Column(
      modifier = Modifier.padding(top = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
        text = stringResource(R.string.friends_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )

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
    onViewMapClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyColumn(
      modifier = modifier,
      contentPadding =
          PaddingValues(
              top = Spacing.extraLarge,
              bottom = Spacing.large,
          ),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    items(
        items = uiState.friends,
        key = { friend -> "friend:${friend.userId}" },
    ) { friend ->
      FriendRow(
          friend = friend,
          onViewMapClick = onViewMapClick,
      )
    }

    items(
        items = uiState.outgoingRequests,
        key = { request -> "request:${request.userId}" },
    ) { request ->
      OutgoingRequestRow(request)
    }
  }
}

@Composable
private fun FriendRow(
    friend: FriendUiModel,
    onViewMapClick: (String) -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_friend_row),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        modifier = Modifier.padding(Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      InitialAvatar(
          displayName = friend.displayName,
          userId = friend.userId,
      )

      Spacer(modifier = Modifier.width(Spacing.medium))

      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text =
                friendMetadata(
                    countriesVisited = friend.countriesVisited,
                    tripsTogether = friend.tripsTogether,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      val viewMapDescription =
          stringResource(
              R.string.friends_view_map_accessibility,
              friend.displayName,
          )

      Surface(
          onClick = { onViewMapClick(friend.userId) },
          modifier =
              Modifier.testTag(C.Tag.friends_view_map_prefix + friend.userId).semantics {
                contentDescription = viewMapDescription
              },
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
    countriesVisited: Int,
    tripsTogether: Int,
): String {
  val countries =
      pluralStringResource(
          R.plurals.friends_country_count,
          countriesVisited,
          countriesVisited,
      )

  val trips =
      pluralStringResource(
          R.plurals.friends_trip_together_count,
          tripsTogether,
          tripsTogether,
      )

  return stringResource(
      R.string.friends_metadata,
      countries,
      trips,
  )
}

@Composable
private fun outgoingRequestMetadata(countriesVisited: Int): String {
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
