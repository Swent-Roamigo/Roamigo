package com.swent.roamigo.model.users

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class FakeUserRepositoryTest {

  private val currentUser =
      User(
          uid = "user-1",
          displayName = "Alex",
          profilePictureUrl = null,
      )

  private val secondUser =
      User(
          uid = "user-2",
          displayName = "Sam",
          profilePictureUrl = null,
      )

  @Test
  fun getCurrentUserReturnsConfiguredCurrentUser() = runBlocking {
    val repository = FakeUserRepository(currentUser = currentUser)

    val result = repository.getCurrentUser()

    Assert.assertEquals(currentUser, result)
  }

  @Test
  fun getUsersReturnsRequestedUsers() = runBlocking {
    val repository =
        FakeUserRepository(
            currentUser = currentUser,
            users = listOf(secondUser),
        )

    val result = repository.getUsers(setOf(currentUser.uid, secondUser.uid))

    Assert.assertEquals(setOf(currentUser, secondUser), result.toSet())
  }

  @Test
  fun getUsersOmitsUnknownIdentifiers() = runBlocking {
    val repository =
        FakeUserRepository(
            currentUser = currentUser,
            users = listOf(secondUser),
        )

    val result = repository.getUsers(setOf(secondUser.uid, "unknown-user"))

    Assert.assertEquals(listOf(secondUser), result)
  }

  @Test
  fun currentUserIsAvailableThroughGetUsers() = runBlocking {
    val repository = FakeUserRepository(currentUser = currentUser)

    val result = repository.getUsers(setOf(currentUser.uid))

    Assert.assertEquals(listOf(currentUser), result)
  }

  @Test
  fun currentUserTakesPrecedenceForDuplicateUid() = runBlocking {
    val outdatedUser =
        User(
            uid = currentUser.uid,
            displayName = "Old name",
            profilePictureUrl = null,
        )

    val repository =
        FakeUserRepository(
            currentUser = currentUser,
            users = listOf(outdatedUser),
        )

    val result = repository.getUsers(setOf(currentUser.uid))

    Assert.assertEquals(listOf(currentUser), result)
  }

  @Test
  fun configuredFailureIsPropagated() {
    val expected = IllegalStateException("Repository failure")
    val repository =
        FakeUserRepository(
            currentUser = currentUser,
            failure = expected,
        )

    val thrown =
        try {
          runBlocking { repository.getCurrentUser() }
          null
        } catch (exception: IllegalStateException) {
          exception
        }

    Assert.assertEquals(expected, thrown)
  }
}
