// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.R
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationEvent
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Renders the inbox and keeps opening an entry separate from marking it as read. */
@Composable
internal fun NotificationsScreen(
    uiState: NotificationsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenNotification: (Notification) -> Unit,
    onMarkAsRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val locale = LocalConfiguration.current.locales[0]
  val zone = ZoneId.systemDefault()
  val timestamps =
      remember(locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
      }
  val loadingLabel = stringResource(R.string.notifications_loading)

  Scaffold(
      modifier = modifier.fillMaxSize().testTag(C.Tag.notifications_screen),
      containerColor = MaterialTheme.colorScheme.background,
      topBar = { NotificationsHeader(onBack) },
  ) { padding ->
    if (uiState.loadState == NotificationsLoadState.SESSION_ENDED) return@Scaffold
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .testTag(C.Tag.notifications_list),
        contentPadding =
            PaddingValues(horizontal = Spacing.screenPadding, vertical = Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      when (uiState.loadState) {
        NotificationsLoadState.LOADING ->
            item(key = "loading") {
              Box(
                  Modifier.fillMaxWidth().padding(Spacing.medium),
                  contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = loadingLabel })
              }
            }
        NotificationsLoadState.FAILED ->
            item(key = "load-error") {
              Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    stringResource(R.string.notifications_load_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = onRetry) {
                  Text(stringResource(R.string.notifications_retry))
                }
              }
            }
        NotificationsLoadState.READY ->
            if (uiState.notifications.isEmpty()) {
              item(key = "empty") {
                Text(
                    stringResource(R.string.notifications_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
        NotificationsLoadState.SESSION_ENDED -> Unit
      }
      items(uiState.notifications, key = { "notification/${it.uid}" }) { entry ->
        NotificationRow(
            entry = entry,
            timestamp = timestamps.format(entry.createdAt),
            markingRead = entry.uid in uiState.markingReadIds,
            readFailed = entry.uid in uiState.failedReadIds,
            onOpen = { onOpenNotification(entry) },
            onMarkAsRead = { onMarkAsRead(entry.uid) },
        )
      }
    }
  }
}

@Composable
private fun NotificationsHeader(onBack: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .windowInsetsPadding(
                  WindowInsets.safeDrawing.only(
                      WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                  )
              )
              .padding(horizontal = Spacing.screenPadding)
              .padding(top = Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
      Icon(
          painterResource(chevronResource(back = true)),
          contentDescription = stringResource(R.string.notifications_back),
          modifier = Modifier.size(24.dp),
      )
    }
    Text(
        stringResource(R.string.notifications_title),
        modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontSize = 20.sp,
    )
  }
}

@Composable
private fun NotificationRow(
    entry: Notification,
    timestamp: String,
    markingRead: Boolean,
    readFailed: Boolean,
    onOpen: () -> Unit,
    onMarkAsRead: () -> Unit,
) {
  val title =
      stringResource(
          when (entry.event) {
            is NotificationEvent.FriendRequestReceived -> R.string.notifications_friend_request
            is NotificationEvent.TripVoteCreated -> R.string.notifications_trip_vote
          }
      )
  val readStatus =
      stringResource(
          if (entry.isRead) R.string.notifications_read else R.string.notifications_unread
      )

  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color =
          if (entry.isRead) MaterialTheme.colorScheme.surface
          else MaterialTheme.colorScheme.surfaceVariant,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
          modifier =
              Modifier.weight(1f)
                  .testTag(C.Tag.notifications_row_prefix + entry.uid)
                  .semantics { stateDescription = readStatus }
                  .clickable(
                      onClickLabel = stringResource(R.string.notifications_open),
                      role = Role.Button,
                      onClick = onOpen,
                  )
                  .padding(horizontal = Spacing.cardPadding, vertical = Spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Box(
            Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
          val tint = MaterialTheme.colorScheme.onPrimaryContainer
          when (entry.event) {
            is NotificationEvent.FriendRequestReceived ->
                Icon(painterResource(R.drawable.ic_users), null, tint = tint)
            is NotificationEvent.TripVoteCreated ->
                Icon(Icons.Outlined.HowToVote, null, tint = tint)
          }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
          Text(
              title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = if (entry.isRead) FontWeight.Medium else FontWeight.Bold,
          )
          Text(
              timestamp,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (readFailed) {
            Text(
                stringResource(R.string.notifications_mark_read_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }
        Icon(
            painterResource(chevronResource(back = false)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (!entry.isRead) {
        val label =
            stringResource(
                if (markingRead) R.string.notifications_marking_read
                else R.string.notifications_mark_read,
                title,
            )
        IconButton(
            onClick = onMarkAsRead,
            enabled = !markingRead,
            modifier =
                Modifier.testTag(C.Tag.notifications_mark_read_prefix + entry.uid).semantics {
                  contentDescription = label
                },
        ) {
          if (markingRead) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
          else Icon(painterResource(R.drawable.ic_trip_details_check), contentDescription = null)
        }
      }
    }
  }
}

@Composable
private fun chevronResource(back: Boolean): Int =
    if (back == (LocalLayoutDirection.current == LayoutDirection.Ltr)) R.drawable.ic_c2_chevron_left
    else R.drawable.ic_c2_chevron_right
