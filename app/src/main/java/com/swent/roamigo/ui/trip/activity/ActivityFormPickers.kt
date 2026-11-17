// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A time picker opened on an activity without a time starts at this hour. */
private const val DEFAULT_PICKER_HOUR = 12

/** Lets the user choose the day of the activity, starting on [date]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityDateDialog(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // The Material date picker works with midnight UTC for each day.
  val pickerState =
      rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
  DatePickerDialog(
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            onClick = {
              pickerState.selectedDateMillis?.let {
                onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
              }
              onDismiss()
            }
        ) {
          Text(stringResource(R.string.activity_form_dialog_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.activity_form_dialog_cancel))
        }
      },
      modifier = modifier.testTag(ActivityFormTestTags.DATE_DIALOG),
  ) {
    DatePicker(pickerState)
  }
}

/** Lets the user choose the start time, starting on [time] or at noon when there is none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityTimeDialog(
    time: LocalTime?,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val pickerState =
      rememberTimePickerState(
          initialHour = time?.hour ?: DEFAULT_PICKER_HOUR,
          initialMinute = time?.minute ?: 0,
          is24Hour = DateFormat.is24HourFormat(LocalContext.current),
      )
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.activity_form_time_dialog_title)) },
      text = {
        // Lets the clock scroll when the dialog is taller than a landscape screen.
        Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(pickerState) }
      },
      confirmButton = {
        TextButton(
            onClick = {
              onPick(LocalTime.of(pickerState.hour, pickerState.minute))
              onDismiss()
            }
        ) {
          Text(stringResource(R.string.activity_form_dialog_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.activity_form_dialog_cancel))
        }
      },
      modifier = modifier.testTag(ActivityFormTestTags.TIME_DIALOG),
  )
}
