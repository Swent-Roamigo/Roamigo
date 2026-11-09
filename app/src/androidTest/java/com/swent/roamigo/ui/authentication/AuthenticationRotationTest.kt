// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android rotations with controlled provider completion; does not open Google's account
 * picker.
 */
@RunWith(AndroidJUnit4::class)
class AuthenticationRotationTest {
  @get:Rule val composeRule = createEmptyComposeRule()
  private lateinit var scenario: ActivityScenario<ComponentActivity>
  private lateinit var application: Application
  private lateinit var activityCallbacks: Application.ActivityLifecycleCallbacks
  private lateinit var navController: NavHostController
  private val user = AuthenticatedUser("test-user", "Test User", null, null)
  private val fake = FakeAuthRepository(user)
  private val votes = InMemoryVoteRepository()
  private var pendingCredentials: CompletableDeferred<GetCredentialResponse>? = null
  private var pendingAuthentication: CompletableDeferred<Unit>? = null
  private var credentialRequests = 0
  private var signInRequests = 0
  private val response = GetCredentialResponse(CustomCredential("test-credential", Bundle()))
  private val repository =
      object : AuthRepository by fake {
        override suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser> {
          signInRequests++
          pendingAuthentication?.await()
          return fake.signInWithGoogle(credential)
        }
      }

  @Before
  fun launch() {
    val activityContent: @Composable () -> Unit = {
      val controller = rememberNavController()
      SideEffect { navController = controller }
      val context = LocalContext.current
      val manager =
          object : CredentialManager by CredentialManager.create(context) {
            override suspend fun getCredential(
                context: Context,
                request: GetCredentialRequest,
            ): GetCredentialResponse {
              credentialRequests++
              return pendingCredentials?.await() ?: response
            }
          }
      RoamigoTheme {
        RoamigoApp(
            destinationRepository = FakeDestinationRepository(),
            voteRepository = votes,
            authRepository = repository,
            credentialManager = manager,
            navController = controller,
        )
      }
    }
    application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    // Install content for every newly created host, including the instances created by rotation.
    // ComponentActivity is already registered by Compose's test-manifest dependency.
    activityCallbacks =
        object : Application.ActivityLifecycleCallbacks {
          override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity.javaClass == ComponentActivity::class.java) {
              (activity as ComponentActivity).setContent { activityContent() }
            }
          }

          override fun onActivityStarted(activity: Activity) {}

          override fun onActivityResumed(activity: Activity) {}

          override fun onActivityPaused(activity: Activity) {}

          override fun onActivityStopped(activity: Activity) {}

          override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

          override fun onActivityDestroyed(activity: Activity) {}
        }
    application.registerActivityLifecycleCallbacks(activityCallbacks)
    scenario = ActivityScenario.launch(ComponentActivity::class.java)
    composeRule.waitForIdle()
  }

  @After
  fun close() {
    if (::scenario.isInitialized) scenario.close()
    if (::activityCallbacks.isInitialized)
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
  }

  private fun rotate(landscape: Boolean) {
    lateinit var previous: ComponentActivity
    scenario.onActivity {
      previous = it
      it.requestedOrientation =
          if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
          else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    composeRule.waitUntil(timeoutMillis = 15_000) {
      var recreated = false
      scenario.onActivity {
        val expected =
            if (landscape) Configuration.ORIENTATION_LANDSCAPE
            else Configuration.ORIENTATION_PORTRAIT
        recreated = it !== previous && it.resources.configuration.orientation == expected
      }
      recreated
    }
    composeRule.waitForIdle()
  }

  private fun signIn() {
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.signInButton)
        .performScrollTo()
        .performClick()
    composeRule.waitForIdle()
  }

  private fun assertPermissions() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(C.Tag.permissions_title).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.permissions_title).assertIsDisplayed()
    scenario.onActivity {
      assertEquals(Route.PERMISSIONS, navController.currentDestination?.route)
      assertNull(navController.previousBackStackEntry)
      assertEquals(1, credentialRequests)
      assertEquals(1, signInRequests)
    }
  }

  @Test
  fun rotationDuringCredentialAcquisitionRetainsPendingSignIn() {
    pendingCredentials = CompletableDeferred()
    signIn()
    lateinit var viewModel: AuthenticationViewModel
    scenario.onActivity {
      viewModel =
          ViewModelProvider(navController.currentBackStackEntry!!)[
              AuthenticationViewModel::class.java]
    }
    rotate(true)
    rotate(false)
    scenario.onActivity {
      assertSame(
          viewModel,
          ViewModelProvider(navController.currentBackStackEntry!!)[
              AuthenticationViewModel::class.java],
      )
      assertEquals(AuthenticationUiState(isLoading = true), viewModel.uiState.value)
      pendingCredentials!!.complete(response)
    }
    assertPermissions()
  }

  @Test
  fun rotationDuringRepositoryAuthenticationRetainsPendingSignIn() {
    pendingAuthentication = CompletableDeferred()
    signIn()
    rotate(true)
    rotate(false)
    scenario.onActivity { pendingAuthentication!!.complete(Unit) }
    assertPermissions()
  }

  @Test
  fun recreationImmediatelyAfterSuccessAndFurtherRotationsKeepOnePermissionsEntry() {
    pendingAuthentication = CompletableDeferred()
    signIn()
    // Hold composition frames so we can await success before navigation consumes it.
    composeRule.mainClock.autoAdvance = false
    try {
      lateinit var viewModel: AuthenticationViewModel
      scenario.onActivity {
        viewModel =
            ViewModelProvider(navController.currentBackStackEntry!!)[
                AuthenticationViewModel::class.java]
        pendingAuthentication!!.complete(Unit)
      }
      composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.user == user }
      scenario.onActivity {
        assertEquals(Route.AUTHENTICATION, navController.currentDestination?.route)
        it.recreate()
      }
    } finally {
      composeRule.mainClock.autoAdvance = true
    }
    assertPermissions()
    var entryId = ""
    scenario.onActivity { entryId = navController.currentBackStackEntry!!.id }
    rotate(true)
    assertPermissions()
    rotate(false)
    assertPermissions()
    scenario.onActivity { assertEquals(entryId, navController.currentBackStackEntry!!.id) }
  }
}
