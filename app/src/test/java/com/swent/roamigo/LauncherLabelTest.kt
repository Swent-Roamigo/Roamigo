// Author : Erik Godel
// Co-author : Claude (Anthropic)
package com.swent.roamigo

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherLabelTest {
  @Test
  fun launcherShowsAppNameForMainActivity() {
    val application = RuntimeEnvironment.getApplication()
    val packageManager = application.packageManager
    val activityInfo =
        packageManager.getActivityInfo(ComponentName(application, MainActivity::class.java), 0)

    assertEquals(
        application.getString(R.string.app_name),
        activityInfo.loadLabel(packageManager).toString(),
    )
  }
}
