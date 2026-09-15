package com.jacolp.system.application.authorization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClientAllowedIpPolicyTest {

    @Test
    void ipv4MatchesBothPrefixBoundariesAndAcceptsANonNetworkConfiguredBase() {
        ClientAllowedIpPolicy policy = ClientAllowedIpPolicy.parse("192.0.2.7/24");

        assertThat(policy.allows("192.0.2.0")).isTrue();
        assertThat(policy.allows("192.0.2.255")).isTrue();
        assertThat(policy.allows("192.0.3.0")).isFalse();
    }

    @Test
    void ipv6MatchesPrefixBoundariesAndCompressedLiterals() {
        ClientAllowedIpPolicy policy = ClientAllowedIpPolicy.parse("2001:db8::8/125");

        assertThat(policy.allows("2001:db8::8")).isTrue();
        assertThat(policy.allows("2001:db8::f")).isTrue();
        assertThat(policy.allows("2001:db8::10")).isFalse();
    }

    @Test
    void ipv6AllowsAnEmbeddedIpv4TailOnlyAtTheEndOfTheEntireLiteral() {
        assertThat(ClientAllowedIpPolicy.parse("::ffff:192.0.2.1/128").allows("::ffff:192.0.2.1")).isTrue();
        assertThat(ClientAllowedIpPolicy.parse("2001:db8::192.0.2.1/128").allows("2001:db8::192.0.2.1"))
                .isTrue();
        assertThat(ClientAllowedIpPolicy.parse("2001:db8:0:0:0:0:192.0.2.1/128")
                .allows("2001:db8:0:0:0:0:192.0.2.1")).isTrue();
    }

    @Test
    void matchesAnyConfiguredCidrButNeverMatchesAcrossAddressFamilies() {
        ClientAllowedIpPolicy policy = ClientAllowedIpPolicy.parse("192.0.2.0/24, 2001:db8::/64");

        assertThat(policy.allows("192.0.2.9")).isTrue();
        assertThat(policy.allows("2001:db8::1")).isTrue();
        assertThat(policy.allows("2001:db9::1")).isFalse();
        assertThat(ClientAllowedIpPolicy.parse("0.0.0.0/0").allows("2001:db8::1")).isFalse();
        assertThat(ClientAllowedIpPolicy.parse("::/0").allows("192.0.2.9")).isFalse();
    }

    @Test
    void familySpecificZeroPrefixesMatchEveryLiteralOfTheirOwnFamilyAndDualStackMatchesBoth() {
        assertThat(ClientAllowedIpPolicy.parse("0.0.0.0/0").allows("203.0.113.7")).isTrue();
        assertThat(ClientAllowedIpPolicy.parse("::/0").allows("2001:db8:1::7")).isTrue();

        ClientAllowedIpPolicy dualStack = ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");
        assertThat(dualStack.allows("::1")).isTrue();
        assertThat(dualStack.allows("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(dualStack.allows("203.0.113.7")).isTrue();
    }

    @Test
    void malformedOrDuplicateConfigurationFailsClosed() {
        assertInvalidConfiguration(null);
        assertInvalidConfiguration("");
        assertInvalidConfiguration("192.0.2.1");
        assertInvalidConfiguration("192.0.2.1/33");
        assertInvalidConfiguration("192.0.2.1/024");
        assertInvalidConfiguration("2001:db8::/129");
        assertInvalidConfiguration("example.com/24");
        assertInvalidConfiguration("[::1]/128");
        assertInvalidConfiguration("fe80::1%eth0/64");
        assertInvalidConfiguration("192.0.2.0/24,");
        assertInvalidConfiguration("192.0.2.0/24, 192.0.2.0/24");
        assertInvalidConfiguration("192.0.2.1/24,192.0.2.2/24");
        assertInvalidConfiguration("2001:db8:::1/64");
        assertInvalidConfiguration("2001:db8::1::2/64");
        assertInvalidConfiguration("192.0.2.1::/64");
        assertInvalidConfiguration("2001:db8:192.0.2.1::/64");
    }

    @Test
    void malformedSocketAddressFailsClosedInsteadOfResolvingAHostName() {
        ClientAllowedIpPolicy policy = ClientAllowedIpPolicy.parse("192.0.2.0/24,2001:db8::/64");

        assertInvalidRemote(policy, "example.com");
        assertInvalidRemote(policy, "[2001:db8::1]");
        assertInvalidRemote(policy, "fe80::1%eth0");
        assertInvalidRemote(policy, "192.0.2.1/24");
        assertInvalidRemote(policy, "999.0.2.1");
        assertInvalidRemote(policy, "2001:db8:::1");
        assertInvalidRemote(policy, "192.0.2.1::");
        assertInvalidRemote(policy, "2001:db8:192.0.2.1::");
    }

    private static void assertInvalidConfiguration(String allowedIps) {
        assertThatIllegalArgumentException().isThrownBy(() -> ClientAllowedIpPolicy.parse(allowedIps))
                .withMessage("Invalid allowed_ips configuration");
    }

    private static void assertInvalidRemote(ClientAllowedIpPolicy policy, String remoteAddress) {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allows(remoteAddress))
                .withMessage("Invalid socket remote address");
    }
}
