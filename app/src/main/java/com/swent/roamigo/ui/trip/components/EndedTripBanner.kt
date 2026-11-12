// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/** Tells that a finished trip can only be viewed, and what stays available. */
@Composable
fun EndedTripBanner(modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier.fillMaxWidth().testTag(C.Tag.trip_ended_banner),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Schedule, contentDescription = null)
      Text(stringResource(R.string.trip_ended_banner), style = MaterialTheme.typography.bodyMedium)
    }
  }
}
