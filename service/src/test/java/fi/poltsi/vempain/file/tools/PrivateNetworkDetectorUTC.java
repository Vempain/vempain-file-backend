package fi.poltsi.vempain.file.tools;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.common.security.NetworkMatcher;
import fi.poltsi.vempain.common.security.PrivateNetworkDetector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class PrivateNetworkDetectorUTC {

    private static InetAddress ip(String text) throws Exception {
        return InetAddress.getByName(text);
    }

    @Test
    void privateRangesAreRecognised() throws Exception {
        assertTrue(PrivateNetworkDetector.isPrivate(ip("10.0.9.5")));
        assertTrue(PrivateNetworkDetector.isPrivate(ip("172.18.0.3")));
        assertTrue(PrivateNetworkDetector.isPrivate(ip("192.168.1.20")));
        assertTrue(PrivateNetworkDetector.isPrivate(ip("fd12:3456:789a::1")));
        assertFalse(PrivateNetworkDetector.isPrivate(ip("127.0.0.1")));
        assertFalse(PrivateNetworkDetector.isPrivate(ip("169.254.1.1")));
        assertFalse(PrivateNetworkDetector.isPrivate(ip("203.0.113.9")));
        assertFalse(PrivateNetworkDetector.isPrivate(ip("::1")));
        assertFalse(PrivateNetworkDetector.isPrivate(ip("fe80::1")));
        assertFalse(PrivateNetworkDetector.isPrivate(ip("2001:db8::1")));
    }

    @Test
    void interfaceAddressesBecomeTheirNetworks() throws Exception {
        var overlay = PrivateNetworkDetector.describe("eth1", ip("10.0.9.5"), (short) 24);
        assertNotNull(overlay);
        assertEquals("10.0.9.0/24", overlay.network());
        assertEquals("eth1", overlay.interfaceName());
        assertEquals("10.0.9.5", overlay.address());

        assertEquals("172.18.0.0/16", PrivateNetworkDetector.describe("eth0", ip("172.18.0.3"), (short) 16)
            .network());
        assertEquals("192.168.4.0/22", PrivateNetworkDetector.describe("eth0", ip("192.168.7.9"), (short) 22)
            .network());
        assertEquals("fd12:3456:789a::/64", PrivateNetworkDetector.describe("eth0", ip("fd12:3456:789a::1"), (short) 64)
            .network());
        // Not private, or an unusable prefix
        assertNull(PrivateNetworkDetector.describe("lo", ip("127.0.0.1"), (short) 8));
        assertNull(PrivateNetworkDetector.describe("eth0", ip("203.0.113.9"), (short) 24));
        assertNull(PrivateNetworkDetector.describe("eth0", ip("10.0.0.1"), (short) 0));
        assertNull(PrivateNetworkDetector.describe("eth0", ip("10.0.0.1"), (short) 33));
    }

    @Test
    void detectionNeverFailsAndReturnsOnlyPrivateNetworks() {
        var found = PrivateNetworkDetector.detect();
        assertNotNull(found);
        for (var network : found) {
            assertTrue(NetworkMatcher.matches(network.network(), network.address()), network.toString());
            assertTrue(network.network()
                .contains("/"));
        }
    }

    @Test
    void anAddressThatIsNeitherIpv4NorIpv6IsNotPrivate() {
        var exotic = mock(InetAddress.class);

        assertFalse(PrivateNetworkDetector.isPrivate(exotic));
        assertNull(PrivateNetworkDetector.describe("eth0", exotic, (short) 24));
        assertNull(PrivateNetworkDetector.describe("eth0", null, (short) 24));
    }

    @Test
    void detectionAnswersNothingWhenTheInterfacesCannotBeListed() {
        try (var interfaces = mockStatic(NetworkInterface.class)) {
            interfaces.when(NetworkInterface::getNetworkInterfaces)
                .thenReturn(null);
            assertEquals(List.of(), PrivateNetworkDetector.detect());

            interfaces.when(NetworkInterface::getNetworkInterfaces)
                .thenThrow(new SocketException("no network"));
            assertEquals(List.of(), PrivateNetworkDetector.detect());
        }
    }
}
