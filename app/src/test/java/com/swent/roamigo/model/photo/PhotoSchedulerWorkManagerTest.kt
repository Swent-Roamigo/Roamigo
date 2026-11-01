// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoSchedulerWorkManagerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var workManager: WorkManager

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    workManager = WorkManager.getInstance(context)
  }

  private fun works(): List<WorkInfo> =
      workManager.getWorkInfosForUniqueWork(PhotoSchedulerWorkManager.WORK_NAME).get()

  @Test
  fun doesNothingUntilAsked() {
    PhotoSchedulerWorkManager { workManager }

    assertTrue(works().isEmpty())
  }

  @Test
  fun queuesOneUploadThatNeedsAConnection() {
    PhotoSchedulerWorkManager { workManager }.schedule()

    val work = works().single()
    assertEquals(WorkInfo.State.ENQUEUED, work.state)
    assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
  }

  @Test
  fun aSecondRequestWaitsBehindTheFirstInsteadOfReplacingIt() {
    val scheduler = PhotoSchedulerWorkManager { workManager }

    scheduler.schedule()
    scheduler.schedule()

    // The list has no promised order: one work is ready, the other waits for it.
    assertEquals(
        setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED),
        works().map { it.state }.toSet(),
    )
    assertEquals(2, works().size)
  }

  @Test
  fun startsWorkManagerOnlyWhenAsked() {
    var created = 0
    val scheduler = PhotoSchedulerWorkManager {
      created++
      workManager
    }
    assertEquals(0, created)

    scheduler.schedule()

    assertEquals(1, created)
  }
}
