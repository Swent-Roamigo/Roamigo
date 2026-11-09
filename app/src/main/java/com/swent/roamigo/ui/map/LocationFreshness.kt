// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.swent.roamigo.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** Age below which a location update is shown as live. */
internal val LIVE_FRESHNESS_THRESHOLD: Duration = 5.seconds

/**
 * Describes how long ago a member location was updated, e.g. "live", "10 s ago" or "2 min ago".
 *
 * Ages are rounded down to whole seconds or minutes. An update dated after [nowMillis], e.g.
 * because the member's clock is ahead, counts as live.
 *
 * @param updatedAtMillis time of the location update, in milliseconds since the epoch.
 * @param nowMillis current time, in milliseconds since the epoch.
 * @param resources resources used to resolve the localized text.
 * @return the freshness text to show next to the member's name.
 */
internal fun formatLocationFreshness(
    updatedAtMillis: Long,
    nowMillis: Long,
    resources: Resources,
): String {
  val ageMillis = nowMillis - updatedAtMillis
  return when {
    ageMillis < LIVE_FRESHNESS_THRESHOLD.inWholeMilliseconds ->
        resources.getString(R.string.trip_map_freshness_live)
    ageMillis < 1.minutes.inWholeMilliseconds -> {
      val seconds = (ageMillis / 1.seconds.inWholeMilliseconds).toInt()
      resources.getQuantityString(R.plurals.trip_map_freshness_seconds_ago, seconds, seconds)
    }
    else -> {
      val minutes = (ageMillis / 1.minutes.inWholeMilliseconds).toInt()
      resources.getQuantityString(R.plurals.trip_map_freshness_minutes_ago, minutes, minutes)
    }
  }
}

/**
 * Current time, in milliseconds since the epoch, refreshed every second while [isTicking] so that
 * the freshness texts keep up with the age of the locations.
 *
 * @param isTicking whether the time is refreshed. When false, it keeps its last value.
 * @return the current time, as of the last refresh.
 */
@Composable
internal fun rememberCurrentTimeMillis(isTicking: Boolean): Long {
  val nowMillis by
      produceState(System.currentTimeMillis(), isTicking) {
        while (isTicking) {
          value = System.currentTimeMillis()
          delay(1.seconds)
        }
      }
  return nowMillis
}
