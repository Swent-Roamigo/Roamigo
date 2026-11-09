// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.CachingDestinationRepository
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoamigoApplicationTest {
  @Test
  fun theDestinationCacheIsOneInstanceForTheWholeApp() {
    val application = ApplicationProvider.getApplicationContext<RoamigoApplication>()

    assertTrue(application.destinationRepository is CachingDestinationRepository)
    assertSame(application.destinationRepository, application.destinationRepository)
  }
}
