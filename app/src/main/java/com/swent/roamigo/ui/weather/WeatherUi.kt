// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.weather

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.swent.roamigo.R
import com.swent.roamigo.model.weather.WeatherCondition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The icon that draws this condition, in the colours of the forecast card. */
@DrawableRes
internal fun WeatherCondition.iconRes(): Int =
    when (this) {
      WeatherCondition.SUNNY -> R.drawable.ic_weather_sunny
      WeatherCondition.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
      WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy
      WeatherCondition.RAIN -> R.drawable.ic_weather_rain
      WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    }

/** The word for this condition, so that no icon has to be decoded. */
@StringRes
internal fun WeatherCondition.labelRes(): Int =
    when (this) {
      WeatherCondition.SUNNY -> R.string.weather_condition_sunny
      WeatherCondition.PARTLY_CLOUDY -> R.string.weather_condition_partly_cloudy
      WeatherCondition.CLOUDY -> R.string.weather_condition_cloudy
      WeatherCondition.RAIN -> R.string.weather_condition_rain
      WeatherCondition.SNOW -> R.string.weather_condition_snow
    }

/** This date in the pattern the [locale] writes the [skeleton] with, e.g. "EEE" or "dMMM". */
internal fun LocalDate.formattedAs(skeleton: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(this)
