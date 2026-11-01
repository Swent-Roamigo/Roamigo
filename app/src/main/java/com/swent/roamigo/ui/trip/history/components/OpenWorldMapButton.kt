package com.swent.roamigo.ui.trip.history.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.theme.Spacing

/** Opens the future explored-world-map feature. */
@Composable
internal fun OpenWorldMapButton(onClick: () -> Unit) {
  OutlinedButton(
      onClick = onClick,
      modifier =
          Modifier.fillMaxWidth()
              .padding(
                  horizontal = Spacing.screenPadding,
                  vertical = Spacing.large,
              )
              .testTag(C.Tag.trip_history_world_map)
              .markNotImplemented(onClick),
  ) {
    Icon(Icons.Outlined.Map, contentDescription = null)
    Spacer(Modifier.width(Spacing.small))
    Text(stringResource(R.string.trip_history_world_map))
  }
}
