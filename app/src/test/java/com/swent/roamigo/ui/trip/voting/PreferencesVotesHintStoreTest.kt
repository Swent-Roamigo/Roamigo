// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.voting

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesVotesHintStoreTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val preferences = context.getSharedPreferences("roamigo_hints", Context.MODE_PRIVATE)

  // Only this flag is reset, so the test leaves any other hint of the file alone.
  @Before fun setUp() = resetFlag()

  @After fun tearDown() = resetFlag()

  private fun resetFlag() {
    preferences.edit().remove("votes_hint_seen").commit()
  }

  @Test
  fun remembersTheHintWasSeenForTheNextStore() {
    val first = PreferencesVotesHintStore(context)
    assertFalse(first.isSeen())

    first.markSeen()

    assertTrue(PreferencesVotesHintStore(context).isSeen())
  }

  @Test
  fun keepsAHintThatWasDismissedBeforeTheStoreExisted() {
    // The key and file of the original implementation: existing users must not see the hint again.
    preferences.edit().putBoolean("votes_hint_seen", true).commit()

    assertTrue(PreferencesVotesHintStore(context).isSeen())
  }

  @Test
  fun neverShowsTheHintForTheNoHintStore() {
    assertTrue(NoVotesHint.isSeen())
    NoVotesHint.markSeen()
  }

  /** A context whose storage cannot be opened, e.g. a full or unreadable disk. */
  private val brokenStorage =
      object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            throw IllegalStateException("storage unavailable")
      }

  @Test
  fun showsTheHintAgainWhenTheStorageCannotBeRead() {
    assertFalse(PreferencesVotesHintStore(brokenStorage).isSeen())
  }

  @Test
  fun doesNotCrashWhenTheStorageCannotBeWritten() {
    val store = PreferencesVotesHintStore(brokenStorage)

    store.markSeen()

    assertFalse(store.isSeen())
  }
}
