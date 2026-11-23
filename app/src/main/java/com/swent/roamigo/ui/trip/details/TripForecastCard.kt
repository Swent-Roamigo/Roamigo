// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.details

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.weather.DailyForecast
import com.swent.roamigo.model.weather.ForecastWindow
import com.swent.roamigo.model.weather.WeatherCondition
import com.swent.roamigo.model.weather.WeatherRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.theme.PaperSubtle
import com.swent.roamigo.ui.theme.SlateMist
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.TextSecondaryAa
import com.swent.roamigo.ui.theme.Warning
import com.swent.roamigo.ui.theme.WarningBorder
import com.swent.roamigo.ui.theme.WarningSurface
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Loads the forecast of the trip's first destination and shows it, or nothing without one. */
@Composable
internal fun TripForecast(details: TripDetails, repository: WeatherRepository) {
  val destination = details.destination ?: return
  val model =
      viewModel(
          key =
              "forecast-${destination.latitude},${destination.longitude}" +
                  "-${details.startDate}-${details.endDate}"
      ) {
        TripForecastViewModel(destination, details.startDate, details.endDate, repository)
      }
  val state by model.state.collectAsStateWithLifecycle()
  val now by remember { mutableStateOf(Instant.now()) }
  TripForecastCard(state, destination.name, now, onRetry = model::retry)
}

/** The weather of each trip day, or why there is none yet. [now] dates the "updated" line. */
@Composable
fun TripForecastCard(
    state: ForecastUiState,
    city: String,
    now: Instant,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (state == ForecastUiState.Hidden) return
  Surface(
      modifier.fillMaxWidth().testTag(C.Tag.weather_card),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
        Modifier.padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
          stringResource(R.string.weather_title, city),
          style = MaterialTheme.typography.titleMedium,
      )
      when (state) {
        ForecastUiState.Loading -> LoadingBody()
        is ForecastUiState.TooFar -> TooFarBody(state.availableFrom)
        ForecastUiState.Failed -> FailedBody(onRetry)
        is ForecastUiState.Ready -> ReadyBody(state, now)
        ForecastUiState.Hidden -> Unit
      }
    }
  }
}

@Composable
private fun ReadyBody(state: ForecastUiState.Ready, now: Instant) {
  val age = ageOf(state.fetchedAt, now)
  if (state.fromCache) StaleNotice(age)
  Text(summaryOf(state.days), style = MaterialTheme.typography.bodyMedium)
  val scrolls = state.days.size > MAX_FITTING_DAYS
  Row(
      Modifier.fillMaxWidth()
          .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    state.days.forEach { day ->
      DayTile(day, if (scrolls) Modifier.width(DAY_TILE_WIDTH) else Modifier.weight(1f))
    }
  }
  Text(
      stringResource(
          if (state.fromCache) R.string.weather_source_saved else R.string.weather_source_updated,
          age,
      ),
      style = MaterialTheme.typography.labelSmall,
      color = TextSecondaryAa,
  )
}

@Composable
private fun StaleNotice(age: String) {
  Surface(
      Modifier.fillMaxWidth().testTag(C.Tag.weather_stale_notice),
      shape = MaterialTheme.shapes.small,
      color = WarningSurface,
      border = BorderStroke(1.dp, WarningBorder),
  ) {
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Default.Warning, contentDescription = null, Modifier.size(16.dp), tint = Warning)
      Text(
          stringResource(R.string.weather_stale_notice, age),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun DayTile(day: DailyForecast, modifier: Modifier) {
  val locale = LocalConfiguration.current.locales[0]
  val weekday = DateTimeFormatter.ofPattern("EEE", locale).format(day.date).uppercase(locale)
  Surface(
      // One item per day, so a screen reader reads the whole day in one go.
      modifier.testTag(C.Tag.weather_day_prefix + day.date).semantics(mergeDescendants = true) {},
      shape = MaterialTheme.shapes.small,
      color = if (day.isWet) SlateMist else PaperSubtle,
  ) {
    Column(
        Modifier.padding(top = 10.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
          weekday,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = TextSecondaryAa,
      )
      Text(
          DateTimeFormatter.ofPattern("d MMM", locale).format(day.date),
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondaryAa,
      )
      Icon(
          painterResource(iconOf(day.condition)),
          contentDescription = null,
          Modifier.padding(top = 4.dp).size(28.dp),
          tint = Color.Unspecified,
      )
      Text(
          stringResource(labelOf(day.condition)),
          Modifier.padding(horizontal = 2.dp),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          minLines = 2,
          maxLines = 2,
      )
      Text(
          stringResource(R.string.weather_degrees, day.highCelsius),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
      )
      Text(
          stringResource(R.string.weather_degrees, day.lowCelsius),
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondaryAa,
      )
      day.rainChancePercent?.let { chance ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
              painterResource(R.drawable.ic_weather_drop),
              contentDescription = stringResource(R.string.weather_rain_chance_description),
              tint = if (day.isWet) MaterialTheme.colorScheme.primary else TextSecondaryAa,
          )
          Text(
              stringResource(R.string.weather_rain_chance, chance),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = if (day.isWet) MaterialTheme.colorScheme.primary else TextSecondaryAa,
          )
        }
      }
    }
  }
}

@Composable
private fun LoadingBody() {
  Column(
      Modifier.testTag(C.Tag.weather_loading).semantics { liveRegion = LiveRegionMode.Polite },
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      repeat(MAX_FITTING_DAYS) {
        Surface(
            Modifier.weight(1f).size(width = 58.dp, height = 112.dp),
            shape = MaterialTheme.shapes.small,
            color = PaperSubtle,
        ) {}
      }
    }
    Text(
        stringResource(R.string.weather_loading),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondaryAa,
    )
  }
}

@Composable
private fun TooFarBody(availableFrom: LocalDate) {
  val locale = LocalConfiguration.current.locales[0]
  Message(
      icon = { Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(20.dp)) },
      title = stringResource(R.string.weather_too_far_title, ForecastWindow.DAYS_AHEAD),
      body =
          stringResource(
              R.string.weather_too_far_body,
              DateTimeFormatter.ofPattern("d MMMM", locale).format(availableFrom),
          ),
  )
}

@Composable
private fun FailedBody(onRetry: () -> Unit) {
  Message(
      icon = {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            Modifier.size(20.dp),
            tint = Warning,
        )
      },
      title = stringResource(R.string.weather_failed_title),
      body = stringResource(R.string.weather_failed_body),
  ) {
    TripActionButton(
        onClick = onRetry,
        modifier = Modifier.testTag(C.Tag.weather_retry),
        outlined = true,
    ) {
      Text(stringResource(R.string.weather_retry), style = MaterialTheme.typography.labelLarge)
    }
  }
}

/** What happened, why, and what to do, in the same order on every message. */
@Composable
private fun Message(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    action: @Composable () -> Unit = {},
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Surface(Modifier.size(36.dp), shape = MaterialTheme.shapes.extraLarge, color = PaperSubtle) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        icon()
      }
    }
    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    Text(
        body,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondaryAa,
        lineHeight = 16.sp,
    )
    action()
  }
}

/** "Rain likely on Monday and Tuesday.", or that none is expected. Snow is not rain. */
@Composable
private fun summaryOf(days: List<DailyForecast>): String {
  val locale = LocalConfiguration.current.locales[0]
  val rainy = days.filter {
    it.condition != WeatherCondition.SNOW &&
        (it.condition == WeatherCondition.RAIN ||
            (it.rainChancePercent ?: 0) >= RAIN_LIKELY_PERCENT)
  }
  if (rainy.isEmpty()) return stringResource(R.string.weather_summary_dry)
  val names = rainy.map { DateTimeFormatter.ofPattern("EEEE", locale).format(it.date) }
  val list =
      if (names.size == 1) names.single()
      else
          stringResource(
              R.string.weather_list_and,
              names.dropLast(1).joinToString(", "),
              names.last(),
          )
  return stringResource(R.string.weather_summary_wet, list)
}

@Composable
private fun ageOf(fetchedAt: Instant, now: Instant): String =
    if (Duration.between(fetchedAt, now).toMillis() < DateUtils.MINUTE_IN_MILLIS) {
      stringResource(R.string.weather_just_now)
    } else {
      DateUtils.getRelativeTimeSpanString(
              fetchedAt.toEpochMilli(),
              now.toEpochMilli(),
              DateUtils.MINUTE_IN_MILLIS,
          )
          .toString()
    }

@DrawableRes
private fun iconOf(condition: WeatherCondition): Int =
    when (condition) {
      WeatherCondition.SUNNY -> R.drawable.ic_weather_sunny
      WeatherCondition.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
      WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy
      WeatherCondition.RAIN -> R.drawable.ic_weather_rain
      WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    }

@StringRes
private fun labelOf(condition: WeatherCondition): Int =
    when (condition) {
      WeatherCondition.SUNNY -> R.string.weather_condition_sunny
      WeatherCondition.PARTLY_CLOUDY -> R.string.weather_condition_partly_cloudy
      WeatherCondition.CLOUDY -> R.string.weather_condition_cloudy
      WeatherCondition.RAIN -> R.string.weather_condition_rain
      WeatherCondition.SNOW -> R.string.weather_condition_snow
    }

private const val MAX_FITTING_DAYS = 5
private const val RAIN_LIKELY_PERCENT = 50
private val DAY_TILE_WIDTH = 58.dp
