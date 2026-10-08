// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

/**
 * Defines the user-profile operations required by the presentation layer.
 *
 * User data is kept separate from trip data so TripRepository remains focused on trips, activities,
 * and trip membership.
 */
interface UserRepository {

  /** Returns the profile of the currently authenticated user. */
  suspend fun getCurrentUser(): User

  /** Returns user profiles matching the requested identifiers. */
  suspend fun getUsers(userIds: Set<String>): List<User>
}
