package com.haf.client.services;

import java.io.File;

/**
 * Application service responsible for end-to-end registration flow.
 */
public interface RegistrationService {

    /**
     * Attempts to register a new user.
     *
     * @param command registration inputs
     * @return result of the registration flow
     */
    RegistrationResult register(RegistrationCommand command);

    record RegistrationCommand(
            String name,
            String regNum,
            String idNum,
            String rank,
            String phone,
            String email,
            String password,
            File idPhoto,
            File selfiePhoto) {
    }

    sealed interface RegistrationResult
            permits RegistrationResult.Success, RegistrationResult.Rejected, RegistrationResult.Failure {
        record Success(String userId) implements RegistrationResult {
        }

        record Rejected(String message) implements RegistrationResult {
        }

        record Failure(String message) implements RegistrationResult {
        }
    }
}
