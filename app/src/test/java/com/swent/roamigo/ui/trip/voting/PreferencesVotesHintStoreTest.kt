// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.voting

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
