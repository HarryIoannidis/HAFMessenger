package com.haf.server.ingress;

import org.java_websocket.WebSocket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LoginPresenceGuardTest {

    @Test
    void duplicate_login_attempt_is_true_when_same_user_is_active() {
        PresenceRegistry presenceRegistry = new PresenceRegistry();
        presenceRegistry.registerConnection("user-1", mock(WebSocket.class));

        assertTrue(HttpIngressServer.isDuplicateLoginAttempt(presenceRegistry, "user-1"));
    }

    @Test
    void duplicate_login_attempt_is_false_when_user_is_offline() {
        PresenceRegistry presenceRegistry = new PresenceRegistry();

        assertFalse(HttpIngressServer.isDuplicateLoginAttempt(presenceRegistry, "user-1"));
    }

    @Test
    void duplicate_login_attempt_is_false_when_different_user_is_active() {
        PresenceRegistry presenceRegistry = new PresenceRegistry();
        presenceRegistry.registerConnection("user-2", mock(WebSocket.class));

        assertFalse(HttpIngressServer.isDuplicateLoginAttempt(presenceRegistry, "user-1"));
    }
}
