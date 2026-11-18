// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.history.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.ScreenHeader
import com.swent.roamigo.ui.components.markNotImplemented

/** Displays the back action, screen title, and completed-trip count when available. */
@Composable
internal fun TripHistoryHeader(
    onBackClick: () -> Unit,
    completedTripCount: Int?,
) {
  ScreenHeader(
      title = stringResource(R.string.trip_history_title),
      subtitle =
          completedTripCount?.let { pluralStringResource(R.plurals.trip_history_count, it, it) },
      onBack = onBackClick,
      backLabel = stringResource(R.string.trip_history_back),
      backModifier = Modifier.testTag(C.Tag.trip_history_back).markNotImplemented(onBackClick),
  )
}
