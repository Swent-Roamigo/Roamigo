// AI assistance was used for test development and code review.
package com.swent.roamigo.model.users

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Controlled Firebase SDK clients; no live Firebase connection is used. */
class UserRepositoryFirestoreTest {

  private val db = mock(FirebaseFirestore::class.java)
  private val auth = mock(FirebaseAuth::class.java)
  private val alice = mock(FirebaseUser::class.java)
  private val users = mock(CollectionReference::class.java)
  private val reference = mock(DocumentReference::class.java)
  private val document = mock(DocumentSnapshot::class.java)

  private var currentUser: FirebaseUser? = alice
  private var failure: Exception? = null
  private var exists = true
  private var uid: String? = "bob"
  private var name: String? = "Bob"
  private var nameKey: String? = "bob"
  private var photo: Any? = null

  private val repository by lazy { UserRepositoryFirestore(db, auth, Dispatchers.Unconfined) }

  @Before
  fun setUp() {
    `when`(alice.uid).thenReturn("alice")
    `when`(alice.displayName).thenReturn("Alice")
    `when`(auth.currentUser).thenAnswer { currentUser }

    `when`(db.collection("users")).thenReturn(users)
    `when`(users.document("bob")).thenReturn(reference)

    `when`(reference.get(Source.DEFAULT)).thenAnswer {
      val error = failure
      if (error != null) {
        Tasks.forException<DocumentSnapshot>(error)
      } else {
        Tasks.forResult(document)
      }
    }

    `when`(document.exists()).thenAnswer { exists }
    `when`(document.id).thenReturn("bob")
    `when`(document.getString("uid")).thenAnswer { uid }
    `when`(document.getString("displayName")).thenAnswer { name }
    `when`(document.getString("nameKey")).thenAnswer { nameKey }
    `when`(document.get("photoUrl")).thenAnswer { photo }
  }

  private fun invalidProfileSnapshot(): DocumentSnapshot {
    val invalid = mock(DocumentSnapshot::class.java)

    `when`(invalid.exists()).thenReturn(true)
    `when`(invalid.id).thenReturn("broken")
    `when`(invalid.getString("uid")).thenReturn("wrong-uid")

    return invalid
  }

  @Test
  fun getUsersKeepsValidProfilesWhenAnotherIsMalformed() = runBlocking {
    val brokenSnapshot = invalidProfileSnapshot()
    val brokenReference = mock(DocumentReference::class.java)

    `when`(users.document("broken")).thenReturn(brokenReference)
    `when`(brokenReference.get(Source.DEFAULT)).thenReturn(Tasks.forResult(brokenSnapshot))

    val result = repository.getUsers(setOf("bob", "broken"))

    assertEquals(
        listOf(User("bob", "Bob", null)),
        result,
    )
  }

  @Test
  fun searchKeepsValidProfilesWhenAnotherIsMalformed() = runBlocking {
    val brokenSnapshot = invalidProfileSnapshot()
    val query = mock(Query::class.java)
    val results = mock(QuerySnapshot::class.java)

    `when`(users.orderBy("nameKey")).thenReturn(query)
    `when`(query.startAt("bo")).thenReturn(query)
    `when`(query.endAt("bo\uf8ff")).thenReturn(query)
    `when`(query.limit(20L)).thenReturn(query)
    `when`(query.get(Source.SERVER)).thenReturn(Tasks.forResult(results))

    `when`(results.documents).thenReturn(listOf(document, brokenSnapshot))

    val found = repository.searchUsers(" BO ")

    assertEquals(
        listOf(User("bob", "Bob", null)),
        found,
    )
  }

  @Test
  fun readsValidProfilesAndIncludesCurrentUser() = runBlocking {
    assertEquals(
        listOf(User("bob", "Bob", null)),
        repository.getUsers(setOf("bob")),
    )

    assertEquals(
        setOf("alice", "bob"),
        repository.getUsers(setOf("alice", "bob")).map { it.uid }.toSet(),
    )

    assertEquals(
        listOf(User("alice", "Alice", null)),
        repository.getUsers(setOf("alice")),
    )

    assertEquals(
        emptyList<User>(),
        repository.getUsers(emptySet()),
    )
  }

  @Test
  fun missingProfilesAreIgnored() = runBlocking {
    exists = false

    assertTrue(repository.getUsers(setOf("bob")).isEmpty())
  }

  @Test
  fun malformedProfilesAreSkipped() = runBlocking {
    val invalidProfiles: List<() -> Unit> =
        listOf(
            { uid = "another-user" },
            { name = null },
            { name = "" },
            { name = " Bob" },
            { name = "B".repeat(121) },
            { nameKey = "incorrect" },
            { photo = 123 },
            { photo = "http://example.com/avatar.png" },
            { photo = "https://" + "x".repeat(2049) },
        )

    for (makeInvalid in invalidProfiles) {
      uid = "bob"
      name = "Bob"
      nameKey = "bob"
      photo = null

      makeInvalid()

      assertEquals(
          "Malformed profiles should be skipped",
          emptyList<User>(),
          repository.getUsers(setOf("bob")),
      )
    }
  }

  @Test
  fun malformedFirestoreTypesAreSkipped() = runBlocking {
    doThrow(ClassCastException("Wrong Firestore field type")).`when`(document).getString("uid")

    assertEquals(
        emptyList<User>(),
        repository.getUsers(setOf("bob")),
    )
  }

  @Test
  fun firestoreFailuresAreMappedToRepositoryErrors() = runBlocking {
    val cases =
        listOf(
            FirebaseFirestoreException.Code.UNAUTHENTICATED to
                UserRepositoryException.NotAuthenticated::class.java,
            FirebaseFirestoreException.Code.PERMISSION_DENIED to
                UserRepositoryException.PermissionDenied::class.java,
            FirebaseFirestoreException.Code.UNKNOWN to UserRepositoryException.Unknown::class.java,
        )

    for ((code, expectedType) in cases) {
      failure = FirebaseFirestoreException("Controlled failure", code)

      val error = runCatching {
        repository.getUsers(setOf("bob"))
      }
          .exceptionOrNull()

      assertTrue(
          "Expected ${expectedType.simpleName} for $code, got $error",
          expectedType.isInstance(error),
      )
    }
  }

  @Test
  fun unavailableProfilesDoNotBreakMemberLookup() = runBlocking {
    for (code in
        listOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        )) {
      failure = FirebaseFirestoreException("Offline", code)

      assertTrue(repository.getUsers(setOf("bob")).isEmpty())
    }
  }

  @Test
  fun changedAuthenticationSessionRejectsResults() = runBlocking {
    val anotherUser = mock(FirebaseUser::class.java)

    `when`(reference.get(Source.DEFAULT)).thenAnswer {
      currentUser = anotherUser
      Tasks.forResult(document)
    }

    val error = runCatching {
      repository.getUsers(setOf("bob"))
    }
        .exceptionOrNull()

    assertTrue(error is UserRepositoryException.NotAuthenticated)
  }

  @Test
  fun cancelledFirestoreOperationsPreserveCancellation() = runBlocking {
    doReturn(Tasks.forCanceled<DocumentSnapshot>()).`when`(reference).get(Source.DEFAULT)

    val error = runCatching {
      repository.getUsers(setOf("bob"))
    }
        .exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  @Test
  fun missingOrBlankAccountNameReturnsEmptyDisplayName() = runBlocking {
    for (name in listOf<String?>(null, "   ")) {
      doReturn(name).`when`(alice).displayName

      assertEquals(
          User("alice", "", null),
          repository.getCurrentUser(),
      )
    }
  }

  @Test
  fun signedOutUserCannotReadCurrentProfile() = runBlocking {
    currentUser = null

    val error = runCatching {
      repository.getCurrentUser()
    }
        .exceptionOrNull()

    assertTrue(error is UserRepositoryException.NotAuthenticated)
  }

  @Test
  fun firebaseAuthReloadFailuresAreMappedCorrectly() = runBlocking {
    val cases =
        listOf(
            FirebaseNetworkException("Offline") to UserRepositoryException.Network::class.java,
            FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "Disabled") to
                UserRepositoryException.NotAuthenticated::class.java,
        )

    for ((cause, expectedType) in cases) {
      doReturn(Tasks.forException<Void>(cause)).`when`(alice).reload()

      val error = runCatching { repository.publishCurrentUser() }.exceptionOrNull()

      assertTrue("Expected ${expectedType.simpleName}, got $error", expectedType.isInstance(error))
    }
  }
}
