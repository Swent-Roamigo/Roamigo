package com.swent.roamigo.model.users

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

  private fun throwIfNeeded() {
    failure?.let { throw it }
  }
}
