// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.connectivity

import kotlinx.coroutines.flow.MutableStateFlow

/** [ConnectivityRepository] for unit tests, whose state is set through [online]. */
class FakeConnectivityRepository(isOnlineInitially: Boolean = true) : ConnectivityRepository {

  val online = MutableStateFlow(isOnlineInitially)

  override val isOnline = online
}
