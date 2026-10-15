// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.navigation

/** Navigation route names, defined once so screens and tests never repeat string literals. */
object Route {
  const val PERMISSIONS = "permissions"
  const val AUTHENTICATION = "authentication"
  const val TRIPS = "trips"
  const val PROFILE = "profile"
  const val FRIENDS = "friends"
  const val NEW_TRIP = "new_trip"

  const val TRIP_ID_ARG = "tripId"
  const val VOTE_ID_ARG = "voteId"
  const val CREATE_VOTE = "trips/{$TRIP_ID_ARG}/votes/new"
  const val VOTE = "trips/{$TRIP_ID_ARG}/votes/{$VOTE_ID_ARG}"

  fun createVote(tripId: String) = "trips/$tripId/votes/new"

  fun vote(tripId: String, voteId: String) = "trips/$tripId/votes/$voteId"
}
