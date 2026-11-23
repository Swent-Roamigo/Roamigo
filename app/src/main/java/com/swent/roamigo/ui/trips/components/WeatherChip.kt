// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trips.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.TextSecondaryAa
import com.swent.roamigo.ui.trips.WeatherChipUiModel
import com.swent.roamigo.ui.weather.iconRes
import com.swent.roamigo.ui.weather.labelRes
import java.time.format.DateTimeFormatter

/** One day's weather on the main trip card: its icon, the day it is for, and the high. */
@Composable
internal fun WeatherChip(weather: WeatherChipUiModel, modifier: Modifier = Modifier) {
  val locale = LocalConfiguration.current.locales[0]
  val today = stringResource(R.string.weather_chip_today)
  val shortDay =
      if (weather.isToday) today
      else DateTimeFormatter.ofPattern("EEE", locale).format(weather.date)
  val fullDay =
      if (weather.isToday) today
      else DateTimeFormatter.ofPattern("EEEE", locale).format(weather.date)
  val description =
      stringResource(
          R.string.weather_chip_description,
          fullDay,
          stringResource(weather.condition.labelRes()),
          weather.highCelsius,
      )
  Surface(
      modifier,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
  ) {
    // One announcement for the whole chip, so a screen reader does not read its parts one by one.
    Row(
        Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
            .clearAndSetSemantics {
              contentDescription = description
              testTag = C.Tag.weather_chip
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(weather.condition.iconRes()),
          contentDescription = null,
          Modifier.size(20.dp),
          tint = Color.Unspecified,
      )
      Text(
          shortDay,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = TextSecondaryAa,
      )
      Text(
          stringResource(R.string.weather_degrees, weather.highCelsius),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
