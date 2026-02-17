package com.haf.client.viewmodels;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RegisterViewModelTest {

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

    private RegisterViewModel vm;

    @BeforeEach
    void setup() {
        vm = new RegisterViewModel();
    }

    private void fillAllFieldsValid() {
        vm.nameProperty().set("Γεώργιος Φασούλας");
        vm.regNumProperty().set("123456");
        vm.idNumProperty().set("Γ-1234");
        vm.rankProperty().set("Σμηνίας");
        vm.phoneNumProperty().set("6971978340");
        vm.emailProperty().set("user@haf.gr");
        vm.passwordProperty().set("strongpass123");
        vm.passwordConfProperty().set("strongpass123");
    }

    @Test
    void validate_all_fields_empty_sets_all_errors() {
        assertFalse(vm.validate());
        assertTrue(vm.nameErrorProperty().get());
        assertTrue(vm.regNumErrorProperty().get());
        assertTrue(vm.idNumErrorProperty().get());
        assertTrue(vm.rankErrorProperty().get());
        assertTrue(vm.phoneNumErrorProperty().get());
        assertTrue(vm.emailErrorProperty().get());
        assertTrue(vm.passwordErrorProperty().get());
        assertTrue(vm.passwordConfErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_ALL_FIELDS_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_all_fields_null_treated_as_empty() {
        vm.nameProperty().set(null);
        vm.regNumProperty().set(null);
        vm.idNumProperty().set(null);
        vm.rankProperty().set(null);
        vm.phoneNumProperty().set(null);
        vm.emailProperty().set(null);
        vm.passwordProperty().set(null);
        vm.passwordConfProperty().set(null);

        assertFalse(vm.validate());
        assertEquals(RegisterViewModel.ERROR_ALL_FIELDS_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_name_empty_sets_name_error() {
        fillAllFieldsValid();
        vm.nameProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.nameErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_NAME_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_reg_num_empty_sets_reg_num_error() {
        fillAllFieldsValid();
        vm.regNumProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.regNumErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_REG_NUM_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_id_num_empty_sets_id_num_error() {
        fillAllFieldsValid();
        vm.idNumProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.idNumErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_ID_NUM_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_rank_empty_sets_rank_error() {
        fillAllFieldsValid();
        vm.rankProperty().set(null);

        assertFalse(vm.validate());
        assertTrue(vm.rankErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_RANK_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_rank_empty_string_sets_rank_error() {
        fillAllFieldsValid();
        vm.rankProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.rankErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_RANK_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_phone_empty_sets_phone_error() {
        fillAllFieldsValid();
        vm.phoneNumProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.phoneNumErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_PHONE_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_email_empty_sets_email_error() {
        fillAllFieldsValid();
        vm.emailProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_EMAIL_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_password_empty_sets_password_error() {
        fillAllFieldsValid();
        vm.passwordProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.passwordErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_PASSWORD_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_password_conf_empty_sets_password_conf_error() {
        fillAllFieldsValid();
        vm.passwordConfProperty().set("");

        assertFalse(vm.validate());
        assertTrue(vm.passwordConfErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_PASSWORD_CONF_EMPTY, vm.getErrorMessage());
    }

    @ParameterizedTest(name = "invalid phone \"{0}\" fails validation")
    @ValueSource(strings = { "697197", "2101234567", "69719783ab", "69719783401" })
    void validate_phone_invalid_formats_fail(String phone) {
        fillAllFieldsValid();
        vm.phoneNumProperty().set(phone);

        assertFalse(vm.validate());
        assertTrue(vm.phoneNumErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_PHONE_INVALID, vm.getErrorMessage());
    }

    @Test
    void validate_email_invalid_format_fails() {
        fillAllFieldsValid();
        vm.emailProperty().set("not-an-email");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_email_wrong_domain_fails() {
        fillAllFieldsValid();
        vm.emailProperty().set("user@gmail.com");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_EMAIL_WRONG_DOMAIN, vm.getErrorMessage());
    }

    @Test
    void validate_email_correct_domain_case_insensitive() {
        fillAllFieldsValid();
        vm.emailProperty().set("user@HAF.GR");

        assertTrue(vm.validate());
    }

    @Test
    void validate_email_with_multiple_at_signs_fails() {
        fillAllFieldsValid();
        vm.emailProperty().set("user@@haf.gr");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_EMAIL_INVALID_FORMAT, vm.getErrorMessage());
    }

    @Test
    void validate_password_too_short_fails() {
        fillAllFieldsValid();
        vm.passwordProperty().set("abc");
        vm.passwordConfProperty().set("abc");

        assertFalse(vm.validate());
        assertTrue(vm.passwordErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_PASSWORD_TOO_SHORT, vm.getErrorMessage());
    }

    @Test
    void validate_password_exactly_min_length_passes() {
        fillAllFieldsValid();
        vm.passwordProperty().set("abcdef"); // 6 chars
        vm.passwordConfProperty().set("abcdef");

        assertTrue(vm.validate());
    }

    @Test
    void validate_passwords_mismatch_fails() {
        fillAllFieldsValid();
        vm.passwordProperty().set("strongpass123");
        vm.passwordConfProperty().set("differentpass");

        assertFalse(vm.validate());
        assertTrue(vm.passwordErrorProperty().get());
        assertTrue(vm.passwordConfErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_PASSWORDS_MISMATCH, vm.getErrorMessage());
    }

    @Test
    void validate_all_valid_fields_passes() {
        fillAllFieldsValid();

        assertTrue(vm.validate());
        assertFalse(vm.nameErrorProperty().get());
        assertFalse(vm.regNumErrorProperty().get());
        assertFalse(vm.idNumErrorProperty().get());
        assertFalse(vm.rankErrorProperty().get());
        assertFalse(vm.phoneNumErrorProperty().get());
        assertFalse(vm.emailErrorProperty().get());
        assertFalse(vm.passwordErrorProperty().get());
        assertFalse(vm.passwordConfErrorProperty().get());
        assertEquals("", vm.getErrorMessage());
    }

    @Test
    void validate_fields_with_whitespace_get_trimmed() {
        fillAllFieldsValid();
        vm.nameProperty().set("  Γεώργιος Φασούλας  ");
        vm.emailProperty().set("  user@haf.gr  ");

        assertTrue(vm.validate());
    }

    @Test
    void clear_errors_resets_all_flags_and_message() {
        // Trigger errors first
        vm.validate();

        // Now clear
        vm.clearErrors();

        assertFalse(vm.nameErrorProperty().get());
        assertFalse(vm.regNumErrorProperty().get());
        assertFalse(vm.idNumErrorProperty().get());
        assertFalse(vm.rankErrorProperty().get());
        assertFalse(vm.phoneNumErrorProperty().get());
        assertFalse(vm.emailErrorProperty().get());
        assertFalse(vm.passwordErrorProperty().get());
        assertFalse(vm.passwordConfErrorProperty().get());
        assertEquals("", vm.getErrorMessage());
    }

    @Test
    void set_registration_error_with_message() {
        vm.setRegistrationError("Custom error");

        assertEquals("Custom error", vm.getErrorMessage());
    }

    @Test
    void set_registration_error_with_null_uses_default() {
        vm.setRegistrationError(null);

        assertEquals(RegisterViewModel.ERROR_REGISTRATION_FAILED, vm.getErrorMessage());
    }

    @Test
    void validate_checks_fields_in_order_name_first() {
        // All empty except password
        vm.passwordProperty().set("validpass");
        vm.passwordConfProperty().set("validpass");

        assertFalse(vm.validate());
        assertTrue(vm.nameErrorProperty().get());
        assertEquals(RegisterViewModel.ERROR_NAME_EMPTY, vm.getErrorMessage());
    }

    @Test
    void validate_email_checked_after_basic_fields() {
        fillAllFieldsValid();
        vm.emailProperty().set("bad-email");

        assertFalse(vm.validate());
        assertTrue(vm.emailErrorProperty().get());
        // Other fields should not have errors
        assertFalse(vm.nameErrorProperty().get());
        assertFalse(vm.regNumErrorProperty().get());
        assertFalse(vm.phoneNumErrorProperty().get());
    }

    @Test
    void validate_passwords_checked_last() {
        fillAllFieldsValid();
        vm.passwordProperty().set("strongpass123");
        vm.passwordConfProperty().set("wrongconfirm");

        assertFalse(vm.validate());
        assertTrue(vm.passwordErrorProperty().get());
        assertTrue(vm.passwordConfErrorProperty().get());
        // Earlier fields should not have errors
        assertFalse(vm.nameErrorProperty().get());
        assertFalse(vm.emailErrorProperty().get());
    }
}
