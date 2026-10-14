// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R

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
  BoxWithConstraints(modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
    val screenHeight = maxHeight
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = screenHeight)
    ) {
      Column(
          Modifier.fillMaxWidth()
              .background(Color(0xFF3D484F))
              .padding(horizontal = 24.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TravelPlaceholders(tileHeight = (screenHeight * 0.15f).coerceIn(64.dp, 120.dp))
        Text(
            stringResource(R.string.auth_brand),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.auth_tagline), color = Color.White, fontSize = 16.sp)
      }
      Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp)) {
        Text(
            stringResource(R.string.auth_heading),
            modifier = Modifier.testTag(AuthenticationScreenTestTags.heading),
            color = Color(0xFF303B41),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.auth_description), color = Color(0xFF687278), fontSize = 14.sp)
        Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
        uiState.errorMsg?.let {
          Text(
              it,
              color = Color(0xFFB3261E),
              modifier =
                  Modifier.testTag(AuthenticationScreenTestTags.errorMessage)
                      .padding(bottom = 12.dp)
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
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFDADCE0)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF303B41)),
        ) {
          if (uiState.isLoading) {
            val loadingDescription = stringResource(R.string.auth_signing_in)
            CircularProgressIndicator(
                Modifier.testTag(AuthenticationScreenTestTags.loadingIndicator)
                    .size(22.dp)
                    .semantics { contentDescription = loadingDescription },
                color = Color(0xFF4285F4),
                strokeWidth = 2.dp,
            )
          } else {
            Text(
                "G",
                color = Color(0xFF4285F4),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(stringResource(R.string.auth_google_sign_in))
          }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.auth_terms),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color(0xFF687278),
            fontSize = 11.sp,
        )
      }
    }
  }
}

/** Temporary decorative tiles until the travel photography is available. */
@Composable
private fun TravelPlaceholders(tileHeight: androidx.compose.ui.unit.Dp) {
  val colors = listOf(Color(0xFFABC2D1), Color(0xFFD2BB9C), Color(0xFF8FA69B))
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    repeat(2) { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { column ->
          Box(
              Modifier.weight(1f)
                  .height(tileHeight)
                  .clip(RoundedCornerShape(12.dp))
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
  AuthenticationScreen(AuthenticationUiState(), onSignInClick = {})
}
