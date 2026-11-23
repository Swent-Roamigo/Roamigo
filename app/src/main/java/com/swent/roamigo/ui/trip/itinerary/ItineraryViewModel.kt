// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.atZone
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.activity.ActivityRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * Observes a trip's accepted activities and groups them by local day for the Plan tab.
 *
 * The repository listener only runs while the UI is subscribed. Dates use the [clock]'s zone, which
 * is also the zone used for the trip's start and end dates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItineraryViewModel(
    private val tripId: String,
    private val activityRepository: ActivityRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val tripDays = MutableStateFlow<List<LocalDate>>(emptyList())
  private val chosenDay = MutableStateFlow<LocalDate?>(null)
  private val attempts = MutableStateFlow(0)

  private val plan =
      attempts
          .flatMapLatest {
            activityRepository
                .observeActivities(tripId)
                .map<List<Activity>, PlanUpdate> { PlanUpdate.Loaded(it.toPlanEntries()) }
                .onStart { emit(PlanUpdate.Retrying) }
                .catch { emit(PlanUpdate.Failed) }
          }
          // Keep the last plan visible when live updates fail after a successful load.
          .runningFold(PlanSnapshot(entries = null, failed = false)) { previous, update ->
            when (update) {
              is PlanUpdate.Loaded -> PlanSnapshot(update.entries, failed = false)
              PlanUpdate.Retrying -> previous.copy(failed = false)
              PlanUpdate.Failed -> previous.copy(failed = true)
            }
          }

  val uiState: StateFlow<ItineraryUiState> =
      combine(plan, tripDays, chosenDay) { snapshot, days, chosen ->
            val entries = snapshot.entries
            when {
              entries != null -> readyState(entries, days, chosen, snapshot.failed)
              snapshot.failed -> ItineraryUiState.Error
              else -> ItineraryUiState.Loading
            }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
              ItineraryUiState.Loading,
          )

  /** Offers every day from [start] to [end] as a chip, even days with nothing planned. */
  fun setTripDates(start: LocalDate, end: LocalDate) {
    // A malformed range still offers its first day rather than no day at all.
    val last = if (end.isBefore(start)) start else end
    tripDays.value =
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
  }

  /** Shows the activities of [day]. */
  fun selectDay(day: LocalDate) {
    chosenDay.value = day
  }

  /** Restarts observation after a failure. */
  fun retry() {
    attempts.value++
  }

  private fun List<Activity>.toPlanEntries(): List<PlanEntry> = mapNotNull { activity ->
    // Accepted activities always have a start; skip legacy documents that do not.
    val start = activity.startTime ?: return@mapNotNull null
    if (activity.activityType != ActivityType.ACCEPTED) return@mapNotNull null
    PlanEntry(activity, start.atZone(clock.zone))
  }

  private fun readyState(
      entries: List<PlanEntry>,
      tripDays: List<LocalDate>,
      chosen: LocalDate?,
      failed: Boolean,
  ): ItineraryUiState.Ready {
    val byDay = entries.groupBy { it.start.toLocalDate() }
    val days = (tripDays + byDay.keys).distinct().sorted()
    val today = LocalDate.now(clock)
    val selected =
        chosen?.takeIf { it in days }
            ?: today.takeIf { it in days }
            ?: byDay.keys.minOrNull()
            ?: days.firstOrNull()
    val activities =
        byDay[selected]
            .orEmpty()
            .sortedWith(compareBy<PlanEntry> { it.start.toInstant() }.thenBy { it.activity.uid })
            .map {
              ItineraryActivity(
                  id = it.activity.uid,
                  name = it.activity.name,
                  locationName = it.activity.location.name,
                  startTime = it.start.toLocalTime(),
              )
            }
    return ItineraryUiState.Ready(days, selected, activities, entries.isNotEmpty(), failed)
  }

  private data class PlanEntry(val activity: Activity, val start: ZonedDateTime)

  private data class PlanSnapshot(val entries: List<PlanEntry>?, val failed: Boolean)

  private sealed interface PlanUpdate {
    data class Loaded(val entries: List<PlanEntry>) : PlanUpdate

    data object Retrying : PlanUpdate

    data object Failed : PlanUpdate
  }

  companion object {
    private const val STOP_TIMEOUT_MILLIS = 5_000L

    /** Creates the ViewModel for [tripId] backed by [activityRepository]. */
    fun factory(tripId: String, activityRepository: ActivityRepository): ViewModelProvider.Factory =
        viewModelFactory {
          initializer { ItineraryViewModel(tripId, activityRepository) }
        }
  }
}
