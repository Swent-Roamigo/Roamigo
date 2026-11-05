// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.navigation

import android.net.Uri

/** Navigation route names, defined once so screens and tests never repeat string literals. */
object Route {
  const val PERMISSIONS = "permissions"
  const val AUTHENTICATION = "authentication"
  const val TRIPS = "trips"
  const val PROFILE = "profile"
  const val FRIENDS = "friends"
  const val NEW_TRIP = "new_trip"
  const val TRIP_READY = "trip/{tripId}/ready"

  fun tripReady(tripId: String): String = "trip/${Uri.encode(tripId)}/ready"

  const val TRIP_HISTORY = "trip_history"
  const val TRIP_DETAILS = "trip/{tripId}/details?readOnly={readOnly}&initialTab={initialTab}"
  const val VOTES = "trip/{tripId}/votes?tripName={tripName}"
  const val VOTE = "trip/{tripId}/vote/{voteId}?readOnly={readOnly}"
  const val NEW_VOTE = "trip/{tripId}/new_vote"
  const val TRIP_OVERVIEW_MAP = "trip_overview_map"
  const val TRIP_MAP = "trip/{tripId}/map"
  const val PHOTOS = "trip/{tripId}/photos?tripName={tripName}"

  fun tripDetails(tripId: String, readOnly: Boolean = false, openMap: Boolean = false): String =
      "trip/${Uri.encode(tripId)}/details?readOnly=$readOnly&initialTab=${if (openMap) "map" else "detail"}"

  fun votes(tripId: String, tripName: String): String =
      "trip/${Uri.encode(tripId)}/votes?tripName=${Uri.encode(tripName)}"

  fun vote(tripId: String, voteId: String, readOnly: Boolean = false): String =
      "trip/${Uri.encode(tripId)}/vote/${Uri.encode(voteId)}?readOnly=$readOnly"

  fun newVote(tripId: String): String = "trip/${Uri.encode(tripId)}/new_vote"

  fun tripMap(tripId: String): String = "trip/${Uri.encode(tripId)}/map"

  fun photos(tripId: String, tripName: String): String =
      "trip/${Uri.encode(tripId)}/photos?tripName=${Uri.encode(tripName)}"
}
