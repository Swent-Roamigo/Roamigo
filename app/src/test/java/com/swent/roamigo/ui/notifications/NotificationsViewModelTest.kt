// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationEvent
import com.swent.roamigo.model.notification.NotificationRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = ViewModelStore()
  private val account = AuthenticatedUser("recipient", "Recipient", null, null)
  private val auth = FakeAuthRepository(account, initiallySignedIn = true)
  private val repository = ControlledNotifications()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun showsLoadingThenEmptyThenLiveEntriesInRepositoryOrder() =
      runTest(dispatcher) {
        val model = viewModel()
        assertEquals(NotificationsLoadState.LOADING, model.uiState.value.loadState)
        runCurrent()
        repository.emit()
        runCurrent()
        assertEquals(
            NotificationsUiState(loadState = NotificationsLoadState.READY),
            model.uiState.value,
        )

        val older = notification("older", second = 10)
        val newer = notification("newer", second = 20)
        repository.emit(newer, older)
        runCurrent()

        assertEquals(listOf(newer, older), model.uiState.value.notifications)
        model.retry()
        runCurrent()
        assertEquals(1, repository.observationStarts)
        assertEquals(1, repository.activeObservers)
      }

  @Test
  fun retryRestartsFailedObservationOnceAndRetainsTheLastInbox() =
      runTest(dispatcher) {
        val previous = notification("previous")
        val model = viewModel()
        repository.emit(previous)
        runCurrent()
        repository.failObservation()
        runCurrent()

        assertEquals(NotificationsLoadState.FAILED, model.uiState.value.loadState)
        assertEquals(listOf(previous), model.uiState.value.notifications)
        assertEquals(0, repository.activeObservers)

        model.retry()
        model.retry()
        assertEquals(NotificationsLoadState.LOADING, model.uiState.value.loadState)
        assertEquals(listOf(previous), model.uiState.value.notifications)
        runCurrent()
        assertEquals(2, repository.observationStarts)
        val latest = notification("latest")
        repository.emit(latest)
        runCurrent()

        assertEquals(NotificationsLoadState.READY, model.uiState.value.loadState)
        assertEquals(listOf(latest), model.uiState.value.notifications)
        assertEquals(1, repository.activeObservers)
      }

  @Test
  fun unexpectedObservationCompletionBecomesARetryableLoadFailure() =
      runTest(dispatcher) {
        val model = viewModel()
        repository.finishObservation()
        runCurrent()

        assertEquals(NotificationsLoadState.FAILED, model.uiState.value.loadState)
        assertTrue(model.uiState.value.notifications.isEmpty())
        assertEquals(0, repository.activeObservers)
      }

  @Test
  fun markReadIgnoresInvalidSelectionsAndWaitsForConfirmedState() =
      runTest(dispatcher) {
        val unread = notification("unread")
        val read = notification("read").copy(isRead = true)
        val write = CompletableDeferred<Unit>()
        repository.onMarkRead = { write.await() }
        val model = viewModel()
        repository.emit(unread, read)
        runCurrent()

        model.markAsRead("missing")
        model.markAsRead(read.uid)
        model.markAsRead(unread.uid)
        model.markAsRead(unread.uid)
        runCurrent()
        assertEquals(listOf(unread.uid), repository.writes)
        assertEquals(setOf(unread.uid), model.uiState.value.markingReadIds)
        assertFalse(model.uiState.value.notifications.first().isRead)

        write.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.markingReadIds.isEmpty())
        assertFalse(model.uiState.value.notifications.first().isRead)

        repository.emit(unread.copy(isRead = true), read)
        runCurrent()
        assertTrue(model.uiState.value.notifications.all { it.isRead })
      }

  @Test
  fun markReadFailureCanBeRetriedForTheAffectedEntry() =
      runTest(dispatcher) {
        val unread = notification("unread")
        val other = notification("other")
        repository.onMarkRead = { throw IllegalStateException("Write failed") }
        val model = viewModel()
        repository.emit(unread, other)
        runCurrent()

        model.markAsRead(unread.uid)
        model.markAsRead(other.uid)
        runCurrent()
        assertEquals(setOf(unread.uid, other.uid), model.uiState.value.failedReadIds)
        assertTrue(model.uiState.value.markingReadIds.isEmpty())
        assertEquals(listOf(unread, other), model.uiState.value.notifications)

        repository.onMarkRead = {}
        model.markAsRead(unread.uid)
        runCurrent()
        assertEquals(listOf(unread.uid, other.uid, unread.uid), repository.writes)
        assertEquals(setOf(other.uid), model.uiState.value.failedReadIds)

        repository.emit(unread.copy(isRead = true))
        runCurrent()
        assertTrue(model.uiState.value.failedReadIds.isEmpty())
      }

  @Test
  fun failedWriteForAnEntryAlreadyReadByAnotherClientIsDismissed() =
      runTest(dispatcher) {
        val item = notification("unread")
        val write = CompletableDeferred<Unit>()
        repository.onMarkRead = { write.await() }
        val model = viewModel()
        repository.emit(item)
        runCurrent()
        model.markAsRead(item.uid)
        runCurrent()

        repository.emit(item.copy(isRead = true))
        runCurrent()
        write.completeExceptionally(IllegalStateException("Write response lost"))
        runCurrent()

        assertTrue(model.uiState.value.notifications.single().isRead)
        assertTrue(model.uiState.value.failedReadIds.isEmpty())
        assertTrue(model.uiState.value.markingReadIds.isEmpty())
      }

  @Test
  fun accountChangeBeforeAQueuedMarkReadSkipsTheWrite() =
      runTest(dispatcher) {
        val item = notification("unread")
        val model = viewModel()
        repository.emit(item)
        runCurrent()
        model.markAsRead(item.uid)
        auth.setCurrentAccount(account.copy(uid = "other"))
        runCurrent()

        assertTrue(repository.writes.isEmpty())
        assertEquals(
            NotificationsUiState(loadState = NotificationsLoadState.SESSION_ENDED),
            model.uiState.value,
        )
      }

  @Test
  fun signOutCancelsAnActiveWriteAndClearsTheInbox() =
      runTest(dispatcher) {
        val pending = CompletableDeferred<Unit>()
        val writeStopped = CompletableDeferred<Unit>()
        repository.onMarkRead = {
          try {
            pending.await()
          } finally {
            writeStopped.complete(Unit)
          }
        }
        val model = viewModel()
        val item = notification("unread")
        repository.emit(item)
        runCurrent()
        model.markAsRead(item.uid)
        runCurrent()
        assertEquals(setOf(item.uid), model.uiState.value.markingReadIds)

        auth.signOut()
        runCurrent()
        assertTrue(writeStopped.isCompleted)
        assertEquals(
            NotificationsUiState(loadState = NotificationsLoadState.SESSION_ENDED),
            model.uiState.value,
        )
        assertEquals(0, repository.activeObservers)
      }

  @Test
  fun switchingAccountsClearsAnInboxEvenWhenItsListenerHasFailed() =
      runTest(dispatcher) {
        val item = notification("unread")
        val model = viewModel()
        repository.emit(item)
        runCurrent()
        repository.failObservation()
        runCurrent()
        assertEquals(listOf(item), model.uiState.value.notifications)

        auth.setCurrentAccount(account.copy(uid = "other"))
        runCurrent()
        assertEquals(
            NotificationsUiState(loadState = NotificationsLoadState.SESSION_ENDED),
            model.uiState.value,
        )

        auth.setCurrentAccount(account)
        model.retry()
        model.markAsRead(item.uid)
        runCurrent()
        assertEquals(1, repository.observationStarts)
        assertEquals(NotificationsLoadState.SESSION_ENDED, model.uiState.value.loadState)
        assertTrue(repository.writes.isEmpty())
      }

  @Test
  fun signedOutConstructionSkipsTheInbox() =
      runTest(dispatcher) {
        auth.signOut()
        val model = viewModel()
        runCurrent()

        assertEquals(
            NotificationsUiState(loadState = NotificationsLoadState.SESSION_ENDED),
            model.uiState.value,
        )
        assertEquals(0, repository.observationStarts)
      }

  @Test
  fun losingSessionObservationClearsPrivateState() =
      runTest(dispatcher) {
        val loseSession = CompletableDeferred<Unit>()
        val failingAuth =
            object : AuthRepository by auth {
              override fun observeCurrentUser(): Flow<AuthenticatedUser?> = flow {
                emit(account)
                loseSession.await()
                error("Account observation failed")
              }
            }
        val model = viewModel(failingAuth)
        repository.emit(notification("unread"))
        runCurrent()
        assertEquals(1, model.uiState.value.notifications.size)

        loseSession.complete(Unit)
        runCurrent()
        assertEquals(
            NotificationsUiState(loadState = NotificationsLoadState.SESSION_ENDED),
            model.uiState.value,
        )
        assertEquals(0, repository.activeObservers)
      }

  private fun viewModel(authRepository: AuthRepository = auth): NotificationsViewModel =
      ViewModelProvider(
          store,
          NotificationsViewModel.factory(repository, authRepository, account.uid),
      )[NotificationsViewModel::class.java]

  private fun notification(id: String, second: Long = 0) =
      Notification(
          uid = id,
          recipientUserId = account.uid,
          event = NotificationEvent.FriendRequestReceived("request-$id", "sender"),
          createdAt = Instant.ofEpochSecond(second),
          isRead = false,
      )

  private class ControlledNotifications : NotificationRepository {
    private val updates = Channel<Result<List<Notification>>>(Channel.UNLIMITED)
    var observationStarts = 0
      private set

    var activeObservers = 0
      private set

    val writes = mutableListOf<String>()
    var onMarkRead: suspend (String) -> Unit = {}

    fun emit(vararg items: Notification) {
      check(updates.trySend(Result.success(items.toList())).isSuccess)
    }

    fun failObservation() {
      check(updates.trySend(Result.failure(IllegalStateException("Observation failed"))).isSuccess)
    }

    fun finishObservation() {
      updates.close()
    }

    override fun observeNotifications(): Flow<List<Notification>> = flow {
      observationStarts++
      activeObservers++
      try {
        for (update in updates) emit(update.getOrThrow())
      } finally {
        activeObservers--
      }
    }

    override fun observeUnreadCount(): Flow<Int> = emptyFlow()

    override suspend fun markAsRead(notificationId: String) {
      writes += notificationId
      onMarkRead(notificationId)
    }
  }
}
