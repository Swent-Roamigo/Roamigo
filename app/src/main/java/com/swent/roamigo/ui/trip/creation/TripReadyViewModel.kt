// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.toDomainInstant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TripReadyState {
  data object Loading : TripReadyState

  data object Error : TripReadyState

  data class Ready(val trip: Trip) : TripReadyState
}

class TripReadyViewModel(private val repository: TripRepository, private val tripId: String) :
    ViewModel() {
  private val mutableState = MutableStateFlow<TripReadyState>(TripReadyState.Loading)
  val state = mutableState.asStateFlow()

  private var loadJob: Job? = null

  init {
    retry()
  }

  fun retry() {
    loadJob?.cancel()
    mutableState.value = TripReadyState.Loading
    loadJob = viewModelScope.launch {
      try {
        val trip = repository.getTrip(tripId)
        val zone = ZoneId.systemDefault()
        val start = trip?.startDate?.toDomainInstant()?.atZone(zone)?.toLocalDate()
        val end = trip?.endDate?.toDomainInstant()?.atZone(zone)?.toLocalDate()
        mutableState.value =
            if (
                trip == null ||
                    trip.uid != tripId ||
                    trip.destinations.isEmpty() ||
                    trip.destinations.any { it.name.isBlank() } ||
                    start == null ||
                    end == null ||
                    end.isBefore(start)
            ) {
              TripReadyState.Error
            } else TripReadyState.Ready(trip)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableState.value = TripReadyState.Error
      }
    }
  }
}
