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
import com.google.firebase.firestore.Transaction
import com.google.firebase.functions.FirebaseFunctions
import com.swent.roamigo.model.FUNCTIONS_REGION
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
    private val reclaimEmailIndex: suspend (String) -> Unit = { email ->
      FirebaseFunctions.getInstance(FUNCTIONS_REGION)
          .getHttpsCallable("reclaimEmailLookup")
          .call(mapOf("email" to email))
          .await()
      Unit
    },
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

    require(text.length <= if ('@' in text) MAX_EMAIL else MAX_NAME) { "Search query is too long" }
    if (text.isEmpty()) return@operation emptyList()

    val emailSnapshots =
        if ('@' in text) {
          findProfilesByEmail(text)
        } else {
          emptyList()
        }

    val nameSnapshots =
        db.collection(USERS)
            .orderBy("nameKey")
            .startAt(text)
            .endAt("$text\uf8ff")
            .limit(MAX_RESULTS)
            .get(Source.SERVER)
            .await()
            .documents

    ensureSession(user)

    (emailSnapshots + nameSnapshots)
        .filter { it.exists() }
        .mapNotNull(::parseValidProfile)
        .distinctBy(User::uid)
        .take(MAX_RESULTS.toInt())
  }

  override suspend fun publishCurrentUser(): Unit = operation { user ->
    user.reload().await()
    user.getIdToken(true).await()
    ensureSession(user)
    val publicUser = user.toUser()
    val name = publicUser.displayName.trim().take(MAX_NAME).trim()

    val photo =
        publicUser.profilePictureUrl?.takeIf { it.length <= 2048 && it.startsWith("https://") }

    val verifiedEmail =
        user.email?.trim()?.lowercase(Locale.ROOT)?.takeIf {
          user.isEmailVerified && it.isNotBlank()
        }

    val newHash = verifiedEmail?.let(::sha256)

    suspend fun publish() {
      db.runTransaction { transaction ->
            updateDirectoryInTransaction(transaction, user, name, photo, newHash)
          }
          .await()
    }

    try {
      publish()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      // Only an existing lookup owned by someone else triggers recovery.
      if (verifiedEmail == null || mapRepositoryError(error) !is UserRepositoryException.Conflict) {
        throw error
      }

      reclaimEmailIndex(verifiedEmail)
      ensureSession(user)

      // Retry after the trusted backend has reassigned the index.
      publish()
    }
  }

  private fun updateDirectoryInTransaction(
      transaction: Transaction,
      user: FirebaseUser,
      name: String,
      photo: String?,
      newHash: String?,
  ) {
    ensureSession(user)

    val metadata = db.collection(SEARCH_METADATA).document(user.uid)
    val lookups = db.collection(EMAIL_INDEX)

    val oldHash = transaction.get(metadata).getString("emailHash")

    // Do not publish an unnamed account without an existing index to maintain.
    if (name.isEmpty() && oldHash == null) return

    val oldReference = oldHash?.takeIf { it != newHash }?.let(lookups::document)
    val oldSnapshot = oldReference?.let(transaction::get)

    val newReference = newHash?.let(lookups::document)
    val newSnapshot = newReference?.let(transaction::get)

    if (newSnapshot?.exists() == true && newSnapshot.getString("uid") != user.uid) {
      throw UserRepositoryException.Conflict()
    }

    ensureSession(user)

    if (name.isNotEmpty()) {
      transaction.set(
          db.collection(USERS).document(user.uid),
          mapOf(
              "uid" to user.uid,
              "displayName" to name,
              "nameKey" to name.lowercase(Locale.ROOT),
              "photoUrl" to photo,
              "updatedAt" to FieldValue.serverTimestamp(),
          ),
      )
    }

    transaction.set(
        metadata,
        mapOf(
            "emailHash" to newHash,
            "updatedAt" to FieldValue.serverTimestamp(),
        ),
    )

    if (
        oldReference != null &&
            oldSnapshot?.exists() == true &&
            oldSnapshot.getString("uid") == user.uid
    ) {
      transaction.delete(oldReference)
    }

    if (
        newReference != null &&
            (newSnapshot?.exists() != true || newSnapshot.getString("uid") != user.uid)
    ) {
      transaction.set(newReference, mapOf("uid" to user.uid))
    }
  }

  private suspend fun findProfilesByEmail(email: String): List<DocumentSnapshot> {
    val index = db.collection(EMAIL_INDEX).document(sha256(email)).get(Source.SERVER).await()

    if (!index.exists()) return emptyList()

    val uid = index.getString("uid")
    if (uid.isNullOrBlank() || '/' in uid || uid == "." || uid == "..") {
      throw UserRepositoryException.InvalidData()
    }

    return listOf(db.collection(USERS).document(uid).get(Source.SERVER).await())
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
          throw mapRepositoryError(error)
        }
      }

  private fun mapRepositoryError(error: Exception): UserRepositoryException {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()

    val domainFailure = causes.filterIsInstance<UserRepositoryException>().firstOrNull()

    if (domainFailure != null) return domainFailure

    val authFailure = causes.firstOrNull {
      it is FirebaseNetworkException || it is FirebaseAuthInvalidUserException
    }

    when (authFailure) {
      is FirebaseNetworkException -> return UserRepositoryException.Network(error)
      is FirebaseAuthInvalidUserException -> return UserRepositoryException.NotAuthenticated(error)
    }

    val firestoreFailure = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()

    return when (firestoreFailure?.code) {
      FirebaseFirestoreException.Code.UNAUTHENTICATED ->
          UserRepositoryException.NotAuthenticated(error)

      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
          UserRepositoryException.PermissionDenied(error)

      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> UserRepositoryException.Network(error)

      else -> UserRepositoryException.Unknown(error)
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

    const val MAX_NAME = MAX_USER_NAME_LENGTH
    const val MAX_EMAIL = MAX_USER_EMAIL_LENGTH

    const val MAX_RESULTS = 20L
  }
}
