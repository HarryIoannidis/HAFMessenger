package com.haf.server.realtime;

import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.RateLimiterService;
import com.haf.server.router.RateLimiterService.RateLimitDecision;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.crypto.MessageSignatureService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.SigningKeyIO;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Objects;

/**
 * Secure message ingress path used by the realtime WSS gateway.
 */
public final class MessageIngressService {

    private static final String SENDER_MISMATCH = "senderId does not match authenticated user";
    private static final String INVALID_SIGNATURE = "invalid message signature";

    private final EncryptedMessageValidator validator;
    private final User userDAO;
    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiterService;
    private final AuditLogger auditLogger;

    /**
     * Create a message ingress service.
     *
     * @param validator          validation service for incoming messages
     * @param userDAO            user lookup (for verifying sender keys)
     * @param mailboxRouter      message routing service
     * @param rateLimiterService rate limiter
     * @param auditLogger        logging service
     */
    public MessageIngressService(
            EncryptedMessageValidator validator,
            User userDAO,
            MailboxRouter mailboxRouter,
            RateLimiterService rateLimiterService,
            AuditLogger auditLogger) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
        this.mailboxRouter = Objects.requireNonNull(mailboxRouter, "mailboxRouter");
        this.rateLimiterService = Objects.requireNonNull(rateLimiterService, "rateLimiterService");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    /**
     * Process a single incoming encrypted message for the authenticated user.
     * This method is called by the WebSocket path only.
     *
     * @param requestId               unique request identifier from API gateway
     * @param authenticatedUserId     the authenticated user (always equals
     *                                message.senderId)
     * @param message                 the encrypted message to ingress
     * @param recipientKeyFingerprint client-provided recipient key fingerprint (for
     *                                detection of stale recipient keys)
     * @param clientMessageId         client-provided message ID for idempotency
     *                                (may be
     *                                null)
     * @param startNanos              timestamp at call start
     * @throws IngressRejectedException if validation, rate limiting, or other
     *                                  pre-routing checks fail
     */
    public IngressAccepted accept(
            String requestId,
            String authenticatedUserId,
            EncryptedMessage message,
            String recipientKeyFingerprint,
            String clientMessageId,
            long startNanos) {
        EncryptedMessageValidator.ValidationResult validationResult = validator.validate(message);
        if (!validationResult.valid()) {
            auditLogger.logValidationFailure(
                    requestId,
                    authenticatedUserId,
                    safeLogValue(message == null ? null : message.getRecipientId()),
                    validationResult.reason());
            throw new IngressRejectedException(400, "invalid_payload", validationResult.reason(), 0);
        }

        if (!authenticatedUserId.equals(message.getSenderId())) {
            auditLogger.logValidationFailure(requestId, authenticatedUserId, message.getRecipientId(),
                    "SENDER_MISMATCH");
            throw new IngressRejectedException(403, "sender_mismatch", SENDER_MISMATCH, 0);
        }

        if (!verifyIngressSignature(message, authenticatedUserId)) {
            auditLogger.logValidationFailure(requestId, authenticatedUserId, message.getRecipientId(),
                    "INVALID_SIGNATURE");
            throw new IngressRejectedException(400, "invalid_signature", INVALID_SIGNATURE, 0);
        }

        if (isRecipientKeyFingerprintStale(recipientKeyFingerprint, message)) {
            throw new IngressRejectedException(409, "stale_recipient_key", "recipient key is stale", 0);
        }

        RateLimitDecision rateDecision = rateLimiterService.checkAndConsume(requestId, authenticatedUserId);
        if (!rateDecision.allowed()) {
            auditLogger.logRateLimit(requestId, authenticatedUserId, rateDecision.retryAfterSeconds());
            throw new IngressRejectedException(
                    429,
                    "rate_limit",
                    "rate limit",
                    rateDecision.retryAfterSeconds());
        }

        MailboxRouter.MailboxIngressResult result = clientMessageId == null || clientMessageId.isBlank()
                ? new MailboxRouter.MailboxIngressResult(mailboxRouter.ingress(message), false)
                : mailboxRouter.ingressIdempotent(message, clientMessageId);
        auditLogger.logIngressAccepted(
                requestId,
                authenticatedUserId,
                message.getRecipientId(),
                202,
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        return new IngressAccepted(
                result.envelope().envelopeId(),
                validationResult.expiresAtMillis(),
                result.duplicate());
    }

    /**
     * Verify that the message signature is valid for the authenticated sender.
     *
     * @param message               encrypted message
     * @param authenticatedSenderId the authenticated user (sender)
     * @return true if the signature is valid and the sender key fingerprint matches
     */
    private boolean verifyIngressSignature(EncryptedMessage message, String authenticatedSenderId) {
        if (message == null || authenticatedSenderId == null || authenticatedSenderId.isBlank()) {
            return false;
        }
        if (!MessageHeader.ALGO_SIGNATURE.equals(message.getSignatureAlgorithm())) {
            return false;
        }
        if (message.getSignatureB64() == null || message.getSignatureB64().isBlank()) {
            return false;
        }
        if (message.getSenderSigningKeyFingerprint() == null
                || message.getSenderSigningKeyFingerprint().isBlank()) {
            return false;
        }

        User.PublicKeyRecord senderKey = userDAO.getPublicKey(authenticatedSenderId);
        if (senderKey == null
                || senderKey.signingPublicKeyPem() == null
                || senderKey.signingPublicKeyPem().isBlank()
                || senderKey.signingFingerprint() == null
                || senderKey.signingFingerprint().isBlank()) {
            return false;
        }

        String providedFingerprint = message.getSenderSigningKeyFingerprint().trim();
        if (!providedFingerprint.equalsIgnoreCase(senderKey.signingFingerprint().trim())) {
            return false;
        }
        try {
            PublicKey signingPublicKey = SigningKeyIO.publicFromPem(senderKey.signingPublicKeyPem());
            String recomputedFingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(signingPublicKey));
            if (!recomputedFingerprint.equalsIgnoreCase(senderKey.signingFingerprint().trim())) {
                return false;
            }
            return MessageSignatureService.verify(message, signingPublicKey);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Check whether the recipient's public key fingerprint provided by the client
     * is stale. A stale fingerprint means the client is using an old public key and
     * should be prompted to rotate their keys.
     *
     * @param providedFingerprint client-provided fingerprint for the recipient
     * @param message             message whose recipient we are checking
     * @return true if the provided fingerprint is stale (should trigger client key
     *         rotation)
     */
    private boolean isRecipientKeyFingerprintStale(String providedFingerprint, EncryptedMessage message) {
        if (providedFingerprint == null || providedFingerprint.isBlank() || message == null) {
            return false;
        }
        String recipientId = message.getRecipientId();
        if (recipientId == null || recipientId.isBlank()) {
            return false;
        }
        User.PublicKeyRecord recipientKey = userDAO.getPublicKey(recipientId);
        if (recipientKey == null || recipientKey.fingerprint() == null || recipientKey.fingerprint().isBlank()) {
            return false;
        }
        return !recipientKey.fingerprint().trim().equalsIgnoreCase(providedFingerprint.trim());
    }

    /**
     * Convert null or blank strings to "unknown" for logging purposes.
     * 
     * @param value string to safely log
     * @return "unknown" if null or blank, otherwise the original string
     */
    private static String safeLogValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * Record of a message that has been accepted for ingress and will be delivered
     * to the recipient's mailbox.
     * 
     * @param envelopeId       the envelope ID
     * @param expiresAtEpochMs expiration time in milliseconds
     * @param duplicate        true if this is a duplicate message
     */
    public record IngressAccepted(String envelopeId, long expiresAtEpochMs, boolean duplicate) {
    }

    /**
     * Exception indicating that an incoming message was rejected due to a
     * validation
     * or rate-limiting failure.
     * 
     * @param statusCode        HTTP status code to return to client
     * @param code              short error code string
     * @param message           human-readable error message
     * @param retryAfterSeconds how long client should wait before retrying (in
     *                          seconds)
     */
    public static final class IngressRejectedException extends RuntimeException {
        private final int statusCode;
        private final String code;
        private final long retryAfterSeconds;

        /**
         * Creates a new IngressRejectedException.
         * 
         * @param statusCode        HTTP status code to return to client
         * @param code              short error code string
         * @param message           human-readable error message
         * @param retryAfterSeconds how long client should wait before retrying (in
         *                          seconds)
         */
        public IngressRejectedException(int statusCode, String code, String message, long retryAfterSeconds) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int statusCode() {
            return statusCode;
        }

        public String code() {
            return code;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
