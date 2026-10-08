// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.UserRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Provides the presentation state and selection logic for the Trips Home screen.
 *
 * The ViewModel depends only on repository abstractions. It selects the highlighted trip and its
 * next activity, prepares the remaining upcoming trips, and converts domain data into
 * presentation-ready models.
 *
 * @property tripRepository the repository providing trips, activities, and trip members.
 * @property userRepository the repository providing the current user and member profiles.
 * @property clock the clock used for time-dependent trip selection and presentation.
 */
class TripsViewModel(
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(TripsUiState())

  private var refreshJob: Job? = null

  /** State currently rendered by the Trips Home screen. */
  val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  /**
   * Reloads the data required by the Trips Home screen.
   *
   * A loading state is exposed while repositories are queried. Repository failures are converted
   * into an error state instead of propagating to the UI.
   */
  fun refresh() {
    refreshJob?.cancel()

    refreshJob = viewModelScope.launch {
      _uiState.value =
          _uiState.value.copy(
              isLoading = true,
              errorMessage = null,
          )

      try {
        _uiState.value = loadTripsState()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.value =
            TripsUiState(
                isLoading = false,
                errorMessage = "Unable to load trips.",
            )
      }
    }
  }

  /**
   * Retrieves domain data and converts it into presentation state.
   *
   * Trip-related data comes from [TripRepository], while profile information comes from
   * [UserRepository].
   *
   * @return the fully prepared state for the Trips Home screen.
   * @throws Exception if one of the repository operations fails.
   */
  private suspend fun loadTripsState(): TripsUiState {
    val now = ZonedDateTime.now(clock)

    val currentUser = userRepository.getCurrentUser()
    val trips = tripRepository.getTrips()

    val mainTrip =
        selectMainTrip(
            trips = trips,
            now = now,
        )

    val mainActivities =
        mainTrip?.let { trip -> tripRepository.getActivitiesForTrip(trip.uid) }.orEmpty()

    val mainMembers = mainTrip?.let { trip -> tripRepository.getMembersForTrip(trip.uid) }.orEmpty()

    val mainUsers =
        userRepository.getUsers(mainMembers.map { member -> member.userId }.toSet()).associateBy {
            user ->
          user.uid
        }

    val memberInitials =
        mainMembers
            .mapNotNull { member ->
              mainUsers[member.userId]
                  ?.displayName
                  ?.trim()
                  ?.takeIf { name -> name.isNotEmpty() }
                  ?.take(1)
                  ?.uppercase()
            }
            .take(3)

    val nextActivity =
        selectNextActivity(
            activities = mainActivities,
            now = now,
        )

    val otherTrips =
        selectOtherTrips(
            trips = trips,
            mainTrip = mainTrip,
            now = now,
        )

    val otherTripUiModels = otherTrips.map { trip ->
      val memberCount = tripRepository.getMembersForTrip(trip.uid).size

      trip.toOtherTripUiModel(
          memberCount = memberCount,
          zoneId = now.zone,
      )
    }

    val firstName = currentUser.displayName.trim().substringBefore(" ").ifBlank { "Traveler" }

    return TripsUiState(
        isLoading = false,
        errorMessage = null,
        greeting = greetingFor(now),
        currentUserName = firstName,
        currentUserInitial = firstName.take(1).uppercase(),
        mainTrip =
            mainTrip?.toMainTripUiModel(
                activities = mainActivities,
                memberInitials = memberInitials,
                now = now,
            ),
        nextActivity =
            if (mainTrip != null && nextActivity != null) {
              nextActivity.toUiModel(
                  trip = mainTrip,
                  zoneId = now.zone,
              )
            } else {
              null
            },
        otherTrips = otherTripUiModels,
    )
  }

  companion object {

    /**
     * Creates a factory that supplies the dependencies required by [TripsViewModel].
     *
     * Repository implementations are selected outside the ViewModel so the presentation layer
     * remains independent from concrete data sources.
     *
     * @param tripRepository the repository providing trip-related data.
     * @param userRepository the repository providing user-profile data.
     * @param clock the clock used for time-dependent presentation logic.
     * @return a factory capable of creating [TripsViewModel] instances.
     */
    fun factory(
        tripRepository: TripRepository,
        userRepository: UserRepository,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
              return TripsViewModel(
                  tripRepository = tripRepository,
                  userRepository = userRepository,
                  clock = clock,
              )
                  as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}

/**
 * Selects the trip that should be highlighted on the Trips Home screen.
 *
 * An active trip takes priority. When no active trip exists, the closest eligible planned trip is
 * selected.
 *
 * @param trips the trips available to the current user.
 * @param now the current date and time used to determine trip eligibility.
 * @return the trip to highlight, or `null` when no eligible trip exists.
 */
internal fun selectMainTrip(
    trips: List<Trip>,
    now: ZonedDateTime,
): Trip? {
  val activeTrip =
      trips
          .filter { trip -> trip.status == TripStatus.ACTIVE }
          .maxByOrNull { trip -> trip.startDate.seconds }

  if (activeTrip != null) {
    return activeTrip
  }

  return trips
      .filter { trip ->
        val tripStartDate = trip.startDate.toDate().toInstant().atZone(now.zone).toLocalDate()

        trip.status == TripStatus.PLANNED && !tripStartDate.isBefore(now.toLocalDate())
      }
      .minByOrNull { trip -> trip.startDate.seconds }
}

/**
 * Selects the closest accepted activity that has not started yet.
 *
 * Suggested activities and accepted activities in the past are excluded.
 *
 * @param activities the activities belonging to the highlighted trip.
 * @param now the current date and time used to determine whether an activity is upcoming.
 * @return the closest eligible activity, or `null` when none exists.
 */
internal fun selectNextActivity(
    activities: List<Activity>,
    now: ZonedDateTime,
): Activity? =
    activities
        .filter { activity ->
          activity.activityType == ActivityType.ACCEPTED &&
              !activity.startTime.toDate().toInstant().atZone(now.zone).isBefore(now)
        }
        .minByOrNull { activity -> activity.startTime.seconds }

/**
 * Selects the upcoming planned trips that are not currently highlighted.
 *
 * The returned trips are ordered chronologically by start date.
 *
 * @param trips the trips available to the current user.
 * @param mainTrip the currently highlighted trip, or `null` when none is highlighted.
 * @param now the current date and time used to determine trip eligibility.
 * @return the remaining eligible trips in chronological order.
 */
internal fun selectOtherTrips(
    trips: List<Trip>,
    mainTrip: Trip?,
    now: ZonedDateTime,
): List<Trip> =
    trips
        .filter { trip ->
          val tripStartDate = trip.startDate.toDate().toInstant().atZone(now.zone).toLocalDate()

          trip.uid != mainTrip?.uid &&
              trip.status == TripStatus.PLANNED &&
              !tripStartDate.isBefore(now.toLocalDate())
        }
        .sortedBy { trip -> trip.startDate.seconds }

/**
 * Converts a highlighted trip into the presentation model used by the main trip card.
 *
 * @param activities the activities belonging to the trip.
 * @param memberInitials the member initials displayed on the card.
 * @param now the current date and time used to derive the trip status label.
 * @return the presentation model for the highlighted trip.
 * @receiver the trip being converted.
 */
private fun Trip.toMainTripUiModel(
    activities: List<Activity>,
    memberInitials: List<String>,
    now: ZonedDateTime,
): MainTripUiModel {
  val start = startDate.toDate().toInstant().atZone(now.zone)

  val end = endDate.toDate().toInstant().atZone(now.zone)

  // Both the first and last calendar day belong to the trip.
  val durationDays =
      ChronoUnit.DAYS.between(
              start.toLocalDate(),
              end.toLocalDate(),
          )
          .toInt() + 1

  val stopCount = activities.count { activity -> activity.activityType == ActivityType.ACCEPTED }

  val badge =
      when (status) {
        TripStatus.ACTIVE -> "In progress"

        TripStatus.PLANNED -> {
          val daysUntilStart =
              ChronoUnit.DAYS.between(
                  now.toLocalDate(),
                  start.toLocalDate(),
              )

          when (daysUntilStart) {
            0L -> "Starts today"
            1L -> "Starts in 1 day"
            else -> "Starts in $daysUntilStart days"
          }
        }

        TripStatus.COMPLETED -> null
      }

  return MainTripUiModel(
      id = uid,
      name = name,
      dateRange =
          formatTripDates(
              start = start.toLocalDate(),
              end = end.toLocalDate(),
          ),
      durationDays = durationDays,
      stopCount = stopCount,
      memberInitials = memberInitials,
      badge = badge,
  )
}

/**
 * Converts an upcoming trip into the presentation model used by the secondary trip list.
 *
 * @param memberCount the number of members belonging to the trip.
 * @param zoneId the time zone used when formatting trip dates.
 * @return the presentation model for the upcoming trip.
 * @receiver the trip being converted.
 */
private fun Trip.toOtherTripUiModel(
    memberCount: Int,
    zoneId: ZoneId,
): OtherTripUiModel {
  val start = startDate.toDate().toInstant().atZone(zoneId).toLocalDate()

  val end = endDate.toDate().toInstant().atZone(zoneId).toLocalDate()

  return OtherTripUiModel(
      id = uid,
      name = name,
      dateRange =
          formatTripDates(
              start = start,
              end = end,
          ),
      memberCount = memberCount,
  )
}

/**
 * Converts an activity into the presentation model used by the "Next up" section.
 *
 * @param trip the trip used to derive the activity's trip-day number.
 * @param zoneId the time zone used when formatting the activity date and time.
 * @return the presentation model for the activity.
 * @receiver the activity being converted.
 */
private fun Activity.toUiModel(
    trip: Trip,
    zoneId: ZoneId,
): ActivityCardUiModel {
  val activityDateTime = startTime.toDate().toInstant().atZone(zoneId)

  val tripStartDate = trip.startDate.toDate().toInstant().atZone(zoneId).toLocalDate()

  val dayNumber =
      ChronoUnit.DAYS.between(
              tripStartDate,
              activityDateTime.toLocalDate(),
          )
          .toInt() + 1

  val formatter =
      DateTimeFormatter.ofPattern(
          "EEE d MMM · HH:mm",
          Locale.ENGLISH,
      )

  return ActivityCardUiModel(
      id = uid,
      name = name,
      subtitle = "Day $dayNumber · ${activityDateTime.format(formatter)}",
  )
}

/**
 * Formats a trip's start and end dates for display.
 *
 * @param start the first calendar day of the trip.
 * @param end the last calendar day of the trip.
 * @return the compact formatted date interval.
 */
private fun formatTripDates(
    start: LocalDate,
    end: LocalDate,
): String {
  if (start.month == end.month && start.year == end.year) {
    val month =
        end.format(
            DateTimeFormatter.ofPattern(
                "MMM",
                Locale.ENGLISH,
            )
        )

    return "${start.dayOfMonth} – ${end.dayOfMonth} $month"
  }

  val formatter =
      DateTimeFormatter.ofPattern(
          "d MMM",
          Locale.ENGLISH,
      )

  return "${start.format(formatter)} – ${end.format(formatter)}"
}

/**
 * Selects the greeting displayed in the screen header for the current time of day.
 *
 * @param now the current local date and time.
 * @return the greeting corresponding to the current time of day.
 */
private fun greetingFor(now: ZonedDateTime): String =
    when (now.hour) {
      in 5..11 -> "Good morning"
      in 12..17 -> "Good afternoon"
      else -> "Good evening"
    }
