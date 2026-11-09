// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestampMillis: Long? = null,
)
