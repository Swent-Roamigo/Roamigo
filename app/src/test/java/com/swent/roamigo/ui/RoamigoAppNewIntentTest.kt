// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.inviting.FakeTripInvitationService
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * MainActivity is singleTop, so an invitation link opened while the app is running reaches the
 * activity as a new intent, not as a new activity. The app has to follow it by itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class RoamigoAppNewIntentTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val token = "AbCdEfGh_ijkLMNo-pQrSt"
  private val service = FakeTripInvitationService()
  private lateinit var navController: NavHostController

  private fun start() {
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            friendRepository = FakeFriendRepository("alex"),
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("alex", "Alex", null, null),
                    initiallySignedIn = true,
                ),
            navController = navController,
            locationRepository = FakeLocationRepository(),
            invitationService = service,
        )
      }
    }
  }

  private fun deliverNewIntent(link: String, newTask: Boolean = false) {
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
          if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    composeRule.runOnUiThread {
      // Activity.onNewIntent is protected, and the activity is only reachable through the rule.
      val method =
          ComponentActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
      method.isAccessible = true
      method.invoke(composeRule.activity, intent)
    }
  }

  private fun route() = composeRule.runOnUiThread { navController.currentDestination?.route }

  @Test
  fun aLinkOpenedWhileTheAppIsRunningOpensTheJoinScreen() {
    start()
    composeRule.waitUntil(5_000) { route() == Route.TRIPS }

    deliverNewIntent("roamigo://invite/$token")

    composeRule.waitUntil(5_000) { route() == Route.JOIN }
    composeRule.waitUntil(5_000) { service.previewed == listOf(token) }
  }

  @Test
  fun aLinkFromAnotherAppOpensOnTopOfTheCurrentScreenAndKeepsTheBackStack() {
    start()
    composeRule.waitUntil(5_000) { route() == Route.TRIPS }
    composeRule.runOnUiThread { navController.navigate(Route.NEW_TRIP) }
    composeRule.waitUntil(5_000) { route() == Route.NEW_TRIP }

    // A link tapped in WhatsApp or Gmail comes with a task of its own, which makes handleDeepLink
    // rebuild the back stack from the start destination and lose the form.
    deliverNewIntent("roamigo://invite/$token", newTask = true)

    composeRule.waitUntil(5_000) { route() == Route.JOIN }
    val stack = composeRule.runOnUiThread {
      navController.currentBackStack.value.map { it.destination.route }
    }
    assertTrue(stack.toString(), Route.NEW_TRIP in stack && Route.TRIPS in stack)
    composeRule.runOnUiThread { navController.popBackStack() }
    composeRule.waitUntil(5_000) { route() == Route.NEW_TRIP }
  }

  @Test
  fun anIntentThatIsNotAnInvitationChangesNothing() {
    start()
    composeRule.waitUntil(5_000) { route() == Route.TRIPS }

    deliverNewIntent("https://example.com/invite/$token")
    composeRule.waitForIdle()

    assertEquals(Route.TRIPS, route())
    assertEquals(emptyList<String>(), service.previewed)
  }
}
