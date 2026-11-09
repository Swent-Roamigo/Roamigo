// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.trip.creation.CreateTripUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

internal object TripCalendarTestTags {
  const val PREVIOUS_MONTH = "c2_month_previous"
  const val NEXT_MONTH = "c2_month_next"

  fun dayFor(date: LocalDate): String = "c2_calendar_day_$date"
}

@Composable
internal fun CalendarCard(
    state: CreateTripUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val monthFormatter = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ENGLISH) }
  val dayDescriptionFormatter = remember {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.ENGLISH)
  }
  val daysInMonth = state.visibleMonth.lengthOfMonth()
  val locale = LocalConfiguration.current.locales[0]
  val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
  val firstDayOffset =
      Math.floorMod(state.visibleMonth.atDay(1).dayOfWeek.value - firstDayOfWeek.value, 7)
  val weeks = (firstDayOffset + daysInMonth + 6) / 7
  val weekdayLabels =
      remember(locale) {
        (0..6).map { offset ->
          firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
        }
      }

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(colors.surface)
              .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(18.dp))
              .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      CalendarMonthButton(
          description = stringResource(R.string.c2_previous_month),
          tag = TripCalendarTestTags.PREVIOUS_MONTH,
          icon = R.drawable.ic_c2_chevron_left,
          enabled = state.visibleMonth.isAfter(YearMonth.now()),
          onClick = onPreviousMonth,
      )
      Text(
          text = state.visibleMonth.atDay(1).format(monthFormatter),
          style = MaterialTheme.typography.titleSmall,
          color = colors.onBackground,
      )
      CalendarMonthButton(
          description = stringResource(R.string.c2_next_month),
          tag = TripCalendarTestTags.NEXT_MONTH,
          icon = R.drawable.ic_c2_chevron_right,
          enabled = true,
          onClick = onNextMonth,
      )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      weekdayLabels.forEach { day ->
        Box(modifier = Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.Center) {
          Text(day, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
      }
    }
    repeat(weeks) { week ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(7) { dayOfWeek ->
          val dayNumber = week * 7 + dayOfWeek - firstDayOffset + 1
          if (dayNumber in 1..daysInMonth) {
            val date = state.visibleMonth.atDay(dayNumber)
            CalendarDay(
                date = date,
                startDate = state.startDate,
                endDate = state.endDate,
                dateDescription = date.format(dayDescriptionFormatter),
                onClick = onSelectDate,
                modifier = Modifier.weight(1f),
            )
          } else {
            Spacer(Modifier.weight(1f).height(38.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun CalendarMonthButton(
    description: String,
    tag: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  Box(
      modifier =
          Modifier.size(28.dp)
              .clip(CircleShape)
              .clickable(enabled = enabled, onClick = onClick)
              .semantics {
                contentDescription = description
                testTag = tag
              },
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint =
            if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    startDate: LocalDate,
    endDate: LocalDate,
    dateDescription: String,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
  val isPast = date.isBefore(LocalDate.now())
  val isEndpoint = !isPast && (date == startDate || date == endDate)
  val inRange = !isPast && date > startDate && date < endDate
  val colors = MaterialTheme.colorScheme
  Box(
      modifier =
          modifier
              .height(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(
                  when {
                    isEndpoint -> colors.primary
                    inRange -> colors.primaryContainer
                    isPast -> colors.surfaceVariant
                    else -> colors.surface
                  }
              )
              .clickable(enabled = !isPast) { onClick(date) }
              .semantics {
                contentDescription = dateDescription
                testTag = TripCalendarTestTags.dayFor(date)
              },
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = date.dayOfMonth.toString(),
        color =
            when {
              isEndpoint -> colors.onPrimary
              isPast -> colors.onSurfaceVariant.copy(alpha = 0.55f)
              else -> colors.onBackground
            },
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}
