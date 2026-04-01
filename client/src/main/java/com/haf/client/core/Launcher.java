package com.haf.client.core;

/**
 * Provides a thin entry point that delegates to ClientApp.
 */
public class Launcher {
    /**
     * Starts the JavaFX client application entry point.
     *
     * @param args command-line arguments forwarded to
     *             {@link ClientApp#main(String[])}
     */
    public static void main(String[] args) {
        ClientApp.main(args);
    }
}
