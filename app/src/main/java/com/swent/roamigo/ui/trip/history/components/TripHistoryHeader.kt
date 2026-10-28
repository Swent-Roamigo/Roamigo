package com.swent.roamigo.ui.trip.history.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.markNotImplemented

/** Displays the back action, screen title, and completed-trip count when available. */
@Composable
internal fun TripHistoryHeader(
    onBackClick: () -> Unit,
    completedTripCount: Int?,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(
        onClick = onBackClick,
        modifier = Modifier.testTag(C.Tag.trip_history_back).markNotImplemented(onBackClick),
    ) {
      Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.trip_history_back),
      )
    }

    Column {
      Text(
          text = stringResource(R.string.trip_history_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
      )
      if (completedTripCount != null) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.trip_history_count,
                    completedTripCount,
                    completedTripCount,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
