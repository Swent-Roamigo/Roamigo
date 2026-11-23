// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.details

import android.icu.text.ListFormatter
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.swent.roamigo.ui.weather.iconRes
import com.swent.roamigo.ui.weather.labelRes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** What the trip screen needs to draw the forecast card. */
@Immutable
class TripForecastUi(
    val state: ForecastUiState,
    val city: String,
    val onRetry: () -> Unit,
)

/**
 * The forecast card state of [details], or null when there is nothing to look up. It lives in the
 * Route, so the Screen only renders it, and it is refreshed whenever the screen comes back.
 */
@Composable
internal fun rememberTripForecast(
    details: TripDetails?,
    repository: WeatherRepository?,
): TripForecastUi? {
  val destination = details?.destination
  if (details == null || destination == null || repository == null) return null
  val model =
      viewModel(
          key =
              "forecast-${destination.latitude},${destination.longitude}" +
                  "-${details.startDate}-${details.endDate}"
      ) {
        TripForecastViewModel(destination, details.startDate, details.endDate, repository)
      }
  LifecycleResumeEffect(model) {
    model.refresh()
    onPauseOrDispose {}
  }
  val state by model.state.collectAsStateWithLifecycle()
  return TripForecastUi(state, destination.name, model::retry)
}

/** The current time, renewed every minute so an "updated … ago" line never goes stale. */
@Composable
internal fun rememberNow(): Instant {
  val now by
      produceState(Instant.now()) {
        while (true) {
          delay(NOW_REFRESH_MILLIS)
          value = Instant.now()
        }
      }
  return now
}

/** The weather of each trip day, or why there is none yet. [now] dates the "updated" line. */
@Composable
fun TripForecastCard(
    state: ForecastUiState,
    city: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    // Read here, not by the screen, so the minute tick redraws the card and not the whole screen.
    now: Instant = rememberNow(),
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
        ForecastUiState.Busy -> FailedBody(onRetry, busy = true)
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
  val configuration = LocalConfiguration.current
  // Tiles of five days do not fit a narrow screen, nor a large font, so they scroll instead.
  val scrolls =
      state.days.size > MAX_FITTING_DAYS ||
          configuration.screenWidthDp < NARROW_SCREEN_DP ||
          configuration.fontScale > MAX_FONT_SCALE_THAT_FITS
  val scroll = rememberScrollState()
  Row(
      Modifier.fillMaxWidth().then(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    state.days.forEach { day ->
      DayTile(
          day,
          if (scrolls) Modifier.width(DAY_TILE_WIDTH * configuration.fontScale.coerceIn(1f, 2f))
          else Modifier.weight(1f),
      )
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
  val weekday = formatted(day.date, "EEE", locale).uppercase(locale)
  val highLow =
      stringResource(R.string.weather_high_low_description, day.highCelsius, day.lowCelsius)
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
          formatted(day.date, "dMMM", locale),
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondaryAa,
      )
      Icon(
          painterResource(day.condition.iconRes()),
          contentDescription = null,
          Modifier.padding(top = 4.dp).size(28.dp),
          tint = Color.Unspecified,
      )
      Text(
          stringResource(day.condition.labelRes()),
          Modifier.padding(horizontal = 2.dp),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          minLines = 2,
          maxLines = 2,
      )
      // The two numbers alone do not say which is the high, so one description names both.
      Text(
          stringResource(R.string.weather_degrees, day.highCelsius),
          Modifier.semantics { contentDescription = highLow },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
      )
      Text(
          stringResource(R.string.weather_degrees, day.lowCelsius),
          Modifier.clearAndSetSemantics {},
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
  val fontScale = LocalConfiguration.current.fontScale.coerceIn(1f, 2f)
  Column(
      Modifier.testTag(C.Tag.weather_loading).semantics { liveRegion = LiveRegionMode.Polite },
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      repeat(MAX_FITTING_DAYS) {
        Surface(
            Modifier.weight(1f).height(LOADING_TILE_HEIGHT * fontScale),
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
      title =
          pluralStringResource(
              R.plurals.weather_too_far_title,
              ForecastWindow.DAYS_AHEAD.toInt(),
              ForecastWindow.DAYS_AHEAD,
          ),
      body =
          stringResource(
              R.string.weather_too_far_body,
              formatted(availableFrom, "dMMMM", locale),
          ),
  )
}

@Composable
private fun FailedBody(onRetry: () -> Unit, busy: Boolean = false) {
  Message(
      icon = {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            Modifier.size(20.dp),
            tint = Warning,
        )
      },
      title =
          stringResource(if (busy) R.string.weather_busy_title else R.string.weather_failed_title),
      body = stringResource(if (busy) R.string.weather_busy_body else R.string.weather_failed_body),
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
  val rainy = days.filter { it.isRainLikely }
  if (rainy.isEmpty()) return stringResource(R.string.weather_summary_dry)
  // A week's names repeat on a longer trip, so the date says which Monday.
  val skeleton = if (days.size > DAYS_IN_A_WEEK) "EEEdMMM" else "EEEE"
  val list = joinedForLocale(rainy.map { formatted(it.date, skeleton, locale) }, locale)
  return pluralStringResource(R.plurals.weather_summary_wet, rainy.size, list)
}

/** "A", "A and B" or "A, B and C", the way the [locale] writes a list. */
@Composable
private fun joinedForLocale(items: List<String>, locale: Locale): String {
  if (items.size == 1) return items.single()
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    return ListFormatter.getInstance(locale).format(items)
  }
  // Older phones have no list formatter, so the English shape is used.
  return stringResource(
      R.string.weather_list_and,
      items.dropLast(1).joinToString(", "),
      items.last(),
  )
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

/** [date] in the pattern the [locale] writes the [skeleton] with, e.g. "EEE" or "dMMM". */
private fun formatted(date: LocalDate, skeleton: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(date)

private const val MAX_FITTING_DAYS = 5
private const val DAYS_IN_A_WEEK = 7
private const val NARROW_SCREEN_DP = 360
private const val MAX_FONT_SCALE_THAT_FITS = 1.15f
private const val NOW_REFRESH_MILLIS = 60_000L
private val DAY_TILE_WIDTH = 58.dp
private val LOADING_TILE_HEIGHT = 112.dp
