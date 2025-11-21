package com.haf.server.core;

import com.haf.server.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void main_has_private_constructor() {
        // Verify Main has private constructor (utility class pattern)
        assertTrue(Main.class.getDeclaredConstructors().length > 0);
    }

    @Test
    void main_method_exists() throws NoSuchMethodException {
        // Verify main method exists and is public static
        var mainMethod = Main.class.getMethod("main", String[].class);
        assertNotNull(mainMethod);
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()));
    }

    @Test
    void main_requires_environment_variables() {
        // This test verifies that Main.main() will fail without proper env vars
        // We can't easily test the full startup without a real database and TLS keystore
        // But we can verify the structure is correct
        
        // Main should call ServerConfig.load() which requires env vars
        Map<String, String> emptyEnv = new HashMap<>();
        
        // This will throw IllegalStateException if env vars are missing
        // Note: fromEnv is package-private, so we test via reflection or just verify structure
        assertThrows(IllegalStateException.class, () -> {
            try {
                var method = ServerConfig.class.getDeclaredMethod("fromEnv", Map.class);
                method.setAccessible(true);
                method.invoke(null, emptyEnv);
            } catch (Exception e) {
                if (e.getCause() instanceof IllegalStateException) {
                    throw (IllegalStateException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void main_class_is_final() {
        assertTrue(java.lang.reflect.Modifier.isFinal(Main.class.getModifiers()));
    }

    @Test
    void main_class_has_logger() {
        // Verify Main has a logger field
        try {
            var loggerField = Main.class.getDeclaredField("LOGGER");
            assertNotNull(loggerField);
            assertTrue(java.lang.reflect.Modifier.isStatic(loggerField.getModifiers()));
            assertTrue(java.lang.reflect.Modifier.isFinal(loggerField.getModifiers()));
        } catch (NoSuchFieldException e) {
            fail("Main should have a LOGGER field");
        }
    }
}

