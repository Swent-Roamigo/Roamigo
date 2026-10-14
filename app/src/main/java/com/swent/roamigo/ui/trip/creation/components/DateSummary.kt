// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.trip.creation.CreateTripUiState
import java.time.format.DateTimeFormatter

@Composable
internal fun DateSummary(state: CreateTripUiState, onFlexibleDatesChange: (Boolean) -> Unit) {
  val start = state.startDate
  val end = state.endDate
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      if (!state.isSelectingEndDate) {
        val dateFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0])
        Text(
            modifier = Modifier.semantics { testTag = C.Tag.c2_date_range },
            text =
                stringResource(
                    R.string.c2_date_range,
                    start.format(dateFormatter),
                    end.format(dateFormatter),
                ),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            modifier = Modifier.semantics { testTag = C.Tag.c2_duration_summary },
            text =
                stringResource(
                    R.string.c2_duration_summary,
                    pluralStringResource(
                        R.plurals.c2_trip_days,
                        state.tripDays.toInt(),
                        state.tripDays.toInt(),
                    ),
                    pluralStringResource(
                        R.plurals.c2_trip_nights,
                        state.tripNights.toInt(),
                        state.tripNights.toInt(),
                    ),
                ),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Text(
            text = stringResource(R.string.c2_select_end_date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = stringResource(R.string.c2_flexible_dates),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlexibleDatesToggle(state.flexibleDates, onFlexibleDatesChange)
    }
  }
}

@Composable
private fun FlexibleDatesToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  val description = stringResource(R.string.c2_flexible_dates)
  Box(
      modifier =
          Modifier.size(48.dp)
              .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
              .semantics {
                contentDescription = description
                testTag = C.Tag.c2_flexible_toggle
              },
      contentAlignment = Alignment.Center,
  ) {
    Box(
        modifier =
            Modifier.size(width = 46.dp, height = 26.dp)
                .clip(CircleShape)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
    ) {
      Box(
          modifier =
              Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                  .padding(3.dp)
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface)
      )
    }
  }
}
