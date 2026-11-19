// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.place.PlaceRepository
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.ui.components.DestructiveConfirmationDialog
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.placepicker.PlacePickerRoute

/**
 * Connects the activity form in [mode] to its repositories. The place is chosen with the full
 * screen place picker, which leaves the rest of the form untouched.
 *
 * Saving and deleting in Edit mode give the unfinished-action feedback until the repository can
 * update and delete activities (#239).
 *
 * @param trip the trip shown in the header, whose dates decide the outside-trip warning.
 * @param onClose called when the user leaves, after confirming if they changed something.
 * @param onActivitySaved called once with the ID of the stored activity.
 * @param viewModelFactory replaces the default factory, for tests.
 */
@Composable
fun ActivityFormRoute(
    activityRepository: ActivityRepository,
    placeRepository: PlaceRepository,
    mode: ActivityFormMode,
    trip: ActivityFormTrip,
    onClose: () -> Unit,
    onActivitySaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModelFactory: ViewModelProvider.Factory? = null,
) {
  val factory =
      remember(activityRepository, mode, trip) {
        ActivityFormViewModel.factory(activityRepository, mode, trip)
      }
  val form: ActivityFormViewModel = viewModel(factory = viewModelFactory ?: factory)
  val state by form.uiState.collectAsStateWithLifecycle()
  var pickingPlace by rememberSaveable { mutableStateOf(false) }
  var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
  val notImplemented = rememberNotImplementedAction()
  val currentOnActivitySaved by rememberUpdatedState(onActivitySaved)
  LaunchedEffect(state.pendingResult) {
    when (val result = form.consumeResult()) {
      is ActivityFormResult.Saved -> currentOnActivitySaved(result.activityId)
      ActivityFormResult.EditUnavailable -> notImplemented()
      null -> Unit
    }
  }
  // Leaving would lose the user's changes, so ask first. Nothing can be left during a save.
  val close: () -> Unit = {
    if (!state.isSaving) {
      if (state.isDirty && !state.isSaved) confirmingDiscard = true else onClose()
    }
  }

  if (pickingPlace) {
    PlacePickerRoute(
        placeRepository = placeRepository,
        onPlacePicked = {
          form.updatePlace(it)
          pickingPlace = false
        },
        onCancel = { pickingPlace = false },
        modifier = modifier,
        initialLocation = state.fields.place,
    )
    return
  }
  BackHandler(onBack = close)
  ActivityFormScreen(
      state = state,
      onNameChange = form::updateName,
      onDateChange = form::updateDate,
      onTimeChange = form::updateTime,
      onPlaceClick = { pickingPlace = true },
      onDescriptionChange = form::updateDescription,
      onSave = form::save,
      onClose = close,
      onDelete = notImplemented,
      modifier = modifier,
  )
  if (confirmingDiscard) {
    DestructiveConfirmationDialog(
        title = stringResource(R.string.activity_form_discard_title),
        message = stringResource(R.string.activity_form_discard_message),
        confirmLabel = stringResource(R.string.discard_changes_confirm),
        dismissLabel = stringResource(R.string.discard_changes_keep_editing),
        onConfirm = {
          confirmingDiscard = false
          onClose()
        },
        onDismiss = { confirmingDiscard = false },
        confirmTestTag = ActivityFormTestTags.DISCARD_CONFIRM,
    )
  }
}
