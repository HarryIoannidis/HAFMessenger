package com.haf.client.viewmodels;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import java.io.File;

public class RegisterViewModel {

    // Allowed email domain
    private static final String ALLOWED_DOMAIN = "@haf.gr";
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
    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty regNumProperty() {
        return regNum;
    }

    public StringProperty idNumProperty() {
        return idNum;
    }

    public StringProperty rankProperty() {
        return rank;
    }

    public StringProperty phoneNumProperty() {
        return phoneNum;
    }

    public StringProperty emailProperty() {
        return email;
    }

    public StringProperty passwordProperty() {
        return password;
    }

    public StringProperty passwordConfProperty() {
        return passwordConf;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public BooleanProperty nameErrorProperty() {
        return nameError;
    }

    public BooleanProperty regNumErrorProperty() {
        return regNumError;
    }

    public BooleanProperty idNumErrorProperty() {
        return idNumError;
    }

    public BooleanProperty rankErrorProperty() {
        return rankError;
    }

    public BooleanProperty phoneNumErrorProperty() {
        return phoneNumError;
    }

    public BooleanProperty emailErrorProperty() {
        return emailError;
    }

    public BooleanProperty passwordErrorProperty() {
        return passwordError;
    }

    public BooleanProperty passwordConfErrorProperty() {
        return passwordConfError;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    private final BooleanProperty passwordVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty passwordConfVisible = new SimpleBooleanProperty(false);

    public BooleanProperty passwordVisibleProperty() {
        return passwordVisible;
    }

    public BooleanProperty passwordConfVisibleProperty() {
        return passwordConfVisible;
    }

    public void togglePasswordVisibility() {
        passwordVisible.set(!passwordVisible.get());
    }

    public void togglePasswordConfVisibility() {
        passwordConfVisible.set(!passwordConfVisible.get());
    }

    public ObjectProperty<File> idPhotoFileProperty() {
        return idPhotoFile;
    }

    public ObjectProperty<File> selfiePhotoFileProperty() {
        return selfiePhotoFile;
    }

    // Getters
    public String getName() {
        return name.get();
    }

    public String getRegNum() {
        return regNum.get();
    }

    public String getIdNum() {
        return idNum.get();
    }

    public String getRank() {
        return rank.get();
    }

    public String getPhoneNum() {
        return phoneNum.get();
    }

    public String getEmail() {
        return email.get();
    }

    public String getPassword() {
        return password.get();
    }

    public String getPasswordConf() {
        return passwordConf.get();
    }

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
        if (!emailVal.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
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

    public boolean validateCredentials() {
        return validate();
    }

    public boolean validateIdPhoto() {
        if (idPhotoFile.get() == null) {
            errorMessage.set(ERROR_ID_PHOTO_MISSING);
            return false;
        }
        return true;
    }

    public boolean validateSelfiePhoto() {
        if (selfiePhotoFile.get() == null) {
            errorMessage.set(ERROR_SELFIE_PHOTO_MISSING);
            return false;
        }
        return true;
    }

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
