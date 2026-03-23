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

    private static final String DEFAULT_IMAGE_NAME = "image-preview.png";

    private ImageSaveSupport() {
    }

    public static String resolveSuggestedFileName(String preferredName, String imageUriOrPath) {
        String preferred = sanitizeFileName(preferredName);
        if (preferred != null) {
            return preferred;
        }

        String fromPath = deriveFileNameFromSource(imageUriOrPath);
        if (fromPath != null) {
            return fromPath;
        }

        return DEFAULT_IMAGE_NAME;
    }

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
        } catch (Exception ignored) {
            // Fallback to direct path parsing.
        }

        try {
            Path candidate = Path.of(imageUriOrPath);
            return Files.exists(candidate) ? candidate : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Path resolveDownloadsDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        if (Files.isDirectory(downloads)) {
            return downloads;
        }
        return home;
    }

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

    public static void configureSaveChooser(
            FileChooser chooser,
            String suggestedFileName,
            String sourceUriOrPath) {
        String finalName = resolveSuggestedFileName(suggestedFileName, sourceUriOrPath);
        configureChooserBase(chooser, finalName);
        chooser.getExtensionFilters().clear();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
    }

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
        } catch (Exception ignored) {
            // Fallback to direct path parsing.
        }

        try {
            Path fromPath = Path.of(imageUriOrPath);
            if (fromPath.getFileName() != null) {
                return sanitizeFileName(fromPath.getFileName().toString());
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

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
