package com.bradandmarsha.wisehomeindex;

import com.bradandmarsha.wisehomeindex.util.NetworkUtil;
import org.junit.jupiter.api.Test;

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
}
