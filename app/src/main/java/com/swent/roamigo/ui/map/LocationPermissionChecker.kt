// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Location permissions requested by the trip map. Either one is enough to share a location. */
internal val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** Answers the questions the trip map asks the system about the location permission. */
interface LocationPermissionChecker {

  /** Whether the app holds the fine or the coarse location permission. */
  fun isGranted(): Boolean

  /**
   * Whether the system would show the permission dialog again. Only meaningful after a refusal:
   * false then means the permission is blocked and only the settings can allow it.
   */
  fun shouldShowRationale(): Boolean
}

/** A [LocationPermissionChecker] asking the system, for the current context and activity. */
@Composable
fun rememberLocationPermissionChecker(): LocationPermissionChecker {
  val context = LocalContext.current
  val activity = LocalActivity.current
  return remember(context, activity) { SystemLocationPermissionChecker(context, activity) }
}

private class SystemLocationPermissionChecker(
    private val context: Context,
    private val activity: Activity?,
) : LocationPermissionChecker {

  override fun isGranted(): Boolean = LOCATION_PERMISSIONS.any { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  override fun shouldShowRationale(): Boolean =
      activity != null &&
          LOCATION_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
          }
}
