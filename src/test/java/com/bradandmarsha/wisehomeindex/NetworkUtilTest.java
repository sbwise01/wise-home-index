package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.util.NetworkUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUtilTest {

    @Test
    void addressesInsideSubnetArePrivate() {
        assertTrue(NetworkUtil.isPrivateNetwork("192.168.0.0"));
        assertTrue(NetworkUtil.isPrivateNetwork("192.168.0.1"));
        assertTrue(NetworkUtil.isPrivateNetwork("192.168.0.255"));
        assertTrue(NetworkUtil.isPrivateNetwork("::ffff:192.168.0.42"));
    }

    @Test
    void addressesOutsideSubnetArePublic() {
        assertFalse(NetworkUtil.isPrivateNetwork("192.168.1.1"));
        assertFalse(NetworkUtil.isPrivateNetwork("192.168.39.255"));
        assertFalse(NetworkUtil.isPrivateNetwork("10.0.0.1"));
        assertFalse(NetworkUtil.isPrivateNetwork("8.8.8.8"));
        assertFalse(NetworkUtil.isPrivateNetwork("not-an-ip"));
        assertFalse(NetworkUtil.isPrivateNetwork(null));
    }

    @Test
    void matchesPublicHostIpWhenAddressIsInTrustedSet() {
        Set<Integer> trusted = Set.of(packIpv4(203, 0, 113, 7), packIpv4(198, 51, 100, 42));
        assertTrue(NetworkUtil.matchesPublicHostIp("203.0.113.7", trusted));
        assertTrue(NetworkUtil.matchesPublicHostIp("198.51.100.42", trusted));
        // IPv4-mapped form should normalize to the same packed value.
        assertTrue(NetworkUtil.matchesPublicHostIp("::ffff:203.0.113.7", trusted));
    }

    @Test
    void doesNotMatchPublicHostIpForOtherOrInvalidAddresses() {
        Set<Integer> trusted = Set.of(packIpv4(203, 0, 113, 7));
        assertFalse(NetworkUtil.matchesPublicHostIp("203.0.113.8", trusted));
        assertFalse(NetworkUtil.matchesPublicHostIp("8.8.8.8", trusted));
        assertFalse(NetworkUtil.matchesPublicHostIp("not-an-ip", trusted));
        assertFalse(NetworkUtil.matchesPublicHostIp(null, trusted));
        // No trusted IPs (e.g. DNS not yet resolved) means nothing matches.
        assertFalse(NetworkUtil.matchesPublicHostIp("203.0.113.7", Set.of()));
        assertFalse(NetworkUtil.matchesPublicHostIp("203.0.113.7", null));
    }

    private static int packIpv4(int a, int b, int c, int d) {
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}
