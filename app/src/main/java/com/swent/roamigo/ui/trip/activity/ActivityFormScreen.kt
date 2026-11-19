// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.Warning
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Minimum height of the primary action, large enough to tap with a thumb. */
private val PRIMARY_ACTION_MIN_HEIGHT = 50.dp

/** Minimum height of a picker field, matching an outlined text field. */
private val FIELD_MIN_HEIGHT = 56.dp

/** Size of the progress indicator inside the primary action. */
private val PROGRESS_SIZE = 24.dp

/** Stroke width of the progress indicator inside the primary action. */
private val PROGRESS_STROKE_WIDTH = 2.dp

/** Border width of a picker field, matching an outlined text field. */
private val FIELD_BORDER_WIDTH = 1.dp

/** Lines the header subtitle may take before a long trip name is cut short. */
private const val SUBTITLE_MAX_LINES = 2

/** Lines the description field shows before it scrolls. */
private const val DESCRIPTION_MIN_LINES = 3

/** Date skeleton for a day, such as "Tue, Oct 13", ordered for the user's locale. */
private const val DAY_SKELETON = "EEEdMMM"

/** Time skeletons matching the clock the time picker shows, such as "19:30" or "7:30 PM". */
private const val TIME_24_HOUR_SKELETON = "Hm"
private const val TIME_12_HOUR_SKELETON = "hm"

/** Renders the activity form from [state] and forwards every change and action. */
@Composable
fun ActivityFormScreen(
    state: ActivityFormUiState,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onPlaceClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val fields = state.fields
  val enabled = state.canSave
  val locale = LocalConfiguration.current.locales[0]
  val dayFormat = remember(locale) { formatter(locale, DAY_SKELETON) }
  // Follow the system 12/24-hour setting, like the time picker, rather than the locale alone.
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  val timeFormat =
      remember(locale, is24Hour) {
        formatter(locale, if (is24Hour) TIME_24_HOUR_SKELETON else TIME_12_HOUR_SKELETON)
      }
  var pickingDate by rememberSaveable { mutableStateOf(false) }
  var pickingTime by rememberSaveable { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val failureRequester = remember { BringIntoViewRequester() }
  // The failure sits below the fields, so show it when a save fails while the user is further up.
  LaunchedEffect(state.failure) { if (state.failure != null) failureRequester.bringIntoView() }
  Column(
      modifier
          .fillMaxSize()
          .background(colors.background)
          .safeDrawingPadding()
          .imePadding()
          .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium)
          .testTag(ActivityFormTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    // Only Save stays pinned: with the keyboard open, little height is left for the fields.
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Header(state, fields.date.format(dayFormat), onClose)
      FormTextField(
          label = stringResource(R.string.activity_form_name),
          placeholder = stringResource(R.string.activity_form_name_placeholder),
          value = fields.name,
          onValueChange = onNameChange,
          error = state.errorFor(ActivityFormField.NAME),
          enabled = enabled,
          tag = ActivityFormTestTags.NAME,
      )
      PickerField(
          label = stringResource(R.string.activity_form_date),
          value = fields.date.format(dayFormat),
          icon = Icons.Outlined.CalendarToday,
          onClick = { pickingDate = true },
          enabled = enabled,
          tag = ActivityFormTestTags.DATE,
      )
      if (state.isOutsideTrip) {
        OutsideTripWarning(
            stringResource(
                R.string.activity_form_outside_trip,
                state.trip.startDate.format(dayFormat),
                state.trip.endDate.format(dayFormat),
            )
        )
      }
      PickerField(
          label = stringResource(R.string.activity_form_time),
          value =
              fields.time?.format(timeFormat)
                  ?: stringResource(R.string.activity_form_time_placeholder),
          isPlaceholder = fields.time == null,
          icon = Icons.Outlined.Schedule,
          onClick = { pickingTime = true },
          enabled = enabled,
          tag = ActivityFormTestTags.TIME,
          error = state.errorFor(ActivityFormField.TIME),
          trailing =
              if (fields.time == null) null
              else {
                {
                  IconButton(
                      onClick = { onTimeChange(null) },
                      enabled = enabled,
                      modifier = Modifier.testTag(ActivityFormTestTags.TIME_CLEAR),
                  ) {
                    Icon(Icons.Default.Close, stringResource(R.string.activity_form_time_clear))
                  }
                }
              },
      )
      PickerField(
          label = stringResource(R.string.activity_form_place),
          value = fields.place?.name ?: stringResource(R.string.activity_form_place_placeholder),
          isPlaceholder = fields.place == null,
          icon = Icons.Outlined.LocationOn,
          onClick = onPlaceClick,
          enabled = enabled,
          tag = ActivityFormTestTags.PLACE,
          error = state.errorFor(ActivityFormField.PLACE),
      )
      FormTextField(
          label = stringResource(R.string.activity_form_description),
          placeholder = stringResource(R.string.activity_form_description_placeholder),
          value = fields.description,
          onValueChange = onDescriptionChange,
          error = state.errorFor(ActivityFormField.DESCRIPTION),
          enabled = enabled,
          tag = ActivityFormTestTags.DESCRIPTION,
          singleLine = false,
      )
      state.failure?.let {
        ErrorText(
            stringResource(it.message),
            Modifier.bringIntoViewRequester(failureRequester).testTag(ActivityFormTestTags.FAILURE),
        )
      }
      if (state.isEdit) {
        // Kept apart from the primary action, as in UX-26, so a delete is never a mistaken tap.
        TextButton(
            onClick = onDelete,
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(ActivityFormTestTags.DELETE)
                    .markNotImplemented(onDelete),
        ) {
          Text(stringResource(R.string.activity_form_delete))
        }
      }
    }
    Button(
        onClick = onSave,
        enabled = enabled,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = PRIMARY_ACTION_MIN_HEIGHT)
                .testTag(ActivityFormTestTags.SUBMIT),
    ) {
      if (state.isSaving) {
        CircularProgressIndicator(
            Modifier.size(PROGRESS_SIZE),
            strokeWidth = PROGRESS_STROKE_WIDTH,
        )
        Spacer(Modifier.width(Spacing.small))
      }
      Text(
          stringResource(
              when {
                state.isEdit -> R.string.activity_form_save_changes
                state.isSaving -> R.string.activity_form_adding
                else -> R.string.activity_form_add
              }
          )
      )
    }
  }
  if (pickingDate) {
    ActivityDateDialog(fields.date, onDateChange, onDismiss = { pickingDate = false })
  }
  if (pickingTime) {
    ActivityTimeDialog(fields.time, onTimeChange, onDismiss = { pickingTime = false })
  }
}

@Composable
private fun Header(state: ActivityFormUiState, day: String, onClose: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(
        onClick = onClose,
        enabled = !state.isSaving,
        modifier = Modifier.testTag(ActivityFormTestTags.CLOSE),
    ) {
      Icon(Icons.Default.Close, stringResource(R.string.activity_form_close))
    }
    Column(Modifier.padding(start = Spacing.small)) {
      Text(
          stringResource(
              if (state.isEdit) R.string.activity_form_title_edit
              else R.string.activity_form_title_new
          ),
          style = MaterialTheme.typography.headlineSmall,
      )
      Text(
          stringResource(R.string.activity_form_subtitle, state.trip.name, day),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = SUBTITLE_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun FormTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: ActivityFormError?,
    enabled: Boolean,
    tag: String,
    singleLine: Boolean = true,
) {
  // A content description would replace the typed text for TalkBack, so the label is read on its
  // own just before the field.
  LabeledField(label, error, announceLabel = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        isError = error != null,
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else DESCRIPTION_MIN_LINES,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
  }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
    isPlaceholder: Boolean = false,
    error: ActivityFormError? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  LabeledField(label, error) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border =
            BorderStroke(FIELD_BORDER_WIDTH, if (error != null) colors.error else colors.outline),
        modifier =
            Modifier.fillMaxWidth().heightIn(min = FIELD_MIN_HEIGHT).testTag(tag).semantics {
              contentDescription = label
              // Read after the label, as the card's description replaces the text it shows.
              stateDescription = value
            },
    ) {
      Row(
          Modifier.heightIn(min = FIELD_MIN_HEIGHT)
              .padding(start = Spacing.large, end = Spacing.small),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
        Text(
            value,
            color = if (isPlaceholder) colors.onSurfaceVariant else colors.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.medium),
        )
        trailing?.invoke()
      }
    }
  }
}

@Composable
private fun LabeledField(
    label: String,
    error: ActivityFormError?,
    announceLabel: Boolean = false,
    field: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        // Unless announced here, the field itself carries the label for accessibility services.
        modifier = if (announceLabel) Modifier else Modifier.clearAndSetSemantics {},
    )
    field()
    error?.let { ErrorText(stringResource(it.message)) }
  }
}

@Composable
private fun OutsideTripWarning(text: String) {
  Row(
      Modifier.fillMaxWidth().testTag(ActivityFormTestTags.OUTSIDE_TRIP).semantics(
          mergeDescendants = true
      ) {
        liveRegion = LiveRegionMode.Polite
      },
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Icon(Icons.Outlined.Info, contentDescription = null, tint = Warning)
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) {
  Text(
      text,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium,
      modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}

private fun formatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
