// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.activity.ActivityCreationException
import com.swent.roamigo.model.trip.activity.ActivityDraft
import com.swent.roamigo.model.trip.activity.ActivityDraftError
import com.swent.roamigo.model.trip.activity.ActivityRepository
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
 */
class ActivityFormViewModel(
    private val repository: ActivityRepository,
    private val mode: ActivityFormMode,
    trip: ActivityFormTrip,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private val editedActivity = (mode as? ActivityFormMode.Edit)?.activity

  // One ID per form: a retry after a failure, even one whose write reached the server, finds the
  // activity already stored instead of creating a second one.
  private val activityId = editedActivity?.uid ?: UUID.randomUUID().toString()

  private val initialFields =
      when (mode) {
        is ActivityFormMode.Create -> ActivityFormFields(date = mode.defaultDate ?: trip.startDate)
        is ActivityFormMode.Edit -> mode.activity.toFields(zone)
      }

  private val _uiState =
      MutableStateFlow(
          ActivityFormUiState(isEdit = editedActivity != null, trip = trip, fields = initialFields)
      )
  val uiState: StateFlow<ActivityFormUiState> = _uiState.asStateFlow()

  // Errors appear once the user tries to save, then follow every change.
  private var saveAttempted = false

  fun updateName(name: String) = edit { it.copy(name = name) }

  fun updateDate(date: LocalDate) = edit { it.copy(date = date) }

  fun updateTime(time: LocalTime?) = edit { it.copy(time = time) }

  fun updatePlace(place: TripLocation) = edit { it.copy(place = place) }

  fun updateDescription(description: String) = edit { it.copy(description = description) }

  /** Claims the outcome of the last save once, so it is not handled again on recomposition. */
  fun consumeResult(): ActivityFormResult? =
      _uiState.getAndUpdate { it.copy(pendingResult = null) }.pendingResult

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
    _uiState.update { it.copy(isSaving = true, errors = emptyList(), failure = null) }
    viewModelScope.launch {
      try {
        val created = repository.createActivity(mode.tripId, activityId, draft)
        _uiState.update {
          it.copy(
              isSaving = false,
              isSaved = true,
              pendingResult = ActivityFormResult.Saved(created.uid),
          )
        }
      } catch (cancelled: CancellationException) {
        _uiState.update { it.copy(isSaving = false) }
        throw cancelled
      } catch (invalid: ActivityCreationException.Validation) {
        _uiState.update {
          it.copy(isSaving = false, errors = invalid.errors.toFormErrors(placeChosen = true))
        }
      } catch (failure: Exception) {
        _uiState.update { it.copy(isSaving = false, failure = failure.toFormFailure()) }
      }
    }
  }

  private fun edit(transform: (ActivityFormFields) -> ActivityFormFields) {
    val state = _uiState.value
    if (!state.canSave) return
    val fields = transform(state.fields)
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
    ) = viewModelFactory { initializer { ActivityFormViewModel(repository, mode, trip, zone) } }
  }
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

private fun Activity.toFields(zone: ZoneId): ActivityFormFields {
  val start = startTime.toDomainInstant().atZone(zone)
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
