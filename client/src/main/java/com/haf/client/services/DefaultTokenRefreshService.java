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

/**
 * Default token-refresh service backed by the HTTPS refresh endpoint.
 */
public class DefaultTokenRefreshService implements TokenRefreshService {

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
            RefreshTokenResponse response = parseRefreshResponse(httpResponse.body());
            if (httpResponse.statusCode() == 200
                    && response != null
                    && response.isSuccess()
                    && response.getSessionId() != null
                    && !response.getSessionId().isBlank()
                    && response.getRefreshToken() != null
                    && !response.getRefreshToken().isBlank()) {
                return TokenRefreshResult.success(
                        response.getSessionId(),
                        response.getRefreshToken(),
                        response.getAccessExpiresAtEpochSeconds(),
                        response.getRefreshExpiresAtEpochSeconds());
            }

            String message = resolveRefreshFailureMessage(response);
            boolean invalidSession = httpResponse.statusCode() == 401
                    || httpResponse.statusCode() == 403
                    || INVALID_SESSION.equalsIgnoreCase(message);
            return TokenRefreshResult.failure(invalidSession, message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return TokenRefreshResult.failure(false, "token refresh was interrupted");
        } catch (Exception ex) {
            return TokenRefreshResult.failure(false, "token refresh failed");
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
        return JsonCodec.fromJson(body, RefreshTokenResponse.class);
    }

    /**
     * Resolves a user-facing failure message from refresh response payload.
     *
     * @param response parsed response payload
     * @return normalized failure message
     */
    private static String resolveRefreshFailureMessage(RefreshTokenResponse response) {
        if (response == null || response.getError() == null || response.getError().isBlank()) {
            return "token refresh failed";
        }
        return response.getError().trim();
    }
}
