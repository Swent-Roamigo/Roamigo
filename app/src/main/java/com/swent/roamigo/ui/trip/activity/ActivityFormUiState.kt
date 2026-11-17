// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.annotation.StringRes
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import java.time.LocalDate
import java.time.LocalTime

/** What the activity form does: add a new activity to a trip, or change an existing one. */
sealed interface ActivityFormMode {
  val tripId: String

  /** Adds an activity to [tripId], on [defaultDate] when the form was opened from a day. */
  data class Create(override val tripId: String, val defaultDate: LocalDate?) : ActivityFormMode

  /** Changes [activity]. Saving and deleting it need repository support that does not exist yet. */
  data class Edit(override val tripId: String, val activity: Activity) : ActivityFormMode
}

/** The trip shown in the form header, whose dates decide when the form warns about a date. */
data class ActivityFormTrip(val name: String, val startDate: LocalDate, val endDate: LocalDate)

/** The values the user can change in the form. */
data class ActivityFormFields(
    val name: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val place: TripLocation? = null,
    val description: String = "",
)

/** The form field next to which a problem is shown. */
enum class ActivityFormField {
  NAME,
  TIME,
  PLACE,
  DESCRIPTION,
}

/** A problem with the entered values, shown next to [field]. */
enum class ActivityFormError(val field: ActivityFormField, @StringRes val message: Int) {
  BLANK_NAME(ActivityFormField.NAME, R.string.activity_form_error_name_blank),
  NAME_TOO_LONG(ActivityFormField.NAME, R.string.activity_form_error_name_too_long),
  DESCRIPTION_TOO_LONG(
      ActivityFormField.DESCRIPTION,
      R.string.activity_form_error_description_too_long,
  ),
  MISSING_PLACE(ActivityFormField.PLACE, R.string.activity_form_error_place_missing),
  INVALID_PLACE(ActivityFormField.PLACE, R.string.activity_form_error_place_invalid),
  END_BEFORE_START(ActivityFormField.TIME, R.string.activity_form_error_end_before_start),
}

/** Why saving failed although the entered values were valid. */
enum class ActivityFormFailure(@StringRes val message: Int) {
  NOT_SIGNED_IN(R.string.activity_form_failure_not_signed_in),
  PERMISSION_DENIED(R.string.activity_form_failure_permission),
  NETWORK(R.string.activity_form_failure_network),
  UNKNOWN(R.string.activity_form_failure_unknown),
}

/** Where the form stands in saving, as one value so that saving and saved cannot both hold. */
enum class ActivityFormSaveStatus {
  /** Nothing is being saved; the user can edit and save. */
  IDLE,

  /** A save is in progress; edits and other saves are ignored. */
  SAVING,

  /** The activity was stored; the form no longer changes. */
  SAVED,
}

/** A one-time outcome of a save, handled once by the Route. */
sealed interface ActivityFormResult {
  /** The activity [activityId] was stored. */
  data class Saved(val activityId: String) : ActivityFormResult

  /** The values were valid, but changes to an existing activity cannot be stored yet. */
  data object EditUnavailable : ActivityFormResult
}

data class ActivityFormUiState(
    val isEdit: Boolean,
    val trip: ActivityFormTrip,
    val fields: ActivityFormFields,
    val isDirty: Boolean = false,
    val saveStatus: ActivityFormSaveStatus = ActivityFormSaveStatus.IDLE,
    val errors: List<ActivityFormError> = emptyList(),
    val failure: ActivityFormFailure? = null,
    val pendingResult: ActivityFormResult? = null,
) {
  /** Dates outside the trip are allowed, since plans can extend it, but the user is warned. */
  val isOutsideTrip: Boolean
    get() = fields.date !in trip.startDate..trip.endDate

  val isSaving: Boolean
    get() = saveStatus == ActivityFormSaveStatus.SAVING

  val isSaved: Boolean
    get() = saveStatus == ActivityFormSaveStatus.SAVED

  val canSave: Boolean
    get() = saveStatus == ActivityFormSaveStatus.IDLE

  fun errorFor(field: ActivityFormField): ActivityFormError? = errors.firstOrNull {
    it.field == field
  }
}
