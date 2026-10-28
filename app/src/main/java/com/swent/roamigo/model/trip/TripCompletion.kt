package com.swent.roamigo.model.trip

import java.time.LocalDate
import java.time.ZoneId

internal fun Trip.isCompletedOn(
    today: LocalDate,
    zone: ZoneId,
): Boolean = endDate.toDate().toInstant().atZone(zone).toLocalDate().isBefore(today)
