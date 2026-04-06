package com.haf.client.core;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores authenticated access/refresh token state for the current user session.
 */
public final class AuthSessionState {

    /**
     * Snapshot of the current auth tokens and expiry metadata.
     *
     * @param accessToken                  access JWT used for authenticated
     *                                     requests
     * @param refreshToken                 refresh token used for rotation
     * @param accessExpiresAtEpochSeconds  access-token expiry (epoch seconds)
     * @param refreshExpiresAtEpochSeconds refresh-token expiry (epoch seconds)
     */
    public record Snapshot(
            String accessToken,
            String refreshToken,
            Long accessExpiresAtEpochSeconds,
            Long refreshExpiresAtEpochSeconds) {
    }

    private static final AtomicReference<Snapshot> SNAPSHOT = new AtomicReference<>();

    private AuthSessionState() {
    }

    /**
     * Stores current auth-token snapshot.
     *
     * @param accessToken                  access JWT
     * @param refreshToken                 refresh token
     * @param accessExpiresAtEpochSeconds  access-token expiry epoch seconds
     * @param refreshExpiresAtEpochSeconds refresh-token expiry epoch seconds
     * @throws IllegalArgumentException when {@code accessToken} is null/blank
     */
    public static void set(
            String accessToken,
            String refreshToken,
            Long accessExpiresAtEpochSeconds,
            Long refreshExpiresAtEpochSeconds) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken");
        }
        SNAPSHOT.set(new Snapshot(
                accessToken,
                normalizeOptional(refreshToken),
                normalizeEpochSeconds(accessExpiresAtEpochSeconds),
                normalizeEpochSeconds(refreshExpiresAtEpochSeconds)));
    }

    /**
     * Returns current auth snapshot, or {@code null} when user is not
     * authenticated.
     *
     * @return current auth snapshot or {@code null}
     */
    public static Snapshot get() {
        return SNAPSHOT.get();
    }

    /**
     * Returns current access token.
     *
     * @return access token or {@code null}
     */
    public static String getAccessToken() {
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null ? null : snapshot.accessToken();
    }

    /**
     * Returns current refresh token.
     *
     * @return refresh token or {@code null}
     */
    public static String getRefreshToken() {
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null ? null : snapshot.refreshToken();
    }

    /**
     * Returns access-token expiry epoch seconds.
     *
     * @return access-token expiry epoch seconds or {@code null}
     */
    public static Long getAccessExpiresAtEpochSeconds() {
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null ? null : snapshot.accessExpiresAtEpochSeconds();
    }

    /**
     * Returns refresh-token expiry epoch seconds.
     *
     * @return refresh-token expiry epoch seconds or {@code null}
     */
    public static Long getRefreshExpiresAtEpochSeconds() {
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null ? null : snapshot.refreshExpiresAtEpochSeconds();
    }

    /**
     * Clears auth snapshot.
     */
    public static void clear() {
        SNAPSHOT.set(null);
    }

    /**
     * Normalizes optional string values by trimming and mapping blanks to
     * {@code null}.
     *
     * @param value candidate value
     * @return normalized value or {@code null} when blank
     */
    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Normalizes epoch-second metadata and maps non-positive values to
     * {@code null}.
     *
     * @param epochSeconds candidate epoch-second value
     * @return normalized epoch seconds or {@code null} when missing/invalid
     */
    private static Long normalizeEpochSeconds(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0L) {
            return null;
        }
        return epochSeconds;
    }
}
