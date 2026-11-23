// AI assistance was used for code review and implementation support.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp

/**
 * Configurable in-memory implementation of [TripRepository].
 *
 * Tests and previews can provide exactly the trips, activities, and members required by a scenario
 * without depending on Firebase. Trips created through [createTrip] are kept in memory and returned
 * by later reads.
 *
 * @param currentUserId the signed-in user who owns created trips, or `null` when signed out.
 */
class FakeTripRepository(
    trips: List<Trip> = emptyList(),
    private val activitiesByTripId: Map<String, List<Activity>> = emptyMap(),
    membersByTripId: Map<String, List<TripMember>> = emptyMap(),
    private val failure: Exception? = null,
    private val currentUserId: String? = "fake-user",
) : TripRepository {

  private val trips = trips.toMutableList()
  private val membersByTripId = membersByTripId.toMutableMap()

  override suspend fun getTrips(): List<Trip> {
    throwIfNeeded()
    return trips.toList()
  }

  override suspend fun getActivitiesForTrip(tripId: String): List<Activity> {
    throwIfNeeded()
    return activitiesByTripId[tripId].orEmpty()
  }

  override suspend fun getMembersForTrip(tripId: String): List<TripMember> {
    throwIfNeeded()
    return membersByTripId[tripId].orEmpty()
  }

  override suspend fun createTrip(tripId: String, draft: TripDraft): Trip {
    throwIfNeeded()
    val ownerId = currentUserId ?: throw TripCreationException.NotAuthenticated()
    val errors = draft.validate()
    if (errors.isNotEmpty()) throw TripCreationException.Validation(errors)

    trips
        .find { it.uid == tripId }
        ?.let { existing ->
          if (existing.ownerId == ownerId) return existing
          throw TripCreationException.PermissionDenied()
        }

    val now = Timestamp.now()
    val trip =
        Trip(
            uid = tripId,
            name = draft.name,
            description = null,
            destinations = draft.destinations,
            startDate = draft.startDate.toFirebaseTimestamp(),
            endDate = draft.endDate.toFirebaseTimestamp(),
            flexibleDates = draft.flexibleDates,
            travelMode = draft.travelMode,
            travelerCount = draft.travelerCount,
            ownerId = ownerId,
            memberIds = listOf(ownerId),
            status = TripStatus.PLANNED,
            createdAt = now,
        )
    trips += trip
    membersByTripId[tripId] = listOf(TripMember(ownerId, TripMemberRole.OWNER, now))
    return trip
  }

  private fun throwIfNeeded() {
    failure?.let { throw it }
  }
}
