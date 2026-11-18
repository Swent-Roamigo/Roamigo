// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
          SyncStatus(SyncState.Offline)
        }
      }
    }

    listOf("Synced", "Offline").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
  }

  @Test
  fun theIndicatorIsAnnouncedAsAPoliteLiveRegion() {
    composeRule.setContent {
      RoamigoTheme {
        Column {
          SyncStatus(SyncState.Offline, Modifier.testTag("pill"))
          SyncStatus(SyncState.Offline, Modifier.testTag("compact"), compact = true)
        }
      }
    }

    listOf("pill", "compact").forEach {
      composeRule
          .onNodeWithTag(it)
          .assert(
              SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
          )
    }
  }
}
