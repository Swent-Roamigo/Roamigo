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
  /** The trip the activity belongs to. */
  val tripId: String

  /** Adds an activity to [tripId], on [defaultDate] when the form was opened from a day. */
  data class Create(override val tripId: String, val defaultDate: LocalDate?) : ActivityFormMode

  /** Changes [activity]. Saving and deleting it need repository support that does not exist yet. */
  data class Edit(override val tripId: String, val activity: Activity) : ActivityFormMode
}

/**
 * The trip shown in the form header, whose dates decide when the form warns about a date.
 *
 * @property name the trip name shown in the header.
 * @property startDate the first day of the trip.
 * @property endDate the last day of the trip, included.
 */
data class ActivityFormTrip(val name: String, val startDate: LocalDate, val endDate: LocalDate)

/**
 * The values the user can change in the form.
 *
 * @property name the activity name, as typed.
 * @property date the day of the activity.
 * @property time when the activity starts on [date], or `null` when no time is chosen.
 * @property place where the activity takes place, or `null` before one is picked.
 * @property description optional notes, as typed.
 */
data class ActivityFormFields(
    val name: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val place: TripLocation? = null,
    val description: String = "",
)

/** The form field next to which a problem is shown. */
enum class ActivityFormField {
  /** The activity name. */
  NAME,

  /** The start time. */
  TIME,

  /** The place. */
  PLACE,

  /** The description. */
  DESCRIPTION,
}

/**
 * A problem with the entered values, shown next to [field].
 *
 * @property field the field the problem is shown under.
 * @property message the text shown to the user.
 */
enum class ActivityFormError(val field: ActivityFormField, @StringRes val message: Int) {
  /** The name is empty or only whitespace. */
  BLANK_NAME(ActivityFormField.NAME, R.string.activity_form_error_name_blank),

  /** The name is longer than an activity name may be. */
  NAME_TOO_LONG(ActivityFormField.NAME, R.string.activity_form_error_name_too_long),

  /** The description is longer than an activity description may be. */
  DESCRIPTION_TOO_LONG(
      ActivityFormField.DESCRIPTION,
      R.string.activity_form_error_description_too_long,
  ),

  /** No place was picked. */
  MISSING_PLACE(ActivityFormField.PLACE, R.string.activity_form_error_place_missing),

  /** The picked place has no usable name or coordinates. */
  INVALID_PLACE(ActivityFormField.PLACE, R.string.activity_form_error_place_invalid),

  /** The activity would end before it starts. */
  END_BEFORE_START(ActivityFormField.TIME, R.string.activity_form_error_end_before_start),
}

/**
 * Why saving failed although the entered values were valid.
 *
 * @property message the text shown to the user.
 */
enum class ActivityFormFailure(@StringRes val message: Int) {
  /** No user is signed in. */
  NOT_SIGNED_IN(R.string.activity_form_failure_not_signed_in),

  /** The user may not add activities to this trip. */
  PERMISSION_DENIED(R.string.activity_form_failure_permission),

  /** The server could not be reached; retrying may work. */
  NETWORK(R.string.activity_form_failure_network),

  /** Any other failure. */
  UNKNOWN(R.string.activity_form_failure_unknown),

  /**
   * An earlier attempt that seemed to fail had stored the activity, so later changes were not
   * applied. The form shows the stored values, and saving them closes it.
   */
  ALREADY_SAVED(R.string.activity_form_failure_already_saved),
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

/**
 * Everything the activity form screen shows.
 *
 * @property isEdit whether the form changes an existing activity instead of creating one.
 * @property trip the trip the activity belongs to.
 * @property fields the values entered so far.
 * @property isDirty whether [fields] differ from the values the form started with.
 * @property saveStatus where the form stands in saving.
 * @property errors the problems with [fields], shown once the user has tried to save.
 * @property failure why the last save failed, or `null`.
 * @property pendingResult the outcome of the last save, until the Route consumes it.
 */
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

  /** Whether a save is in progress. */
  val isSaving: Boolean
    get() = saveStatus == ActivityFormSaveStatus.SAVING

  /** Whether the activity was stored. */
  val isSaved: Boolean
    get() = saveStatus == ActivityFormSaveStatus.SAVED

  /** Whether the user can edit the fields and save. */
  val canSave: Boolean
    get() = saveStatus == ActivityFormSaveStatus.IDLE

  /** Returns the first problem shown under [field], or `null`. */
  fun errorFor(field: ActivityFormField): ActivityFormError? = errors.firstOrNull {
    it.field == field
  }
}
