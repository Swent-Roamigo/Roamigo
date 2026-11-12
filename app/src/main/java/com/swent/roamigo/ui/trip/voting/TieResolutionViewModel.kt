// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.isCompletedOn
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TieResolutionState(
    val eligibility: ResolutionEligibility = ResolutionEligibility.Loading,
    val refreshing: Boolean = false,
    val revoteSource: Vote? = null,
    val ownerVoteId: String? = null,
    val ownerOptions: List<VoteOption> = emptyList(),
    val selectedOptionId: String? = null,
    val submitting: Boolean = false,
    val failed: Boolean = false,
) {
  val canResolve: Boolean
    get() = eligibility.canResolve

  val isOwner: Boolean
    get() = (eligibility as? ResolutionEligibility.Ready)?.isOwner == true
}

/**
 * Owns resolution drafts; all writes revalidate final results and permissions in the repository.
 */
class TieResolutionViewModel(
    private val repository: VoteRepository,
    private val tripRepository: TripRepository,
    private val tripId: String,
    private val userId: String,
    private val readOnly: Boolean,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val _state = MutableStateFlow(TieResolutionState())
  val state = _state.asStateFlow()

  private var eligibilityRequest: Deferred<ResolutionEligibility>? = null

  init {
    refreshEligibility()
  }

  /** Single-flight refresh on retry, resume, calendar-day changes and before each write. */
  fun refreshEligibility() {
    viewModelScope.launch { reloadEligibility() }
  }

  private suspend fun reloadEligibility(): ResolutionEligibility {
    val request =
        eligibilityRequest?.takeIf { it.isActive }
            ?: viewModelScope
                .async {
                  // Keep the last result visible; only the initial state is Loading.
                  _state.update { it.copy(refreshing = true) }
                  val eligibility =
                      try {
                        val trip = tripRepository.getTrip(tripId)
                        if (trip == null) ResolutionEligibility.Failed(missingTrip = true)
                        else
                            ResolutionEligibility.Ready(
                                isOwner = trip.ownerId == userId,
                                restriction =
                                    when {
                                      readOnly -> ResolutionRestriction.READ_ONLY
                                      userId !in trip.memberIds -> ResolutionRestriction.NOT_MEMBER
                                      trip.isCompletedOn(LocalDate.now(clock), clock.zone) ->
                                          ResolutionRestriction.ENDED
                                      else -> null
                                    },
                            )
                      } catch (cancelled: CancellationException) {
                        _state.update { it.copy(refreshing = false) }
                        throw cancelled
                      } catch (_: Exception) {
                        ResolutionEligibility.Failed()
                      }
                  _state.update { it.copy(eligibility = eligibility, refreshing = false) }
                  eligibility
                }
                .also { eligibilityRequest = it }
    return request.await()
  }

  /** Owns validation in this ViewModel; the create flow awaits the fresh result before writing. */
  fun validateRevoteSubmission(): Deferred<Boolean> = viewModelScope.async {
    reloadEligibility().canResolve
  }

  private fun eligible(closed: VoteUiState.Closed): Boolean =
      _state.value.canResolve && !_state.value.submitting && closed.isUnresolvedTie

  fun startRevote(closed: VoteUiState.Closed) {
    if (!eligible(closed)) return
    _state.update {
      it.copy(
          revoteSource =
              closed.vote.copy(
                  options =
                      closed.vote.options.filter { option ->
                        option.uid in closed.result.winningOptionIds
                      }
              ),
          failed = false,
      )
    }
  }

  fun startOwnerDecision(closed: VoteUiState.Closed) {
    if (!eligible(closed) || !_state.value.isOwner) return
    _state.update {
      it.copy(
          ownerVoteId = closed.vote.uid,
          ownerOptions =
              closed.vote.options.filter { option -> option.uid in closed.result.winningOptionIds },
          selectedOptionId = null,
          failed = false,
      )
    }
  }

  fun selectOwnerOption(id: String) {
    if (_state.value.submitting || _state.value.ownerOptions.none { it.uid == id }) return
    _state.update { it.copy(selectedOptionId = id) }
  }

  fun dismiss() {
    if (!_state.value.submitting)
        _state.update {
          it.copy(
              revoteSource = null,
              ownerVoteId = null,
              ownerOptions = emptyList(),
              selectedOptionId = null,
              failed = false,
          )
        }
  }

  fun confirmOwnerDecision(closed: VoteUiState.Closed) {
    val selected = _state.value.selectedOptionId ?: return
    if (closed.vote.uid != _state.value.ownerVoteId) return
    if (!eligible(closed) || !_state.value.isOwner || selected !in closed.result.winningOptionIds)
        return
    _state.update { it.copy(submitting = true, failed = false) }
    viewModelScope.launch {
      try {
        val eligibility = reloadEligibility()
        if (
            !eligibility.canResolve ||
                eligibility !is ResolutionEligibility.Ready ||
                !eligibility.isOwner
        )
            return@launch
        repository.resolveByOwner(tripId, closed.vote.uid, selected, userId)
        _state.update {
          it.copy(
              submitting = false,
              ownerVoteId = null,
              ownerOptions = emptyList(),
              selectedOptionId = null,
          )
        }
      } catch (cancelled: CancellationException) {
        _state.update { it.copy(submitting = false) }
        throw cancelled
      } catch (_: Exception) {
        _state.update { it.copy(failed = true) }
      } finally {
        _state.update { it.copy(submitting = false) }
      }
    }
  }

  companion object {
    internal fun factory(
        repository: VoteRepository,
        trips: TripRepository,
        tripId: String,
        userId: String,
        readOnly: Boolean,
    ) = viewModelFactory {
      initializer { TieResolutionViewModel(repository, trips, tripId, userId, readOnly) }
    }
  }
}
