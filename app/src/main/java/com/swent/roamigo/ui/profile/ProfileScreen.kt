// Implemented with assistance from Claude (Anthropic).
// Sign-out implemented with AI assistance.
package com.swent.roamigo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.InfoRow
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.Ochre
import com.swent.roamigo.ui.theme.Paper
import com.swent.roamigo.ui.theme.Spacing

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onTripsClick: () -> Unit = rememberNotImplementedAction(),
    modifier: Modifier = Modifier,
    onSignOutClick: () -> Unit = {},
) {
  val notImplemented = rememberNotImplementedAction()
  Scaffold(
      modifier = modifier,
      bottomBar = {
        // The profile is not one of the three tabs.
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = notImplemented,
            onPhotosClick = notImplemented,
            tripsSelected = false,
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
          }
          Text(
              state.user.displayName,
              modifier = Modifier.testTag(C.Tag.profile_name),
              style = MaterialTheme.typography.titleLarge,
          )
          Text(
              stringResource(R.string.profile_email, state.email),
              modifier = Modifier.testTag(C.Tag.profile_email),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
          )
        }
        InfoRow(
            R.drawable.ic_user,
            stringResource(R.string.profile_display_name),
            state.user.displayName,
        ) {
          EditAction(stringResource(R.string.profile_edit))
        }
        InfoRow(
            R.drawable.ic_camera,
            stringResource(R.string.profile_picture),
            stringResource(R.string.profile_picture_description),
        ) {
          EditAction(stringResource(R.string.profile_change))
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
          EditAction(stringResource(R.string.profile_view))
        }
      }
      state.errorMessageRes?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
      }
      OutlinedButton(
          onClick = onSignOutClick,
          enabled = !state.signedOut,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.profile_log_out),
      ) {
        Text(stringResource(R.string.profile_log_out))
      }
    }
  }
}

@Composable
private fun EditAction(label: String) {
  val notImplemented = rememberNotImplementedAction()
  TextButton(
      onClick = notImplemented,
      modifier = Modifier.markNotImplemented(notImplemented).testTag(C.Tag.profile_action),
  ) {
    Text(label)
  }
}
