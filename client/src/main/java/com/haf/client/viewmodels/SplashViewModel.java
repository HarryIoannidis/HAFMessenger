package com.haf.client.viewmodels;

import com.haf.client.utils.SslContextUtils;
import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.client.utils.UiConstants;
import com.haf.shared.constants.CryptoConstants;
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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.Objects;

/**
 * Coordinates startup checks and progress reporting for the splash screen.
 */
public class SplashViewModel {
    private static final int NETWORK_CHECK_MAX_ATTEMPTS = 3;
    private static final long NETWORK_CHECK_RETRY_DELAY_MILLIS = 500L;

    @FunctionalInterface
    public interface ConfigLoader {
        /**
         * Loads application version metadata for splash display.
         *
         * @return resolved application version text
         * @throws IOException when version metadata cannot be read
         */
        String loadVersion() throws IOException;
    }

    @FunctionalInterface
    public interface CryptoInitializer {
        /**
         * Verifies required cryptographic primitives/providers.
         *
         * @throws GeneralSecurityException when required crypto primitives are
         *                                  unavailable
         */
        void initialize() throws GeneralSecurityException;
    }

    @FunctionalInterface
    public interface ResourceChecker {
        /**
         * Validates availability of required local resources.
         *
         * @throws IOException when one or more required resources are missing
         */
        void verify() throws IOException;
    }

    @FunctionalInterface
    public interface NetworkChecker {
        /**
         * Verifies remote server/network reachability.
         *
         * @throws IOException when reachability check fails
         */
        void check() throws IOException;
    }

    private final StringProperty status = new SimpleStringProperty(UiConstants.BOOTSTRAP_STARTING);
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty version = new SimpleStringProperty("1.0.0");
    private final StringProperty percentage = new SimpleStringProperty("0%");
    private final javafx.beans.property.BooleanProperty error = new javafx.beans.property.SimpleBooleanProperty(false);
    private final ConfigLoader configLoader;
    private final CryptoInitializer cryptoInitializer;
    private final ResourceChecker resourceChecker;
    private final NetworkChecker networkChecker;

    private Task<Void> runningTask;

    /**
     * Creates splash view-model with default bootstrap dependency providers.
     */
    public SplashViewModel() {
        this(defaultConfigLoader(), defaultCryptoInitializer(), defaultResourceChecker(), defaultNetworkChecker());
    }

    /**
     * Creates splash view-model with injectable bootstrap dependency providers.
     *
     * @param configLoader      loader used to resolve app version
     * @param cryptoInitializer validator for required crypto capabilities
     * @param resourceChecker   validator for required local assets/resources
     * @param networkChecker    validator for backend reachability
     */
    public SplashViewModel(
            ConfigLoader configLoader,
            CryptoInitializer cryptoInitializer,
            ResourceChecker resourceChecker,
            NetworkChecker networkChecker) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.cryptoInitializer = Objects.requireNonNull(cryptoInitializer, "cryptoInitializer");
        this.resourceChecker = Objects.requireNonNull(resourceChecker, "resourceChecker");
        this.networkChecker = Objects.requireNonNull(networkChecker, "networkChecker");
        percentage.bind(progress.multiply(100).asString("%.0f%%"));
    }

    /**
     * Factory method returning a splash view-model configured with production
     * defaults.
     *
     * @return default-configured {@link SplashViewModel}
     */
    public static SplashViewModel createDefault() {
        return new SplashViewModel();
    }

    /**
     * Exposes splash status text property.
     *
     * @return observable status property
     */
    public StringProperty statusProperty() {
        return status;
    }

    /**
     * Exposes splash progress value property.
     *
     * @return observable progress property in range {@code [0,1]}
     */
    public DoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Exposes application version text property.
     *
     * @return observable version property
     */
    public StringProperty versionProperty() {
        return version;
    }

    /**
     * Exposes formatted percentage text property derived from progress.
     *
     * @return observable percentage property
     */
    public StringProperty percentageProperty() {
        return percentage;
    }

    /**
     * Exposes startup error-state property.
     *
     * @return observable error flag property
     */
    public javafx.beans.property.BooleanProperty errorProperty() {
        return error;
    }

    /**
     * Starts the bootstrap process on a background thread.
     *
     * @param onSuccess Callback to run when bootstrap completes successfully.
     * @param onFailure Callback to run when bootstrap fails.
     */
    public synchronized void startBootstrap(Runnable onSuccess, java.util.function.Consumer<Throwable> onFailure) {
        if (runningTask != null) {
            return;
        }

        // Reset UI-related state before starting a new run
        error.set(false);
        percentage.unbind();
        status.unbind();
        progress.unbind();

        progress.set(0.0);
        percentage.bind(progress.multiply(100).asString("%.0f%%"));

        Task<Void> task = new Task<>() {
            /**
             * Executes staged bootstrap checks and updates progress/message bindings.
             *
             * @return {@code null} when bootstrap completes
             * @throws Exception when any bootstrap stage fails
             */
            @Override
            protected Void call() throws Exception {
                update(UiConstants.BOOTSTRAP_STARTING, 0.0);
                delay(2000L);

                update(UiConstants.BOOTSTRAP_CONFIG, 0.1);
                String detectedVersion = configLoader.loadVersion();
                if (detectedVersion != null && !detectedVersion.isBlank()) {
                    version.set(detectedVersion);
                }
                delay(100L);

                update(UiConstants.BOOTSTRAP_SECURITY, 0.3);
                cryptoInitializer.initialize();
                delay(100L);

                update(UiConstants.BOOTSTRAP_RESOURCES, 0.6);
                resourceChecker.verify();
                delay(100L);

                update(UiConstants.BOOTSTRAP_NETWORK, 0.8);
                verifyNetworkWithRetries();

                update(UiConstants.BOOTSTRAP_READY, 1.0);
                delay(200L);
                return null;
            }

            /**
             * Pushes message/progress updates into task-bound observable properties.
             *
             * @param msg status message
             * @param p   progress value between 0 and 1
             */
            private void update(String msg, double p) {
                updateMessage(msg);
                updateProgress(p, 1.0);
            }
        };

        // Reflect task state into observable properties
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
            status.set(UiConstants.BOOTSTRAP_FAILED);

            if (onFailure != null) {
                onFailure.accept(task.getException());
            }
        });

        runningTask = task;

        Thread t = new Thread(task, "splash-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Verifies server reachability with a maximum number of retry attempts.
     *
     * @throws IOException when all retry attempts fail
     */
    private void verifyNetworkWithRetries() throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= NETWORK_CHECK_MAX_ATTEMPTS; attempt++) {
            try {
                networkChecker.check();
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt < NETWORK_CHECK_MAX_ATTEMPTS) {
                    delay(NETWORK_CHECK_RETRY_DELAY_MILLIS);
                }
            }
        }

        throw new IOException(
                "Initialization could not be completed after "
                        + NETWORK_CHECK_MAX_ATTEMPTS
                        + " network attempts.",
                lastFailure);
    }

    /**
     * Adds slight random delay jitter to bootstrap steps for smoother UX pacing.
     *
     * @param millis base delay duration in milliseconds
     */
    private static void delay(long millis) {
        try {
            long randomOffset = ThreadLocalRandom.current().nextLong(-100, 101);
            long sleepTime = Math.max(0, millis + randomOffset);
            Thread.sleep(sleepTime);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Default implementations for the ConfigLoader.
     * It attempts to read the version from the application's manifest, then from an
     * environment variable,
     *
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
     *
     * @return a Runnable that initializes security modules
     */
    private static CryptoInitializer defaultCryptoInitializer() {
        return () -> {
            // Ensure strong SecureRandom is available
            SecureRandom.getInstanceStrong().nextBytes(new byte[16]);

            // Verify AES/GCM availability
            javax.crypto.Cipher.getInstance(CryptoConstants.AES_GCM_TRANSFORMATION);

            // Verify X25519 (ECDH) availability (Critical for Key Exchange)
            javax.crypto.KeyAgreement.getInstance(CryptoConstants.KEY_AGREEMENT_ALGO);

            // Verify SHA-256 availability
            java.security.MessageDigest.getInstance("SHA-256");
        };
    }

    /**
     * Builds the default checker that validates all required local app resources
     * (FXML, CSS, images, fonts).
     *
     * @return resource-check callback used during splash bootstrap
     */
    private static ResourceChecker defaultResourceChecker() {
        return () -> {
            // FXML resources
            requireResource(UiConstants.FXML_SPLASH, "Splash view");
            requireResource(UiConstants.FXML_LOGIN, "Login view");
            requireResource(UiConstants.FXML_REGISTER, "Register view");
            requireResource(UiConstants.FXML_MAIN, "Main view");
            requireResource(UiConstants.FXML_CHAT, "Chat view");
            requireResource(UiConstants.FXML_PLACEHOLDER, "Placeholder view");
            requireResource(UiConstants.FXML_CONTACT_CELL, "Contact cell view");
            requireResource(UiConstants.FXML_SEARCH, "Search view");
            requireResource(UiConstants.FXML_SEARCH_RESULT_ITEM, "Search result item view");
            requireResource(UiConstants.FXML_PROFILE, "Profile view");
            requireResource(UiConstants.FXML_PREVIEW, "Preview view");
            requireResource(UiConstants.FXML_POPUP_MESSAGE, "Popup message view");

            // CSS resources
            requireResource(UiConstants.CSS_GLOBAL, "Global stylesheet");
            requireResource(UiConstants.CSS_REGISTER, "Register stylesheet");
            requireResource(UiConstants.CSS_MAIN, "Main stylesheet");
            requireResource(UiConstants.CSS_SPLASH, "Splash stylesheet");
            requireResource(UiConstants.CSS_CHAT, "Chat stylesheet");
            requireResource(UiConstants.CSS_SEARCH, "Search stylesheet");
            requireResource(UiConstants.CSS_OPTIONS, "Options stylesheet");
            requireResource(UiConstants.CSS_PROFILE, "Profile stylesheet");
            requireResource(UiConstants.CSS_PREVIEW, "Preview stylesheet");
            requireResource(UiConstants.CSS_POPUP, "Popup stylesheet");

            // Images
            requireResource(UiConstants.IMAGE_APP_LOGO, "Application logo");
            requireResource(UiConstants.IMAGE_APP_LOGO_DOWNSCALE, "Application logo downscale");
            requireResource(UiConstants.IMAGE_APP_LOGO_UPSCALE, "Application logo upscale");
            requireResource(UiConstants.IMAGE_APP_LOGO_SVG, "Application logo SVG");
            requireResource(UiConstants.IMAGE_APP_LOGO_ICO, "Application logo ICO");
            requireResource(UiConstants.IMAGE_LOGO_PNG, "Logo PNG");
            requireResource(UiConstants.IMAGE_AVATAR, "Avatar image");
            requireResource(UiConstants.IMAGE_EMPTY_CHAT, "Empty chat image");

            // Rank Icons
            requireResource(UiConstants.ICON_RANK_YPOSMINIAS, "Yposminias rank icon");
            requireResource(UiConstants.ICON_RANK_SMINIAS, "Sminias rank icon");
            requireResource(UiConstants.ICON_RANK_EPISMINIAS, "Episminias rank icon");
            requireResource(UiConstants.ICON_RANK_ARCHISMINIAS, "Archisminias rank icon");
            requireResource(UiConstants.ICON_RANK_ANTHYPASPISTIS, "Anthypaspistis rank icon");
            requireResource(UiConstants.ICON_RANK_ANTHYPOSMINAGOS, "Anthyposminagos rank icon");
            requireResource(UiConstants.ICON_RANK_YPOSMINAGOS, "Yposminagos rank icon");
            requireResource(UiConstants.ICON_RANK_SMINAGOS, "Sminagos rank icon");
            requireResource(UiConstants.ICON_RANK_EPISMINAGOS, "Episminagos rank icon");
            requireResource(UiConstants.ICON_RANK_ANTISMINARCHOS, "Antisminarchos rank icon");
            requireResource(UiConstants.ICON_RANK_SMINARCHOS, "Sminarchos rank icon");
            requireResource(UiConstants.ICON_RANK_TAKSIARCOS, "Taksiarcos rank icon");
            requireResource(UiConstants.ICON_RANK_YPOPTERARCHOS, "Ypopterarchos rank icon");
            requireResource(UiConstants.ICON_RANK_ANTIPTERARCHOS, "Antipterarchos rank icon");
            requireResource(UiConstants.ICON_RANK_PTERARCHOS, "Pterarchos rank icon");
            requireResource(UiConstants.ICON_RANK_DEFAULT, "Default rank icon");

            // Rank Cell Icons
            requireResource(UiConstants.ICON_RANK_ANTHYPASPISTIS_CELL, "Anthypaspistis rank cell icon");
            requireResource(UiConstants.ICON_RANK_ANTHYPOSMINAGOS_CELL, "Anthyposminagos rank cell icon");
            requireResource(UiConstants.ICON_RANK_YPOSMINAGOS_CELL, "Yposminagos rank cell icon");
            requireResource(UiConstants.ICON_RANK_SMINAGOS_CELL, "Sminagos rank cell icon");
            requireResource(UiConstants.ICON_RANK_EPISMINAGOS_CELL, "Episminagos rank cell icon");
            requireResource(UiConstants.ICON_RANK_ANTISMINARCHOS_CELL, "Antisminarchos rank cell icon");
            requireResource(UiConstants.ICON_RANK_SMINARCHOS_CELL, "Sminarchos rank cell icon");
            requireResource(UiConstants.ICON_RANK_TAKSIARCOS_CELL, "Taksiarcos rank cell icon");
            requireResource(UiConstants.ICON_RANK_YPOPTERARCHOS_CELL, "Ypopterarchos rank cell icon");
            requireResource(UiConstants.ICON_RANK_ANTIPTERARCHOS_CELL, "Antipterarchos rank cell icon");
            requireResource(UiConstants.ICON_RANK_PTERARCHOS_CELL, "Pterarchos rank cell icon");

            // Fonts
            requireResource(UiConstants.FONT_MANROPE, "Manrope font");
            requireResource(UiConstants.FONT_MANROPE_BOLD, "Manrope Bold font");
        };
    }

    /**
     * Default implementation for the ResourceChecker.
     *
     * @param path  The resource path
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
     *
     * @return a Runnable that checks server reachability
     */
    private static NetworkChecker defaultNetworkChecker() {
        return () -> {
            ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
            URI serverBaseUri = runtimeConfig.healthCheckBaseUri();

            javax.net.ssl.SSLContext sslContext;
            try {
                sslContext = SslContextUtils.getStrictSslContext();
            } catch (Exception e) {
                throw new IOException("Failed to initialize SSL context for network check", e);
            }

            URI healthEndpoint = serverBaseUri.resolve("/api/v1/health");

            try (
                    HttpClient client = HttpClient.newBuilder()
                            .sslContext(sslContext)
                            .sslParameters(SslContextUtils.createHttpsSslParameters())
                            .connectTimeout(Duration.ofSeconds(2))
                            .build()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(healthEndpoint)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(3))
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 400) {
                    throw new IOException("Server unreachable, status " + response.statusCode());
                }
            } catch (IOException e) {
                throw new IOException("Failed to check server reachability", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while checking server reachability", e);
            }
        };
    }

}
