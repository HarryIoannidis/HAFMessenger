package com.haf.client.services;

/**
 * Application service responsible for authenticating a user and bootstrapping
 * the secure messaging session.
 */
public interface LoginService {

    /**
     * Attempts login and secure-session initialization.
     *
     * @param command login credentials
     * @return result of the login flow
     */
    LoginResult login(LoginCommand command);

    record LoginCommand(String email, String password) {
    }

    sealed interface LoginResult permits LoginResult.Success, LoginResult.Rejected, LoginResult.Failure {
        record Success() implements LoginResult {
        }

        record Rejected(String message) implements LoginResult {
        }

        record Failure(String message) implements LoginResult {
        }
    }
}
