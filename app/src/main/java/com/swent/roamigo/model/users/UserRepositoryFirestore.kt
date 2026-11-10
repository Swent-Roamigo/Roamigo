// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.security.MessageDigest
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
 * Name search uses a case-insensitive prefix. Email search uses an exact SHA-256 lookup. Public
 * profiles never contain raw email addresses.
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
          .flatMap { batch ->
            batch
                .map { id ->
                  async {
                    try {
                      db.collection(USERS).document(id).get(Source.DEFAULT).await()
                    } catch (error: FirebaseFirestoreException) {
                      if (
                          error.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                              error.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                      ) {
                        null
                      } else {
                        throw error
                      }
                    }
                  }
                }
                .awaitAll()
          }
    }

    ensureSession(user)

    val profiles = snapshots.filterNotNull().filter { it.exists() }.map(::parseProfile)

    if (user.uid in userIds) {
      profiles + user.toUser()
    } else {
      profiles
    }
  }

  override suspend fun searchUsers(query: String): List<User> = operation { user ->
    val text = query.trim().lowercase(Locale.ROOT)

    require(text.length <= if ('@' in text) MAX_EMAIL else MAX_NAME) { "Search query is too long" }

    if (text.isEmpty()) return@operation emptyList()

    val snapshots =
        if ('@' in text) {
          val index = db.collection(EMAIL_INDEX).document(sha256(text)).get(Source.SERVER).await()

          if (!index.exists()) {
            emptyList()
          } else {
            val id = index.getString("uid")

            if (id.isNullOrBlank() || '/' in id || id == "." || id == "..") {
              throw UserRepositoryException.InvalidData()
            }

            listOf(db.collection(USERS).document(id).get(Source.SERVER).await())
          }
        } else {
          db.collection(USERS)
              .orderBy("nameKey")
              .startAt(text)
              .endAt("$text\uf8ff")
              .limit(MAX_RESULTS)
              .get(Source.SERVER)
              .await()
              .documents
        }

    ensureSession(user)

    snapshots.filter { it.exists() }.map(::parseProfile).distinctBy(User::uid)
  }

  override suspend fun publishCurrentUser(): Unit = operation { user ->
    // Refresh the account and its token before writing verified-email indexes.
    user.reload().await()
    user.getIdToken(true).await()
    ensureSession(user)
    val publicUser = user.toUser()
    val name = publicUser.displayName.trim().take(MAX_NAME).trim()
    val photo =
        publicUser.profilePictureUrl?.takeIf { it.length <= 2048 && it.startsWith("https://") }

    val newHash =
        user.email
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { user.isEmailVerified && it.isNotBlank() }
            ?.let(::sha256)

    val profiles = db.collection(USERS)
    val metadata = db.collection(SEARCH_METADATA).document(user.uid)
    val lookups = db.collection(EMAIL_INDEX)

    db.runTransaction { transaction ->
          ensureSession(user)

          val oldHash = transaction.get(metadata).getString("emailHash")

          val oldReference = oldHash?.takeIf { it != newHash }?.let(lookups::document)

          val oldSnapshot = oldReference?.let(transaction::get)

          val newReference = newHash?.let(lookups::document)

          val newSnapshot = newReference?.let(transaction::get)

          if (newSnapshot?.exists() == true && newSnapshot.getString("uid") != user.uid) {
            throw UserRepositoryException.Conflict()
          }

          if (oldSnapshot?.exists() == true && oldSnapshot.getString("uid") != user.uid) {
            throw UserRepositoryException.Conflict()
          }

          ensureSession(user)

          transaction.set(
              profiles.document(user.uid),
              mapOf(
                  "uid" to user.uid,
                  "displayName" to name,
                  "nameKey" to name.lowercase(Locale.ROOT),
                  "photoUrl" to photo,
                  "updatedAt" to FieldValue.serverTimestamp(),
              ),
          )

          transaction.set(
              metadata,
              mapOf(
                  "emailHash" to newHash,
                  "updatedAt" to FieldValue.serverTimestamp(),
              ),
          )

          if (oldSnapshot?.exists() == true) {
            transaction.delete(requireNotNull(oldReference))
          }

          if (newReference != null) {
            transaction.set(
                newReference,
                mapOf("uid" to user.uid),
            )
          }

          Unit
        }
        .await()
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

      return User(uid, name, photo as String?)
    } catch (error: UserRepositoryException.InvalidData) {
      throw error
    } catch (error: RuntimeException) {
      throw UserRepositoryException.InvalidData(error)
    }
  }

  private fun FirebaseUser.toUser(): User {
    val name = displayName?.trim()?.takeIf(String::isNotEmpty) ?: "Traveler"

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

  private fun sha256(value: String): String =
      MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
          ""
      ) {
        "%02x".format(it.toInt() and 255)
      }

  private companion object {
    const val USERS = "users"
    const val EMAIL_INDEX = "userEmailLookups"
    const val SEARCH_METADATA = "userSearchMetadata"

    const val MAX_NAME = 120
    const val MAX_EMAIL = 254
    const val MAX_RESULTS = 20L
  }
}
