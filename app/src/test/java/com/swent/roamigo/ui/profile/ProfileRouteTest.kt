// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.isNotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class ProfileRouteTest {
  @get:Rule val composeRule = createComposeRule()

  private val auth =
      FakeAuthRepository(
          AuthenticatedUser("sam", "Sam", "sam@epfl.ch", null),
          initiallySignedIn = true,
      )

  @Test
  fun defaultNavigationActionsKeepProfileVisibleAndExplainUnfinishedDestinations() {
    var signOutEvents = 0
    composeRule.setContent {
      RoamigoTheme { ProfileRoute(authRepository = auth, onSignedOut = { signOutEvents++ }) }
    }

    listOf(C.Tag.trips_navigation_trips, C.Tag.trips_navigation_friends).forEach { tag ->
      ShadowToast.reset()
      composeRule.onNodeWithTag(tag).assert(isNotImplemented).performClick()
      composeRule.runOnIdle {
        assertEquals(
            RuntimeEnvironment.getApplication().getString(R.string.feature_not_implemented),
            ShadowToast.getTextOfLatestToast(),
        )
      }
      composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam")
    }
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(0, signOutEvents) }
  }

  @Test
  fun suppliedNavigationCallbacksAndModifierReachTheScreen() {
    val actions = mutableListOf<String>()
    composeRule.setContent {
      RoamigoTheme {
        ProfileRoute(
            authRepository = auth,
            onSignedOut = {},
            modifier = Modifier.height(300.dp).testTag("compact_profile"),
            onTripsClick = { actions.add("trips") },
            onFriendsClick = { actions.add("friends") },
            onMapClick = { actions.add("map") },
        )
      }
    }

    composeRule.onNodeWithTag("compact_profile").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsDisplayed()
    composeRule.onNodeWithText("View").performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.runOnIdle { assertEquals(listOf("friends", "trips", "friends", "map"), actions) }
  }

  @Test
  fun switchingProfileScopesKeepsAccountsAndSignOutSeparate() {
    val firstOwner =
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
    val otherOwner =
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
    val otherAuth =
        FakeAuthRepository(
            AuthenticatedUser("taylor", "Taylor", "taylor@epfl.ch", null),
            initiallySignedIn = true,
        )
    var showOther by mutableStateOf(false)
    val signedOutAccounts = mutableListOf<String>()
    try {
      composeRule.setContent {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides if (showOther) otherOwner else firstOwner
        ) {
          val accountId = if (showOther) "taylor" else "sam"
          RoamigoTheme {
            ProfileRoute(
                authRepository = if (showOther) otherAuth else auth,
                onSignedOut = { signedOutAccounts.add(accountId) },
            )
          }
        }
      }

      composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam")
      composeRule.runOnIdle { showOther = true }
      composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Taylor")
      composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsEnabled().performClick()
      composeRule.waitUntil { signedOutAccounts.isNotEmpty() }
      composeRule.runOnIdle {
        assertEquals(listOf("taylor"), signedOutAccounts)
        assertNull(otherAuth.getCurrentUser())
        assertEquals("sam", auth.getCurrentUser()?.uid)
        showOther = false
      }

      composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam")
      composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsEnabled()
      composeRule.runOnIdle {
        assertEquals(listOf("taylor"), signedOutAccounts)
        assertEquals("sam", auth.getCurrentUser()?.uid)
      }
    } finally {
      composeRule.runOnUiThread {
        firstOwner.viewModelStore.clear()
        otherOwner.viewModelStore.clear()
      }
    }
  }

  @Test
  fun recompositionRetainsSignedOutStateAndDoesNotRepeatNavigation() {
    var modifier by mutableStateOf<Modifier>(Modifier)
    var parentUpdates by mutableStateOf(0)
    var accountReads = 0
    var signOutRequests = 0
    var signOutEvents = 0
    val repository =
        object : AuthRepository by auth {
          override fun getCurrentUser(): AuthenticatedUser? {
            accountReads++
            return auth.getCurrentUser()
          }

          override fun signOut(): Result<Unit> {
            signOutRequests++
            return auth.signOut()
          }
        }
    val onSignedOut: () -> Unit = { signOutEvents++ }
    composeRule.setContent {
      RoamigoTheme {
        Column {
          Text("Parent updates: $parentUpdates")
          ProfileRoute(
              authRepository = repository,
              onSignedOut = onSignedOut,
              modifier = modifier,
          )
        }
      }
    }

    composeRule.runOnIdle { parentUpdates++ }
    composeRule.onNodeWithText("Parent updates: 1").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam")
    composeRule.runOnIdle {
      assertEquals(1, accountReads)
      assertEquals(0, signOutRequests)
      assertEquals(0, signOutEvents)
    }

    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    composeRule.waitUntil { signOutEvents == 1 }
    composeRule.runOnIdle { modifier = Modifier.testTag("recomposed_profile") }

    composeRule.onNodeWithTag("recomposed_profile").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam")
    composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsNotEnabled().performClick()
    composeRule.runOnIdle {
      assertEquals(1, accountReads)
      assertEquals(1, signOutRequests)
      assertEquals(1, signOutEvents)
    }
  }
}
