// Author : Constantin Dunoyer de Segonzac
// Co-author : Claude (Anthropic)
// AI assistance was used for implementation support.
package com.swent.roamigo.model.authentication

import android.content.Context
import android.os.Bundle
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import kotlinx.coroutines.CompletableDeferred

/**
 * Answers sign-in with [credential] instead of opening Google's account picker. Tests can make a
 * request fail or wait, and check how often sign-in and sign-out reached the credential provider.
 */
internal class FakeCredentialManager(
    context: Context,
    val credential: Credential = CustomCredential("test-credential", Bundle()),
) : CredentialManager by CredentialManager.create(context) {
  /** The response returned when no [failure] or [pendingResponse] is set. */
  val response = GetCredentialResponse(credential)
  /** Number of sign-in requests received, including failed and pending ones. */
  var requests = 0
    private set

  /** Thrown by every sign-in request until cleared. */
  var failure: Exception? = null
  /** When set, sign-in waits for it instead of answering [response]. */
  var pendingResponse: CompletableDeferred<GetCredentialResponse>? = null
  /** Number of requests to clear credential state received. */
  var clearRequests = 0
    private set

  /** Thrown by every request to clear credential state, after it is counted. */
  var clearFailure: Exception? = null
  /** When set, clearing credential state waits for it. */
  var pendingClear: CompletableDeferred<Unit>? = null

  override suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
  ): GetCredentialResponse {
    requests++
    failure?.let { throw it }
    return pendingResponse?.await() ?: response
  }

  override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {
    clearRequests++
    pendingClear?.await()
    clearFailure?.let { throw it }
  }
}
