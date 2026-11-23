// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.voting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesVotesHintStoreTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun remembersTheHintWasSeenForTheNextStore() {
    context.getSharedPreferences("roamigo_hints", Context.MODE_PRIVATE).edit().clear().commit()
    val first = PreferencesVotesHintStore(context)
    assertFalse(first.isSeen())

    first.markSeen()

    assertTrue(PreferencesVotesHintStore(context).isSeen())
  }

  @Test
  fun neverShowsTheHintForTheNoHintStore() {
    assertTrue(NoVotesHint.isSeen())
    NoVotesHint.markSeen()
  }
}
