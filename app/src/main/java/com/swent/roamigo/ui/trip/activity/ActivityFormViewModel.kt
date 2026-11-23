// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.activity.ActivityCreationException
import com.swent.roamigo.model.trip.activity.ActivityDraft
import com.swent.roamigo.model.trip.activity.ActivityDraftError
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.atZone
import com.swent.roamigo.model.trip.toDomainInstant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds one activity form in [mode]. Navigation is left to the caller through [consumeResult].
 *
 * Without a time, the activity starts at the beginning of its day in [zone]: trips have no time
 * zone of their own, and stored start times cannot be empty yet.
 *
 * The draft and its activity ID are kept in [savedState], so the form survives process death while
 * the user is in the place picker, and a retry after it still reuses the same ID.
 */
class ActivityFormViewModel(
    private val repository: ActivityRepository,
    private val mode: ActivityFormMode,
    trip: ActivityFormTrip,
    private val savedState: SavedStateHandle,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private val editedActivity = (mode as? ActivityFormMode.Edit)?.activity

  // One ID per form: a retry after a failure, even one whose write reached the server, finds the
  // activity already stored instead of creating a second one.
  private val activityId: String =
      editedActivity?.uid
          ?: savedState[KEY_ACTIVITY_ID]
          ?: UUID.randomUUID().toString().also { savedState[KEY_ACTIVITY_ID] = it }

  private val initialFields =
      when (mode) {
        is ActivityFormMode.Create -> ActivityFormFields(date = mode.defaultDate ?: trip.startDate)
        is ActivityFormMode.Edit -> mode.activity.toFields(zone)
      }

  // Errors appear once the user tries to save, then follow every change.
  private var saveAttempted: Boolean = savedState[KEY_SAVE_ATTEMPTED] ?: false
    set(value) {
      field = value
      savedState[KEY_SAVE_ATTEMPTED] = value
    }

  private val _uiState =
      MutableStateFlow(
          (savedState.restoreFields() ?: initialFields).let { fields ->
            ActivityFormUiState(
                isEdit = editedActivity != null,
                trip = trip,
                fields = fields,
                isDirty = fields != initialFields,
                errors = if (saveAttempted) fields.errors() else emptyList(),
            )
          }
      )
  /** The state the form screen renders. */
  val uiState: StateFlow<ActivityFormUiState> = _uiState.asStateFlow()

  /** Changes the activity name. */
  fun updateName(name: String) = edit { it.copy(name = name) }

  /** Changes the day of the activity. */
  fun updateDate(date: LocalDate) = edit { it.copy(date = date) }

  /** Changes the start time, or removes it with `null`. */
  fun updateTime(time: LocalTime?) = edit { it.copy(time = time) }

  /** Sets the place picked in the place picker. */
  fun updatePlace(place: TripLocation) = edit { it.copy(place = place) }

  /** Changes the optional description. */
  fun updateDescription(description: String) = edit { it.copy(description = description) }

  /** Claims the outcome of the last save once, so it is not handled again on recomposition. */
  fun consumeResult(): ActivityFormResult? =
      _uiState.getAndUpdate { it.copy(pendingResult = null) }.pendingResult

  /**
   * Validates the fields and saves them. Invalid values only show their errors. In Edit mode a
   * valid save reports [ActivityFormResult.EditUnavailable] and stores nothing. When a retry finds
   * the activity already stored with other values, the form shows them with
   * [ActivityFormFailure.ALREADY_SAVED] instead of reporting a save.
   */
  fun save() {
    val state = _uiState.value
    if (!state.canSave) return
    saveAttempted = true
    val errors = state.fields.errors()
    val draft = state.fields.toDraft(zone, editedActivity)
    if (errors.isNotEmpty() || draft == null) {
      _uiState.update { it.copy(errors = errors, failure = null) }
      return
    }
    if (mode is ActivityFormMode.Edit) {
      // Storing changes needs ActivityRepository.updateActivity (#239); never report a fake save.
      _uiState.update {
        it.copy(errors = emptyList(), pendingResult = ActivityFormResult.EditUnavailable)
      }
      return
    }
    // Set before launching so that a second tap cannot start a concurrent write.
    _uiState.update {
      it.copy(saveStatus = ActivityFormSaveStatus.SAVING, errors = emptyList(), failure = null)
    }
    viewModelScope.launch {
      try {
        val stored = repository.createActivity(mode.tripId, activityId, draft)
        if (stored.matches(draft)) {
          _uiState.update {
            it.copy(
                saveStatus = ActivityFormSaveStatus.SAVED,
                pendingResult = ActivityFormResult.Saved(stored.uid),
            )
          }
        } else {
          showAlreadySaved(stored)
        }
      } catch (cancelled: CancellationException) {
        _uiState.update { it.copy(saveStatus = ActivityFormSaveStatus.IDLE) }
        throw cancelled
      } catch (invalid: ActivityCreationException.Validation) {
        _uiState.update {
          it.copy(
              saveStatus = ActivityFormSaveStatus.IDLE,
              errors = invalid.errors.toFormErrors(placeChosen = true),
          )
        }
      } catch (callerBug: IllegalArgumentException) {
        // An invalid trip or activity ID is a programming error, not something the user can retry.
        throw callerBug
      } catch (failure: Exception) {
        _uiState.update {
          it.copy(saveStatus = ActivityFormSaveStatus.IDLE, failure = failure.toFormFailure())
        }
      }
    }
  }

  // A retry after a write that reached the server returns the first version, which createActivity
  // cannot change. Showing it keeps the form from reporting changes that were never stored.
  private fun showAlreadySaved(stored: Activity) {
    val fields = stored.toFields(zone)
    savedState.storeFields(fields)
    _uiState.update {
      it.copy(
          fields = fields,
          isDirty = fields != initialFields,
          saveStatus = ActivityFormSaveStatus.IDLE,
          failure = ActivityFormFailure.ALREADY_SAVED,
      )
    }
  }

  private fun edit(transform: (ActivityFormFields) -> ActivityFormFields) {
    val state = _uiState.value
    if (!state.canSave) return
    val fields = transform(state.fields)
    savedState.storeFields(fields)
    _uiState.value =
        state.copy(
            fields = fields,
            isDirty = fields != initialFields,
            errors = if (saveAttempted) fields.errors() else emptyList(),
            failure = null,
        )
  }

  private fun ActivityFormFields.errors(): List<ActivityFormError> =
      draftAt(place ?: NO_PLACE, zone, editedActivity)
          .validate()
          .toFormErrors(placeChosen = place != null)

  companion object {
    /** Builds the form's ViewModel; the place picker gets its own repository from the Route. */
    fun factory(
        repository: ActivityRepository,
        mode: ActivityFormMode,
        trip: ActivityFormTrip,
        zone: ZoneId = ZoneId.systemDefault(),
    ) = viewModelFactory {
      initializer { ActivityFormViewModel(repository, mode, trip, createSavedStateHandle(), zone) }
    }
  }
}

private const val KEY_ACTIVITY_ID = "activity_form_id"
private const val KEY_SAVE_ATTEMPTED = "activity_form_save_attempted"
private const val KEY_NAME = "activity_form_name"
private const val KEY_DATE = "activity_form_date"
private const val KEY_TIME = "activity_form_time"
private const val KEY_PLACE_NAME = "activity_form_place_name"
private const val KEY_PLACE_LATITUDE = "activity_form_place_latitude"
private const val KEY_PLACE_LONGITUDE = "activity_form_place_longitude"
private const val KEY_PLACE_COUNTRY = "activity_form_place_country"
private const val KEY_DESCRIPTION = "activity_form_description"

// Dates and times are stored as ISO strings, which a saved-state Bundle can always hold.
private fun SavedStateHandle.storeFields(fields: ActivityFormFields) {
  this[KEY_NAME] = fields.name
  this[KEY_DATE] = fields.date.toString()
  this[KEY_TIME] = fields.time?.toString()
  this[KEY_PLACE_NAME] = fields.place?.name
  this[KEY_PLACE_LATITUDE] = fields.place?.latitude
  this[KEY_PLACE_LONGITUDE] = fields.place?.longitude
  this[KEY_PLACE_COUNTRY] = fields.place?.countryCode
  this[KEY_DESCRIPTION] = fields.description
}

/** Returns the fields saved by [storeFields], or `null` when nothing was changed yet. */
private fun SavedStateHandle.restoreFields(): ActivityFormFields? {
  val date = get<String>(KEY_DATE)?.let(LocalDate::parse) ?: return null
  val placeName = get<String>(KEY_PLACE_NAME)
  val latitude = get<Double>(KEY_PLACE_LATITUDE)
  val longitude = get<Double>(KEY_PLACE_LONGITUDE)
  return ActivityFormFields(
      name = get<String>(KEY_NAME).orEmpty(),
      date = date,
      time = get<String>(KEY_TIME)?.let(LocalTime::parse),
      place =
          if (placeName == null || latitude == null || longitude == null) null
          else TripLocation(latitude, longitude, placeName, get(KEY_PLACE_COUNTRY)),
      description = get<String>(KEY_DESCRIPTION).orEmpty(),
  )
}

// A draft always has a location, so a missing place is validated as an empty one and reported as
// MISSING_PLACE. This keeps every rule in ActivityDraft.validate.
private val NO_PLACE = TripLocation(Double.NaN, Double.NaN, "")

/**
 * Converts the entered values into a draft, or returns `null` without a place.
 *
 * When [edited] has an end, the activity keeps its length, so moving the start moves the end too.
 */
internal fun ActivityFormFields.toDraft(zone: ZoneId, edited: Activity?): ActivityDraft? =
    place?.let {
      draftAt(it, zone, edited)
    }

private fun ActivityFormFields.draftAt(
    location: TripLocation,
    zone: ZoneId,
    edited: Activity?,
): ActivityDraft {
  val start = (time?.let { date.atTime(it).atZone(zone) } ?: date.atStartOfDay(zone)).toInstant()
  val length =
      edited?.endTime?.let {
        Duration.between(edited.startTime.toDomainInstant(), it.toDomainInstant())
      }
  return ActivityDraft(
      name = name.trim(),
      description = description.trim().ifEmpty { null },
      location = location,
      startTime = start,
      endTime = length?.let { start + it },
  )
}

private fun Activity.matches(draft: ActivityDraft): Boolean =
    name == draft.name &&
        description == draft.description &&
        location == draft.location &&
        startTime.toDomainInstant() == draft.startTime &&
        endTime?.toDomainInstant() == draft.endTime

private fun Activity.toFields(zone: ZoneId): ActivityFormFields {
  val start = startTime.atZone(zone)
  return ActivityFormFields(
      name = name,
      date = start.toLocalDate(),
      time = start.toLocalTime(),
      place = location,
      description = description.orEmpty(),
  )
}

private fun List<ActivityDraftError>.toFormErrors(placeChosen: Boolean): List<ActivityFormError> =
    map {
      when (it) {
        ActivityDraftError.BlankName -> ActivityFormError.BLANK_NAME
        ActivityDraftError.NameTooLong -> ActivityFormError.NAME_TOO_LONG
        ActivityDraftError.DescriptionTooLong -> ActivityFormError.DESCRIPTION_TOO_LONG
        ActivityDraftError.BlankLocationName,
        ActivityDraftError.InvalidLocationCoordinates ->
            if (placeChosen) ActivityFormError.INVALID_PLACE else ActivityFormError.MISSING_PLACE
        ActivityDraftError.EndBeforeStart -> ActivityFormError.END_BEFORE_START
      }
    }
    .distinct()

private fun Exception.toFormFailure(): ActivityFormFailure =
    when (this) {
      is ActivityCreationException.NotAuthenticated -> ActivityFormFailure.NOT_SIGNED_IN
      is ActivityCreationException.PermissionDenied -> ActivityFormFailure.PERMISSION_DENIED
      is ActivityCreationException.Network -> ActivityFormFailure.NETWORK
      else -> ActivityFormFailure.UNKNOWN
    }
