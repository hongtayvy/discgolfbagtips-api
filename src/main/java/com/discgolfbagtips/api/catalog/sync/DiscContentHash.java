package com.discgolfbagtips.api.catalog.sync;

import com.discgolfbagtips.api.catalog.Disc;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fingerprints the fields that feed a disc's embedding passage. When the fingerprint changes the
 * stored vector no longer describes the disc, and the backfill job rebuilds it.
 */
public final class DiscContentHash {

    private DiscContentHash() {
    }

    public static String of(Disc disc) {
        String payload = String.join("|",
                String.valueOf(disc.name()),
                String.valueOf(disc.brand()),
                String.valueOf(disc.category()),
                String.valueOf(disc.speed()),
                String.valueOf(disc.glide()),
                String.valueOf(disc.turn()),
                String.valueOf(disc.fade()),
                String.valueOf(disc.stabilityLabel()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
