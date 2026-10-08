// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trips

/**
 * Represents all presentation state required by the Trips Home screen.
 *
 * @property isLoading whether trip data is currently being loaded.
 * @property errorMessage the user-facing loading error, or `null` when no error is present.
 * @property greeting the time-dependent greeting displayed in the header.
 * @property currentUserName the current user's display name shown in the header.
 * @property currentUserInitial the initial displayed in the current user's avatar.
 * @property mainTrip the trip highlighted on the screen, or `null` when none is available.
 * @property nextActivity the next accepted activity of the highlighted trip, or `null` when absent.
 * @property otherTrips the remaining upcoming trips displayed below the highlighted trip.
 */
data class TripsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val greeting: String = "",
    val currentUserName: String = "",
    val currentUserInitial: String = "",
    val mainTrip: MainTripUiModel? = null,
    val nextActivity: ActivityCardUiModel? = null,
    val otherTrips: List<OtherTripUiModel> = emptyList(),
)

/**
 * Represents the trip highlighted on the Trips Home screen.
 *
 * @property id the unique identifier of the trip.
 * @property name the trip name displayed to the user.
 * @property dateRange the formatted start and end dates.
 * @property durationDays the inclusive number of calendar days in the trip.
 * @property stopCount the number of accepted activities belonging to the trip.
 * @property memberInitials up to three member initials displayed on the trip card.
 * @property badge the contextual status label, or `null` when no label should be displayed.
 */
data class MainTripUiModel(
    val id: String,
    val name: String,
    val dateRange: String,
    val durationDays: Int,
    val stopCount: Int,
    val memberInitials: List<String>,
    val badge: String?,
)

/**
 * Represents an upcoming activity displayed in the "Next up" section.
 *
 * @property id the unique identifier of the activity.
 * @property name the activity name displayed to the user.
 * @property subtitle the formatted trip-day, date, and time information.
 */
data class ActivityCardUiModel(
    val id: String,
    val name: String,
    val subtitle: String,
)

/**
 * Represents an upcoming trip displayed in the secondary trip list.
 *
 * @property id the unique identifier of the trip.
 * @property name the trip name displayed to the user.
 * @property dateRange the formatted start and end dates.
 * @property memberCount the number of members belonging to the trip.
 */
data class OtherTripUiModel(
    val id: String,
    val name: String,
    val dateRange: String,
    val memberCount: Int,
)
