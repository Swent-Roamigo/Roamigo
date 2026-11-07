// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Friendship storage using the document names and transitions enforced by `firestore.rules`.
 * Dependencies are supplied by the caller. Reads and mutations require the server; observation may
 * also emit cached snapshots with resolved timestamps. Parsing runs on [dispatcher].
 */
class FriendRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FriendRepository {

  override suspend fun getFriends(): List<Friendship> = operation { user ->
    friendships(friendQuery(user.uid).get(Source.SERVER).await())
  }

  override fun observeFriends(): Flow<List<Friendship>> = flow {
    val user = authenticatedUser()
    emitAll(
        snapshots(user)
            .map { snapshot ->
              ensureSession(user)
              friendships(snapshot)
            }
            .distinctUntilChanged()
            .takeWhile { auth.currentUser === user }
    )
  }
      .flowOn(dispatcher)
      .catch { error -> throw if (error is Exception) error.asRepositoryFailure() else error }

  override suspend fun getIncomingRequests(): List<FriendRequest> = operation { user ->
    pendingRequests(user.uid, "recipientUserId")
  }

  override suspend fun getOutgoingRequests(): List<FriendRequest> = operation { user ->
    pendingRequests(user.uid, "senderUserId")
  }

  override suspend fun sendFriendRequest(otherUserId: String): FriendRequest = operation { user ->
    val reference = document(user.uid, otherUserId)
    // Keep this ID stable if Firestore retries the transaction after concurrent changes.
    val newRequestId = UUID.randomUUID().toString()
    val requestId =
        db.runTransaction { transaction ->
              ensureSession(user)
              val snapshot = transaction.get(reference)
              val existing =
                  if (snapshot.exists()) friendRequestFromFirestore(snapshot.id, snapshot.data)
                  else null
              when (existing?.status) {
                null,
                FriendRequestStatus.DECLINED -> {
                  transaction.set(reference, newRequest(user.uid, otherUserId, newRequestId))
                  newRequestId
                }
                FriendRequestStatus.PENDING -> {
                  if (existing.recipientUserId == user.uid) {
                    transaction.update(reference, response(FriendRequestStatus.ACCEPTED))
                  }
                  existing.uid
                }
                FriendRequestStatus.ACCEPTED -> existing.uid
              }
            }
            .await()
    readRequest(reference, requestId).also {
      if (it.status == FriendRequestStatus.DECLINED) throw FriendRepositoryException.Conflict()
    }
  }

  override suspend fun acceptFriendRequest(otherUserId: String, requestId: String): Friendship =
      operation { user ->
        val reference = document(user.uid, otherUserId)
        respond(user, reference, requestId, FriendRequestStatus.ACCEPTED)
        val request = readRequest(reference, requestId)
        if (request.status != FriendRequestStatus.ACCEPTED)
            throw FriendRepositoryException.Conflict()
        Friendship(
            request.uid,
            request.senderUserId,
            request.recipientUserId,
            requireNotNull(request.respondedAt),
        )
      }

  override suspend fun declineFriendRequest(otherUserId: String, requestId: String) {
    operation { user ->
      respond(user, document(user.uid, otherUserId), requestId, FriendRequestStatus.DECLINED)
    }
  }

  override suspend fun removeFriend(otherUserId: String, friendshipId: String) {
    operation { user ->
      requireFriendshipIdentifier(friendshipId)
      val reference = document(user.uid, otherUserId)
      db.runTransaction { transaction ->
            ensureSession(user)
            val snapshot = transaction.get(reference)
            if (snapshot.exists()) {
              val request = friendRequestFromFirestore(snapshot.id, snapshot.data)
              if (request.uid != friendshipId || request.status != FriendRequestStatus.ACCEPTED) {
                throw FriendRepositoryException.Conflict()
              }
              transaction.delete(reference)
            }
            Unit
          }
          .await()
    }
  }

  private suspend fun respond(
      user: FirebaseUser,
      reference: DocumentReference,
      requestId: String,
      status: FriendRequestStatus,
  ) {
    requireFriendshipIdentifier(requestId)
    try {
      db.runTransaction { transaction ->
            ensureSession(user)
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw FriendRepositoryException.Conflict()
            val request = friendRequestFromFirestore(snapshot.id, snapshot.data)
            if (request.uid != requestId) throw FriendRepositoryException.Conflict()
            if (request.recipientUserId != user.uid)
                throw FriendRepositoryException.PermissionDenied()
            if (request.status != FriendRequestStatus.PENDING && request.status != status) {
              throw FriendRepositoryException.Conflict()
            }
            if (request.status == FriendRequestStatus.PENDING) {
              transaction.update(reference, response(status))
            }
            Unit
          }
          .await()
    } catch (error: FirebaseFirestoreException) {
      if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
      // A competing response may reach the rules before this transaction's version check.
      // Reconcile with the server without submitting another write.
      ensureSession(user)
      val current = readRequest(reference, requestId)
      if (current.recipientUserId != user.uid || current.status == FriendRequestStatus.PENDING) {
        throw error
      }
      if (current.status != status) throw FriendRepositoryException.Conflict(error)
    }
  }

  private suspend fun readRequest(reference: DocumentReference, requestId: String): FriendRequest {
    val snapshot = reference.get(Source.SERVER).await()
    if (!snapshot.exists()) throw FriendRepositoryException.Conflict()
    val request = friendRequestFromFirestore(snapshot.id, snapshot.data)
    if (request.uid != requestId) throw FriendRepositoryException.Conflict()
    return request
  }

  private suspend fun pendingRequests(userId: String, direction: String): List<FriendRequest> =
      query(userId, FriendRequestStatus.PENDING)
          .whereEqualTo(direction, userId)
          .get(Source.SERVER)
          .await()
          .documents
          .map { friendRequestFromFirestore(it.id, it.data) }

  private fun friendships(snapshot: QuerySnapshot): List<Friendship> =
      snapshot.documents.map { friendshipFromFirestore(it.id, it.data) }

  private fun query(userId: String, status: FriendRequestStatus): Query =
      db.collection("friendships")
          .whereArrayContains("participantIds", userId)
          .whereEqualTo("status", status.name)

  private fun friendQuery(userId: String): Query = query(userId, FriendRequestStatus.ACCEPTED)

  private fun document(userId: String, otherUserId: String): DocumentReference =
      db.collection("friendships").document(friendshipDocumentId(userId, otherUserId))

  private fun snapshots(user: FirebaseUser): Flow<QuerySnapshot> = callbackFlow {
    val accountListener = FirebaseAuth.AuthStateListener { if (it.currentUser !== user) close() }
    var registration: ListenerRegistration? = null
    try {
      auth.addAuthStateListener(accountListener)
      ensureSession(user)
      registration =
          friendQuery(user.uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            when {
              auth.currentUser !== user -> close()
              error != null -> close(error)
              snapshot == null -> close(FriendRepositoryException.InvalidData())
              // A local acceptance has no resolved server response timestamp until acknowledged.
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

  private fun newRequest(
      senderId: String,
      recipientId: String,
      requestId: String,
  ): Map<String, Any?> =
      mapOf(
          "requestId" to requestId,
          "senderUserId" to senderId,
          "recipientUserId" to recipientId,
          "participantIds" to listOf(senderId, recipientId),
          "status" to FriendRequestStatus.PENDING.name,
          "createdAt" to FieldValue.serverTimestamp(),
          "respondedAt" to null,
      )

  private fun response(status: FriendRequestStatus): Map<String, Any> =
      mapOf("status" to status.name, "respondedAt" to FieldValue.serverTimestamp())

  private fun authenticatedUser(): FirebaseUser =
      auth.currentUser ?: throw FriendRepositoryException.NotAuthenticated()

  private fun ensureSession(user: FirebaseUser) {
    if (auth.currentUser !== user) throw FriendRepositoryException.NotAuthenticated()
  }

  private suspend fun <T> operation(action: suspend (FirebaseUser) -> T): T =
      withContext(dispatcher) {
        try {
          currentCoroutineContext().ensureActive()
          val user = authenticatedUser()
          val result = action(user)
          ensureSession(user)
          result
        } catch (error: Exception) {
          throw error.asRepositoryFailure()
        }
      }

  private fun Exception.asRepositoryFailure(): Exception {
    if (
        this is CancellationException ||
            this is FriendRepositoryException ||
            this is IllegalArgumentException
    ) {
      return this
    }
    // Firestore may wrap an exception thrown by a transaction callback.
    val domainFailure =
        generateSequence(cause) { it.cause }
            .firstOrNull { it is FriendRepositoryException || it is CancellationException }
    if (domainFailure is Exception) return domainFailure
    return when ((this as? FirebaseFirestoreException)?.code) {
      FirebaseFirestoreException.Code.UNAUTHENTICATED ->
          FriendRepositoryException.NotAuthenticated(this)
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
          FriendRepositoryException.PermissionDenied(this)
      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> FriendRepositoryException.Network(this)
      FirebaseFirestoreException.Code.ABORTED,
      FirebaseFirestoreException.Code.ALREADY_EXISTS,
      FirebaseFirestoreException.Code.NOT_FOUND -> FriendRepositoryException.Conflict(this)
      FirebaseFirestoreException.Code.DATA_LOSS -> FriendRepositoryException.InvalidData(this)
      else -> FriendRepositoryException.Unknown(this)
    }
  }
}
