// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/** Renders user discovery and friend requests using presentation state and callbacks. */
@Composable
fun FriendsSearchScreen(
    state: FriendsSearchState,
    onQueryChange: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onRetryRelationships: () -> Unit,
    onDismissSendFailure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .safeDrawingPadding()
              .imePadding()
              .padding(horizontal = Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
          onClick = onBack,
          modifier = Modifier.testTag(C.Tag.friends_search_back),
      ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.friends_search_back),
        )
      }
      Text(
          text = stringResource(R.string.friends_search_title),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.semantics { heading() },
      )
    }

    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_directory_query),
        label = { Text(stringResource(R.string.friends_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        enabled = !state.sessionEnded,
    )

    if (state.relationshipsFailed && !state.sessionEnded) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            stringResource(R.string.friends_search_relationships_error),
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = onRetryRelationships,
            modifier = Modifier.testTag(C.Tag.friends_directory_relationship_retry),
        ) {
          Text(stringResource(R.string.friends_retry))
        }
      }
    } else if (state.relationshipsLoading && !state.sessionEnded && state.query.isNotBlank()) {
      Text(
          stringResource(R.string.friends_search_relationships_loading),
          style = MaterialTheme.typography.bodySmall,
      )
    }

    state.sendFailure
        ?.takeIf { !state.sessionEnded }
        ?.let { failure ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                stringResource(
                    when (failure.reason) {
                      SendFailure.CONFLICT -> R.string.friends_search_send_conflict
                      SendFailure.NETWORK -> R.string.friends_search_send_network
                      SendFailure.PERMISSION -> R.string.friends_search_send_permission
                      SendFailure.OTHER -> R.string.friends_search_send_unknown
                    }
                ),
                modifier =
                    Modifier.weight(1f).testTag(C.Tag.friends_directory_send_error).semantics {
                      liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                onClick = onDismissSendFailure,
                modifier = Modifier.testTag(C.Tag.friends_directory_dismiss_error),
            ) {
              Text(stringResource(R.string.friends_search_dismiss))
            }
          }
        }

    LazyColumn(
        modifier = Modifier.weight(1f).testTag(C.Tag.friends_directory_results),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      if (state.isSearching) {
        item(key = "loading") {
          LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.friends_directory_loading)
          )
        }
      }

      when {
        state.sessionEnded ->
            item(key = "session") { SearchMessage(R.string.friends_session_ended) }

        state.searchFailure == SearchFailure.INVALID_QUERY ->
            item(key = "invalid") { SearchMessage(R.string.friends_search_invalid) }

        state.searchFailure == SearchFailure.DIRECTORY ->
            item(key = "directory_error") {
              Column {
                SearchMessage(R.string.friends_search_directory_error)
                TextButton(
                    onClick = onRetrySearch,
                    modifier = Modifier.testTag(C.Tag.friends_directory_retry),
                ) {
                  Text(stringResource(R.string.friends_retry))
                }
              }
            }

        state.query.isBlank() ->
            item(key = "hint") { SearchMessage(R.string.friends_search_empty_query) }

        !state.isSearching && state.results.isEmpty() ->
            item(key = "empty") { SearchMessage(R.string.friends_search_no_results) }
      }

      if (!state.sessionEnded) {
        items(
            items = state.results,
            key = { it.user.uid },
        ) { match ->
          val sending = match.user.uid in state.sendingIds

          val available =
              match.relationship == SearchRelationship.AVAILABLE &&
                  !state.relationshipsLoading &&
                  !state.relationshipsFailed &&
                  !sending

          val displayName =
              match.user.displayName.takeIf { it.isNotBlank() }
                  ?: stringResource(R.string.friends_unknown_traveler)

          val sendDescription =
              stringResource(
                  R.string.friends_search_add_accessibility,
                  displayName,
              )

          Card(
              modifier =
                  Modifier.fillMaxWidth()
                      .testTag(C.Tag.friends_directory_user_prefix + match.user.uid),
          ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
              InitialAvatar(
                  displayName,
                  match.user.uid,
                  match.user.profilePictureUrl,
              )

              Text(
                  displayName,
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.titleMedium,
              )

              when (match.relationship) {
                SearchRelationship.AVAILABLE ->
                    Button(
                        onClick = { onSendRequest(match.user.uid) },
                        enabled = available,
                        modifier =
                            Modifier.testTag(C.Tag.friends_directory_send_prefix + match.user.uid)
                                .semantics { contentDescription = sendDescription },
                    ) {
                      if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                      } else {
                        Text(stringResource(R.string.friends_search_add))
                      }
                    }

                SearchRelationship.OUTGOING_PENDING ->
                    Text(stringResource(R.string.friends_search_pending))

                SearchRelationship.INCOMING_PENDING ->
                    Text(stringResource(R.string.friends_search_incoming))

                SearchRelationship.FRIEND ->
                    Text(stringResource(R.string.friends_search_already_friends))

                SearchRelationship.UNKNOWN -> {
                  val statusResource =
                      if (state.relationshipsLoading && !state.relationshipsFailed) {
                        R.string.friends_search_status_checking
                      } else {
                        R.string.friends_search_status_unknown
                      }

                  Text(stringResource(statusResource))
                }
              }
            }
          }
        }
      }
    }
  }
}

/** Displays search guidance or feedback, announced to accessibility services. */
@Composable
private fun SearchMessage(resourceId: Int) {
  Text(
      stringResource(resourceId),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}
