// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.PlaceProximity
import com.swent.roamigo.model.place.PlaceRepository

/**
 * Full-screen search picker. Supply RoamigoApplication.placeRepository through the owning Route;
 * keep the caller's draft in its own ViewModel and update only its location in [onPlacePicked].
 * Cancel leaves that draft untouched. No navigation controller or persistence is required.
 *
 * A composition-scoped owner isolates each opening and clears its ViewModels on removal. AndroidX
 * retains the owner across configuration changes and restores its saved state.
 */
@Composable
fun PlacePickerRoute(
    placeRepository: PlaceRepository,
    onPlacePicked: (TripLocation) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialLocation: TripLocation? = null,
    proximity: PlaceProximity? = null,
    viewModelFactory: ViewModelProvider.Factory? = null,
) {
  val owner = rememberViewModelStoreOwner()
  val factory =
      remember(placeRepository, initialLocation, proximity) {
        PlacePickerViewModel.factory(placeRepository, initialLocation, proximity)
      }
  val picker: PlacePickerViewModel =
      viewModel(viewModelStoreOwner = owner, factory = viewModelFactory ?: factory)
  val state by picker.uiState.collectAsStateWithLifecycle()
  val currentOnPlacePicked by rememberUpdatedState(onPlacePicked)
  LaunchedEffect(state.finished) { picker.consumeResult()?.let { currentOnPlacePicked(it) } }
  val cancel = { if (picker.cancel()) onCancel() }
  BackHandler(onBack = cancel)
  PlacePickerScreen(
      state,
      picker::updateQuery,
      picker::selectCandidate,
      picker::retry,
      picker::confirm,
      cancel,
      modifier,
  )
}
