// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(name = "Welcome", showBackground = true, widthDp = 390, heightDp = 796)
@Composable
private fun AuthenticationScreenPreview() {
  RoamigoTheme { AuthenticationScreen(AuthenticationUiState(), onSignInClick = {}) }
}

@Preview(name = "Sign-in failed", showBackground = true, widthDp = 390, heightDp = 796)
@Composable
private fun AuthenticationFailurePreview() {
  RoamigoTheme {
    AuthenticationScreen(AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_failed), {})
  }
}

@Preview(name = "Sign-in cancelled", showBackground = true, widthDp = 390, heightDp = 796)
@Composable
private fun AuthenticationCancellationPreview() {
  RoamigoTheme {
    AuthenticationScreen(
        AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_cancelled),
        {},
    )
  }
}
