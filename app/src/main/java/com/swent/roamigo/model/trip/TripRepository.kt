package com.swent.roamigo.model.trip

/**
 * Defines the trip-related data operations required by the presentation layer.
 *
 * ViewModels depend on this interface rather than on a concrete data source, allowing Firebase and
 * in-memory implementations to be exchanged without changing presentation code.
 */
interface TripRepository {

  /** Returns all trips belonging to the current user. */
  suspend fun getTrips(): List<Trip>

  /** Returns the activities belonging to the requested trip. */
  suspend fun getActivitiesForTrip(tripId: String): List<Activity>

  /** Returns the members belonging to the requested trip. */
  suspend fun getMembersForTrip(tripId: String): List<TripMember>
}
