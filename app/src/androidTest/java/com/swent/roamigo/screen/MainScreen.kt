package com.swent.roamigo.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.authentication.AuthenticationScreenTestTags
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

class MainScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<MainScreen>(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag(C.Tag.main_screen_container) },
    ) {

  val title: KNode = child { hasTestTag(AuthenticationScreenTestTags.heading) }
}
