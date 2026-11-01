// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.AuthErrorBody
import com.swent.roamigo.ui.theme.AuthErrorBorder
import com.swent.roamigo.ui.theme.AuthErrorSurface
import com.swent.roamigo.ui.theme.AuthErrorTitle
import com.swent.roamigo.ui.theme.AuthNoticeBorder
import com.swent.roamigo.ui.theme.AuthNoticeSurface
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
      modifier
          .testTag(AuthenticationScreenTestTags.screen)
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .safeDrawingPadding()
  ) {
    val density = LocalDensity.current
    var heroHeight by remember { mutableStateOf(0.dp) }
    val contentMinHeight = (maxHeight - heroHeight).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
      AuthenticationHero(Modifier.onSizeChanged { heroHeight = with(density) { it.height.toDp() } })
      Column(
          Modifier.fillMaxWidth()
              .heightIn(min = contentMinHeight)
              .padding(horizontal = Spacing.extraLarge, vertical = 28.dp),
          verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
              stringResource(R.string.auth_heading),
              modifier = Modifier.testTag(AuthenticationScreenTestTags.heading),
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
          )
          Text(
              stringResource(R.string.auth_description),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(Modifier.height(Spacing.extraLarge * 2))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          uiState.errorMessageRes?.let { AuthenticationFeedback(it) }
          GoogleSignInButton(uiState, onSignInClick)
          Text(
              stringResource(R.string.auth_terms),
              modifier = Modifier.fillMaxWidth(),
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style =
                  MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.85.sp),
          )
        }
      }
    }
  }
}

@Composable
private fun GoogleSignInButton(uiState: AuthenticationUiState, onSignInClick: () -> Unit) {
  OutlinedButton(
      onClick = onSignInClick,
      enabled = !uiState.isLoading && uiState.user == null,
      modifier =
          Modifier.testTag(AuthenticationScreenTestTags.signInButton)
              .fillMaxWidth()
              .heightIn(min = 60.dp),
      contentPadding = PaddingValues(horizontal = Spacing.screenPadding, vertical = 15.dp),
      shape = MaterialTheme.shapes.small,
      border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
      colors =
          ButtonDefaults.outlinedButtonColors(
              containerColor = MaterialTheme.colorScheme.surface,
              contentColor = MaterialTheme.colorScheme.onSurface,
          ),
  ) {
    if (uiState.isLoading) {
      val loadingDescription = stringResource(R.string.auth_signing_in)
      CircularProgressIndicator(
          Modifier.testTag(AuthenticationScreenTestTags.loadingIndicator).size(22.dp).semantics {
            contentDescription = loadingDescription
          },
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 2.dp,
      )
    } else {
      Image(
          painterResource(R.drawable.google_g),
          contentDescription = null,
          modifier = Modifier.padding(end = Spacing.small).size(20.dp),
      )
      val label =
          if (
              uiState.errorMessageRes != null &&
                  uiState.errorMessageRes != R.string.auth_sign_in_cancelled
          )
              R.string.auth_try_again
          else R.string.auth_google_sign_in
      Text(stringResource(label), textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun AuthenticationFeedback(messageRes: Int) {
  val cancelled = messageRes == R.string.auth_sign_in_cancelled
  val shape = MaterialTheme.shapes.small
  Column(
      Modifier.fillMaxWidth()
          .background(if (cancelled) AuthNoticeSurface else AuthErrorSurface, shape)
          .border(1.dp, if (cancelled) AuthNoticeBorder else AuthErrorBorder, shape)
          .semantics { liveRegion = LiveRegionMode.Polite }
          .padding(horizontal = 17.dp, vertical = 15.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
        stringResource(
            if (cancelled) R.string.auth_sign_in_cancelled else R.string.auth_error_title
        ),
        color = if (cancelled) MaterialTheme.colorScheme.onSurface else AuthErrorTitle,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    val body =
        when (messageRes) {
          R.string.auth_sign_in_cancelled -> R.string.auth_cancelled_description
          R.string.auth_sign_in_failed -> R.string.auth_error_description
          else -> messageRes
        }
    Text(
        stringResource(body),
        modifier = Modifier.testTag(AuthenticationScreenTestTags.errorMessage),
        color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else AuthErrorBody,
        style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun AuthenticationHero(modifier: Modifier = Modifier) {
  Column(
      modifier
          .fillMaxWidth()
          .clipToBounds()
          .background(MaterialTheme.colorScheme.primary)
          .padding(top = 14.dp, bottom = 36.dp),
  ) {
    TravelPhotoRow(
        listOf(
            R.drawable.auth_lisbon to R.string.auth_lisbon,
            R.drawable.auth_porto to R.string.auth_porto,
            R.drawable.auth_barcelona to R.string.auth_barcelona,
            R.drawable.auth_prague to R.string.auth_prague,
            R.drawable.auth_rome to R.string.auth_rome,
            R.drawable.auth_amsterdam to R.string.auth_amsterdam,
        ),
        offset = (-20).dp,
    )
    Spacer(Modifier.height(Spacing.medium))
    TravelPhotoRow(
        listOf(
            R.drawable.auth_belem to R.string.auth_belem,
            R.drawable.auth_market to R.string.auth_market,
            R.drawable.auth_lx_factory to R.string.auth_lx_factory,
            R.drawable.auth_monte to R.string.auth_monte,
            R.drawable.auth_tram to R.string.auth_tram,
            R.drawable.auth_bridge to R.string.auth_bridge,
        ),
        offset = (-380).dp,
    )
    Column(
        Modifier.padding(start = 28.dp, end = 28.dp, top = 17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Image(
            painterResource(R.drawable.auth_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(61.dp, 46.dp),
        )
        Image(
            painterResource(R.drawable.auth_logo_wordmark),
            contentDescription = stringResource(R.string.auth_brand),
            modifier = Modifier.size(91.dp, 24.dp),
        )
      }
      Text(
          stringResource(R.string.auth_tagline),
          color = MaterialTheme.colorScheme.onPrimary,
          style =
              MaterialTheme.typography.bodyLarge.copy(
                  fontSize = 18.sp,
                  lineHeight = 25.sp,
                  fontWeight = FontWeight.Medium,
              ),
      )
    }
  }
}

@Composable
private fun TravelPhotoRow(photos: List<Pair<Int, Int>>, offset: Dp) {
  // These deliberately cropped rows reproduce the collage without making it a scroll target.
  BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics {}) {
    val photoWidth = 150.dp
    val stride = photoWidth + Spacing.medium
    // Repeat the decorative collage far enough to cover the viewport after cropping.
    val photoCount = kotlin.math.ceil((maxWidth - offset + Spacing.medium) / stride).toInt()
    Row(
        Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
            .requiredWidth(stride * photoCount - Spacing.medium)
            .offset(x = offset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      repeat(photoCount) { index ->
        val (image, label) = photos[index % photos.size]
        Box(Modifier.size(photoWidth, 120.dp).clip(RoundedCornerShape(18.dp))) {
          Image(
              painterResource(image),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
          )
          Text(
              stringResource(label),
              color = MaterialTheme.colorScheme.onPrimary,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              modifier =
                  Modifier.align(Alignment.BottomStart)
                      .padding(Spacing.small)
                      .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                      .padding(horizontal = Spacing.small, vertical = 3.dp),
          )
        }
      }
    }
  }
}
