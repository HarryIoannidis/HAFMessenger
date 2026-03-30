package com.haf.client.models;

import java.time.LocalDateTime;

/**
 * Immutable view-model record for a single chat message.
 *
 * @param outgoing  true → message was sent by the local user
 * @param type      content type: TEXT, IMAGE, or FILE
 * @param content   plain-text body (TEXT) or local file:// path of temp file
 *                  (IMAGE)
 * @param localPath local file:// path of temp file for FILE messages
 * @param fileName  display name for FILE messages
 * @param fileSize  human-readable size string for FILE messages (e.g. "2.4 MB")
 * @param timestamp when the message was created / received
 * @param loading   true while an attachment bubble is still resolving content
 */
public record MessageVM(
        boolean outgoing,
        MessageType type,
        String content,
        String localPath,
        String fileName,
        String fileSize,
        LocalDateTime timestamp,
        boolean loading) {

    /**
     * Convenience factory for outgoing text messages.
     * 
     * @param text      text payload
     * @param timestamp message timestamp
     * @return outgoing text message view-model
     */
    public static MessageVM outgoingText(String text, LocalDateTime timestamp) {
        return new MessageVM(true, MessageType.TEXT, text, null, null, null, timestamp, false);
    }

    /**
     * Convenience factory for incoming text messages.
     *
     * @param text      text payload
     * @param timestamp message timestamp
     * @return incoming text message view-model
     */
    public static MessageVM incomingText(String text, LocalDateTime timestamp) {
        return new MessageVM(false, MessageType.TEXT, text, null, null, null, timestamp, false);
    }

    /**
     * Convenience factory for outgoing image messages (localPath = file:// path).
     *
     * @param localPath local image path
     * @param timestamp message timestamp
     * @return outgoing image message view-model
     */
    public static MessageVM outgoingImage(String localPath, LocalDateTime timestamp) {
        return new MessageVM(true, MessageType.IMAGE, localPath, null, null, null, timestamp, false);
    }

    /**
     * Convenience factory for incoming image messages (localPath = file:// path).
     *
     * @param localPath local image path
     * @param timestamp message timestamp
     * @return incoming image message view-model
     */
    public static MessageVM incomingImage(String localPath, LocalDateTime timestamp) {
        return new MessageVM(false, MessageType.IMAGE, localPath, null, null, null, timestamp, false);
    }

    /**
     * Convenience factory for incoming image placeholders while chunked payloads
     * are downloading.
     *
     * @param timestamp message timestamp
     * @return incoming loading-image placeholder view-model
     */
    public static MessageVM incomingLoadingImage(LocalDateTime timestamp) {
        return new MessageVM(false, MessageType.IMAGE, null, null, null, null, timestamp, true);
    }

    /**
     * Convenience factory for incoming image placeholders while chunked payloads
     * are downloading.
     *
     * @param fileName  source file name
     * @param timestamp message timestamp
     * @return incoming loading-image placeholder view-model
     */
    public static MessageVM incomingLoadingImage(String fileName, LocalDateTime timestamp) {
        return new MessageVM(false, MessageType.IMAGE, null, null, fileName, null, timestamp, true);
    }

    /**
     * Convenience factory for outgoing image placeholders while chunked payloads
     * are uploading.
     *
     * @param fileName  source file name
     * @param timestamp message timestamp
     * @return outgoing loading-image placeholder view-model
     */
    public static MessageVM outgoingLoadingImage(String fileName, LocalDateTime timestamp) {
        return new MessageVM(true, MessageType.IMAGE, null, null, fileName, null, timestamp, true);
    }

    /**
     * Convenience factory for outgoing file messages (localPath = file:// path).
     *
     * @param localPath local file path
     * @param fileName  file display name
     * @param fileSize  human-readable file size
     * @param timestamp message timestamp
     * @return outgoing file message view-model
     */
    public static MessageVM outgoingFile(String localPath, String fileName, String fileSize, LocalDateTime timestamp) {
        return new MessageVM(true, MessageType.FILE, null, localPath, fileName, fileSize, timestamp, false);
    }

    /**
     * Convenience factory for incoming file messages (localPath = file:// path).
     *
     * @param localPath local file path
     * @param fileName  file display name
     * @param fileSize  human-readable file size
     * @param timestamp message timestamp
     * @return incoming file message view-model
     */
    public static MessageVM incomingFile(String localPath, String fileName, String fileSize, LocalDateTime timestamp) {
        return new MessageVM(false, MessageType.FILE, null, localPath, fileName, fileSize, timestamp, false);
    }

    /**
     * Convenience factory for incoming file placeholders while chunked payloads
     * are downloading.
     *
     * @param fileName  file display name
     * @param fileSize  human-readable file size
     * @param timestamp message timestamp
     * @return incoming loading-file placeholder view-model
     */
    public static MessageVM incomingLoadingFile(String fileName, String fileSize, LocalDateTime timestamp) {
        return new MessageVM(false, MessageType.FILE, null, null, fileName, fileSize, timestamp, true);
    }

    /**
     * Convenience factory for outgoing file placeholders while chunked payloads
     * are uploading.
     *
     * @param fileName  file display name
     * @param fileSize  human-readable file size
     * @param timestamp message timestamp
     * @return outgoing loading-file placeholder view-model
     */
    public static MessageVM outgoingLoadingFile(String fileName, String fileSize, LocalDateTime timestamp) {
        return new MessageVM(true, MessageType.FILE, null, null, fileName, fileSize, timestamp, true);
    }

    /**
     * Alias so call-sites can use isOutgoing() or outgoing() interchangeably.
     * 
     * @return true when message is outgoing
     */
    public boolean isOutgoing() {
        return outgoing;
    }

    /**
     * Alias so call-sites can use isLoading() or loading() interchangeably.
     *
     * @return whether this message is still resolving/loading attachment content
     */
    public boolean isLoading() {
        return loading;
    }
}
