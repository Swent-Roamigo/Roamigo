// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.Success
import com.swent.roamigo.ui.theme.Warning

/** Whether what the user did on this phone has reached the group. */
enum class SyncState {
  Synced,
  Syncing,
  Offline,
}

/**
 * The one sync indicator of a screen's header. Rows and cards show their own state only when
 * something fails, so a missing connection is said here once instead of next to every item. A
 * [compact] indicator is an icon and a word without the pill, so it fits on a line of text.
 */
@Composable
fun SyncStatus(state: SyncState, modifier: Modifier = Modifier, compact: Boolean = false) {
  val label =
      stringResource(
          when (state) {
            SyncState.Synced -> R.string.sync_synced
            SyncState.Syncing -> R.string.sync_syncing
            SyncState.Offline -> R.string.sync_offline
          }
      )
  if (compact) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
      SyncIcon(state)
      Text(
          label,
          modifier = Modifier.padding(start = Spacing.compact),
          style = MaterialTheme.typography.labelMedium,
          color =
              if (state == SyncState.Offline) Warning
              else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }
  RoamigoChip(
      text = label,
      modifier = modifier,
      tone = if (state == SyncState.Offline) ChipTone.Warning else ChipTone.Neutral,
      textStyle = MaterialTheme.typography.labelMedium,
      leading = { SyncIcon(state) },
  )
}

@Composable
private fun SyncIcon(state: SyncState) {
  Icon(
      imageVector =
          when (state) {
            SyncState.Synced -> Icons.Outlined.Check
            SyncState.Syncing -> Icons.Outlined.Sync
            SyncState.Offline -> Icons.Outlined.CloudOff
          },
      contentDescription = null,
      modifier = Modifier.size(16.dp),
      tint = if (state == SyncState.Offline) Warning else Success,
  )
}
