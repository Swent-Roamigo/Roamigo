// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDirectorySearchTest {

  private val me = User("alice", "Alice", null)
  private val sam = User("sam", "Sam Example", "https://example.com/sam")
  private val sara = User("sara", "Sara", null)

  private fun repository(failure: Exception? = null): FakeUserRepository =
      FakeUserRepository(
          currentUser = me,
          users = listOf(sam, sara),
          failure = failure,
      )

  @Test
  fun nameSearchIsTrimmedCaseInsensitiveAndPrefixBased() = runBlocking {
    assertEquals(listOf(sam, sara), repository().searchUsers("  SA  "))
    assertEquals(listOf(sam), repository().searchUsers("SAM ex"))
    assertTrue(repository().searchUsers("example").isEmpty())
    assertTrue(repository().searchUsers("   ").isEmpty())
  }

  @Test
  fun knownUsersCanBeLoadedAndPublicationIsIdempotent() = runBlocking {
    val repository = repository()

    assertEquals(
        listOf(sam),
        repository.getUsers(setOf("sam", "unknown")),
    )

    repository.publishCurrentUser()
    repository.publishCurrentUser()

    assertEquals(me, repository.getCurrentUser())
  }

  @Test
  fun resultsAreLimitedToTwenty() = runBlocking {
    val many = (1..30).map { User("id-$it", "Traveler $it", null) }

    val repository = FakeUserRepository(me, many)

    assertEquals(20, repository.searchUsers("Traveler").size)
  }

  @Test
  fun invalidQueryAndConfiguredFailuresAreReported() = runBlocking {
    assertTrue(
        runCatching {
          repository().searchUsers("a".repeat(121))
        }
            .exceptionOrNull() is IllegalArgumentException
    )

    val error = IOException("offline")
    val repository = repository(error)

    assertEquals(
        error,
        runCatching {
          repository.searchUsers("Sam")
        }
            .exceptionOrNull(),
    )

    assertEquals(
        error,
        runCatching {
          repository.publishCurrentUser()
        }
            .exceptionOrNull(),
    )
  }
}
