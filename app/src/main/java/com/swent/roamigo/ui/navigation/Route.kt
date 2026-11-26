// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import com.swent.roamigo.ui.trip.activity.ActivityFormTrip
import java.time.LocalDate

/** Navigation route names, defined once so screens and tests never repeat string literals. */
object Route {
  const val PERMISSIONS = "permissions"
  const val AUTHENTICATION = "authentication"
  const val TRIPS = "trips"
  const val PROFILE = "profile"
  const val NOTIFICATIONS = "notifications"
  const val FRIENDS = "friends"
  const val FRIENDS_SEARCH = "friends/search"
  const val NEW_TRIP = "new_trip"
  const val TRIP_READY = "trip/{tripId}/ready"

  fun tripReady(tripId: String): String = "trip/${Uri.encode(tripId)}/ready"

  const val TRIP_HISTORY = "trip_history"
  const val EXPLORED_WORLD = "explored_world"
  const val TRIP_DETAILS = "trip/{tripId}/details?readOnly={readOnly}&initialTab={initialTab}"
  const val VOTE = "trip/{tripId}/vote/{voteId}?readOnly={readOnly}"
  const val NEW_VOTE = "trip/{tripId}/new_vote"
  const val NEW_ACTIVITY =
      "trip/{tripId}/new_activity?tripName={tripName}&startDate={startDate}&endDate={endDate}"
  const val MEMBERS = "trip/{tripId}/members"
  const val TRIP_OVERVIEW_MAP = "trip_overview_map"
  const val JOIN = "join/{token}"

  fun join(token: String): String = "join/${Uri.encode(token)}"

  fun tripDetails(tripId: String, readOnly: Boolean = false, openMap: Boolean = false): String =
      "trip/${Uri.encode(tripId)}/details?readOnly=$readOnly&initialTab=${if (openMap) "map" else "detail"}"

  fun vote(tripId: String, voteId: String, readOnly: Boolean = false): String =
      "trip/${Uri.encode(tripId)}/vote/${Uri.encode(voteId)}?readOnly=$readOnly"

  fun members(tripId: String): String = "trip/${Uri.encode(tripId)}/members"

  fun newVote(tripId: String): String = "trip/${Uri.encode(tripId)}/new_vote"

  /** Opens the activity form for [trip], whose name and dates the trip screen already has. */
  fun newActivity(tripId: String, trip: ActivityFormTrip): String =
      "trip/${Uri.encode(tripId)}/new_activity?tripName=${Uri.encode(trip.name)}" +
          "&startDate=${trip.startDate}&endDate=${trip.endDate}"
}

// Argument names are written in the route patterns above, so they are read back in one place.

/** The trip this destination was opened for. */
internal fun NavBackStackEntry.tripId(): String = requireNotNull(arguments?.getString("tripId"))

/** The invitation token this destination was opened for. */
internal fun NavBackStackEntry.token(): String = requireNotNull(arguments?.getString("token"))

/** The trip name and dates given to [Route.newActivity]. */
internal fun NavBackStackEntry.activityFormTrip(): ActivityFormTrip {
  fun argument(name: String): String =
      requireNotNull(arguments?.getString(name)) { "Missing route argument $name" }
  return ActivityFormTrip(
      name = argument("tripName"),
      startDate = LocalDate.parse(argument("startDate")),
      endDate = LocalDate.parse(argument("endDate")),
  )
}

/** The vote this destination was opened for. */
internal fun NavBackStackEntry.voteId(): String = requireNotNull(arguments?.getString("voteId"))

/** Whether the destination was opened read-only, as for a past trip. */
internal fun NavBackStackEntry.isReadOnly(): Boolean = arguments?.getString("readOnly") == "true"

/** Whether a trip should open on its Map tab. */
internal fun NavBackStackEntry.opensMap(): Boolean = arguments?.getString("initialTab") == "map"
