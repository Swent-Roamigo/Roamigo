// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.TripRepository
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads only trips accessible through the current user's repository. */
class TripDetailsViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(TripDetailsUiState())
  val uiState = mutableUiState.asStateFlow()
  private var loadJob: Job? = null

  init {
    refresh()
  }

  /** Retries a failed load, cancelling any previous request. */
  fun refresh() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      mutableUiState.value = TripDetailsUiState()
      try {
        val trip = tripRepository.getTrips().find { it.uid == tripId }
        if (trip == null) {
          mutableUiState.value = TripDetailsUiState(false, TripDetailsError.NOT_FOUND)
          return@launch
        }
        val members = tripRepository.getMembersForTrip(tripId)
        val activities = tripRepository.getActivitiesForTrip(tripId)
        mutableUiState.value =
            TripDetailsUiState(
                isLoading = false,
                details =
                    TripDetails(
                        name = trip.name,
                        // Match the local calendar dates used by trip creation and Trips Home.
                        startDate =
                            trip.startDate.toDate().toInstant().atZone(zoneId).toLocalDate(),
                        endDate = trip.endDate.toDate().toInstant().atZone(zoneId).toLocalDate(),
                        travelerCount = trip.travelerCount,
                        memberCount = members.map { it.userId }.distinct().size,
                        suggestedPlaceCount =
                            activities.count { it.activityType == ActivityType.SUGGESTION },
                    ),
            )
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        mutableUiState.value = TripDetailsUiState(false, TripDetailsError.LOAD_FAILED)
      }
    }
  }
}
