// AI assistance was used for implementation support.
package com.swent.roamigo.model.notification

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val INBOX_LIMIT = 50L
private const val TAG = "NotificationRepository"

/** Reads the inbox and updates read status using the signed-in Firebase session. */
class NotificationRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotificationRepository {

  override fun observeNotifications(): Flow<List<Notification>> = observe { it }

  override fun observeUnreadCount(): Flow<Int> = observe { inbox -> inbox.count { !it.isRead } }

  override suspend fun markAsRead(notificationId: String) {
    withContext(dispatcher) {
      val user = authenticatedUser()
      inbox(user.uid).document(validIdentifier(notificationId)).update("isRead", true).await()
    }
  }

  private fun <T> observe(project: (List<Notification>) -> T): Flow<T> = flow {
    val user = authenticatedUser()
    emitAll(
        snapshots(user)
            .map { snapshot ->
              project(snapshot.documents.mapNotNull { it.toNotificationOrNull(user.uid) })
            }
            .flowOn(dispatcher)
            .takeWhile { auth.currentUser === user }
            .distinctUntilChanged()
    )
  }

  private fun snapshots(user: FirebaseUser): Flow<QuerySnapshot> = callbackFlow {
    val accountListener = FirebaseAuth.AuthStateListener { if (it.currentUser !== user) close() }
    var registration: ListenerRegistration? = null
    try {
      auth.addAuthStateListener(accountListener)
      if (auth.currentUser !== user) {
        close()
        return@callbackFlow
      }
      registration =
          inbox(user.uid)
              .orderBy("createdAt", Query.Direction.DESCENDING)
              .limit(INBOX_LIMIT)
              .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                  auth.currentUser !== user -> close()
                  error != null -> close(error)
                  snapshot == null -> close(IllegalStateException("Missing notification snapshot"))
                  // Wait for acknowledgment because a local mark-read may be rejected.
                  !snapshot.metadata.hasPendingWrites() -> trySend(snapshot)
                }
              }
      awaitClose {}
    } finally {
      registration?.remove()
      auth.removeAuthStateListener(accountListener)
    }
  }
      .buffer(Channel.CONFLATED)

  private fun authenticatedUser(): FirebaseUser =
      checkNotNull(auth.currentUser) { "An authenticated user is required for notifications" }

  private fun inbox(userId: String) =
      db.collection("users").document(userId).collection("notifications")
}

private fun DocumentSnapshot.toNotificationOrNull(userId: String): Notification? {
  val fields = data
  val recipient = fields?.get("recipientUserId") as? String
  require(recipient == null || recipient == userId) {
    "Notification recipient does not match the inbox"
  }
  return try {
    requireNotNull(fields) { "Missing notification fields" }
    requireNotNull(recipient) { "Missing or invalid notification recipient" }
    fields.toNotification(id, recipient)
  } catch (error: IllegalArgumentException) {
    Log.w(TAG, "Skipping unreadable notification", error)
    null
  }
}

private fun Map<String, Any?>.toNotification(id: String, recipient: String): Notification {
  val event =
      when (this["type"]) {
        "FRIEND_REQUEST_RECEIVED" ->
            NotificationEvent.FriendRequestReceived(
                requiredIdentifier("requestId"),
                requiredIdentifier("senderUserId"),
            )
        "TRIP_VOTE_CREATED" ->
            NotificationEvent.TripVoteCreated(
                requiredIdentifier("tripId"),
                requiredIdentifier("voteId"),
            )
        else -> throw IllegalArgumentException("Unsupported notification type")
      }
  val createdAt = requireNotNull(this["createdAt"] as? Timestamp) { "Invalid creation timestamp" }
  return Notification(
      uid = validIdentifier(id),
      recipientUserId = recipient,
      event = event,
      createdAt = Instant.ofEpochSecond(createdAt.seconds, createdAt.nanoseconds.toLong()),
      isRead = requireNotNull(this["isRead"] as? Boolean) { "Invalid notification read status" },
  )
}

private fun Map<String, Any?>.requiredIdentifier(field: String): String =
    validIdentifier(requireNotNull(this[field] as? String) { "Missing or invalid $field" })

private fun validIdentifier(value: String): String {
  require(value.isNotBlank() && '/' !in value && value != "." && value != "..") {
    "Invalid notification identifier"
  }
  return value
}
