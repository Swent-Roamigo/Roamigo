// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Paper
import com.swent.roamigo.ui.theme.Spacing

/** Creates the Members screen state for [tripId] and shows it. */
@Composable
fun TripMembersRoute(
    tripId: String,
    currentUserId: String,
    tripRepository: TripRepository,
    userRepository: UserRepository,
    onBack: () -> Unit,
    currentUserName: String? = null,
) {
  val viewModel: TripMembersViewModel =
      viewModel(
          key = "trip_members_$tripId",
          factory =
              TripMembersViewModel.factory(
                  tripId,
                  tripRepository,
                  userRepository,
                  currentUserId,
                  currentUserName,
              ),
      )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  TripMembersScreen(state, currentUserId, onBack, viewModel::retry)
}

/** Lists the people on a trip, with the organiser marked and the current user labelled. */
@Composable
fun TripMembersScreen(
    state: TripMembersUiState,
    currentUserId: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
  Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(C.Tag.trip_members_screen)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack, modifier = Modifier.testTag(C.Tag.trip_members_back)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.trip_details_back),
        )
      }
      Column {
        Text(
            text = stringResource(R.string.trip_members_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (state is TripMembersUiState.Loaded) {
          Text(
              text =
                  pluralStringResource(
                      R.plurals.trip_details_member_count,
                      state.members.size,
                      state.members.size,
                  ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      when (state) {
        TripMembersUiState.Loading -> CircularProgressIndicator()
        TripMembersUiState.Error -> {
          Text(
              stringResource(R.string.trip_members_load_error),
              color = MaterialTheme.colorScheme.error,
          )
          TextButton(onClick = onRetry) { Text(stringResource(R.string.trip_map_retry)) }
        }
        is TripMembersUiState.Loaded ->
            state.members.forEach { MemberRow(it, isYou = it.userId == currentUserId) }
      }
    }
  }
}

@Composable
private fun MemberRow(member: TripMemberUiModel, isYou: Boolean) {
  val name = member.name.ifBlank { stringResource(R.string.trip_members_unknown) }
  Row(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.trip_members_row_prefix + member.userId),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          // A code point, not a UTF-16 char: an emoji at the start of a name must not be split.
          text = name.substring(0, name.offsetByCodePoints(0, 1)).uppercase(),
          style = MaterialTheme.typography.titleMedium,
          color = Paper,
      )
    }
    Spacer(Modifier.width(Spacing.medium))
    Column {
      Text(
          text = if (isYou) stringResource(R.string.trip_members_you, name) else name,
          style = MaterialTheme.typography.titleMedium,
      )
      Text(
          text =
              stringResource(
                  if (member.isOrganiser) R.string.trip_members_organiser
                  else R.string.trip_members_member
              ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
