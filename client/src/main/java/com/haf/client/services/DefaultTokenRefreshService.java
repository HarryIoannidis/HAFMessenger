package com.haf.client.services;

import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.client.utils.SslContextUtils;
import com.haf.shared.requests.RefreshTokenRequest;
import com.haf.shared.responses.RefreshTokenResponse;
import com.haf.shared.utils.JsonCodec;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default token-refresh service backed by the HTTPS refresh endpoint.
 */
public class DefaultTokenRefreshService implements TokenRefreshService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTokenRefreshService.class);
    private static final int REFRESH_TIMEOUT_SECONDS = 10;
    private static final String INVALID_SESSION = "invalid session";

    /**
     * Refreshes token pair using current refresh token.
     *
     * @param refreshToken current refresh token
     * @return refresh result
     */
    @Override
    public TokenRefreshResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return TokenRefreshResult.failure(false, "refresh token is missing");
        }

        try {
            ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(SslContextUtils.getSslContextForMode(runtimeConfig.isDev()))
                    .sslParameters(SslContextUtils.createHttpsSslParameters())
                    .build();

            RefreshTokenRequest payload = new RefreshTokenRequest();
            payload.setRefreshToken(refreshToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(runtimeConfig.serverBaseUri().resolve("/api/v1/token/refresh"))
                    .timeout(Duration.ofSeconds(REFRESH_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonCodec.toJson(payload)))
                    .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            String responseBody = httpResponse.body();
            RefreshTokenResponse response = parseRefreshResponse(responseBody);

            String resolvedAccessToken = resolveAccessToken(response, responseBody);
            String resolvedRefreshToken = resolveRefreshToken(response, responseBody);
            Long resolvedAccessExpiry = resolveAccessExpiry(response, responseBody);
            Long resolvedRefreshExpiry = resolveRefreshExpiry(response, responseBody);
            boolean hasExplicitError = hasExplicitError(response, responseBody);

            if (statusCode == 200
                    && !hasExplicitError
                    && resolvedAccessToken != null
                    && !resolvedAccessToken.isBlank()
                    && resolvedRefreshToken != null
                    && !resolvedRefreshToken.isBlank()) {
                return TokenRefreshResult.success(
                        resolvedAccessToken,
                        resolvedRefreshToken,
                        resolvedAccessExpiry,
                        resolvedRefreshExpiry);
            }

            String message = resolveRefreshFailureMessage(response, statusCode, responseBody);
            boolean invalidSession = isInvalidSessionStatus(statusCode)
                    || isInvalidSessionMessage(message);
            LOGGER.warn(
                    "Token refresh rejected (status={}, invalidSession={}, message={}, body={})",
                    statusCode,
                    invalidSession,
                    message,
                    abbreviateForLog(responseBody));
            return TokenRefreshResult.failure(invalidSession, message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return TokenRefreshResult.failure(false, "token refresh was interrupted");
        } catch (Exception ex) {
            String message = resolveExceptionFailureMessage(ex);
            LOGGER.warn("Token refresh request failed before response parsing: {}", message, ex);
            return TokenRefreshResult.failure(isInvalidSessionMessage(message), message);
        }
    }

    /**
     * Parses refresh response JSON into DTO model.
     *
     * @param body response body text
     * @return parsed refresh response, or {@code null} when body is blank
     */
    private static RefreshTokenResponse parseRefreshResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonCodec.fromJson(body, RefreshTokenResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Resolves a user-facing failure message from refresh response payload.
     *
     * @param response   parsed response payload
     * @param statusCode HTTP status code from refresh endpoint
     * @param rawBody    raw response body
     * @return normalized failure message
     */
    static String resolveRefreshFailureMessage(RefreshTokenResponse response, int statusCode, String rawBody) {
        if (response != null && response.getError() != null && !response.getError().isBlank()) {
            return response.getError().trim();
        }
        String rawError = extractJsonString(rawBody, "error", "message", "detail");
        if (rawError != null && !rawError.isBlank()) {
            return rawError.trim();
        }
        if (statusCode > 0) {
            return "token refresh failed (HTTP " + statusCode + ")";
        }
        return "token refresh failed";
    }

    /**
     * Checks whether an HTTP status indicates invalid/expired authorization
     * context for token refresh.
     *
     * @param statusCode refresh endpoint response status
     * @return {@code true} when status maps to invalid session state
     */
    static boolean isInvalidSessionStatus(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    /**
     * Checks whether an error message indicates the refresh token/session is no
     * longer valid and user should re-authenticate.
     *
     * @param message refresh failure message
     * @return {@code true} when message maps to invalid session state
     */
    static boolean isInvalidSessionMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(INVALID_SESSION)) {
            return true;
        }
        if (normalized.contains("unauthorized") || normalized.contains("forbidden")) {
            return true;
        }
        if (normalized.contains("session") && (normalized.contains("expired") || normalized.contains("revoked"))) {
            return true;
        }
        return normalized.contains("refresh token")
                && (normalized.contains("invalid") || normalized.contains("expired")
                        || normalized.contains("revoked"));
    }

    /**
     * Resolves access token from typed response model with compatibility fallback
     * for legacy response field names.
     *
     * @param response parsed typed response model
     * @param rawBody  raw response body
     * @return resolved access token string, or {@code null} when absent
     */
    static String resolveAccessToken(RefreshTokenResponse response, String rawBody) {
        if (response != null && response.getSessionId() != null && !response.getSessionId().isBlank()) {
            return response.getSessionId().trim();
        }
        return extractJsonString(rawBody, "sessionId", "accessToken", "access_token");
    }

    /**
     * Resolves rotated refresh token from typed response model with compatibility
     * fallback for legacy response field names.
     *
     * @param response parsed typed response model
     * @param rawBody  raw response body
     * @return resolved refresh token string, or {@code null} when absent
     */
    static String resolveRefreshToken(RefreshTokenResponse response, String rawBody) {
        if (response != null && response.getRefreshToken() != null && !response.getRefreshToken().isBlank()) {
            return response.getRefreshToken().trim();
        }
        return extractJsonString(rawBody, "refreshToken", "refresh_token");
    }

    /**
     * Resolves access-token expiry from typed response model with compatibility
     * fallback for alternate field names.
     *
     * @param response parsed typed response model
     * @param rawBody  raw response body
     * @return expiry epoch seconds, or {@code null} when absent
     */
    static Long resolveAccessExpiry(RefreshTokenResponse response, String rawBody) {
        if (response != null && response.getAccessExpiresAtEpochSeconds() != null) {
            return response.getAccessExpiresAtEpochSeconds();
        }
        return extractJsonLong(rawBody, "accessExpiresAtEpochSeconds", "access_expires_at_epoch_seconds");
    }

    /**
     * Resolves refresh-token expiry from typed response model with compatibility
     * fallback for alternate field names.
     *
     * @param response parsed typed response model
     * @param rawBody  raw response body
     * @return expiry epoch seconds, or {@code null} when absent
     */
    static Long resolveRefreshExpiry(RefreshTokenResponse response, String rawBody) {
        if (response != null && response.getRefreshExpiresAtEpochSeconds() != null) {
            return response.getRefreshExpiresAtEpochSeconds();
        }
        return extractJsonLong(rawBody, "refreshExpiresAtEpochSeconds", "refresh_expires_at_epoch_seconds");
    }

    /**
     * Returns whether refresh response contains an explicit error field either in
     * typed model or raw JSON body.
     *
     * @param response parsed typed response model
     * @param rawBody  raw response body
     * @return {@code true} when explicit error value is present
     */
    static boolean hasExplicitError(RefreshTokenResponse response, String rawBody) {
        if (response != null && response.getError() != null && !response.getError().isBlank()) {
            return true;
        }
        String rawError = extractJsonString(rawBody, "error", "message", "detail");
        return rawError != null && !rawError.isBlank();
    }

    /**
     * Resolves fallback message for transport/serialization failures during refresh
     * requests.
     *
     * @param error refresh failure exception
     * @return user-facing message text
     */
    private static String resolveExceptionFailureMessage(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "token refresh failed";
        }
        return error.getMessage().trim();
    }

    /**
     * Extracts first non-blank string value from provided JSON keys.
     *
     * @param rawBody raw JSON body
     * @param keys    candidate keys in lookup order
     * @return extracted string value, or {@code null} when not present
     */
    private static String extractJsonString(String rawBody, String... keys) {
        Map<?, ?> parsed = parseBodyAsMap(rawBody);
        if (parsed.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            Object value = parsed.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * Extracts first numeric long value from provided JSON keys.
     *
     * @param rawBody raw JSON body
     * @param keys    candidate keys in lookup order
     * @return extracted long value, or {@code null} when not present
     */
    private static Long extractJsonLong(String rawBody, String... keys) {
        Map<?, ?> parsed = parseBodyAsMap(rawBody);
        if (parsed.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            Object value = parsed.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // Ignore malformed numeric strings and continue scanning keys.
                }
            }
        }
        return null;
    }

    /**
     * Parses raw JSON body into map representation for compatibility field lookups.
     *
     * @param rawBody raw response body
     * @return parsed map, or empty map when body is blank/non-object
     */
    private static Map<?, ?> parseBodyAsMap(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = JsonCodec.fromJson(rawBody, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return map;
            }
            return Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * Produces compact single-line preview for response-body logging.
     *
     * @param rawBody raw response body
     * @return compact, bounded preview text
     */
    private static String abbreviateForLog(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "<empty>";
        }
        String normalized = rawBody.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        int maxLength = 240;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
