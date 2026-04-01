package com.haf.client.viewmodels;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import java.io.File;

/**
 * Represents UI state and validation logic for the registration form.
 */
public class RegisterViewModel {

    // Allowed email domain
    private static final int MIN_PASSWORD_LENGTH = 6;

    // Error messages
    public static final String ERROR_ALL_FIELDS_EMPTY = "Please fill in all fields.";
    public static final String ERROR_NAME_EMPTY = "Please enter your full name.";
    public static final String ERROR_REG_NUM_EMPTY = "Please enter your registration number.";
    public static final String ERROR_ID_NUM_EMPTY = "Please enter your ID number.";
    public static final String ERROR_RANK_EMPTY = "Please select your rank.";
    public static final String ERROR_PHONE_EMPTY = "Please enter your phone number.";
    public static final String ERROR_PHONE_INVALID = "Phone number must be 10 digits starting with 69.";
    public static final String ERROR_EMAIL_EMPTY = "Please enter your email.";
    public static final String ERROR_EMAIL_INVALID_FORMAT = "Invalid email format.";
    public static final String ERROR_EMAIL_WRONG_DOMAIN = "Email must belong to the @haf.gr domain.";
    public static final String ERROR_PASSWORD_EMPTY = "Please enter a password.";
    public static final String ERROR_PASSWORD_TOO_SHORT = "Password must be at least " + MIN_PASSWORD_LENGTH
            + " characters.";
    public static final String ERROR_PASSWORD_CONF_EMPTY = "Please confirm your password.";
    public static final String ERROR_PASSWORDS_MISMATCH = "Passwords do not match.";

    public static final String ERROR_ID_PHOTO_MISSING = "Please upload your ID photo.";
    public static final String ERROR_SELFIE_PHOTO_MISSING = "Please upload your selfie.";
    public static final String ERROR_FILE_TOO_LARGE = "File is too large (max 10MB).";
    public static final String ERROR_INVALID_FILE_TYPE = "Invalid file type. Only images are allowed.";
    public static final String ERROR_REGISTRATION_FAILED = "Registration failed. Please try again.";

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    // Observable properties
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty regNum = new SimpleStringProperty("");
    private final StringProperty idNum = new SimpleStringProperty("");
    private final StringProperty rank = new SimpleStringProperty(null);
    private final StringProperty phoneNum = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty passwordConf = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    // Error flags for each field
    private final BooleanProperty nameError = new SimpleBooleanProperty(false);
    private final BooleanProperty regNumError = new SimpleBooleanProperty(false);
    private final BooleanProperty idNumError = new SimpleBooleanProperty(false);
    private final BooleanProperty rankError = new SimpleBooleanProperty(false);
    private final BooleanProperty phoneNumError = new SimpleBooleanProperty(false);
    private final BooleanProperty emailError = new SimpleBooleanProperty(false);
    private final BooleanProperty passwordError = new SimpleBooleanProperty(false);
    private final BooleanProperty passwordConfError = new SimpleBooleanProperty(false);

    private final ObjectProperty<File> idPhotoFile = new SimpleObjectProperty<>();
    private final ObjectProperty<File> selfiePhotoFile = new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    // Property accessors
    /**
     * Exposes full-name input property.
     *
     * @return observable name property
     */
    public StringProperty nameProperty() {
        return name;
    }

    /**
     * Exposes registration-number input property.
     *
     * @return observable registration-number property
     */
    public StringProperty regNumProperty() {
        return regNum;
    }

    /**
     * Exposes id-number input property.
     *
     * @return observable id-number property
     */
    public StringProperty idNumProperty() {
        return idNum;
    }

    /**
     * Exposes selected rank property.
     *
     * @return observable rank property
     */
    public StringProperty rankProperty() {
        return rank;
    }

    /**
     * Exposes phone-number input property.
     *
     * @return observable phone-number property
     */
    public StringProperty phoneNumProperty() {
        return phoneNum;
    }

    /**
     * Exposes email input property.
     *
     * @return observable email property
     */
    public StringProperty emailProperty() {
        return email;
    }

    /**
     * Exposes password input property.
     *
     * @return observable password property
     */
    public StringProperty passwordProperty() {
        return password;
    }

    /**
     * Exposes password-confirmation input property.
     *
     * @return observable password-confirmation property
     */
    public StringProperty passwordConfProperty() {
        return passwordConf;
    }

    /**
     * Exposes registration error message property.
     *
     * @return observable error-message property
     */
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /**
     * Exposes name-field error flag.
     *
     * @return observable name-error property
     */
    public BooleanProperty nameErrorProperty() {
        return nameError;
    }

    /**
     * Exposes registration-number error flag.
     *
     * @return observable registration-number error property
     */
    public BooleanProperty regNumErrorProperty() {
        return regNumError;
    }

    /**
     * Exposes id-number error flag.
     *
     * @return observable id-number error property
     */
    public BooleanProperty idNumErrorProperty() {
        return idNumError;
    }

    /**
     * Exposes rank-selection error flag.
     *
     * @return observable rank error property
     */
    public BooleanProperty rankErrorProperty() {
        return rankError;
    }

    /**
     * Exposes phone-number error flag.
     *
     * @return observable phone error property
     */
    public BooleanProperty phoneNumErrorProperty() {
        return phoneNumError;
    }

    /**
     * Exposes email error flag.
     *
     * @return observable email error property
     */
    public BooleanProperty emailErrorProperty() {
        return emailError;
    }

    /**
     * Exposes password error flag.
     *
     * @return observable password error property
     */
    public BooleanProperty passwordErrorProperty() {
        return passwordError;
    }

    /**
     * Exposes password-confirmation error flag.
     *
     * @return observable password-confirmation error property
     */
    public BooleanProperty passwordConfErrorProperty() {
        return passwordConfError;
    }

    /**
     * Exposes registration loading flag.
     *
     * @return observable loading property
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    private final BooleanProperty passwordVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty passwordConfVisible = new SimpleBooleanProperty(false);

    /**
     * Exposes password-visibility state.
     *
     * @return observable password visibility property
     */
    public BooleanProperty passwordVisibleProperty() {
        return passwordVisible;
    }

    /**
     * Exposes confirm-password visibility state.
     *
     * @return observable confirm-password visibility property
     */
    public BooleanProperty passwordConfVisibleProperty() {
        return passwordConfVisible;
    }

    /**
     * Toggles password visibility state.
     */
    public void togglePasswordVisibility() {
        passwordVisible.set(!passwordVisible.get());
    }

    /**
     * Toggles confirm-password visibility state.
     */
    public void togglePasswordConfVisibility() {
        passwordConfVisible.set(!passwordConfVisible.get());
    }

    /**
     * Exposes selected ID photo file property.
     *
     * @return observable ID-photo file property
     */
    public ObjectProperty<File> idPhotoFileProperty() {
        return idPhotoFile;
    }

    /**
     * Exposes selected selfie photo file property.
     *
     * @return observable selfie-photo file property
     */
    public ObjectProperty<File> selfiePhotoFileProperty() {
        return selfiePhotoFile;
    }

    // Getters
    /**
     * Returns current full-name value.
     *
     * @return entered full name
     */
    public String getName() {
        return name.get();
    }

    /**
     * Returns current registration-number value.
     *
     * @return entered registration number
     */
    public String getRegNum() {
        return regNum.get();
    }

    /**
     * Returns current id-number value.
     *
     * @return entered id number
     */
    public String getIdNum() {
        return idNum.get();
    }

    /**
     * Returns current rank value.
     *
     * @return selected rank
     */
    public String getRank() {
        return rank.get();
    }

    /**
     * Returns current phone-number value.
     *
     * @return entered phone number
     */
    public String getPhoneNum() {
        return phoneNum.get();
    }

    /**
     * Returns current email value.
     *
     * @return entered email address
     */
    public String getEmail() {
        return email.get();
    }

    /**
     * Returns current password value.
     *
     * @return entered password
     */
    public String getPassword() {
        return password.get();
    }

    /**
     * Returns current password-confirmation value.
     *
     * @return entered password-confirmation text
     */
    public String getPasswordConf() {
        return passwordConf.get();
    }

    /**
     * Returns current validation/registration error message.
     *
     * @return error message text
     */
    public String getErrorMessage() {
        return errorMessage.get();
    }

    /**
     * Validates all registration fields.
     *
     * @return true if all fields are valid, false otherwise
     */
    public boolean validate() {
        clearErrors();

        String nameVal = getOrDefault(name);
        String regNumVal = getOrDefault(regNum);
        String idNumVal = getOrDefault(idNum);
        String rankVal = rank.get();
        String phoneVal = getOrDefault(phoneNum);
        String emailVal = getOrDefault(email);
        String passVal = password.get() == null ? "" : password.get();
        String passConfVal = passwordConf.get() == null ? "" : passwordConf.get();

        boolean hasError = false;

        // Check required fields and mark them individually
        if (nameVal.isEmpty()) {
            nameError.set(true);
            hasError = true;
        }
        if (regNumVal.isEmpty()) {
            regNumError.set(true);
            hasError = true;
        }
        if (idNumVal.isEmpty()) {
            idNumError.set(true);
            hasError = true;
        }
        if (rankVal == null || rankVal.isEmpty()) {
            rankError.set(true);
            hasError = true;
        }
        if (phoneVal.isEmpty()) {
            phoneNumError.set(true);
            hasError = true;
        }
        if (emailVal.isEmpty()) {
            emailError.set(true);
            hasError = true;
        }
        if (passVal.isEmpty()) {
            passwordError.set(true);
            hasError = true;
        }
        if (passConfVal.isEmpty()) {
            passwordConfError.set(true);
            hasError = true;
        }

        if (hasError) {
            errorMessage.set(ERROR_ALL_FIELDS_EMPTY);
            return false;
        }

        // Specific validations (only if fields are not empty)
        if (!isValidPhoneNumber(phoneVal)) {
            return setFieldError(phoneNumError, ERROR_PHONE_INVALID);
        }
        if (!validateEmail(emailVal)) {
            return false;
        }
        return validatePasswords(passVal, passConfVal);
    }

    /**
     * Helper method to get the value of a StringProperty, trimming whitespace and
     * returning an empty string if the value is null.
     * 
     * @param prop The StringProperty to get the value from.
     * @return The value of the StringProperty, trimmed of whitespace, or an empty
     *         string if the value is null.
     */
    private String getOrDefault(StringProperty prop) {
        return prop.get() == null ? "" : prop.get().trim();
    }

    /**
     * Helper method to set an error flag and the error message.
     * 
     * @param errorFlag The BooleanProperty to set to true.
     * @param message   The error message to set.
     * @return false always.
     */
    private boolean setFieldError(BooleanProperty errorFlag, String message) {
        errorFlag.set(true);
        errorMessage.set(message);
        return false;
    }

    /**
     * Validates the email address format and domain.
     * 
     * @param emailVal The email address to validate.
     * @return true if the email address is valid, false otherwise.
     */
    private boolean validateEmail(String emailVal) {
        if (!isValidEmailFormat(emailVal)) {
            return setFieldError(emailError, ERROR_EMAIL_INVALID_FORMAT);
        }
        if (!emailVal.toLowerCase().endsWith("@haf.gr")) {
            return setFieldError(emailError, ERROR_EMAIL_WRONG_DOMAIN);
        }
        return true;
    }

    /**
     * Validates the password and password confirmation fields.
     * 
     * @param passVal     The password to validate.
     * @param passConfVal The password confirmation to validate.
     * @return true if the password and password confirmation are valid, false
     *         otherwise.
     */
    private boolean validatePasswords(String passVal, String passConfVal) {
        if (passVal.length() < MIN_PASSWORD_LENGTH) {
            return setFieldError(passwordError, ERROR_PASSWORD_TOO_SHORT);
        }

        if (!passVal.equals(passConfVal)) {
            passwordError.set(true);
            passwordConfError.set(true);
            errorMessage.set(ERROR_PASSWORDS_MISMATCH);
            return false;
        }
        return true;

    }

    /**
     * Validates the credentials step fields.
     *
     * @return {@code true} when credential fields are valid
     */
    public boolean validateCredentials() {
        return validate();
    }

    /**
     * Validates whether ID-photo step has a selected file.
     *
     * @return {@code true} when ID photo has been selected
     */
    public boolean validateIdPhoto() {
        if (idPhotoFile.get() == null) {
            errorMessage.set(ERROR_ID_PHOTO_MISSING);
            return false;
        }
        return true;
    }

    /**
     * Validates whether selfie-photo step has a selected file.
     *
     * @return {@code true} when selfie photo has been selected
     */
    public boolean validateSelfiePhoto() {
        if (selfiePhotoFile.get() == null) {
            errorMessage.set(ERROR_SELFIE_PHOTO_MISSING);
            return false;
        }
        return true;
    }

    /**
     * Validates selected photo file against size/type restrictions.
     *
     * @param file selected file
     * @return {@code true} when file satisfies validation constraints
     */
    public boolean validateFile(File file) {
        if (file == null) {
            return false;
        }
        if (file.length() > MAX_FILE_SIZE_BYTES) {
            errorMessage.set(ERROR_FILE_TOO_LARGE);
            return false;
        }
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
            errorMessage.set(ERROR_INVALID_FILE_TYPE);
            return false;
        }
        return true;
    }

    /**
     * Basic phone number validation.
     * Must be exactly 10 digits and start with "69".
     *
     * @param phone the phone string to validate
     * @return true if valid
     */
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.length() != 10) {
            return false;
        }
        if (!phone.startsWith("69")) {
            return false;
        }
        for (char c : phone.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
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

        // Local part must not be empty, domain part must contain a dot
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
        nameError.set(false);
        regNumError.set(false);
        idNumError.set(false);
        rankError.set(false);
        phoneNumError.set(false);
        emailError.set(false);
        passwordError.set(false);
        passwordConfError.set(false);
        errorMessage.set("");
    }

    /**
     * Sets a registration failure error message.
     *
     * @param message the error message to display
     */
    public void setRegistrationError(String message) {
        errorMessage.set(message != null ? message : ERROR_REGISTRATION_FAILED);
    }
}
