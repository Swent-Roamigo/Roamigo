// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.connectivity

import kotlinx.coroutines.flow.Flow

/** Tells whether the device can currently reach the internet. */
interface ConnectivityRepository {
  /**
   * Emits whether the device is online, starting with the current state, then on every change.
   * Consecutive values are distinct.
   */
  val isOnline: Flow<Boolean>
}
