// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.R
import com.swent.roamigo.ui.trip.creation.CreateTripUiState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object DateSummaryTestTags {
  const val DATE_RANGE = "c2_date_range"
  const val DURATION_SUMMARY = "c2_duration_summary"
  const val FLEXIBLE_TOGGLE = "c2_flexible_toggle"
}

@Composable
internal fun DateSummary(state: CreateTripUiState, onFlexibleDatesChange: (Boolean) -> Unit) {
  val dateFormatter = remember {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH)
  }
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      if (!state.isSelectingEndDate) {
        Text(
            modifier = Modifier.semantics { testTag = DateSummaryTestTags.DATE_RANGE },
            text =
                stringResource(
                    R.string.c2_date_range,
                    state.startDate.format(dateFormatter),
                    state.endDate.format(dateFormatter),
                ),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            modifier = Modifier.semantics { testTag = DateSummaryTestTags.DURATION_SUMMARY },
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
  Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier =
          Modifier.semantics {
            contentDescription = description
            testTag = DateSummaryTestTags.FLEXIBLE_TOGGLE
          },
      colors =
          SwitchDefaults.colors(
              checkedThumbColor = MaterialTheme.colorScheme.surface,
              checkedTrackColor = MaterialTheme.colorScheme.primary,
              uncheckedThumbColor = MaterialTheme.colorScheme.surface,
              uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
          ),
  )
}
