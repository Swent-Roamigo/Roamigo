// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

import java.util.Locale

/**
 * Configurable in-memory implementation of [UserRepository].
 *
 * It allows the presentation layer to be developed and tested independently from the production
 * user data source.
 */
class FakeUserRepository(
    private val currentUser: User,
    users: List<User> = emptyList(),
    private val failure: Exception? = null,
    private val emailsByUserId: Map<String, String> = emptyMap(),
) : UserRepository {

  private val usersById = (users + currentUser).associateBy { it.uid }

  override suspend fun getCurrentUser(): User {
    throwIfNeeded()
    return currentUser
  }

  override suspend fun getUsers(userIds: Set<String>): List<User> {
    throwIfNeeded()
    return userIds.mapNotNull { usersById[it] }
  }

  override suspend fun searchUsers(query: String): List<User> {
    throwIfNeeded()
    val normalized = query.trim().lowercase(Locale.ROOT)
    require(normalized.length <= if ('@' in normalized) 254 else 120)

    if (normalized.isEmpty()) return emptyList()

    return usersById.values
        .filter { user ->
          if ('@' in normalized) {
            emailsByUserId[user.uid]?.trim()?.lowercase(Locale.ROOT) == normalized
          } else {
            user.displayName.trim().lowercase(Locale.ROOT).startsWith(normalized)
          }
        }
        .take(20)
  }

  override suspend fun publishCurrentUser() {
    throwIfNeeded()
  }

  private fun throwIfNeeded() {
    failure?.let { throw it }
  }
}
