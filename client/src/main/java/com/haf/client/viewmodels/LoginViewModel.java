package com.haf.client.viewmodels;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents UI state and validation logic for the login form.
 */
public class LoginViewModel {

    // Allowed email domain
    private static final int MIN_PASSWORD_LENGTH = 6;

    public static final String ERROR_EMAIL_EMPTY = "Please enter your email.";
    public static final String ERROR_PASSWORD_EMPTY = "Please enter your password.";
    public static final String ERROR_ALL_FIELDS_EMPTY = "Please fill in all fields.";
    public static final String ERROR_EMAIL_INVALID_FORMAT = "Invalid email format.";
    public static final String ERROR_EMAIL_WRONG_DOMAIN = "Email must belong to the @haf.gr domain.";
    public static final String ERROR_PASSWORD_TOO_SHORT = "Password must be at least " + MIN_PASSWORD_LENGTH
            + " characters.";
    public static final String ERROR_LOGIN_FAILED = "Login failed. Please check your credentials.";

    // Observable properties
    private final StringProperty email = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final BooleanProperty rememberCredentials = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty emailError = new SimpleBooleanProperty(false);
    private final BooleanProperty passwordError = new SimpleBooleanProperty(false);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty passwordVisible = new SimpleBooleanProperty(false);

    /**
     * Exposes the email input property used by the login form.
     *
     * @return observable email property
     */
    public StringProperty emailProperty() {
        return email;
    }

    /**
     * Exposes the password input property used by the login form.
     *
     * @return observable password property
     */
    public StringProperty passwordProperty() {
        return password;
    }

    /**
     * Exposes the remember-me toggle property.
     *
     * @return observable remember-credentials property
     */
    public BooleanProperty rememberCredentialsProperty() {
        return rememberCredentials;
    }

    /**
     * Exposes the login error message shown in the UI.
     *
     * @return observable error message property
     */
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /**
     * Exposes whether the email field is currently in an error state.
     *
     * @return observable email-error property
     */
    public BooleanProperty emailErrorProperty() {
        return emailError;
    }

    /**
     * Exposes whether the password field is currently in an error state.
     *
     * @return observable password-error property
     */
    public BooleanProperty passwordErrorProperty() {
        return passwordError;
    }

    /**
     * Exposes whether a login operation is in progress.
     *
     * @return observable loading property
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Exposes whether the password should be shown in plain text.
     *
     * @return observable password-visibility property
     */
    public BooleanProperty passwordVisibleProperty() {
        return passwordVisible;
    }

    /**
     * Returns the current email value.
     *
     * @return email value entered by the user
     */
    public String getEmail() {
        return email.get();
    }

    /**
     * Updates the email value.
     *
     * @param value new email value
     */
    public void setEmail(String value) {
        email.set(value);
    }

    /**
     * Returns the current password value.
     *
     * @return password value entered by the user
     */
    public String getPassword() {
        return password.get();
    }

    /**
     * Updates the password value.
     *
     * @param value new password value
     */
    public void setPassword(String value) {
        password.set(value);
    }

    /**
     * Indicates whether remember-credentials is enabled.
     *
     * @return {@code true} when remember-me is selected
     */
    public boolean isRememberCredentials() {
        return rememberCredentials.get();
    }

    /**
     * Returns the currently displayed error message.
     *
     * @return login error message text
     */
    public String getErrorMessage() {
        return errorMessage.get();
    }

    /**
     * Validates all login fields.
     *
     * @return true if all fields are valid, false otherwise
     */
    public boolean validate() {
        clearErrors();

        String emailValue = email.get() == null ? "" : email.get().trim();
        String passwordValue = password.get() == null ? "" : password.get();

        boolean emailEmpty = emailValue.isEmpty();
        boolean passwordEmpty = passwordValue.isEmpty();

        // Both fields empty
        if (emailEmpty && passwordEmpty) {
            emailError.set(true);
            passwordError.set(true);
            errorMessage.set(ERROR_ALL_FIELDS_EMPTY);
            return false;
        }

        // Email empty
        if (emailEmpty) {
            emailError.set(true);
            errorMessage.set(ERROR_EMAIL_EMPTY);
            return false;
        }

        // Password empty
        if (passwordEmpty) {
            passwordError.set(true);
            errorMessage.set(ERROR_PASSWORD_EMPTY);
            return false;
        }

        // Email format validation — must contain exactly one '@' and have content
        // before it
        if (!isValidEmailFormat(emailValue)) {
            emailError.set(true);
            errorMessage.set(ERROR_EMAIL_INVALID_FORMAT);
            return false;
        }

        // Email domain validation — must end with @haf.gr
        if (!emailValue.toLowerCase().endsWith("@haf.gr")) {
            emailError.set(true);
            errorMessage.set(ERROR_EMAIL_WRONG_DOMAIN);
            return false;
        }

        // Password minimum length
        if (passwordValue.length() < MIN_PASSWORD_LENGTH) {
            passwordError.set(true);
            errorMessage.set(ERROR_PASSWORD_TOO_SHORT);
            return false;
        }

        return true;
    }

    /**
     * Basic email format check.
     * Validates that the email contains exactly one '@', has content before and
     * after it, and the domain part contains at least one dot.
     *
     * @param email the email string to validate
     * @return true if the format is acceptable
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        int atIndex = email.indexOf('@');
        int lastAtIndex = email.lastIndexOf('@');

        // Must contain exactly one '@'
        if (atIndex <= 0 || atIndex != lastAtIndex) {
            return false;
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);

        // The local part must not be empty, the domain part must contain a dot
        if (localPart.isBlank() || domainPart.isBlank() || !domainPart.contains(".")) {
            return false;
        }

        // Domain part must not start/end with a dot
        return !domainPart.startsWith(".") && !domainPart.endsWith(".");
    }

    /**
     * Clears all error states and messages.
     */
    public void clearErrors() {
        emailError.set(false);
        passwordError.set(false);
        errorMessage.set("");
    }

    /**
     * Toggles password visibility.
     */
    public void togglePasswordVisibility() {
        passwordVisible.set(!passwordVisible.get());
    }

    /**
     * Sets a login failure error message.
     *
     * @param message the error message to display
     */
    public void setLoginError(String message) {
        errorMessage.set(message != null ? message : ERROR_LOGIN_FAILED);
    }
}
