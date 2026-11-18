// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStatusTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun sayswhatEachStateMeans() {
    composeRule.setContent {
      RoamigoTheme {
        Column {
          SyncStatus(SyncState.Synced)
          SyncStatus(SyncState.Syncing)
          SyncStatus(SyncState.Offline)
        }
      }
    }

    listOf("Synced", "Syncing…", "Offline").forEach {
      composeRule.onNodeWithText(it).assertIsDisplayed()
    }
  }
}
