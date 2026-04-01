package com.haf.client.utils;

import javafx.stage.FileChooser;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Utility helpers for resolving local image paths and configuring save dialogs.
 */
public final class ImageSaveSupport {

    /**
     * Prevents instantiation of this utility class.
     */
    private ImageSaveSupport() {
    }

    /**
     * Resolves the best filename suggestion for save dialogs.
     *
     * Resolution order: explicit preferred name, filename derived from source
     * URI/path, then a default fallback.
     *
     * @param preferredName  explicit filename preferred by caller
     * @param imageUriOrPath source image URI/path used as fallback for filename
     *                       extraction
     * @return sanitized filename suggestion that can be displayed by file choosers
     */
    public static String resolveSuggestedFileName(String preferredName, String imageUriOrPath) {
        String preferred = sanitizeFileName(preferredName);
        if (preferred != null) {
            return preferred;
        }

        String fromPath = deriveFileNameFromSource(imageUriOrPath);
        if (fromPath != null) {
            return fromPath;
        }

        return "image-preview.png";
    }

    /**
     * Attempts to resolve a local filesystem {@link Path} from the provided image
     * source.
     *
     * Supports both {@code file://} URIs and plain filesystem paths, returning only
     * existing files.
     *
     * @param imageUriOrPath source image URI or local path string
     * @return existing local path, or {@code null} when the source is
     *         invalid/non-local
     */
    public static Path resolveLocalSourcePath(String imageUriOrPath) {
        if (imageUriOrPath == null || imageUriOrPath.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(imageUriOrPath);
            if (uri.getScheme() != null && "file".equalsIgnoreCase(uri.getScheme())) {
                Path path = Path.of(uri);
                return Files.exists(path) ? path : null;
            }
        } catch (Exception _) {
            // Fallback to direct path parsing.
        }

        try {
            Path candidate = Path.of(imageUriOrPath);
            return Files.exists(candidate) ? candidate : null;
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Resolves the preferred downloads target directory for save dialogs.
     *
     * @return user's {@code Downloads} folder when present, otherwise the home
     *         directory
     */
    public static Path resolveDownloadsDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        if (Files.isDirectory(downloads)) {
            return downloads;
        }
        return home;
    }

    /**
     * Configures a {@link FileChooser} for image-export workflows.
     *
     * @param chooser           chooser instance to configure
     * @param suggestedFileName preferred output filename
     * @param imageUriOrPath    source URI/path used for filename inference fallback
     */
    public static void configureImageSaveChooser(
            FileChooser chooser,
            String suggestedFileName,
            String imageUriOrPath) {
        String finalName = resolveSuggestedFileName(suggestedFileName, imageUriOrPath);
        configureChooserBase(chooser, finalName);

        chooser.getExtensionFilters().clear();
        String extension = extensionFromName(finalName);
        if (extension != null) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Image (*" + extension + ")",
                    "*" + extension));
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
    }

    /**
     * Configures a generic save chooser for non-image attachments.
     *
     * @param chooser           chooser instance to configure
     * @param suggestedFileName preferred output filename
     * @param sourceUriOrPath   source URI/path used for filename inference fallback
     */
    public static void configureSaveChooser(
            FileChooser chooser,
            String suggestedFileName,
            String sourceUriOrPath) {
        String finalName = resolveSuggestedFileName(suggestedFileName, sourceUriOrPath);
        configureChooserBase(chooser, finalName);
        chooser.getExtensionFilters().clear();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
    }

    /**
     * Applies common file chooser settings (filename + initial directory).
     *
     * @param chooser   chooser instance to configure
     * @param finalName resolved filename to prefill
     */
    private static void configureChooserBase(FileChooser chooser, String finalName) {
        if (chooser == null) {
            return;
        }
        chooser.setInitialFileName(finalName);
        Path downloadsDir = resolveDownloadsDirectory();
        if (Files.isDirectory(downloadsDir)) {
            chooser.setInitialDirectory(downloadsDir.toFile());
        }
    }

    /**
     * Extracts a filename candidate from a URI/path source string.
     *
     * @param imageUriOrPath source URI/path
     * @return sanitized filename candidate, or {@code null} when none can be
     *         derived
     */
    private static String deriveFileNameFromSource(String imageUriOrPath) {
        if (imageUriOrPath == null || imageUriOrPath.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(imageUriOrPath);
            if (uri.getScheme() != null && "file".equalsIgnoreCase(uri.getScheme())) {
                Path fromUri = Path.of(uri);
                if (fromUri.getFileName() != null) {
                    return sanitizeFileName(fromUri.getFileName().toString());
                }
            }
        } catch (Exception _) {
            // Fallback to direct path parsing.
        }

        try {
            Path fromPath = Path.of(imageUriOrPath);
            if (fromPath.getFileName() != null) {
                return sanitizeFileName(fromPath.getFileName().toString());
            }
        } catch (Exception _) {
            return null;
        }

        return null;
    }

    /**
     * Validates and sanitizes a raw filename string.
     *
     * @param value candidate filename
     * @return trimmed filename, or {@code null} when invalid/unsafe
     */
    private static String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            return null;
        }
        return trimmed;
    }

    /**
     * Returns a known image extension for the provided filename.
     *
     * @param fileName filename to inspect
     * @return one of supported image extensions (including dot), or {@code null} if
     *         unknown
     */
    private static String extensionFromName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".jpg")) {
            return ".jpg";
        }
        if (lower.endsWith(".jpeg")) {
            return ".jpeg";
        }
        if (lower.endsWith(".gif")) {
            return ".gif";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        return null;
    }
}
