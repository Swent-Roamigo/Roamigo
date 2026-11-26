// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.voting

import android.content.Context
import androidx.core.content.edit

/** Remembers whether the user has dismissed the hint that explains how votes work. */
interface VotesHintStore {
  fun isSeen(): Boolean

  fun markSeen()
}

/** A store for screens that never show the hint. */
object NoVotesHint : VotesHintStore {
  override fun isSeen() = true

  override fun markSeen() = Unit
}

/**
 * Keeps the flag in the app's preferences. Losing it only shows the hint once more, so a storage
 * failure is not worth surfacing.
 */
class PreferencesVotesHintStore(context: Context) : VotesHintStore {
  private val appContext = context.applicationContext

  override fun isSeen(): Boolean = runCatching {
    preferences().getBoolean(VOTES_HINT_SEEN, false)
  }
      .getOrDefault(false)

  override fun markSeen() {
    runCatching { preferences().edit { putBoolean(VOTES_HINT_SEEN, true) } }
  }

  private fun preferences() =
      appContext.getSharedPreferences(HINTS_PREFERENCES, Context.MODE_PRIVATE)

  private companion object {
    const val HINTS_PREFERENCES = "roamigo_hints"
    const val VOTES_HINT_SEEN = "votes_hint_seen"
  }
}
