// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Authenticated directory of discoverable Roamigo profiles.
 *
 * Name search uses a case-insensitive prefix. Public profiles do not contain emails.
 */
class UserRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserRepository {

  override suspend fun getCurrentUser(): User = operation { user -> user.toUser() }

  override suspend fun getUsers(userIds: Set<String>): List<User> = operation { user ->
    userIds.forEach(::validateId)

    val snapshots = coroutineScope {
      userIds
          .filterNot { it == user.uid }
          .chunked(10)
          .flatMap { batch -> batch.map { id -> async { fetchProfileSnapshot(id) } }.awaitAll() }
    }

    ensureSession(user)

    val profiles = snapshots.filterNotNull().filter { it.exists() }.mapNotNull(::parseValidProfile)

    if (user.uid in userIds) {
      profiles + user.toUser()
    } else {
      profiles
    }
  }

  override suspend fun searchUsers(query: String): List<User> = operation { user ->
    val text = query.trim().lowercase(Locale.ROOT)
    require(text.length <= MAX_NAME) { "Search query is too long" }
    if (text.isEmpty()) return@operation emptyList()

    val snapshots =
        db.collection(USERS)
            .orderBy("nameKey")
            .startAt(text)
            .endAt("$text\uf8ff")
            .limit(MAX_RESULTS)
            .get(Source.SERVER)
            .await()
            .documents
    ensureSession(user)
    snapshots.filter { it.exists() }.mapNotNull(::parseValidProfile).distinctBy(User::uid)
  }

  override suspend fun publishCurrentUser(): Unit = operation { user ->
    user.reload().await()
    ensureSession(user)
    val publicUser = user.toUser()
    val name = publicUser.displayName.trim().take(MAX_NAME).trim()
    if (name.isEmpty()) return@operation
    val photo =
        publicUser.profilePictureUrl?.takeIf { it.length <= 2048 && it.startsWith("https://") }

    db.collection(USERS)
        .document(user.uid)
        .set(
            mapOf(
                "uid" to user.uid,
                "displayName" to name,
                "nameKey" to name.lowercase(Locale.ROOT),
                "photoUrl" to photo,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        .await()
  }

  private suspend fun fetchProfileSnapshot(id: String): DocumentSnapshot? =
      try {
        db.collection(USERS).document(id).get(Source.DEFAULT).await()
      } catch (error: FirebaseFirestoreException) {
        when (error.code) {
          FirebaseFirestoreException.Code.UNAVAILABLE,
          FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> null

          else -> throw error
        }
      }

  private fun parseValidProfile(snapshot: DocumentSnapshot): User? =
      try {
        parseProfile(snapshot)
      } catch (_: UserRepositoryException.InvalidData) {
        // One malformed profile must not prevent other users from loading.
        null
      }

  private fun parseProfile(snapshot: DocumentSnapshot): User {
    try {
      val uid = snapshot.getString("uid")
      val name = snapshot.getString("displayName")
      val key = snapshot.getString("nameKey")
      val photo = snapshot.get("photoUrl")

      if (
          uid != snapshot.id ||
              name.isNullOrBlank() ||
              name != name.trim() ||
              name.length > MAX_NAME ||
              key != name.lowercase(Locale.ROOT) ||
              (photo != null &&
                  (photo !is String || photo.length > 2048 || !photo.startsWith("https://")))
      ) {
        throw UserRepositoryException.InvalidData()
      }

      return User(uid, name, photo)
    } catch (error: UserRepositoryException.InvalidData) {
      throw error
    } catch (error: RuntimeException) {
      throw UserRepositoryException.InvalidData(error)
    }
  }

  private fun FirebaseUser.toUser(): User {
    val name = displayName?.trim().orEmpty()

    return User(
        uid = uid,
        displayName = name,
        profilePictureUrl = photoUrl?.toString(),
    )
  }

  private fun validateId(uid: String) {
    require(uid.isNotBlank() && '/' !in uid && uid != "." && uid != "..") { "Invalid user ID" }
  }

  private fun ensureSession(user: FirebaseUser) {
    if (auth.currentUser !== user) {
      throw UserRepositoryException.NotAuthenticated()
    }
  }

  private suspend fun <T> operation(action: suspend (FirebaseUser) -> T): T =
      withContext(dispatcher) {
        try {
          currentCoroutineContext().ensureActive()

          val user = auth.currentUser ?: throw UserRepositoryException.NotAuthenticated()

          val result = action(user)
          ensureSession(user)
          result
        } catch (error: CancellationException) {
          throw error
        } catch (error: IllegalArgumentException) {
          throw error
        } catch (error: UserRepositoryException) {
          throw error
        } catch (error: Exception) {
          val domainFailure =
              generateSequence(error as Throwable?) { it.cause }
                  .filterIsInstance<UserRepositoryException>()
                  .firstOrNull()

          if (domainFailure != null) throw domainFailure

          val authFailure =
              generateSequence(error as Throwable?) { it.cause }
                  .firstOrNull {
                    it is FirebaseNetworkException || it is FirebaseAuthInvalidUserException
                  }

          when (authFailure) {
            is FirebaseNetworkException -> throw UserRepositoryException.Network(error)
            is FirebaseAuthInvalidUserException ->
                throw UserRepositoryException.NotAuthenticated(error)
          }

          val firestoreFailure =
              generateSequence(error as Throwable?) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()

          throw when (firestoreFailure?.code) {
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                UserRepositoryException.NotAuthenticated(error)

            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                UserRepositoryException.PermissionDenied(error)

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                UserRepositoryException.Network(error)

            else -> UserRepositoryException.Unknown(error)
          }
        }
      }

  private companion object {
    const val USERS = "users"

    const val MAX_NAME = 120
    const val MAX_RESULTS = 20L
  }
}
