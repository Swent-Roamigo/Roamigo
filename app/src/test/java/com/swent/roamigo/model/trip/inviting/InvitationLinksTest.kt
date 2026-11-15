// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

import org.junit.Assert.assertEquals
import org.junit.Test

class InvitationLinksTest {
  @Test
  fun aLinkHoldsTheTokenAfterTheSchemeAndHost() {
    assertEquals(
        "roamigo://invite/AbCdEfGh_ijkLMNo-pQrSt",
        InvitationLinks.linkFor("AbCdEfGh_ijkLMNo-pQrSt"),
    )
  }
}
