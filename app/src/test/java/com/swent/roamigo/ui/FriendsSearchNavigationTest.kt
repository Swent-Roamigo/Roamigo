// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Friends-to-search navigation and friend request creation flow. */
@RunWith(AndroidJUnit4::class)
class FriendsSearchNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val store = FakeFriendRepository.Store()
  private val alice = FakeFriendRepository("alice", store)
  private val bob = FakeFriendRepository("bob", store)

  private val auth =
      FakeAuthRepository(
          AuthenticatedUser("alice", "Alice", null, null),
          initiallySignedIn = true,
      )

  private val users =
      FakeUserRepository(
          User("alice", "Alice", null),
          listOf(User("bob", "Bob", null)),
      )

  private lateinit var nav: NavHostController

  private fun show() {
    compose.setContent {
      nav = rememberNavController()

      RoamigoTheme {
        RoamigoApp(
            friendRepository = alice,
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = auth,
            userRepository = users,
            navController = nav,
            locationRepository = FakeLocationRepository(),
        )
      }
    }
  }

  private fun waitForTag(tag: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun addFriendOpensRealSearchAndBackReturnsToFriends() {
    show()
    waitForTag(C.Tag.trips_navigation_friends)

    compose.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    waitForTag(C.Tag.friends_add_friend)

    compose.onNodeWithTag(C.Tag.friends_add_friend).performClick()
    waitForTag(C.Tag.friends_directory_query)

    compose.runOnIdle { assertEquals(Route.FRIENDS_SEARCH, nav.currentDestination?.route) }

    compose.onNodeWithTag(C.Tag.friends_search_back).performClick()
    waitForTag(C.Tag.friends_screen)

    compose.runOnIdle { assertEquals(Route.FRIENDS, nav.currentDestination?.route) }
  }

  @Test
  fun signingOutFromSearchRedirectsToAuthentication() {
    show()
    waitForTag(C.Tag.trips_navigation_friends)

    compose.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    waitForTag(C.Tag.friends_add_friend)

    compose.onNodeWithTag(C.Tag.friends_add_friend).performClick()
    waitForTag(C.Tag.friends_directory_query)

    compose.onNodeWithTag(C.Tag.friends_directory_query).performTextInput("Bob")
    waitForTag(C.Tag.friends_directory_user_prefix + "bob")

    compose.runOnIdle { auth.signOut() }

    compose.waitUntil(5_000) { nav.currentDestination?.route == Route.AUTHENTICATION }

    compose.runOnIdle { assertEquals(Route.AUTHENTICATION, nav.currentDestination?.route) }
  }

  @Test
  fun searchFromFriendsCanCreateARequestVisibleBackOnFriends() {
    show()
    waitForTag(C.Tag.trips_navigation_friends)

    compose.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    waitForTag(C.Tag.friends_add_friend)

    compose.onNodeWithTag(C.Tag.friends_add_friend).performClick()
    waitForTag(C.Tag.friends_directory_query)

    compose.onNodeWithTag(C.Tag.friends_directory_query).performTextInput("Bob")
    waitForTag(C.Tag.friends_directory_send_prefix + "bob")

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").performClick()

    compose.waitUntil(5_000) { runBlocking { bob.getIncomingRequests().size == 1 } }

    compose.onNodeWithTag(C.Tag.friends_search_back).performClick()

    waitForTag(C.Tag.friends_screen)

    // Verify that the request appears in the outgoing requests section.
    waitForTag(C.Tag.friends_pending_request_row)

    compose.onNodeWithTag(C.Tag.friends_pending_request_row).assertExists()

    compose
        .onNodeWithTag(C.Tag.friends_list)
        .performScrollToNode(hasTestTag(C.Tag.friends_pending_request_row))

    compose.onNodeWithText("Bob").assertIsDisplayed()

    assertEquals(
        1,
        runBlocking { bob.getIncomingRequests().size },
    )
  }
}
