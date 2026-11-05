// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.Manifest
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Tests [rememberLocationPermissionChecker] against the permission state Robolectric reports. */
@RunWith(AndroidJUnit4::class)
class LocationPermissionCheckerTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val application
    get() = shadowOf(composeRule.activity.application)

  private val packageManager
    get() = shadowOf(composeRule.activity.packageManager)

  @Test
  fun isGrantedIsFalseWithoutAnyLocationPermission() {
    application.denyPermissions(*LOCATION_PERMISSIONS)

    assertFalse(rememberedChecker().isGranted())
  }

  @Test
  fun isGrantedIsTrueWithOnlyTheCoarsePermission() {
    application.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    assertTrue(rememberedChecker().isGranted())
  }

  @Test
  fun isGrantedIsTrueWithOnlyTheFinePermission() {
    application.denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    assertTrue(rememberedChecker().isGranted())
  }

  @Test
  fun shouldShowRationaleIsTrueWhenTheSystemWouldAskAgain() {
    packageManager.setShouldShowRequestPermissionRationale(
        Manifest.permission.ACCESS_FINE_LOCATION,
        true,
    )

    assertTrue(rememberedChecker().shouldShowRationale())
  }

  @Test
  fun shouldShowRationaleIsFalseWhenTheSystemWouldNotAskAgain() {
    LOCATION_PERMISSIONS.forEach { permission ->
      packageManager.setShouldShowRequestPermissionRationale(permission, false)
    }

    assertFalse(rememberedChecker().shouldShowRationale())
  }

  @Test
  fun shouldShowRationaleIsFalseWithoutAnActivity() {
    // Would be true with an activity, so that only the missing activity can make it false.
    LOCATION_PERMISSIONS.forEach { permission ->
      packageManager.setShouldShowRequestPermissionRationale(permission, true)
    }

    assertFalse(rememberedChecker(activity = null).shouldShowRationale())
  }

  @Test
  fun theSameCheckerIsKeptAcrossRecompositions() {
    val recompositions = mutableIntStateOf(0)
    val checkers = mutableListOf<LocationPermissionChecker>()
    composeRule.setContent {
      recompositions.intValue // Read so that changing it recomposes this content.
      checkers += rememberLocationPermissionChecker()
    }

    composeRule.runOnIdle { recompositions.intValue++ }
    composeRule.waitForIdle()

    assertEquals(2, checkers.size)
    assertSame(checkers.first(), checkers.last())
  }

  /** The checker remembered in a composition whose [LocalActivity] is [activity]. */
  private fun rememberedChecker(
      activity: Activity? = composeRule.activity
  ): LocationPermissionChecker {
    lateinit var checker: LocationPermissionChecker
    composeRule.setContent {
      CompositionLocalProvider(LocalActivity provides activity) {
        checker = rememberLocationPermissionChecker()
      }
    }
    composeRule.waitForIdle()
    return checker
  }
}
