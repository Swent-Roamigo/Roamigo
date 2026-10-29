// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AuthenticationActivityRecreationTest {
  @get:Rule val composeRule = createEmptyComposeRule()
  private lateinit var controller: ActivityController<AuthenticationRecreationActivity>
  private lateinit var fixture: AuthenticationRecreationFixture

  @Before
  fun launch() {
    fixture = AuthenticationRecreationFixture()
    AuthenticationRecreationActivity.fixture = fixture
    controller =
        Robolectric.buildActivity(AuthenticationRecreationActivity::class.java).setup().visible()
  }

  @After
  fun close() {
    controller.pause().stop().destroy()
    AuthenticationRecreationActivity.fixture = null
  }

  private fun recreate() {
    val previous = controller.get()
    controller.recreate()
    assertNotSame(previous, controller.get())
  }

  private fun signIn() {
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.signInButton)
        .performScrollTo()
        .performClick()
  }

  private fun waitForPermissions() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(C.Tag.permissions_title).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.permissions_title).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.PERMISSIONS, fixture.navController.currentDestination?.route)
      assertNull(fixture.navController.previousBackStackEntry)
      assertEquals(1, fixture.credentialRequests)
      assertEquals(1, fixture.signInRequests)
    }
  }

  @Test
  fun repeatedRecreationWhileSignedOutKeepsAuthentication() {
    repeat(2) {
      composeRule.runOnIdle { recreate() }
      composeRule.onNodeWithTag(AuthenticationScreenTestTags.heading).assertIsDisplayed()
      composeRule.runOnIdle {
        assertEquals(Route.AUTHENTICATION, fixture.navController.currentDestination?.route)
        assertEquals(0, fixture.credentialRequests)
        assertEquals(0, fixture.signInRequests)
      }
    }
  }

  @Test
  fun repeatedRecreationWhileCredentialsArePendingCompletesOneSignIn() {
    fixture.pendingCredentials = CompletableDeferred()
    signIn()
    lateinit var viewModel: AuthenticationViewModel
    composeRule.runOnIdle {
      viewModel =
          ViewModelProvider(fixture.navController.currentBackStackEntry!!)[
              AuthenticationViewModel::class.java]
      assertEquals(AuthenticationUiState(isLoading = true), viewModel.uiState.value)
      recreate()
    }
    composeRule.runOnIdle {
      assertSame(
          viewModel,
          ViewModelProvider(fixture.navController.currentBackStackEntry!!)[
              AuthenticationViewModel::class.java],
      )
      recreate()
    }
    composeRule.runOnIdle { fixture.pendingCredentials!!.complete(fixture.credentialResponse) }
    waitForPermissions()
  }

  @Test
  fun repeatedRecreationWhileRepositoryIsPendingCompletesOneSignIn() {
    fixture.pendingAuthentication = CompletableDeferred()
    signIn()
    composeRule.runOnIdle {
      assertEquals(1, fixture.signInRequests)
      recreate()
    }
    composeRule.runOnIdle { recreate() }
    composeRule.runOnIdle { fixture.pendingAuthentication!!.complete(Unit) }
    waitForPermissions()
  }

  @Test
  fun recreationAfterSuccessBeforeNavigationDoesNotDuplicateOrLoseNavigation() {
    fixture.pendingAuthentication = CompletableDeferred()
    signIn()
    composeRule.runOnIdle {
      val viewModel =
          ViewModelProvider(fixture.navController.currentBackStackEntry!!)[
              AuthenticationViewModel::class.java]
      fixture.pendingAuthentication!!.complete(Unit)
      // Both assertions must hold before recreating: success is published but navigation has not
      // run.
      assertEquals(fixture.user, viewModel.uiState.value.user)
      assertEquals(Route.AUTHENTICATION, fixture.navController.currentDestination?.route)
      recreate()
    }
    waitForPermissions()
    var permissionsEntryId = ""
    composeRule.runOnIdle { permissionsEntryId = fixture.navController.currentBackStackEntry!!.id }
    repeat(2) {
      composeRule.runOnIdle { recreate() }
      waitForPermissions()
      composeRule.runOnIdle {
        assertEquals(permissionsEntryId, fixture.navController.currentBackStackEntry!!.id)
      }
    }
  }
}

/**
 * Owns the composition in onCreate so ActivityController.recreate performs real activity
 * recreation.
 */
class AuthenticationRecreationActivity : ComponentActivity() {
  companion object {
    internal var fixture: AuthenticationRecreationFixture? = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val testFixture = checkNotNull(fixture)
    val manager =
        object : CredentialManager by CredentialManager.create(this) {
          override suspend fun getCredential(
              context: Context,
              request: GetCredentialRequest,
          ): GetCredentialResponse {
            testFixture.credentialRequests++
            return testFixture.pendingCredentials?.await() ?: testFixture.credentialResponse
          }
        }
    setContent {
      val navController = rememberNavController()
      SideEffect { testFixture.navController = navController }
      RoamigoTheme {
        RoamigoApp(
            destinationRepository = sampleDestinationRepository(),
            voteRepository = testFixture.votes,
            authRepository = testFixture.repository,
            credentialManager = manager,
            navController = navController,
        )
      }
    }
  }
}

internal class AuthenticationRecreationFixture {
  val user = AuthenticatedUser("test-user", "Test User", null, null)
  private val fake = FakeAuthRepository(user)
  val votes = InMemoryVoteRepository()
  val credentialResponse = GetCredentialResponse(CustomCredential("test-credential", Bundle()))
  var pendingCredentials: CompletableDeferred<GetCredentialResponse>? = null
  var pendingAuthentication: CompletableDeferred<Unit>? = null
  var credentialRequests = 0
  var signInRequests = 0
  lateinit var navController: NavHostController
  val repository =
      object : AuthRepository by fake {
        override suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser> {
          signInRequests++
          pendingAuthentication?.await()
          return fake.signInWithGoogle(credential)
        }
      }
}
