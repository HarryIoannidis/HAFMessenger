package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.viewmodels.MessagesViewModel;
import com.haf.shared.constants.AttachmentConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link ChatAttachmentService} that delegates to the active chat
 * session.
 */
public class DefaultChatAttachmentService implements ChatAttachmentService {

    private static final Map<String, String> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("csv", "text/csv"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("webp", "image/webp"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("flac", "audio/flac"),
            Map.entry("aac", "audio/aac"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("webm", "video/webm"),
            Map.entry("mpeg", "video/mpeg"),
            Map.entry("mpg", "video/mpeg"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip"),
            Map.entry("tgz", "application/gzip"),
            Map.entry("bz2", "application/x-bzip2"),
            Map.entry("xz", "application/x-xz"),
            Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("json", "application/json"),
            Map.entry("java", "text/x-java-source"),
            Map.entry("js", "text/javascript"),
            Map.entry("jsx", "text/javascript"),
            Map.entry("ts", "text/typescript"),
            Map.entry("tsx", "text/tsx"),
            Map.entry("css", "text/css"),
            Map.entry("scss", "text/x-scss"),
            Map.entry("sql", "application/sql"),
            Map.entry("py", "text/x-python"),
            Map.entry("sh", "application/x-sh"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("log", "text/plain"),
            Map.entry("exe", "application/vnd.microsoft.portable-executable"),
            Map.entry("msi", "application/x-msi"),
            Map.entry("dmg", "application/x-apple-diskimage"),
            Map.entry("app", "application/octet-stream"),
            Map.entry("apk", "application/vnd.android.package-archive"),
            Map.entry("deb", "application/vnd.debian.binary-package"),
            Map.entry("rpm", "application/x-rpm"),
            Map.entry("bat", "application/x-bat"),
            Map.entry("cmd", "application/x-cmd"));

    @FunctionalInterface
    interface AttachmentGateway {
        /**
         * Sends an attachment through the active message pipeline.
         *
         * @param recipientId recipient identifier
         * @param filePath    path to the file to send
         * @param mimeType    MIME type hint for transport metadata
         */
        void sendAttachment(String recipientId, Path filePath, String mimeType);
    }

    @FunctionalInterface
    interface AttachmentGatewayProvider {
        /**
         * Returns the currently available attachment gateway.
         *
         * @return gateway instance, or {@code null} when chat session is unavailable
         */
        AttachmentGateway current();
    }

    private final AttachmentGatewayProvider attachmentGatewayProvider;

    /**
     * Creates a service bound to the current {@link ChatSession} message
     * view-model.
     */
    public DefaultChatAttachmentService() {
        this(() -> {
            MessagesViewModel vm = ChatSession.get();
            return vm == null ? null : vm::sendAttachment;
        });
    }

    /**
     * Creates a service with an injected gateway provider (primarily for tests).
     *
     * @param attachmentGatewayProvider provider used to resolve the current send
     *                                  gateway
     */
    DefaultChatAttachmentService(AttachmentGatewayProvider attachmentGatewayProvider) {
        this.attachmentGatewayProvider = Objects.requireNonNull(attachmentGatewayProvider, "attachmentGatewayProvider");
    }

    /**
     * Sends a local file attachment to the provided recipient when both inputs are
     * valid.
     *
     * @param recipientId recipient identifier
     * @param filePath    local path of attachment to send
     */
    @Override
    public void sendAttachment(String recipientId, Path filePath) {
        if (recipientId == null || recipientId.isBlank() || filePath == null) {
            return;
        }

        AttachmentGateway gateway = attachmentGatewayProvider.current();
        if (gateway == null) {
            return;
        }

        gateway.sendAttachment(recipientId, filePath, resolveMimeType(filePath));
    }

    /**
     * Derives a MIME type from attachment filename extension.
     *
     * @param path file path whose extension should be mapped
     * @return known/probed MIME type, or octet-stream when the type is unknown
     */
    static String resolveMimeType(Path path) {
        if (path == null || path.getFileName() == null) {
            return AttachmentConstants.APPLICATION_OCTET_STREAM;
        }

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < name.length()) {
            String mapped = MIME_BY_EXTENSION.get(name.substring(dot + 1));
            if (mapped != null) {
                return mapped;
            }
        }

        try {
            String probed = AttachmentConstants.normalizeMimeType(Files.probeContentType(path));
            if (AttachmentConstants.isValidMimeType(probed)) {
                return probed;
            }
        } catch (IOException ignored) {
            // Octet-stream fallback below.
        }
        return AttachmentConstants.APPLICATION_OCTET_STREAM;
    }
}
