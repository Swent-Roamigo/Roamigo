// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing

/** Connects authentication to its ViewModel and delegates successful sign-in to navigation. */
@Composable
fun AuthenticationScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthenticationViewModel = viewModel(),
    credentialManager: CredentialManager? = null,
) {
  val context = LocalContext.current
  val manager = credentialManager ?: remember(context) { CredentialManager.create(context) }
  val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
  val currentOnSignedIn by rememberUpdatedState(onSignedIn)

  LaunchedEffect(uiState.user) { uiState.user?.let { currentOnSignedIn() } }

  AuthenticationScreen(
      uiState = uiState,
      onSignInClick = { authViewModel.signIn(context, manager) },
      modifier = modifier,
  )
}

/** Renders authentication state and forwards the Google sign-in action to its owner. */
@Composable
fun AuthenticationScreen(
    uiState: AuthenticationUiState,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
      modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
  ) {
    val screenHeight = maxHeight
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = screenHeight)
    ) {
      Column(
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.primary)
              .padding(horizontal = Spacing.extraLarge, vertical = Spacing.large),
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        TravelPlaceholders(tileHeight = (screenHeight * 0.15f).coerceIn(64.dp, 120.dp))
        Text(
            stringResource(R.string.auth_brand),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            stringResource(R.string.auth_tagline),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
      }
      Column(Modifier.fillMaxWidth().weight(1f).padding(Spacing.extraLarge)) {
        Text(
            stringResource(R.string.auth_heading),
            modifier = Modifier.testTag(AuthenticationScreenTestTags.heading),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(Spacing.medium))
        Text(
            stringResource(R.string.auth_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f).heightIn(min = Spacing.extraLarge * 2))
        uiState.errorMessageRes?.let {
          Text(
              stringResource(it),
              color = MaterialTheme.colorScheme.error,
              modifier =
                  Modifier.testTag(AuthenticationScreenTestTags.errorMessage)
                      .padding(bottom = Spacing.medium)
                      .semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
        OutlinedButton(
            onClick = onSignInClick,
            enabled = !uiState.isLoading && uiState.user == null,
            modifier =
                Modifier.testTag(AuthenticationScreenTestTags.signInButton)
                    .fillMaxWidth()
                    .height(52.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
        ) {
          if (uiState.isLoading) {
            val loadingDescription = stringResource(R.string.auth_signing_in)
            CircularProgressIndicator(
                Modifier.testTag(AuthenticationScreenTestTags.loadingIndicator)
                    .size(22.dp)
                    .semantics { contentDescription = loadingDescription },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
          } else {
            Image(
                painter = painterResource(R.drawable.google_g),
                contentDescription = null,
                modifier = Modifier.padding(end = Spacing.medium).size(20.dp),
            )
            Text(stringResource(R.string.auth_google_sign_in))
          }
        }
        Spacer(Modifier.height(Spacing.large))
        Text(
            stringResource(R.string.auth_terms),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

/** Temporary decorative tiles until the travel photography is available. */
@Composable
private fun TravelPlaceholders(tileHeight: androidx.compose.ui.unit.Dp) {
  val colors =
      listOf(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.surfaceContainerHigh,
      )
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    repeat(2) { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        repeat(3) { column ->
          Box(
              Modifier.weight(1f)
                  .height(tileHeight)
                  .clip(MaterialTheme.shapes.medium)
                  .background(colors[(row + column) % colors.size]),
              contentAlignment = Alignment.Center,
          ) {
            // Decorative photo placeholders have no accessibility label.
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun AuthenticationScreenPreview() {
  RoamigoTheme { AuthenticationScreen(AuthenticationUiState(), onSignInClick = {}) }
}
