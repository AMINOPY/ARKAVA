package dev.arkava.vpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test fun acceptsMinimalXrayOutboundArray() {
        assertTrue(ProfileValidator.validateJson("""{"outbounds":[{"protocol":"freedom","tag":"direct"}]}""").isSuccess)
    }

    @Test fun rejectsMissingOutbounds() {
        assertFalse(ProfileValidator.validateJson("""{"log":{}}""").isSuccess)
    }

    @Test fun rejectsNonObjectOutbound() {
        assertFalse(ProfileValidator.validateJson("""{"outbounds":["not-an-object"]}""").isSuccess)
    }

    @Test fun acceptsSupportedShareLinkScheme() {
        assertTrue(ProfileValidator.validateShareLink("vless://example@example.com:443").isSuccess)
    }

    @Test fun rejectsMultilineShareLinks() {
        assertFalse(ProfileValidator.validateShareLink("vless://a\nvless://b").isSuccess)
    }

    @Test fun rejectsUnsupportedShareLinkScheme() {
        assertFalse(ProfileValidator.validateShareLink("unknown://example").isSuccess)
    }

    @Test fun boundsSubscriptionInput() {
        assertFalse(ProfileValidator.validateSubscriptionText(" ").isSuccess)
    }
}
