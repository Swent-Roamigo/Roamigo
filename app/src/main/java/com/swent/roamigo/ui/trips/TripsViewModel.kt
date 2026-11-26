// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.atZone
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.toDomainInstant
import com.swent.roamigo.model.toLocalDate
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.isCompletedOn
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.model.weather.ForecastWindow
import com.swent.roamigo.model.weather.WeatherRepository
import com.swent.roamigo.ui.trip.formatTripDateRange
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * @property weatherRepository the forecast shown on the main trip card; no chip without one.
 */
class TripsViewModel(
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val destinationRepository: DestinationRepository? = null,
    private val authRepository: AuthRepository? = null,
    private val weatherRepository: WeatherRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(TripsUiState())

  private var refreshJob: Job? = null
  private var hasLoadedTrips = false

  // Only cache successful lookups for the featured trip, scoped to this ViewModel's lifetime.
  private data class PhotoKey(val tripId: String, val location: TripLocation)

  private var cachedPhoto: Pair<PhotoKey, DestinationPhoto>? = null

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
              isLoading = !hasLoadedTrips,
              isRefreshing = hasLoadedTrips,
              errorMessage = null,
              refreshError = null,
          )

      try {
        val currentUser = loadCurrentUser()
        val now = ZonedDateTime.now(clock)
        val firstName = currentUser.displayName.trim().substringBefore(" ").ifBlank { "Traveler" }
        _uiState.value =
            _uiState.value.copy(
                greeting = greetingFor(now),
                currentUserName = firstName,
                currentUserInitial = firstName.take(1).uppercase(),
                currentUserPhotoUrl = currentUser.profilePictureUrl?.takeIf { it.isNotBlank() },
            )
        val (state, location, days) = loadTripsState(now)
        hasLoadedTrips = true
        coroutineScope {
          // The forecast is fetched while the photo is looked up, and shown once the photo step has
          // published its state, which would otherwise overwrite it.
          val key = weatherKeyOf(state.mainTrip?.id, location, days, now)
          val chip = async { lookUpWeather(key, location, days, now.toLocalDate()) }
          // The chip of another trip, day or place must not stay on the new card meanwhile.
          loadFeaturedPhoto(
              state.copy(mainTripWeather = state.mainTripWeather?.takeIf { key == weatherKey }),
              location,
          )
          applyWeather(chip.await(), key)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.value =
            if (hasLoadedTrips) {
              _uiState.value.copy(
                  isRefreshing = false,
                  refreshError = R.string.trips_refresh_error,
              )
            } else {
              _uiState.value.copy(isLoading = false, errorMessage = R.string.trips_load_error)
            }
      }
    }
  }

  /** Marks a failed image as eligible for lookup on the next refresh. */
  fun onPhotoLoadError(photo: DestinationPhoto) {
    if (cachedPhoto?.second == photo) cachedPhoto = null
  }

  /** What to do with the weather chip once the forecast has been looked up. */
  private sealed interface ChipResult {
    /** There is nothing to show for this trip. */
    data object Clear : ChipResult

    /** The forecast could not be loaded; keep the chip of the same trip, if any. */
    data object Keep : ChipResult

    data class Show(val chip: WeatherChipUiModel) : ChipResult
  }

  /** What the chip was looked up for, so that a kept chip is never one for something else. */
  private data class WeatherKey(val tripId: String, val location: TripLocation, val day: LocalDate)

  private var weatherKey: WeatherKey? = null

  /** The main trip, place and day to look the weather up for, or null if there is none. */
  private fun weatherKeyOf(
      tripId: String?,
      location: TripLocation?,
      days: ClosedRange<LocalDate>?,
      now: ZonedDateTime,
  ): WeatherKey? {
    val today = now.toLocalDate()
    if (
        weatherRepository == null ||
            tripId == null ||
            location == null ||
            days == null ||
            !ForecastWindow.isAvailable(days.start, today)
    ) {
      return null
    }
    return WeatherKey(tripId, location, maxOf(days.start, today))
  }

  /**
   * The forecast of today, or of the first trip day, for the main trip. The whole trip is asked
   * for, as the trip screen does, so that the forecast saved for offline is never cut to one day.
   */
  private suspend fun lookUpWeather(
      key: WeatherKey?,
      location: TripLocation?,
      days: ClosedRange<LocalDate>?,
      today: LocalDate,
  ): ChipResult {
    if (key == null || weatherRepository == null || location == null || days == null) {
      return ChipResult.Clear
    }
    return try {
      val forecast =
          weatherRepository.forecast(
              location.latitude,
              location.longitude,
              days.start,
              days.endInclusive,
          )
      // The destination's own "today" may be a day off the device's, and it is the one the
      // provider counts from, so it decides which day is wanted and which one is "Today".
      val destinationToday = forecast.destinationToday ?: today
      val wanted = maxOf(days.start, destinationToday)
      forecast.days
          .firstOrNull { it.date >= wanted }
          ?.let {
            ChipResult.Show(
                WeatherChipUiModel(
                    it.date,
                    it.date == destinationToday,
                    it.condition,
                    it.highCelsius,
                )
            )
          } ?: ChipResult.Clear
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // The chip is optional and must not prevent the trip from being displayed.
      ChipResult.Keep
    }
  }

  private fun applyWeather(result: ChipResult, key: WeatherKey?) {
    val chip =
        when (result) {
          is ChipResult.Show -> result.chip.also { weatherKey = key }
          ChipResult.Keep ->
              _uiState.value.mainTripWeather?.takeIf { key != null && weatherKey == key }
          ChipResult.Clear -> null
        }
    if (chip == null) weatherKey = null
    _uiState.value = _uiState.value.copy(mainTripWeather = chip)
  }

  private suspend fun loadFeaturedPhoto(state: TripsUiState, location: TripLocation?) {
    val tripId = state.mainTrip?.id
    val key = if (tripId != null && location != null) PhotoKey(tripId, location) else null
    val previousPhoto = cachedPhoto?.takeIf { it.first == key }?.second
    if (previousPhoto == null) cachedPhoto = null
    _uiState.value = state.copy(mainTripPhoto = previousPhoto)
    if (key == null || previousPhoto != null || destinationRepository == null) return

    try {
      val destination =
          destinationRepository
              .searchDestinations(key.location.name)
              .filter {
                (key.location.countryCode == null || it.countryCode == key.location.countryCode) &&
                    kotlin.math.abs(it.latitude - key.location.latitude) <
                        PHOTO_MATCH_TOLERANCE_DEGREES &&
                    kotlin.math.abs(it.longitude - key.location.longitude) <
                        PHOTO_MATCH_TOLERANCE_DEGREES
              }
              .minByOrNull {
                kotlin.math.abs(it.latitude - key.location.latitude) +
                    kotlin.math.abs(it.longitude - key.location.longitude)
              }
      val photo = destination?.let { destinationRepository.findPhoto(it) }
      if (photo != null) cachedPhoto = key to photo
      _uiState.value =
          _uiState.value.copy(
              mainTripPhoto = photo,
              mainTripPhotoAttempt = _uiState.value.mainTripPhotoAttempt + 1,
          )
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // Photo lookup is optional and must not prevent the trip from being displayed.
    }
  }

  private suspend fun loadCurrentUser(): User =
      if (authRepository != null) {
        val account = authRepository.getCurrentUser()
        User(account?.uid.orEmpty(), account?.displayName.orEmpty(), account?.photoUrl)
      } else {
        userRepository.getCurrentUser()
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
  private suspend fun loadTripsState(
      now: ZonedDateTime
  ): Triple<TripsUiState, TripLocation?, ClosedRange<LocalDate>?> {
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
        try {
          userRepository.getUsers(mainMembers.map { it.userId }.toSet()).associateBy { it.uid }
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          // Member avatars and initials are optional presentation data.
          emptyMap()
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
          now = now,
      )
    }

    val state =
        _uiState.value.copy(
            isLoading = false,
            isRefreshing = false,
            errorMessage = null,
            refreshError = null,
            mainTrip =
                mainTrip?.toMainTripUiModel(
                    activities = mainActivities,
                    memberInitials = memberInitials,
                    now = now,
                ),
            nextActivity =
                if (mainTrip != null && nextActivity?.startTime != null) {
                  nextActivity.toUiModel(
                      trip = mainTrip,
                      start = nextActivity.startTime,
                      zoneId = now.zone,
                  )
                } else {
                  null
                },
            otherTrips = otherTripUiModels,
        )
    val days = mainTrip?.let {
      it.startDate.toLocalDate(now.zone)..it.endDate.toLocalDate(now.zone)
    }
    return Triple(state, mainTrip?.destinations?.firstOrNull(), days)
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
        destinationRepository: DestinationRepository? = null,
        authRepository: AuthRepository? = null,
        weatherRepository: WeatherRepository? = null,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
              return TripsViewModel(
                  tripRepository = tripRepository,
                  userRepository = userRepository,
                  clock = clock,
                  destinationRepository = destinationRepository,
                  authRepository = authRepository,
                  weatherRepository = weatherRepository,
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
 * An active trip takes priority, followed by an ongoing trip and then the closest upcoming trip.
 * End dates determine eligibility, even when the stored status is already COMPLETED.
 *
 * @param trips the trips available to the current user.
 * @param now the current date and time used to determine trip eligibility.
 * @return the trip to highlight, or `null` when no eligible trip exists.
 */
internal fun selectMainTrip(
    trips: List<Trip>,
    now: ZonedDateTime,
): Trip? {
  val eligibleTrips = trips.filter { !it.isCompletedOn(now.toLocalDate(), now.zone) }
  val activeTrip =
      eligibleTrips
          .filter { trip -> trip.status == TripStatus.ACTIVE }
          .maxByOrNull { trip -> trip.startDate.seconds }

  if (activeTrip != null) {
    return activeTrip
  }

  return eligibleTrips
      .filter { trip ->
        val tripStartDate = trip.startDate.toLocalDate(now.zone)

        tripStartDate.isBefore(now.toLocalDate())
      }
      .maxByOrNull { trip -> trip.startDate.seconds }
      ?: eligibleTrips.minByOrNull { trip -> trip.startDate.seconds }
}

/**
 * Selects the closest accepted activity that has not started yet.
 *
 * Suggested activities, activities without a start time, and accepted activities in the past are
 * excluded.
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
        .mapNotNull { activity -> activity.startTime?.let { start -> activity to start } }
        .filter { (activity, start) ->
          activity.activityType == ActivityType.ACCEPTED &&
              !start.toDomainInstant().isBefore(now.toInstant())
        }
        .minByOrNull { (_, start) -> start }
        ?.first

/**
 * Selects the remaining ongoing and upcoming trips that are not currently highlighted.
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
          trip.uid != mainTrip?.uid && !trip.isCompletedOn(now.toLocalDate(), now.zone)
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
  val start = startDate.atZone(now.zone)

  val end = endDate.atZone(now.zone)

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
        TripStatus.ACTIVE -> MainTripBadge.InProgress

        TripStatus.PLANNED,
        TripStatus.COMPLETED -> {
          val daysUntilStart =
              ChronoUnit.DAYS.between(
                  now.toLocalDate(),
                  start.toLocalDate(),
              )

          when {
            daysUntilStart < 0 -> MainTripBadge.InProgress
            daysUntilStart == 0L -> MainTripBadge.StartsToday
            else -> MainTripBadge.StartsIn(daysUntilStart.toInt())
          }
        }
      }

  return MainTripUiModel(
      id = uid,
      name = name,
      dateRange = formatTripDateRange(start.toLocalDate(), end.toLocalDate(), TRIPS_HOME_LOCALE),
      durationDays = durationDays,
      stopCount = stopCount,
      memberInitials = memberInitials,
      badge = badge,
  )
}

/**
 * Converts an ongoing or upcoming trip into the secondary card's presentation model.
 *
 * @param memberCount the number of members belonging to the trip.
 * @param now the local date and time used for trip dates and the ongoing indicator.
 * @return the presentation model for the secondary trip.
 * @receiver the trip being converted.
 */
private fun Trip.toOtherTripUiModel(
    memberCount: Int,
    now: ZonedDateTime,
): OtherTripUiModel {
  val start = startDate.toLocalDate(now.zone)

  val end = endDate.toLocalDate(now.zone)

  return OtherTripUiModel(
      id = uid,
      name = name,
      dateRange = formatTripDateRange(start, end, TRIPS_HOME_LOCALE),
      memberCount = memberCount,
      isInProgress = status == TripStatus.ACTIVE || !start.isAfter(now.toLocalDate()),
  )
}

/**
 * Converts an activity into the presentation model used by the "Next up" section.
 *
 * @param trip the trip used to derive the activity's trip-day number.
 * @param start when the activity starts; only scheduled activities are shown as next.
 * @param zoneId the time zone used when formatting the activity date and time.
 * @return the presentation model for the activity.
 * @receiver the activity being converted.
 */
private fun Activity.toUiModel(
    trip: Trip,
    start: Timestamp,
    zoneId: ZoneId,
): ActivityCardUiModel {
  val activityDateTime = start.atZone(zoneId)

  val tripStartDate = trip.startDate.toLocalDate(zoneId)

  val dayNumber =
      ChronoUnit.DAYS.between(
              tripStartDate,
              activityDateTime.toLocalDate(),
          )
          .toInt() + 1

  val formatter =
      DateTimeFormatter.ofPattern(
          "EEE d MMM · HH:mm",
          TRIPS_HOME_LOCALE,
      )

  return ActivityCardUiModel(
      id = uid,
      name = name,
      subtitle = "Day $dayNumber · ${activityDateTime.format(formatter)}",
  )
}

/**
 * Locale of the dates on the Trips home. The greeting and the "Day" label are written in English
 * here, so the dates match them.
 */
private val TRIPS_HOME_LOCALE: Locale = Locale.ENGLISH

/**
 * How far, in degrees of latitude and of longitude, a searched destination may be from the main
 * trip's location for its photo to be shown (about 2 km).
 */
private const val PHOTO_MATCH_TOLERANCE_DEGREES = 0.02

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
