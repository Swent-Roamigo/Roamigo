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
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 * Observes a trip's accepted activities and groups them by local day for the Plan tab, and counts
 * the suggestions whose vote is still open.
 *
 * The repository listeners only run while the UI is subscribed. Dates use the [clock]'s zone, which
 * is also the zone used for the trip's start and end dates.
 */
class ItineraryViewModel(
    private val tripId: String,
    private val activityRepository: ActivityRepository,
    private val voteRepository: VoteRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val tripDays = MutableStateFlow<List<LocalDate>>(emptyList())
  private val chosenDay = MutableStateFlow<LocalDate?>(null)
  private val attempts = MutableStateFlow(0)

  private val activities = attempts.observeKeepingLast {
    activityRepository.observeActivities(tripId)
  }

  // Votes only feed the suggestion count, so their failure warns like a failed plan refresh
  // instead of replacing the plan with an error.
  private val votes = attempts.observeKeepingLast { voteRepository.observeVotes(tripId) }

  val uiState: StateFlow<ItineraryUiState> =
      combine(activities, votes, tripDays, chosenDay) { activities, votes, days, chosen ->
            val loaded = activities.value
            when {
              loaded != null ->
                  readyState(loaded, votes.value, days, chosen, activities.failed || votes.failed)
              activities.failed -> ItineraryUiState.Error
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

  // Legacy suggestions without a linked vote have nothing to vote on.
  private fun List<Activity>.suggestionVoteIds(): List<String> = mapNotNull {
    if (it.activityType == ActivityType.SUGGESTION) it.linkedVoteId else null
  }

  private fun readyState(
      activities: List<Activity>,
      votes: List<Vote>?,
      tripDays: List<LocalDate>,
      chosen: LocalDate?,
      failed: Boolean,
  ): ItineraryUiState.Ready {
    val entries = activities.toPlanEntries()
    val byDay = entries.groupBy { it.start.toLocalDate() }
    val days = (tripDays + byDay.keys).distinct().sorted()
    val today = LocalDate.now(clock)
    val selected =
        chosen?.takeIf { it in days }
            ?: today.takeIf { it in days }
            ?: byDay.keys.minOrNull()
            ?: days.firstOrNull()
    val rows =
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
    // Until the votes have loaded once, no suggestion is known to be waiting.
    val waitingSuggestions =
        votes?.let { activities.suggestionVoteIds().countSuggestionsWithOpenRound(it) } ?: 0
    return ItineraryUiState.Ready(
        days,
        selected,
        rows,
        entries.isNotEmpty(),
        failed,
        waitingSuggestions,
    )
  }

  private data class PlanEntry(val activity: Activity, val start: ZonedDateTime)

  companion object {
    private const val STOP_TIMEOUT_MILLIS = 5_000L

    /** Creates the ViewModel for [tripId] backed by [activityRepository] and [voteRepository]. */
    fun factory(
        tripId: String,
        activityRepository: ActivityRepository,
        voteRepository: VoteRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { ItineraryViewModel(tripId, activityRepository, voteRepository) }
    }
  }
}

/**
 * Counts the suggestions, given by the IDs of their initial votes, whose current round is still
 * open. A revote continues the suggestion's vote through [Vote.nextRoundVoteId].
 */
private fun List<String>.countSuggestionsWithOpenRound(votes: List<Vote>): Int {
  val votesById = votes.associateBy { it.uid }
  return count { initialVoteId ->
    currentRound(initialVoteId, votesById)?.status == VoteStatus.OPEN
  }
}

private fun currentRound(initialVoteId: String, votesById: Map<String, Vote>): Vote? {
  var vote = votesById[initialVoteId] ?: return null
  val visited = mutableSetOf(vote.uid)
  while (true) {
    // A next round that has not arrived yet leaves the latest known round as the current one.
    val next = vote.nextRoundVoteId?.let(votesById::get) ?: return vote
    if (!visited.add(next.uid)) return vote
    vote = next
  }
}

/**
 * The latest value of a live source, or `null` before the first one, and whether its updates have
 * stopped after a failure.
 */
private data class LiveValue<T>(val value: T?, val failed: Boolean)

/**
 * Collects [source] and starts it again each time this counter changes, as a retry. A failure keeps
 * the last value and only sets [LiveValue.failed], so the screen can keep showing it with a
 * warning; the next attempt clears the flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<Int>.observeKeepingLast(source: () -> Flow<T>): Flow<LiveValue<T>> =
    flatMapLatest {
      source()
          .map<T, (LiveValue<T>) -> LiveValue<T>> { value -> { LiveValue(value, false) } }
          .onStart { emit { it.copy(failed = false) } }
          .catch { emit { it.copy(failed = true) } }
    }
    // Each event is a change to the previous state, so a failure cannot erase the last value.
    .runningFold(LiveValue<T>(value = null, failed = false)) { last, change -> change(last) }
