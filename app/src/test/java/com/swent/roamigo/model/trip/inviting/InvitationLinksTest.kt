// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvitationLinksTest {
  private val token = "AbCdEfGh_ijkLMNo-pQrSt"

  @Test
  fun aLinkHoldsTheTokenAndIsReadBack() {
    val link = InvitationLinks.linkFor(token)

    assertEquals("roamigo://invite/$token", link)
    assertEquals(token, InvitationLinks.tokenFrom(link))
  }

  @Test
  fun aBareCodeIsAccepted() {
    assertEquals(token, InvitationLinks.tokenFrom(token))
  }

  @Test
  fun spacesAroundAPastedLinkAreIgnored() {
    assertEquals(token, InvitationLinks.tokenFrom("  roamigo://invite/$token \n"))
  }

  @Test
  fun aQueryOrFragmentAfterTheTokenIsDropped() {
    assertEquals(token, InvitationLinks.tokenFrom("roamigo://invite/$token?utm=chat#top"))
  }

  @Test
  fun theSchemeIsMatchedWithoutRegardToCase() {
    assertEquals(token, InvitationLinks.tokenFrom("ROAMIGO://INVITE/$token"))
  }

  @Test
  fun textThatIsNotATokenIsRejected() {
    assertNull(InvitationLinks.tokenFrom(""))
    assertNull(InvitationLinks.tokenFrom("   "))
    assertNull(InvitationLinks.tokenFrom("hello"))
    assertNull(InvitationLinks.tokenFrom("https://example.com/invite/$token"))
    assertNull(InvitationLinks.tokenFrom("roamigo://invite/"))
    assertNull(InvitationLinks.tokenFrom("roamigo://other/$token"))
    assertNull(InvitationLinks.tokenFrom("$token and more words"))
    assertNull(InvitationLinks.tokenFrom("a".repeat(65)))
  }
}
