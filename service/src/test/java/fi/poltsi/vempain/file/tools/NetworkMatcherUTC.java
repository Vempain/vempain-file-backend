package fi.poltsi.vempain.file.tools;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.common.security.NetworkMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkMatcherUTC {

    @Test
    void singleAddressesBecomeHostNetworks() {
        assertEquals("10.1.2.3/32", NetworkMatcher.normalize("10.1.2.3"));
        assertEquals("2001:db8::1/128", NetworkMatcher.normalize("2001:db8::1"));
        assertEquals("10.0.0.0/8", NetworkMatcher.normalize(" 10.0.0.0/8 "));
        assertEquals("2001:db8::/32", NetworkMatcher.normalize("2001:0db8:0000::/32"));
        assertEquals("::1/128", NetworkMatcher.normalize("0:0:0:0:0:0:0:1"));
        assertEquals("::/0", NetworkMatcher.normalize("::/0"));
        assertEquals("2001:db8:0:1::1/64", NetworkMatcher.normalize("2001:DB8:0:1:0:0:0:1/64"));
        assertEquals("1:0:0:2::3/128", NetworkMatcher.normalize("1:0:0:2:0:0:0:3"));
    }

    @Test
    void malformedNetworksAreRejected() {
        for (var bad : new String[]{
            "",
            " ",
            "example.com",
            "10.1.2",
            "10.1.2.300",
            "10.0.0.0/33",
            "10.0.0.0/-1",
            "10.0.0.0/x",
            "::1/129",
            "1.2.3.4/8/8",
            "10.0.0.0/8; DROP" }) {
            assertThrows(IllegalArgumentException.class, () -> NetworkMatcher.normalize(bad), bad);
        }
    }

    @Test
    void ipv4MembershipHonoursThePrefix() {
        assertTrue(NetworkMatcher.matches("10.1.2.3/32", "10.1.2.3"));
        assertFalse(NetworkMatcher.matches("10.1.2.3/32", "10.1.2.4"));
        assertTrue(NetworkMatcher.matches("10.1.2.0/24", "10.1.2.200"));
        assertFalse(NetworkMatcher.matches("10.1.2.0/24", "10.1.3.1"));
        assertTrue(NetworkMatcher.matches("10.0.0.0/8", "10.255.0.1"));
        assertTrue(NetworkMatcher.matches("192.168.4.0/22", "192.168.7.255"));
        assertFalse(NetworkMatcher.matches("192.168.4.0/22", "192.168.8.0"));
        assertTrue(NetworkMatcher.matches("0.0.0.0/0", "203.0.113.9"));
        // An IPv6 client never matches an IPv4 network and vice versa
        assertFalse(NetworkMatcher.matches("10.0.0.0/8", "::ffff:10.1.1.1".replace("::ffff:", "2001:db8::")));
        assertFalse(NetworkMatcher.matches("2001:db8::/32", "10.1.1.1"));
    }

    @Test
    void ipv6MembershipHonoursThePrefixAndZones() {
        assertTrue(NetworkMatcher.matches("2001:db8::1/128", "2001:db8::1"));
        assertTrue(NetworkMatcher.matches("2001:db8::1", "2001:0db8:0000:0000:0000:0000:0000:0001"));
        assertFalse(NetworkMatcher.matches("2001:db8::1/128", "2001:db8::2"));
        assertTrue(NetworkMatcher.matches("2001:db8::/32", "2001:db8:ffff::9"));
        assertFalse(NetworkMatcher.matches("2001:db8::/32", "2001:db9::1"));
        assertTrue(NetworkMatcher.matches("fe80::/10", "fe80::1%eth0"));
        assertTrue(NetworkMatcher.matches("::/0", "2001:db8::1"));
    }

    @Test
    void malformedClientsNeverMatch() {
        assertFalse(NetworkMatcher.matches("10.0.0.0/8", null));
        assertFalse(NetworkMatcher.matches("10.0.0.0/8", ""));
        assertFalse(NetworkMatcher.matches("10.0.0.0/8", "localhost"));
        assertFalse(NetworkMatcher.matches("not-a-network", "10.1.1.1"));
    }
}
