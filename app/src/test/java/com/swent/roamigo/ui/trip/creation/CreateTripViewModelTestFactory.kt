// Test-only factory for the SavedStateHandle constructor reserved for tests.
package com.swent.roamigo.ui.trip.creation

import androidx.lifecycle.SavedStateHandle
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.trip.TripRepository
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal fun createTestCreateTripViewModel(
    repository: TripRepository,
    zoneId: ZoneId = ZoneId.systemDefault(),
    destinationRepository: DestinationRepository,
    requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
): CreateTripViewModel =
    CreateTripViewModel(
        repository,
        zoneId,
        destinationRepository,
        requestDispatcher,
        savedStateHandle,
    )
