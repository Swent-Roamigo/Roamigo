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
  const val TRIP_HISTORY = "trip_history"
  const val VOTES = "trip/{tripId}/votes?tripName={tripName}"
  const val VOTE = "trip/{tripId}/vote/{voteId}"
  const val NEW_VOTE = "trip/{tripId}/new_vote"

  fun votes(tripId: String, tripName: String): String =
      "trip/${Uri.encode(tripId)}/votes?tripName=${Uri.encode(tripName)}"

  fun vote(tripId: String, voteId: String): String =
      "trip/${Uri.encode(tripId)}/vote/${Uri.encode(voteId)}"

  fun newVote(tripId: String): String = "trip/${Uri.encode(tripId)}/new_vote"
}
