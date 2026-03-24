package com.haf.client.controllers;

import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.PopupMessageSpec;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.SplashViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.security.GeneralSecurityException;

/**
 * Controller for the Splash view ({@code splash.fxml}).
 */
public class SplashController {

    record FailurePresentation(String title, String message) {
    }

    // Splash container and branding
    @FXML
    private StackPane rootContainer;
    @FXML
    private ImageView logo;
    @FXML
    private Text title;
    @FXML
    private Text subtitle;

    // Startup status indicators
    @FXML
    private Text status;
    @FXML
    private Text percentage;
    @FXML
    private Text version;
    @FXML
    private ProgressBar progressBar;

    private final SplashViewModel viewModel = SplashViewModel.createDefault();

    /**
     * Initializes bindings and starts bootstrap flow.
     */
    @FXML
    public void initialize() {
        bindViewModel();
        viewModel.startBootstrap(this::navigateToLogin, this::showFailureDialog);
    }

    /**
     * Binds ViewModel properties to UI components.
     */
    private void bindViewModel() {
        status.textProperty().bind(viewModel.statusProperty());
        progressBar.progressProperty().bind(viewModel.progressProperty());
        percentage.textProperty().bind(viewModel.percentageProperty());
        version.textProperty().bind(viewModel.versionProperty());

        progressBar.visibleProperty().bind(viewModel.errorProperty().not());
        progressBar.managedProperty().bind(viewModel.errorProperty().not());
        percentage.visibleProperty().bind(viewModel.errorProperty().not());
        percentage.managedProperty().bind(viewModel.errorProperty().not());
    }

    /**
     * Navigates to the Login screen.
     */
    private void navigateToLogin() {
        ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN);
    }

    /**
     * Displays a failure popup with retry/exit options.
     *
     * @param error the Throwable containing the error details
     */
    private void showFailureDialog(Throwable error) {
        PopupMessageSpec spec = buildFailurePopupSpec(
                error,
                () -> viewModel.startBootstrap(this::navigateToLogin, this::showFailureDialog),
                ViewRouter::close);

        PopupMessageBuilder.create()
                .popupKey(spec.popupKey())
                .title(spec.title())
                .message(spec.message())
                .actionText(spec.actionText())
                .cancelText(spec.cancelText())
                .showCancel(spec.showCancel())
                .dangerAction(spec.dangerAction())
                .onAction(spec.onAction())
                .onCancel(spec.onCancel())
                .show();
    }

    /**
     * Creates a standardized startup-failure popup specification.
     *
     * @param error bootstrap error that should be presented
     * @param onRetry callback executed when the user retries startup
     * @param onExit callback executed when the user exits the app
     * @return popup spec tailored to the failure category
     */
    static PopupMessageSpec buildFailurePopupSpec(Throwable error, Runnable onRetry, Runnable onExit) {
        FailurePresentation presentation = classifyFailure(error);
        return new PopupMessageSpec(
                UiConstants.POPUP_SPLASH_FAILURE,
                presentation.title(),
                presentation.message(),
                "Retry",
                "Exit",
                true,
                false,
                onRetry,
                onExit);
    }

    /**
     * Classifies startup failures into user-friendly categories/messages.
     *
     * @param error raw startup error
     * @return title/message pair suitable for popup presentation
     */
    static FailurePresentation classifyFailure(Throwable error) {
        Throwable root = rootCause(error);
        String details = root == null || root.getMessage() == null || root.getMessage().isBlank()
                ? "No additional details available."
                : root.getMessage();

        String normalized = details.toLowerCase();

        if (isNetworkFailure(root, normalized)) {
            return new FailurePresentation(
                    "Cannot reach server",
                    "Network reachability check failed. " + details);
        }

        if (isResourceFailure(normalized)) {
            return new FailurePresentation(
                    "Application files missing",
                    "Some required local resources are unavailable. " + details);
        }

        if (isSecurityFailure(root, normalized)) {
            return new FailurePresentation(
                    "Security initialization failed",
                    "Security modules could not be initialized. " + details);
        }

        return new FailurePresentation(
                "Startup failed",
                "Initialization could not complete. " + details);
    }

    /**
     * Determines whether a startup failure is network-related.
     *
     * @param root root throwable for type checks
     * @param normalizedMessage lowercased error message text
     * @return {@code true} when the failure appears to be connectivity-related
     */
    private static boolean isNetworkFailure(Throwable root, String normalizedMessage) {
        if (root instanceof ConnectException
                || root instanceof UnknownHostException
                || root instanceof SocketTimeoutException
                || root instanceof HttpTimeoutException) {
            return true;
        }

        return normalizedMessage.contains("network")
                || normalizedMessage.contains("reachability")
                || normalizedMessage.contains("server unreachable")
                || normalizedMessage.contains("connection")
                || normalizedMessage.contains("timeout")
                || normalizedMessage.contains("refused");
    }

    /**
     * Determines whether a startup failure indicates missing local resources.
     *
     * @param normalizedMessage lowercased error message text
     * @return {@code true} when failure text indicates missing FXML/assets/resources
     */
    private static boolean isResourceFailure(String normalizedMessage) {
        return normalizedMessage.contains("missing at")
                || normalizedMessage.contains("resource")
                || normalizedMessage.contains("/fxml/")
                || normalizedMessage.contains("/images/")
                || normalizedMessage.contains("/css/");
    }

    /**
     * Determines whether a startup failure indicates security/crypto setup issues.
     *
     * @param root root throwable for type checks
     * @param normalizedMessage lowercased error message text
     * @return {@code true} when failure appears security-related
     */
    private static boolean isSecurityFailure(Throwable root, String normalizedMessage) {
        if (root instanceof GeneralSecurityException) {
            return true;
        }

        return normalizedMessage.contains("security")
                || normalizedMessage.contains("ssl")
                || normalizedMessage.contains("cipher")
                || normalizedMessage.contains("keystore")
                || normalizedMessage.contains("key agreement");
    }

    /**
     * Unwraps nested throwables to the deepest available cause.
     *
     * @param error throwable to inspect
     * @return deepest cause, or {@code null} when input is {@code null}
     */
    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
