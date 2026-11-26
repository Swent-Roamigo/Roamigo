// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Time skeletons matching the clock the time picker shows, such as "19:30" or "7:30 PM". */
private const val TIME_24_HOUR_SKELETON = "Hm"
private const val TIME_12_HOUR_SKELETON = "hm"

/**
 * Formats a time like the time picker shows it: the system 12/24-hour setting decides the clock,
 * not the locale alone, so a time reads the same wherever the app shows it.
 */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
  val locale = LocalConfiguration.current.locales[0]
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  return remember(locale, is24Hour) {
    bestPatternFormatter(locale, if (is24Hour) TIME_24_HOUR_SKELETON else TIME_12_HOUR_SKELETON)
  }
}

/** Formats with the field order and separators of [locale] for the fields of [skeleton]. */
fun bestPatternFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
