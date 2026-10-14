// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.swent.roamigo.model.TripLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [TripRepository], following `model/database_structure.txt`.
 *
 * @param db the Firestore instance holding the trips.
 * @param currentUserId returns the ID of the signed-in user, or `null` when signed out.
 */
class TripRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val currentUserId: () -> String?,
) : TripRepository {

  // A signed-out user belongs to no trip.
  override suspend fun getTrips(): List<Trip> {
    val userId = currentUserId() ?: return emptyList()
    return trips().whereArrayContains("memberIds", userId).get().await().documents.map {
      it.toTrip()
    }
  }

  override suspend fun getActivitiesForTrip(tripId: String): List<Activity> =
      trips()
          .document(validDocumentId(tripId))
          .collection("activities")
          .get()
          .await()
          .documents
          .map { activityFromFirestore(it.id, tripId, it.requireData()) }

  override suspend fun getMembersForTrip(tripId: String): List<TripMember> =
      members(tripId).get().await().documents.map { tripMemberFromFirestore(it.requireData()) }

  override suspend fun createTrip(tripId: String, draft: TripDraft): Trip {
    val ownerId = currentUserId() ?: throw TripCreationException.NotAuthenticated()
    val errors = draft.validate()
    if (errors.isNotEmpty()) throw TripCreationException.Validation(errors)

    val trip =
        try {
          val tripDocument = trips().document(validDocumentId(tripId))
          val memberDocument = members(tripId).document(validDocumentId(ownerId))
          db.runTransaction { transaction ->
                val existing = transaction.get(tripDocument)
                if (existing.exists()) {
                  existing.toTrip()
                } else {
                  val now = Timestamp.now()
                  val created = draft.toTrip(tripId, ownerId, now)
                  transaction.set(tripDocument, created.toFirestoreData())
                  transaction.set(
                      memberDocument,
                      TripMember(ownerId, TripMemberRole.OWNER, now).toFirestoreData(),
                  )
                  created
                }
              }
              .await()
        } catch (error: CancellationException) {
          throw error
        } catch (error: FirebaseFirestoreException) {
          throw error.toTripCreationException()
        } catch (error: Exception) {
          throw TripCreationException.Unknown(error)
        }

    // Deciding outside the transaction keeps the exception types independent of how Firestore
    // reports failures thrown from inside a transaction function.
    if (trip.ownerId != ownerId) throw TripCreationException.PermissionDenied()
    return trip
  }

  private fun trips(): CollectionReference = db.collection("trips")

  private fun members(tripId: String): CollectionReference =
      trips().document(validDocumentId(tripId)).collection("members")
}

private fun FirebaseFirestoreException.toTripCreationException(): TripCreationException =
    when (code) {
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
          TripCreationException.PermissionDenied(this)
      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> TripCreationException.Network(this)
      else -> TripCreationException.Unknown(this)
    }

private fun validDocumentId(id: String): String {
  require(id.isNotBlank() && '/' !in id && id != "." && id != "..") {
    "Expected a non-blank Firestore document ID without path separators"
  }
  return id
}

private fun DocumentSnapshot.requireData(): Map<String, Any?> =
    requireNotNull(data) { "Missing data at $reference" }

private fun DocumentSnapshot.toTrip(): Trip = tripFromFirestore(id, requireData())

private fun TripDraft.toTrip(tripId: String, ownerId: String, createdAt: Timestamp): Trip =
    Trip(
        uid = tripId,
        name = name,
        description = null,
        destinations = destinations,
        startDate = startDate,
        endDate = endDate,
        flexibleDates = flexibleDates,
        travelMode = travelMode,
        travelerCount = travelerCount,
        ownerId = ownerId,
        memberIds = listOf(ownerId),
        status = TripStatus.PLANNED,
        createdAt = createdAt,
    )

// Explicit mapping preserves the immutable models, which have no Firestore no-argument constructor.
// IDs come from the document path, so they are not stored as fields.

internal fun Trip.toFirestoreData(): Map<String, Any?> =
    mapOf(
        "name" to name,
        "description" to description,
        "destinations" to destinations.map { it.toFirestoreData() },
        "startDate" to startDate,
        "endDate" to endDate,
        "flexibleDates" to flexibleDates,
        "travelMode" to travelMode.name,
        "travelerCount" to travelerCount,
        "ownerId" to ownerId,
        "memberIds" to memberIds,
        "status" to status.name,
        "createdAt" to createdAt,
    )

internal fun tripFromFirestore(id: String, data: Map<String, Any?>): Trip =
    Trip(
        uid = id,
        name = data.requiredString("name"),
        description = data.optionalString("description"),
        destinations = data.requiredList("destinations").map { tripLocationFromFirestore(it) },
        startDate = data.requiredTimestamp("startDate"),
        endDate = data.requiredTimestamp("endDate"),
        flexibleDates = data.requiredBoolean("flexibleDates"),
        travelMode = data.requiredEnum<TravelMode>("travelMode"),
        travelerCount = data.requiredInt("travelerCount"),
        ownerId = data.requiredString("ownerId"),
        memberIds =
            data.requiredList("memberIds").map {
              requireNotNull(it as? String) { "Invalid memberIds entry" }
            },
        status = data.requiredEnum<TripStatus>("status"),
        createdAt = data.requiredTimestamp("createdAt"),
    )

internal fun TripMember.toFirestoreData(): Map<String, Any?> =
    mapOf("userId" to userId, "role" to role.name, "joinTime" to joinTime)

internal fun tripMemberFromFirestore(data: Map<String, Any?>): TripMember =
    TripMember(
        userId = data.requiredString("userId"),
        role = data.requiredEnum<TripMemberRole>("role"),
        joinTime = data.requiredTimestamp("joinTime"),
    )

internal fun activityFromFirestore(id: String, tripId: String, data: Map<String, Any?>): Activity =
    Activity(
        uid = id,
        tripId = tripId,
        activityType = data.requiredEnum<ActivityType>("activityType"),
        name = data.requiredString("name"),
        description = data.optionalString("description"),
        location = tripLocationFromFirestore(data["location"]),
        startTime = data.requiredTimestamp("startTime"),
        endTime = data.optionalTimestamp("endTime"),
        createdByUserId = data.requiredString("createdByUserId"),
        creationTime = data.requiredTimestamp("creationTime"),
    )

private fun TripLocation.toFirestoreData(): Map<String, Any?> =
    mapOf("name" to name, "latitude" to latitude, "longitude" to longitude)

private fun tripLocationFromFirestore(value: Any?): TripLocation {
  val location = requireNotNull(value as? Map<*, *>) { "Invalid location" }
  return TripLocation(
      latitude = location.requiredDouble("latitude"),
      longitude = location.requiredDouble("longitude"),
      name = location.requiredString("name"),
  )
}

private fun Map<*, *>.requiredString(field: String): String =
    requireNotNull(this[field] as? String) { "Missing or invalid $field" }

private fun Map<*, *>.optionalString(field: String): String? {
  val value = this[field]
  require(value == null || value is String) { "Invalid $field" }
  return value
}

private fun Map<*, *>.requiredBoolean(field: String): Boolean =
    requireNotNull(this[field] as? Boolean) { "Missing or invalid $field" }

// Firestore returns whole numbers as Long, while unsaved maps hold Int.
private fun Map<*, *>.requiredInt(field: String): Int =
    when (val value = this[field]) {
      is Int -> value
      is Long -> {
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Out of range $field" }
        value.toInt()
      }
      else -> throw IllegalArgumentException("Missing or invalid $field")
    }

// Firestore returns a coordinate stored as a whole number as Long.
private fun Map<*, *>.requiredDouble(field: String): Double =
    when (val value = this[field]) {
      is Double -> value
      is Long -> value.toDouble()
      else -> throw IllegalArgumentException("Missing or invalid $field")
    }

private fun Map<*, *>.requiredTimestamp(field: String): Timestamp =
    requireNotNull(this[field] as? Timestamp) { "Missing or invalid $field" }

private fun Map<*, *>.optionalTimestamp(field: String): Timestamp? {
  val value = this[field]
  require(value == null || value is Timestamp) { "Invalid $field" }
  return value
}

private fun Map<*, *>.requiredList(field: String): List<*> =
    requireNotNull(this[field] as? List<*>) { "Missing or invalid $field" }

private inline fun <reified T : Enum<T>> Map<*, *>.requiredEnum(field: String): T {
  val name = requiredString(field)
  return requireNotNull(enumValues<T>().find { it.name == name }) { "Invalid $field: $name" }
}
