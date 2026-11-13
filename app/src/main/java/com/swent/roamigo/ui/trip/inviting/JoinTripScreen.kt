// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.inviting.InvitationPreview
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.theme.Paper
import com.swent.roamigo.ui.theme.Spacing

/** Checks the invitation [token] and joins the trip when the user accepts. */
@Composable
fun JoinTripRoute(
    token: String,
    service: TripInvitationService,
    onJoined: (tripId: String) -> Unit,
    onClose: () -> Unit,
) {
  val viewModel: JoinTripViewModel =
      viewModel(
          key = "join_$token",
          factory = JoinTripViewModel.factory(token, service),
      )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  (state as? JoinTripUiState.Joined)?.let { joined ->
    LaunchedEffect(joined.tripId) { onJoined(joined.tripId) }
  }
  JoinTripScreen(state, onJoin = viewModel::join, onRetry = viewModel::load, onClose = onClose)
}

/** Says which trip the user is invited to, or why the invitation cannot be used. */
@Composable
fun JoinTripScreen(
    state: JoinTripUiState,
    onJoin: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
  Box(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().testTag(C.Tag.join_screen)) {
    IconButton(
        onClick = onClose,
        modifier = Modifier.align(Alignment.TopStart).testTag(C.Tag.join_back),
    ) {
      Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.join_back),
      )
    }
    Column(
        Modifier.align(Alignment.Center)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      when (state) {
        JoinTripUiState.Loading -> {
          CircularProgressIndicator(Modifier.testTag(C.Tag.join_loading))
          Message(R.string.join_loading)
        }
        is JoinTripUiState.Ready -> {
          Invitation(state.preview)
          if (state.joinFailed) Message(R.string.join_failed)
          JoinButton(
              if (state.preview.alreadyMember) R.string.join_open_trip else R.string.join_button,
              loading = false,
              onClick = onJoin,
          )
        }
        is JoinTripUiState.Joining -> {
          Invitation(state.preview)
          JoinButton(R.string.join_joining, loading = true, onClick = {})
        }
        // The route leaves this screen as soon as the trip is joined.
        is JoinTripUiState.Joined -> CircularProgressIndicator(Modifier.testTag(C.Tag.join_loading))
        JoinTripUiState.Invalid -> Message(R.string.join_invalid, title = true)
        is JoinTripUiState.Full -> {
          Invitation(state.preview)
          Message(R.string.join_full)
        }
        JoinTripUiState.Failed -> {
          Message(R.string.join_check_failed)
          TripActionButton(onClick = onRetry, modifier = Modifier.testTag(C.Tag.join_retry)) {
            Text(stringResource(R.string.invite_retry))
          }
        }
      }
    }
  }
}

@Composable
private fun Invitation(preview: InvitationPreview) {
  Text(
      text =
          preview.inviterName?.let { stringResource(R.string.join_invited_by, it) }
              ?: stringResource(R.string.join_invited),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
  )
  Text(
      text = preview.tripName,
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
  )
  if (preview.alreadyMember) Message(R.string.join_already_member)
}

@Composable
private fun Message(messageRes: Int, title: Boolean = false) {
  Text(
      text = stringResource(messageRes),
      modifier = Modifier.testTag(C.Tag.join_message),
      style =
          if (title) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
      color =
          if (title) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
  )
}

@Composable
private fun JoinButton(labelRes: Int, loading: Boolean, onClick: () -> Unit) {
  TripActionButton(
      onClick = onClick,
      modifier = Modifier.testTag(C.Tag.join_button),
      loading = loading,
  ) {
    Text(stringResource(labelRes))
  }
}
