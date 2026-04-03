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

    /**
     * Performs explicit account takeover by rotating local key material and
     * re-authenticating.
     *
     * @param command login credentials
     * @return result of takeover flow
     */
    LoginResult performKeyTakeover(LoginCommand command);

    record LoginCommand(String email, String password) {
    }

    /**
     * Typed reason for takeover-required login outcomes.
     */
    enum TakeoverReason {
        DUPLICATE_SESSION,
        KEY_MISMATCH
    }

    sealed interface LoginResult permits LoginResult.Success, LoginResult.Rejected, LoginResult.Failure,
            LoginResult.TakeoverRequired {
        record Success() implements LoginResult {
        }

        record Rejected(String message) implements LoginResult {
        }

        record Failure(String message) implements LoginResult {
        }

        record TakeoverRequired(TakeoverReason reason, String message) implements LoginResult {
        }
    }
}
