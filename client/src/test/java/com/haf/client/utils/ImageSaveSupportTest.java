package com.haf.client.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSaveSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void suggested_name_uses_preferred_name_when_valid() {
        assertEquals(
                "chosen-name.jpg",
                ImageSaveSupport.resolveSuggestedFileName("chosen-name.jpg", "file:///tmp/source.png"));
    }

    @Test
    void suggested_name_falls_back_to_source_file_name_then_default() {
        assertEquals(
                "source.webp",
                ImageSaveSupport.resolveSuggestedFileName(null, "file:///tmp/source.webp"));

        assertEquals(
                "image-preview.png",
                ImageSaveSupport.resolveSuggestedFileName(null, null));
    }

    @Test
    void resolve_source_path_accepts_file_uris_and_rejects_unknown_values() throws Exception {
        Path expected = tempDir.resolve("pic.png");
        Files.write(expected, new byte[] { 1, 2, 3 });
        assertEquals(expected, ImageSaveSupport.resolveLocalSourcePath(expected.toUri().toString()));
        assertNull(ImageSaveSupport.resolveLocalSourcePath("not-a-valid-source"));
    }

    @Test
    void downloads_directory_resolves_to_existing_directory() {
        assertNotNull(ImageSaveSupport.resolveDownloadsDirectory());
    }
}
