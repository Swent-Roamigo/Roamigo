package com.swent.roamigo.ui.trips.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.theme.Spacing

/** Button that opens the user's completed trip history. */
@Composable
internal fun HistoryButton(onClick: () -> Unit) {
  TextButton(
      onClick = onClick,
      modifier =
          Modifier.markNotImplemented(onClick).semantics { testTag = C.Tag.trips_history_button },
  ) {
    Icon(
        imageVector = Icons.Outlined.History,
        contentDescription = null,
    )
    Spacer(modifier = Modifier.width(Spacing.compact))
    Text(stringResource(R.string.trips_history))
  }
}
