package com.viyable.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * A short-lived link to a PRIVATE file in file-uploader: {@code …/file/{uuid}?exp=<epoch s>&sig=<HMAC>}.
 *
 * <p><b>⚠⚠ ONE DEFINITION, BOTH ENDS.</b> The services that hand out links sign with this class, and
 * file-uploader verifies with this same class. Two copies of "how a link is signed" would agree on the
 * day they were written and drift the first time either changed — and the failure is every private
 * file refusing to open, everywhere at once.
 *
 * <p><b>⚠ A link is minted ONLY after the minting service has decided this person may see this file</b>
 * (signed in, same firm, allowed). The signature proves that decision was taken a moment ago; it is not
 * itself an authorisation check. So a link is short (minutes, never a day), and a page never embeds one —
 * it links to a stable address on its own service that checks and then redirects to a fresh link,
 * because a link embedded in a page dies on a tab left open over lunch.
 *
 * <p>⚠ What is signed is {@code uuid|exp}. The host and path are deliberately not, so the same link
 * verifies whether it arrived through CloudFront, nginx or straight at the container.
 */
public final class SignedFileUrl {

    public static final String EXP = "exp";
    public static final String SIG = "sig";

    /** ⚠ A link valid further ahead than this is refused even when correctly signed. */
    public static final Duration MAX_LIFETIME = Duration.ofMinutes(10);

    private static final int MIN_SECRET_BYTES = 32;
    private static final String HMAC = "HmacSHA256";

    private SignedFileUrl() {}

    /**
     * Appends {@code exp} and {@code sig} to a link the caller already builds the usual way
     * ({@code {base}/file/{uuid}}). The caller keeps owning the base url; this owns only the signature.
     *
     * @throws IllegalArgumentException when the secret is missing or short, or the lifetime is outside
     *                                  (0, {@link #MAX_LIFETIME}] — a link that cannot be verified is a
     *                                  broken link, and it should break where it is made, not where it is opened
     */
    public static String sign(String unsignedUrl, String fileUuid, String secret, Duration lifetime, Instant now) {
        if (unsignedUrl == null || unsignedUrl.isBlank()) throw new IllegalArgumentException("No url to sign.");
        if (fileUuid == null || fileUuid.isBlank()) throw new IllegalArgumentException("No file id to sign.");
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("A signed file link lives between 1 second and " + MAX_LIFETIME.toMinutes()
                    + " minutes; asked for " + lifetime + ".");
        }
        long exp = now.plus(lifetime).getEpochSecond();
        String sep = unsignedUrl.indexOf('?') >= 0 ? "&" : "?";
        return unsignedUrl + sep + EXP + "=" + exp + "&" + SIG + "=" + signature(fileUuid, exp, key(secret));
    }

    /**
     * True when {@code exp}/{@code sig} are a genuine, unexpired signature for this file.
     *
     * <p>⚠ Never throws on what a stranger sends — a malformed exp or sig is simply not valid. It DOES
     * throw on a missing/short secret, because that is our configuration, not their request.
     */
    public static boolean valid(String fileUuid, String exp, String sig, String secret, Instant now) {
        byte[] key = key(secret);
        if (fileUuid == null || exp == null || sig == null) return false;
        long expiry;
        try {
            expiry = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            return false;
        }
        long nowS = now.getEpochSecond();
        if (expiry < nowS) return false;
        // ⚠ Too far ahead = minted with somebody else's idea of "short", or forged with a stolen key.
        if (expiry > nowS + MAX_LIFETIME.getSeconds() + 60) return false;
        byte[] expected = signature(fileUuid, expiry, key).getBytes(StandardCharsets.US_ASCII);
        // ⚠ Constant time: a byte-by-byte compare that stops early tells a patient caller how much was right.
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] key(String secret) {
        if (secret == null) throw new IllegalStateException("The file-link signing secret is not configured.");
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("The file-link signing secret must be at least " + MIN_SECRET_BYTES
                    + " bytes; it is " + key.length + ".");
        }
        return key;
    }

    private static String signature(String fileUuid, long exp, byte[] key) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] raw = mac.doFinal((fileUuid + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
