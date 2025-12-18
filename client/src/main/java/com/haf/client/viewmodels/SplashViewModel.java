package com.haf.client.viewmodels;

import com.haf.client.utils.UiConstants;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;

/**
 * ViewModel responsible for bootstrapping the client while the splash screen is shown.
 * It exposes observable status/progress properties and runs the heavy lifting off the FX thread.
 */
public class SplashViewModel {

    @FunctionalInterface
    public interface ConfigLoader {
        String loadVersion() throws Exception;
    }

    @FunctionalInterface
    public interface CryptoInitializer {
        void initialize() throws Exception;
    }

    @FunctionalInterface
    public interface ResourceChecker {
        void verify() throws Exception;
    }

    @FunctionalInterface
    public interface NetworkChecker {
        void check() throws Exception;
    }

    private final StringProperty status = new SimpleStringProperty("Starting...");
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty version = new SimpleStringProperty("1.0.0");
    private final StringProperty percentage = new SimpleStringProperty("0%");
    private final javafx.beans.property.BooleanProperty error = new javafx.beans.property.SimpleBooleanProperty(false);

    private final ConfigLoader configLoader;
    private final CryptoInitializer cryptoInitializer;
    private final ResourceChecker resourceChecker;
    private final NetworkChecker networkChecker;

    private Task<Void> runningTask;

    public SplashViewModel() {
        this(defaultConfigLoader(), defaultCryptoInitializer(), defaultResourceChecker(), defaultNetworkChecker());
    }

    public SplashViewModel(
            ConfigLoader configLoader,
            CryptoInitializer cryptoInitializer,
            ResourceChecker resourceChecker,
            NetworkChecker networkChecker
    ) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.cryptoInitializer = Objects.requireNonNull(cryptoInitializer, "cryptoInitializer");
        this.resourceChecker = Objects.requireNonNull(resourceChecker, "resourceChecker");
        this.networkChecker = Objects.requireNonNull(networkChecker, "networkChecker");
        percentage.bind(progress.multiply(100).asString("%.0f%%"));
    }

    public static SplashViewModel createDefault() {
        return new SplashViewModel();
    }

    public StringProperty statusProperty() {
        return status;
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public StringProperty versionProperty() {
        return version;
    }

    public StringProperty percentageProperty() {
        return percentage;
    }

    public javafx.beans.property.BooleanProperty errorProperty() {
        return error;
    }

    /**
     * Starts the bootstrap process on a background thread.
     * @param onSuccess Callback to run when bootstrap completes successfully.
     * @param onFailure Callback to run when bootstrap fails.
     */
    public synchronized void startBootstrap(Runnable onSuccess, java.util.function.Consumer<Throwable> onFailure) {
        if (runningTask != null && runningTask.isRunning()) {
            return;
        }

        // Reset UI-related state before starting a new run
        error.set(false);
        percentage.unbind();

        progress.set(0.0);
        percentage.bind(progress.multiply(100).asString("%.0f%%"));

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                update("Starting...", 0.0);
                delay(1500L);

                update("Loading configuration...", 0.1);
                String detectedVersion = configLoader.loadVersion();
                if (detectedVersion != null && !detectedVersion.isBlank()) {
                    version.set(detectedVersion);
                }
                delay(500L);

                update("Initializing security modules...", 0.3);
                cryptoInitializer.initialize();
                delay(600L);

                update("Checking local resources...", 0.6);
                resourceChecker.verify();
                delay(1000L);

                update("Verifying network reachability...", 0.8);
                networkChecker.check();
                delay(700L);

                update("Ready", 1.0);
                delay(400L);
                return null;
            }

            private void update(String msg, double p) {
                updateMessage(msg);
                updateProgress(p, 1.0);
            }
        };

        // Reflect task state into observable properties
        status.unbind();
        progress.unbind();
        status.bind(task.messageProperty());
        progress.bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            runningTask = null;
            if (onSuccess != null) {
                onSuccess.run();
            }
        });

        task.setOnFailed(e -> {
            runningTask = null;
            status.unbind();
            progress.unbind();
            percentage.unbind();
            percentage.set("");
            error.set(true);
            status.set("Initialization failed.");

            if (onFailure != null) {
                onFailure.accept(task.getException());
            }
        });

        runningTask = task;

        Thread t = new Thread(task, "splash-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private static void delay(long millis) {
        try {
            long randomOffset = (long) (Math.random() * 201) - 100;
            long sleepTime = Math.max(0, millis + randomOffset);
            Thread.sleep(sleepTime);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Default implementations for the ConfigLoader.
     * It attempts to read the version from the application's manifest, then from an environment variable,
     * @return the detected version or "1.0.0" if none found
     */
    private static ConfigLoader defaultConfigLoader() {
        return () -> {
            String fromManifest = SplashViewModel.class.getPackage().getImplementationVersion();

            if (fromManifest != null && !fromManifest.isBlank()) {
                return fromManifest;
            }

            String envVersion = System.getenv("HAF_APP_VERSION");
            if (envVersion != null && !envVersion.isBlank()) {
                return envVersion;
            }

            return "1.0.0";
        };
    }

    /**
     * Default implementation for the CryptoInitializer.
     * @return a Runnable that initializes security modules
     */
    private static CryptoInitializer defaultCryptoInitializer() {
        return () -> {
            // Ensure strong SecureRandom is available
            SecureRandom.getInstanceStrong().nextBytes(new byte[16]);

            // Verify AES/GCM availability
            javax.crypto.Cipher.getInstance(com.haf.shared.constants.CryptoConstants.AES_GCM_TRANSFORMATION);

            // Verify RSA-OAEP availability (Critical for Key Exchange)
            javax.crypto.Cipher.getInstance(com.haf.shared.constants.CryptoConstants.RSA_OAEP_TRANSFORMATION);

            // Verify SHA-256 availability
            java.security.MessageDigest.getInstance(com.haf.shared.constants.CryptoConstants.OAEP_HASH);
        };
    }

    /**
     * Default implementation for the ResourceChecker.
     * @return a Runnable that checks for required resources
     */
    private static ResourceChecker defaultResourceChecker() {
        return () -> {
            requireResource(UiConstants.FXML_LOGIN, "Login view");
            requireResource(UiConstants.FXML_SPLASH, "Splash view");
            requireResource(UiConstants.IMAGE_APP_LOGO, "Application logo");
            requireResource(UiConstants.CSS_GLOBAL, "Global stylesheet");
        };
    }

    /**
     * Default implementation for the ResourceChecker.
     * @param path The resource path
     * @param label The resource label for error messages
     * @throws IOException if the resource is not found
     */
    private static void requireResource(String path, String label) throws IOException {
        if (SplashViewModel.class.getResource(path) == null) {
            throw new IOException(label + " missing at " + path);
        }
    }

    /**
     * Default implementation for the NetworkChecker.
     * It checks if the configured server is reachable.
     * @return a Runnable that checks server reachability
     */
    private static NetworkChecker defaultNetworkChecker() {
        return () -> {
            String endpoint = System.getProperty("haf.server.url", System.getenv("HAF_SERVER_URL"));
            if (endpoint == null || endpoint.isBlank()) {
                // No endpoint configured: skip reachability check
                return;
            }

            try (
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(3))
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 400) {
                    throw new IOException("Server unreachable, status " + response.statusCode());
                }
            } catch (IOException e) {
                throw new IOException("Failed to check server reachability", e);
            }
        };
    }

}
