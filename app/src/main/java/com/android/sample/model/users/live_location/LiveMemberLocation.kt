package com.android.sample.model.users.live_location

//Placeholder - depends on MapBox requirements?
//structure:
// tripLocations/
//    {tripId}/
//        {userId}/
//            latitude
//            longitude
//            updatedAt
data class LiveMemberLocation(
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long,
)