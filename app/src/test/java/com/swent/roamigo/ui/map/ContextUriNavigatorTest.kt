// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextUriNavigatorTest {
  // The app's screens provide their activity as the context.
  private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

  @Test
  fun startsAViewActivityForTheUri() {
    val uri = Uri.parse("geo:0,0?q=48.8606,2.3376(Louvre)")

    ContextUriNavigator(activity).openUri(uri)

    val started = shadowOf(activity).nextStartedActivity
    assertEquals(Intent.ACTION_VIEW, started.action)
    assertEquals(uri, started.data)
  }

  @Test
  fun letsAMissingAppThroughToTheCaller() {
    shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)

    assertThrows(ActivityNotFoundException::class.java) {
      ContextUriNavigator(activity).openUri(Uri.parse("geo:0,0?q=0,0"))
    }
  }
}
