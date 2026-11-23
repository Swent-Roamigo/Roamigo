// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.notification.NotificationRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.InfoRow
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.Ochre
import com.swent.roamigo.ui.theme.Paper
import com.swent.roamigo.ui.theme.Spacing

/**
 * Connects [authRepository] to [ProfileScreen] and forwards successful sign-out.
 *
 * @param onEditNameClick Edits the display name; its button is hidden while it is null.
 * @param onChangePictureClick Changes the profile picture; its button is hidden while it is null.
 */
@Composable
fun ProfileRoute(
    authRepository: AuthRepository,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = {},
    onEditNameClick: (() -> Unit)? = null,
    onChangePictureClick: (() -> Unit)? = null,
    notificationRepository: NotificationRepository? = null,
    onNotificationsClick: (() -> Unit)? = null,
) {
  val viewModel = viewModel { ProfileViewModel(authRepository, notificationRepository) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
  LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
  ProfileScreen(
      state = state,
      modifier = modifier,
      onTripsClick = onTripsClick,
      onFriendsClick = onFriendsClick,
      onSignOutClick = viewModel::signOut,
      onMapClick = onMapClick,
      onEditNameClick = onEditNameClick,
      onChangePictureClick = onChangePictureClick,
      unreadCount = unreadCount,
      onNotificationsClick = onNotificationsClick,
  )
}

/**
 * Shows the signed-in account. A null [onEditNameClick] or [onChangePictureClick] hides its button,
 * so the screen never offers an action that does nothing.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    onSignOutClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onEditNameClick: (() -> Unit)? = null,
    onChangePictureClick: (() -> Unit)? = null,
    unreadCount: Int = 0,
    onNotificationsClick: (() -> Unit)? = null,
) {
  var confirmingLogOut by rememberSaveable { mutableStateOf(false) }
  Scaffold(
      modifier = modifier,
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            tripsSelected = false,
            onFriendsClick = onFriendsClick,
        )
      },
  ) { padding ->
    // Scaffold reports the footer height so the content is not drawn underneath it.
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      // Only the content scrolls, so the button stays reachable with large fonts or small screens.
      Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Text(
            stringResource(R.string.profile_title),
            modifier = Modifier.testTag(C.Tag.profile_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.profile_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
              modifier = Modifier.size(88.dp).background(Ochre, CircleShape),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                state.user.displayName.take(1),
                modifier = Modifier.testTag(C.Tag.profile_avatar),
                style = MaterialTheme.typography.displaySmall,
                color = Paper,
            )
            // Drawn over the initial, which stays visible while loading or if the photo fails.
            state.user.profilePictureUrl?.let { url ->
              AsyncImage(
                  model = url,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize().clip(CircleShape).testTag(C.Tag.profile_photo),
              )
            }
          }
          Text(
              state.user.displayName,
              modifier = Modifier.testTag(C.Tag.profile_name),
              style = MaterialTheme.typography.titleLarge,
          )
          state.email?.let { email ->
            Text(
                stringResource(R.string.profile_email, email),
                modifier = Modifier.testTag(C.Tag.profile_email),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
          }
        }
        InfoRow(
            R.drawable.ic_user,
            stringResource(R.string.profile_display_name),
            state.user.displayName,
            action = onEditNameClick?.let { rowAction(R.string.profile_edit, it) },
        )
        InfoRow(
            R.drawable.ic_camera,
            stringResource(R.string.profile_picture),
            stringResource(R.string.profile_picture_description),
            action = onChangePictureClick?.let { rowAction(R.string.profile_change, it) },
        )
        if (onNotificationsClick != null && !state.signedOut) {
          ProfileNotificationsRow(unreadCount, onNotificationsClick)
        }
        InfoRow(
            R.drawable.ic_users,
            stringResource(R.string.profile_friends),
            pluralStringResource(
                R.plurals.profile_friend_count,
                state.friendCount,
                state.friendCount,
            ),
            Modifier.testTag(C.Tag.profile_friends_row),
        ) {
          RowAction(
              label = stringResource(R.string.profile_view),
              onClick = onFriendsClick,
          )
        }
      }
      state.errorMessageRes?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
      }
      OutlinedButton(
          onClick = { confirmingLogOut = true },
          enabled = !state.signedOut,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.profile_log_out),
      ) {
        Text(stringResource(R.string.profile_log_out))
      }
    }
  }

  if (confirmingLogOut) {
    AlertDialog(
        onDismissRequest = { confirmingLogOut = false },
        title = { Text(stringResource(R.string.profile_log_out_title)) },
        text = { Text(stringResource(R.string.profile_log_out_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                confirmingLogOut = false
                onSignOutClick()
              },
              modifier = Modifier.testTag(C.Tag.profile_log_out_confirm),
          ) {
            Text(
                stringResource(R.string.profile_log_out),
                color = MaterialTheme.colorScheme.error,
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmingLogOut = false }) {
            Text(stringResource(R.string.profile_log_out_cancel))
          }
        },
    )
  }
}

/** Wraps [RowAction] as the trailing content of an [InfoRow]. */
private fun rowAction(@StringRes label: Int, onClick: () -> Unit): @Composable () -> Unit = {
  RowAction(stringResource(label), onClick)
}

@Composable
private fun ProfileNotificationsRow(unreadCount: Int, onClick: () -> Unit) {
  val unreadDescription =
      pluralStringResource(R.plurals.notifications_unread_count, unreadCount, unreadCount)
  val chevron =
      if (LocalLayoutDirection.current == LayoutDirection.Ltr) R.drawable.ic_c2_chevron_right
      else R.drawable.ic_c2_chevron_left
  Surface(
      onClick = onClick,
      modifier =
          Modifier.fillMaxWidth().testTag(C.Tag.profile_notifications_row).semantics {
            if (unreadCount > 0) stateDescription = unreadDescription
          },
      shape = MaterialTheme.shapes.medium,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
        modifier =
            Modifier.heightIn(min = 64.dp)
                .padding(horizontal = Spacing.cardPadding + 1.dp, vertical = Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(R.drawable.ic_bell),
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
            stringResource(R.string.notifications_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.notifications_profile_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (unreadCount > 0) {
        Badge(Modifier.clearAndSetSemantics {}) {
          Text(stringResource(R.string.notifications_badge_number, unreadCount))
        }
      }
      Icon(
          painterResource(chevron),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun RowAction(
    label: String,
    onClick: () -> Unit,
) {
  TextButton(
      onClick = onClick,
      modifier = Modifier.testTag(C.Tag.profile_action),
  ) {
    Text(label)
  }
}
