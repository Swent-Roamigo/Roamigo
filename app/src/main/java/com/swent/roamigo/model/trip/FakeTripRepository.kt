package com.swent.roamigo.model.trip

/**
 * Configurable in-memory implementation of [TripRepository].
 *
 * Tests and previews can provide exactly the trips, activities, and members required by a scenario
 * without depending on Firebase.
 */
class FakeTripRepository(
    private val trips: List<Trip> = emptyList(),
    private val activitiesByTripId: Map<String, List<Activity>> = emptyMap(),
    private val membersByTripId: Map<String, List<TripMember>> = emptyMap(),
    private val failure: Exception? = null,
) : TripRepository {

  override suspend fun getTrips(): List<Trip> {
    throwIfNeeded()
    return trips
  }

  override suspend fun getActivitiesForTrip(tripId: String): List<Activity> {
    throwIfNeeded()
    return activitiesByTripId[tripId].orEmpty()
  }

  override suspend fun getMembersForTrip(tripId: String): List<TripMember> {
    throwIfNeeded()
    return membersByTripId[tripId].orEmpty()
  }

  private fun throwIfNeeded() {
    failure?.let { throw it }
  }
}
