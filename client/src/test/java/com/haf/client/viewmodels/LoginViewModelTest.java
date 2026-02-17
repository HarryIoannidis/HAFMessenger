package com.haf.client.viewmodels;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginViewModelTest {

    @BeforeAll
    static void initJavaFx() throws Exception {
        // Initialize JavaFX toolkit once for all tests; ignore if already initialized
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // already started
        }
    }

    private LoginViewModel vm;

    @BeforeEach
    void setup() {
        vm = new LoginViewModel();
    }

    @Test
    void validate_both_fields_empty_sets_all_errors() {
        vm.emailProperty().set("");
        vm.passwordProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertTrue(vm.passwordErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_ALL_FIELDS_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_email_empty_sets_email_error() {
        vm.emailProperty().set("");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertFalse(vm.passwordErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_password_empty_sets_password_error() {
        vm.emailProperty().set("user@haf.gr");
        vm.passwordProperty().set("");

        assertFalse(vm.validate());
        assertFalse(vm.emailErrorProperty().get());
        assertTrue(vm.passwordErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_PASSWORD_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_null_fields_treated_as_empty() {
        vm.emailProperty().set(null);
        vm.passwordProperty().set(null);

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertTrue(vm.passwordErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_ALL_FIELDS_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_email_without_at_sign_fails() {
        vm.emailProperty().set("userhaf.gr");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_with_multiple_at_signs_fails() {
        vm.emailProperty().set("user@@haf.gr");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_with_no_local_part_fails() {
        vm.emailProperty().set("@haf.gr");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_with_no_domain_dot_fails() {
        vm.emailProperty().set("user@hafgr");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_domain_starting_with_dot_fails() {
        vm.emailProperty().set("user@.haf.gr");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_domain_ending_with_dot_fails() {
        vm.emailProperty().set("user@haf.gr.");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_wrong_domain_fails() {
        vm.emailProperty().set("user@gmail.com");
        vm.passwordProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_EMAIL_WRONG_DOMAIN, vm.getErrorMessage());
    }

    @Test
    void validate_email_correct_domain_case_insensitive() {
        vm.emailProperty().set("user@HAF.GR");
        vm.passwordProperty().set("validpass");

        assertTrue(vm.validate());
    }

    @Test
    void validate_password_too_short_fails() {
        vm.emailProperty().set("user@haf.gr");
        vm.passwordProperty().set("abc");

        assertFalse(vm.validate());
        assertTrue(vm.passwordErrorProperty().get());
        assertEquals(LoginViewModel.ERROR_PASSWORD_TOO_SHORT, vm.getErrorMessage());
    }

    @Test
    void validate_password_exactly_min_length_passes() {
        vm.emailProperty().set("user@haf.gr");
        vm.passwordProperty().set("abcdef"); // 6 chars

        assertTrue(vm.validate());
    }

    @Test
    void validate_valid_credentials_passes() {
        vm.emailProperty().set("user@haf.gr");
        vm.passwordProperty().set("strongpass123");

        assertTrue(vm.validate());
        assertFalse(vm.emailErrorProperty().get());
        assertFalse(vm.passwordErrorProperty().get());
        assertEquals("", vm.getErrorMessage());
    }

    @Test
    void validate_email_with_whitespace_gets_trimmed() {
        vm.emailProperty().set("  user@haf.gr  ");
        vm.passwordProperty().set("validpass");

        assertTrue(vm.validate());
    }

    @Test
    void clear_errors_resets_all_flags() {
        // Trigger errors first
        vm.emailProperty().set("");
        vm.passwordProperty().set("");
        vm.validate();

        // Now clear
        vm.clearErrors();

        assertFalse(vm.emailErrorProperty().get());
        assertFalse(vm.passwordErrorProperty().get());
        assertEquals("", vm.getErrorMessage());
    }

    @Test
    void toggle_password_visibility_flips_flag() {
        assertFalse(vm.passwordVisibleProperty().get());

        vm.togglePasswordVisibility();
        assertTrue(vm.passwordVisibleProperty().get());

        vm.togglePasswordVisibility();
        assertFalse(vm.passwordVisibleProperty().get());
    }

    @Test
    void set_login_error_with_message() {
        vm.setLoginError("Custom error");

        assertEquals("Custom error", vm.getErrorMessage());
    }

    @Test
    void set_login_error_with_null_uses_default() {
        vm.setLoginError(null);

        assertEquals(LoginViewModel.ERROR_LOGIN_FAILED, vm.getErrorMessage());
    }
}
