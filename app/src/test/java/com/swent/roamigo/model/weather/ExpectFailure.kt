// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import org.junit.Assert.assertTrue

/**
 * Runs [action] and returns the exception it throws, which must be a [T]. It is suspending, so a
 * test can call it inside `runTest` without nesting `runBlocking`.
 */
internal suspend inline fun <reified T : Throwable> expectFailure(action: suspend () -> Unit): T {
  val failure = runCatching { action() }.exceptionOrNull()
  assertTrue("Expected ${T::class.java.simpleName}, got $failure", failure is T)
  return failure as T
}
