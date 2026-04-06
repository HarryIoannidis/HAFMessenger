package com.haf.server.security;

import com.haf.shared.utils.JsonCodec;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Issues and verifies HS256 JWT access tokens.
 */
public final class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "JWT";
    private static final String TOKEN_ALGORITHM = "HS256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] signingSecret;
    private final String issuer;
    private final long accessTtlSeconds;

    /**
     * Creates JWT service.
     *
     * @param signingSecret    secret used for HS256 signing
     * @param issuer           issuer claim value
     * @param accessTtlSeconds access-token TTL in seconds
     */
    public JwtTokenService(String signingSecret, String issuer, long accessTtlSeconds) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("signingSecret");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer");
        }
        if (accessTtlSeconds <= 0) {
            throw new IllegalArgumentException("accessTtlSeconds");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    /**
     * Returns configured access-token TTL.
     *
     * @return access-token TTL in seconds
     */
    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    /**
     * Issues an access JWT.
     *
     * @param userId    subject user id
     * @param jti       token id
     * @param issuedAt  issued-at instant
     * @param expiresAt expiry instant
     * @return signed JWT
     */
    public String issueAccessToken(String userId, String jti, Instant issuedAt, Instant expiresAt) {
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId");
        }
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti");
        }
        String payloadJson = JsonCodec.toJson(Map.of(
                "iss", issuer,
                "sub", userId,
                "jti", jti,
                "iat", issuedAt.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()));

        String headerPart = base64UrlEncode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadPart = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerPart + "." + payloadPart;
        String signaturePart = base64UrlEncode(hmac(signingInput));
        return signingInput + "." + signaturePart;
    }

    /**
     * Verifies and decodes access JWT.
     *
     * @param token raw JWT
     * @return decoded token claims
     * @throws JwtValidationException when token is invalid
     */
    public VerifiedToken verifyAccessToken(String token) {
        TokenParts tokenParts = parseTokenParts(token);
        verifySignature(tokenParts);

        DecodedToken decodedToken = decodeToken(tokenParts);
        validateHeader(decodedToken.header());

        TokenClaims claims = extractClaims(decodedToken.payload());
        validateClaims(claims);

        return new VerifiedToken(
                claims.userId(),
                claims.jti(),
                claims.issuedAtEpochSeconds(),
                claims.expiresAtEpochSeconds());
    }

    private static TokenParts parseTokenParts(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("token is blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException("token must contain 3 parts");
        }
        return new TokenParts(parts[0], parts[1], parts[2]);
    }

    private void verifySignature(TokenParts tokenParts) {
        byte[] expectedSignature = hmac(tokenParts.signingInput());
        byte[] providedSignature;
        try {
            providedSignature = base64UrlDecode(tokenParts.signaturePart());
        } catch (IllegalArgumentException ex) {
            throw new JwtValidationException("token signature is malformed", ex);
        }
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new JwtValidationException("token signature is invalid");
        }
    }

    private static DecodedToken decodeToken(TokenParts tokenParts) {
        try {
            Map<?, ?> header = decodeMap(tokenParts.headerPart());
            Map<?, ?> payload = decodeMap(tokenParts.payloadPart());
            if (header == null || payload == null) {
                throw new JwtValidationException("token payload is missing");
            }
            return new DecodedToken(header, payload);
        } catch (JwtValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JwtValidationException("token payload is malformed", ex);
        }
    }

    private static Map<?, ?> decodeMap(String encodedPart) {
        String json = new String(base64UrlDecode(encodedPart), StandardCharsets.UTF_8);
        return JsonCodec.fromJson(json, Map.class);
    }

    private static void validateHeader(Map<?, ?> header) {
        if (!TOKEN_ALGORITHM.equals(String.valueOf(header.get("alg")))
                || !TOKEN_TYPE.equals(String.valueOf(header.get("typ")))) {
            throw new JwtValidationException("token header is invalid");
        }
    }

    private static TokenClaims extractClaims(Map<?, ?> payload) {
        return new TokenClaims(
                asString(payload.get("iss")),
                asString(payload.get("sub")),
                asString(payload.get("jti")),
                asLong(payload.get("iat")),
                asLong(payload.get("exp")));
    }

    private void validateClaims(TokenClaims claims) {
        if (!issuer.equals(claims.tokenIssuer())) {
            throw new JwtValidationException("token issuer mismatch");
        }
        if (claims.userId() == null || claims.userId().isBlank()) {
            throw new JwtValidationException("token subject is missing");
        }
        if (claims.jti() == null || claims.jti().isBlank()) {
            throw new JwtValidationException("token jti is missing");
        }

        long now = Instant.now().getEpochSecond();
        if (claims.expiresAtEpochSeconds() <= now) {
            throw new JwtValidationException("token expired");
        }
        if (claims.issuedAtEpochSeconds() > now + 60L) {
            throw new JwtValidationException("token iat is in the future");
        }
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            throw new JwtValidationException("numeric claim missing");
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new JwtValidationException("numeric claim invalid", ex);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /**
     * Verified JWT claims.
     *
     * @param userId                subject user id
     * @param jti                   token id
     * @param issuedAtEpochSeconds  issued-at epoch seconds
     * @param expiresAtEpochSeconds expiry epoch seconds
     */
    public record VerifiedToken(
            String userId,
            String jti,
            long issuedAtEpochSeconds,
            long expiresAtEpochSeconds) {
    }

    private record TokenParts(String headerPart, String payloadPart, String signaturePart) {
        private String signingInput() {
            return headerPart + "." + payloadPart;
        }
    }

    private record DecodedToken(Map<?, ?> header, Map<?, ?> payload) {
    }

    private record TokenClaims(
            String tokenIssuer,
            String userId,
            String jti,
            long issuedAtEpochSeconds,
            long expiresAtEpochSeconds) {
    }

    /**
     * JWT validation failure.
     */
    public static final class JwtValidationException extends RuntimeException {
        /**
         * Creates validation error.
         *
         * @param message error message
         */
        public JwtValidationException(String message) {
            super(message);
        }

        /**
         * Creates validation error.
         *
         * @param message error message
         * @param cause   root cause
         */
        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
