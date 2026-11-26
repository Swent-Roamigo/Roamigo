package com.swent.roamigo

import org.junit.Assert.fail
import org.junit.Test

/** Throwaway: fails on purpose to show the retry of a failed shard in CI. Reverted right after. */
class ThrowawayRetryCheckTest {
  @Test
  fun failsOnPurpose() {
    fail("Throwaway failure that shows the first attempt upload, the retry and a failing job")
  }
}
