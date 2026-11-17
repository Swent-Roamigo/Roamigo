// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.MAX_PLACE_QUERY_LENGTH
import com.swent.roamigo.model.place.MIN_PLACE_QUERY_LENGTH
import com.swent.roamigo.model.place.Place
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.model.place.PlaceProximity
import com.swent.roamigo.model.place.PlaceRepository
import com.swent.roamigo.model.place.PlaceSearchException
import com.swent.roamigo.model.validCountryCodeOrNull
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns one picker draft. No candidate or in-flight operation is restored across provider sessions.
 */
class PlacePickerViewModel(
    private val repository: PlaceRepository,
    private val savedStateHandle: SavedStateHandle,
    initialLocation: TripLocation? = null,
    proximity: PlaceProximity? = null,
) : ViewModel() {
  private val session = synchronized(sessions) { sessions.getOrPut(repository) { PickerSession() } }
  private val proximity = proximity?.takeIf { validCoordinates(it.latitude, it.longitude) }
  private val restored = savedStateHandle.get<Boolean>("initialized") == true
  private val selection = if (restored) restoreSelection() else initialLocation?.validated()
  private val _uiState =
      MutableStateFlow(
          PlacePickerUiState(
              query = savedStateHandle["query"] ?: "",
              selectedLocation = selection,
              attribution = savedStateHandle["attribution"],
              finished = savedStateHandle["finished"] ?: false,
          )
      )
  val uiState = _uiState.asStateFlow()
  private var operation: Job? = null
  private var generation = 0L
  private var candidateRevision = -1L
  private var pendingResult: TripLocation? =
      if (savedStateHandle.get<Boolean>("resultPending") == true) selection else null

  init {
    saveSelection(selection)
    // Only primitives survive process death; get fresh candidates in the new provider session.
    if (savedStateHandle.get<Boolean>("searchActive") == true && !uiState.value.finished) {
      search(debounce = false)
    }
  }

  fun updateQuery(input: String) {
    val query = input.take(MAX_PLACE_QUERY_LENGTH)
    if (uiState.value.finished) return
    val normalized = query.trim()
    val previous = uiState.value.query.trim()
    savedStateHandle["query"] = query
    _uiState.update { it.copy(query = query) }
    if (normalized == previous) return
    search(debounce = true)
  }

  /** A resolution retry searches again: even an invalid retrieve may have ended the session. */
  fun retry() {
    if (!uiState.value.finished && uiState.value.resolvingCandidateId == null)
        search(debounce = false)
  }

  private fun search(debounce: Boolean) {
    val query = uiState.value.query.trim()
    val request = supersede()
    savedStateHandle["searchActive"] = query.length >= MIN_PLACE_QUERY_LENGTH
    _uiState.update {
      it.copy(
          candidates = emptyList(),
          resolvingCandidateId = null,
          searchError = null,
          resolutionError = null,
          searchStatus =
              if (query.length < MIN_PLACE_QUERY_LENGTH) PlaceSearchStatus.INITIAL
              else PlaceSearchStatus.LOADING,
      )
    }
    if (query.length < MIN_PLACE_QUERY_LENGTH) return
    operation = viewModelScope.launch {
      if (debounce) delay(300)
      session.mutex.withLock {
        ensureActive()
        executeSearch(query, request)
      }
    }
  }

  private suspend fun executeSearch(query: String, request: Long) {
    try {
      session.revision++
      val results = repository.searchPlaces(query, proximity)
      if (request == generation) showSearchResults(results)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      if (request == generation) {
        _uiState.update {
          it.copy(searchStatus = PlaceSearchStatus.ERROR, searchError = error.toPickerError())
        }
      }
    }
  }

  private fun showSearchResults(results: List<PlaceCandidate>) {
    candidateRevision = session.revision
    val candidates =
        results.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }
    _uiState.update {
      it.copy(
          candidates = candidates,
          searchStatus =
              if (candidates.isEmpty()) PlaceSearchStatus.EMPTY else PlaceSearchStatus.RESULTS,
          attribution =
              results.firstNotNullOfOrNull { candidate -> candidate.attribution } ?: it.attribution,
      )
    }
  }

  fun selectCandidate(candidate: PlaceCandidate) {
    val state = uiState.value
    if (state.finished || state.resolvingCandidateId != null || candidate !in state.candidates)
        return
    if (candidateRevision != session.revision) {
      search(debounce = false)
      return
    }
    val request = supersede()
    _uiState.update {
      it.copy(
          candidates = emptyList(),
          searchStatus = PlaceSearchStatus.INITIAL,
          resolvingCandidateId = candidate.id,
          resolutionError = null,
      )
    }
    operation = viewModelScope.launch {
      session.mutex.withLock {
        ensureActive()
        resolveCandidate(candidate, request)
      }
    }
  }

  private suspend fun resolveCandidate(candidate: PlaceCandidate, request: Long) {
    // Another picker may have completed a retrieve while this one waited for the lock.
    if (candidateRevision != session.revision) {
      if (request == generation) search(debounce = false)
      return
    }
    session.revision++
    try {
      val place = repository.resolvePlace(candidate)
      val location = place.validatedLocation() ?: throw PlaceSearchException.InvalidPlace()
      if (request == generation) showResolvedPlace(place, candidate, location)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      if (request == generation) {
        _uiState.update {
          it.copy(resolvingCandidateId = null, resolutionError = error.toPickerError())
        }
      }
    }
  }

  private fun showResolvedPlace(place: Place, candidate: PlaceCandidate, location: TripLocation) {
    saveSelection(location)
    savedStateHandle["searchActive"] = false
    savedStateHandle["attribution"] = place.attribution ?: candidate.attribution
    _uiState.update {
      it.copy(
          selectedLocation = location,
          resolvingCandidateId = null,
          attribution = place.attribution ?: candidate.attribution ?: it.attribution,
      )
    }
  }

  fun confirm() {
    val state = uiState.value
    if (!state.canConfirm) return
    val location = state.selectedLocation?.validated() ?: return
    pendingResult = location
    savedStateHandle["resultPending"] = true
    finish()
  }

  /** Consumed by the Route before calling the parent, so recomposition cannot redeliver it. */
  fun consumeResult(): TripLocation? = pendingResult.also {
    pendingResult = null
    savedStateHandle["resultPending"] = false
  }

  fun cancel(): Boolean {
    if (uiState.value.finished) return false
    finish()
    return true
  }

  // AndroidX retains this scope across configuration changes and clears it on actual removal.
  // Invalidate even non-cooperative provider calls when that scope is finally destroyed.
  override fun onCleared() {
    stop()
  }

  /** Cancels work and discards provider candidates when this picker is shut down. */
  fun stop() {
    supersede()
    _uiState.update {
      it.copy(
          candidates = emptyList(),
          resolvingCandidateId = null,
          searchStatus =
              if (
                  it.searchStatus == PlaceSearchStatus.LOADING ||
                      it.searchStatus == PlaceSearchStatus.RESULTS
              )
                  PlaceSearchStatus.INITIAL
              else it.searchStatus,
      )
    }
  }

  private fun finish() {
    supersede()
    savedStateHandle["searchActive"] = false
    savedStateHandle["finished"] = true
    _uiState.update { it.copy(finished = true, resolvingCandidateId = null) }
  }

  private fun supersede(): Long {
    operation?.cancel()
    return ++generation
  }

  private fun saveSelection(location: TripLocation?) {
    savedStateHandle["initialized"] = true
    savedStateHandle["name"] = location?.name
    savedStateHandle["latitude"] = location?.latitude
    savedStateHandle["longitude"] = location?.longitude
    savedStateHandle["country"] = location?.countryCode
  }

  private fun restoreSelection(): TripLocation? {
    val name = savedStateHandle.get<String>("name") ?: return null
    val latitude = savedStateHandle.get<Double>("latitude") ?: return null
    val longitude = savedStateHandle.get<Double>("longitude") ?: return null
    val country = savedStateHandle.get<String>("country")
    return Place(name, null, latitude, longitude, country).validatedLocation()
  }

  companion object {
    // Serialize picker operations sharing the app-wide provider, including cancelled calls still
    // unwinding. Revision invalidates candidate lists held by another picker after a retrieve.
    private val sessions = WeakHashMap<PlaceRepository, PickerSession>()

    fun factory(
        repository: PlaceRepository,
        initialLocation: TripLocation?,
        proximity: PlaceProximity?,
    ) = viewModelFactory {
      initializer {
        PlacePickerViewModel(repository, createSavedStateHandle(), initialLocation, proximity)
      }
    }
  }
}

private class PickerSession {
  val mutex = Mutex()
  var revision = 0L
}

private fun validCoordinates(latitude: Double, longitude: Double) =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in MIN_LATITUDE..MAX_LATITUDE &&
        longitude in MIN_LONGITUDE..MAX_LONGITUDE

private fun Place.validatedLocation(): TripLocation? =
    if (
        name.isBlank() ||
            !validCoordinates(latitude, longitude) ||
            (countryCode != null && countryCode.validCountryCodeOrNull() == null)
    )
        null
    else copy(name = name.trim()).toTripLocation()

private fun TripLocation.validated(): TripLocation? =
    Place(name, null, latitude, longitude, countryCode).validatedLocation()

private fun Exception.toPickerError() =
    when (this) {
      is PlaceSearchException.Configuration -> PlacePickerError.CONFIGURATION
      is PlaceSearchException.InvalidToken -> PlacePickerError.INVALID_TOKEN
      is PlaceSearchException.QuotaExceeded -> PlacePickerError.QUOTA
      is PlaceSearchException.InvalidPlace -> PlacePickerError.INVALID_PLACE
      else -> PlacePickerError.UNAVAILABLE
    }
