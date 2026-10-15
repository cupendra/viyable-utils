package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The signed private-file link, end to end: what one service mints, file-uploader must accept — and
 * nothing else.
 */
class SignedFileUrlTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-test-secret";
    private static final String FILE = "3f2b6c1e-9a7d-4c2e-8b1a-5d6e7f8a9b0c";
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private static String param(String url, String name) {
        for (String part : url.substring(url.indexOf('?') + 1).split("&")) {
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return null;
    }

    @Test
    void aMintedLinkVerifies() {
        String url = SignedFileUrl.sign("https://files.firmland.in/file/" + FILE, FILE, SECRET, Duration.ofSeconds(60), NOW);
        assertTrue(url.startsWith("https://files.firmland.in/file/" + FILE + "?exp="));
        assertTrue(SignedFileUrl.valid(FILE, param(url, "exp"), param(url, "sig"), SECRET, NOW.plusSeconds(59)));
    }

    /**
     * ⚠ Pinned vector, computed independently:
     * {@code printf '%s' "<uuid>|1792058460" | openssl dgst -sha256 -hmac <secret> -binary | base64url}.
     * A change to the signing input breaks this test, not prod's private files.
     */
    @Test
    void theSignatureIsStable() {
        String url = SignedFileUrl.sign("x", FILE, SECRET, Duration.ofSeconds(60), NOW);
        assertEquals("x?exp=1792058460&sig=rP54RcBqMs7s4n-9_VatDIQHju-xBPGrLu6Hf1fN7qY", url);
    }

    @Test
    void anExpiredLinkIsRefused() {
        String url = SignedFileUrl.sign("x", FILE, SECRET, Duration.ofSeconds(60), NOW);
        assertFalse(SignedFileUrl.valid(FILE, param(url, "exp"), param(url, "sig"), SECRET, NOW.plusSeconds(61)));
    }

    @Test
    void aLinkForOneFileDoesNotOpenAnother() {
        String url = SignedFileUrl.sign("x", FILE, SECRET, Duration.ofSeconds(60), NOW);
        assertFalse(SignedFileUrl.valid("aaaaaaaa-9a7d-4c2e-8b1a-5d6e7f8a9b0c", param(url, "exp"), param(url, "sig"), SECRET, NOW));
    }

    @Test
    void stretchingTheExpiryBreaksTheSignature() {
        String url = SignedFileUrl.sign("x", FILE, SECRET, Duration.ofSeconds(60), NOW);
        long later = Long.parseLong(param(url, "exp")) + 300;
        assertFalse(SignedFileUrl.valid(FILE, String.valueOf(later), param(url, "sig"), SECRET, NOW));
    }

    @Test
    void anotherSecretDoesNotVerify() {
        String url = SignedFileUrl.sign("x", FILE, SECRET, Duration.ofSeconds(60), NOW);
        assertFalse(SignedFileUrl.valid(FILE, param(url, "exp"), param(url, "sig"), SECRET + "-other", NOW));
    }

    @Test
    void garbageIsNotValidAndDoesNotThrow() {
        assertFalse(SignedFileUrl.valid(FILE, "soon", "abc", SECRET, NOW));
        assertFalse(SignedFileUrl.valid(FILE, null, null, SECRET, NOW));
        assertFalse(SignedFileUrl.valid(FILE, String.valueOf(NOW.getEpochSecond() + 30), "", SECRET, NOW));
    }

    @Test
    void aLinkMintedTooFarAheadIsRefusedEvenIfGenuine() {
        // Simulates a minter configured for an hour: verify must not honour it.
        long exp = NOW.getEpochSecond() + 3600;
        String forged = SignedFileUrl.sign("x", FILE, SECRET, Duration.ofSeconds(60), NOW.plusSeconds(3540));
        assertEquals(String.valueOf(exp), param(forged, "exp"));
        assertFalse(SignedFileUrl.valid(FILE, param(forged, "exp"), param(forged, "sig"), SECRET, NOW));
    }

    @Test
    void lifetimeOutsideTheWindowIsRefusedWhereTheLinkIsMade() {
        assertThrows(IllegalArgumentException.class, () -> SignedFileUrl.sign("x", FILE, SECRET, Duration.ofHours(1), NOW));
        assertThrows(IllegalArgumentException.class, () -> SignedFileUrl.sign("x", FILE, SECRET, Duration.ZERO, NOW));
    }

    @Test
    void aShortOrMissingSecretIsAConfigurationError() {
        assertThrows(IllegalStateException.class, () -> SignedFileUrl.sign("x", FILE, "short", Duration.ofSeconds(60), NOW));
        assertThrows(IllegalStateException.class, () -> SignedFileUrl.valid(FILE, "1", "s", null, NOW));
    }

    @Test
    void anExistingQueryIsKept() {
        String url = SignedFileUrl.sign("https://f/file/" + FILE + "?download=1", FILE, SECRET, Duration.ofSeconds(60), NOW);
        assertTrue(url.contains("?download=1&exp="));
    }
}
