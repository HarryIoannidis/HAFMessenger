package com.haf.server.ingress;

import com.haf.server.metrics.AuditLogger;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PresencePushTest {

        @Test
        void push_contact_presence_to_requester_sends_active_true_when_contact_online() {
                PresenceRegistry presenceRegistry = new PresenceRegistry();
                AuditLogger auditLogger = mock(AuditLogger.class);
                WebSocket requesterConnection = mock(WebSocket.class);
                WebSocket onlineContactConnection = mock(WebSocket.class);

                presenceRegistry.registerConnection("requester", requesterConnection);
                presenceRegistry.registerConnection("contact-online", onlineContactConnection);

                HttpIngressServer.pushContactPresenceToRequester(
                                presenceRegistry,
                                auditLogger,
                                "req-1",
                                "requester",
                                "contact-online");

                verify(requesterConnection, times(1))
                                .send("{\"type\":\"presence\",\"userId\":\"contact-online\",\"active\":true,\"hidden\":false}");
                verify(auditLogger, never()).logError(any(), any(), any(), any(Throwable.class), anyMap());
        }

        @Test
        void push_contact_presence_to_requester_sends_active_false_when_contact_offline() {
                PresenceRegistry presenceRegistry = new PresenceRegistry();
                AuditLogger auditLogger = mock(AuditLogger.class);
                WebSocket requesterConnection = mock(WebSocket.class);

                presenceRegistry.registerConnection("requester", requesterConnection);

                HttpIngressServer.pushContactPresenceToRequester(
                                presenceRegistry,
                                auditLogger,
                                "req-2",
                                "requester",
                                "contact-offline");

                verify(requesterConnection, times(1))
                                .send("{\"type\":\"presence\",\"userId\":\"contact-offline\",\"active\":false,\"hidden\":false}");
                verify(auditLogger, never()).logError(any(), any(), any(), any(Throwable.class), anyMap());
        }

        @Test
        void push_contact_presence_to_requester_masks_activity_when_contact_hides_presence() {
                PresenceRegistry presenceRegistry = new PresenceRegistry();
                AuditLogger auditLogger = mock(AuditLogger.class);
                WebSocket requesterConnection = mock(WebSocket.class);
                WebSocket hiddenContactConnection = mock(WebSocket.class);

                presenceRegistry.registerConnection("requester", requesterConnection);
                presenceRegistry.registerConnection("contact-hidden", hiddenContactConnection);
                presenceRegistry.setPresenceHidden("contact-hidden", true);

                HttpIngressServer.pushContactPresenceToRequester(
                                presenceRegistry,
                                auditLogger,
                                "req-hidden",
                                "requester",
                                "contact-hidden");

                verify(requesterConnection, times(1))
                                .send("{\"type\":\"presence\",\"userId\":\"contact-hidden\",\"active\":false,\"hidden\":true}");
                verify(auditLogger, never()).logError(any(), any(), any(), any(Throwable.class), anyMap());
        }

        @Test
        void push_contact_presence_to_requester_is_noop_without_requester_connections() {
                PresenceRegistry presenceRegistry = new PresenceRegistry();
                AuditLogger auditLogger = mock(AuditLogger.class);
                WebSocket onlineContactConnection = mock(WebSocket.class);

                presenceRegistry.registerConnection("contact-online", onlineContactConnection);

                HttpIngressServer.pushContactPresenceToRequester(
                                presenceRegistry,
                                auditLogger,
                                "req-3",
                                "requester",
                                "contact-online");

                verify(auditLogger, never()).logError(any(), any(), any(), any(Throwable.class), anyMap());
        }

        @Test
        void push_contact_presence_to_requester_logs_send_failures_without_throwing() {
                PresenceRegistry presenceRegistry = new PresenceRegistry();
                AuditLogger auditLogger = mock(AuditLogger.class);
                WebSocket requesterConnection = mock(WebSocket.class);

                presenceRegistry.registerConnection("requester", requesterConnection);
                doThrow(new RuntimeException("send failed")).when(requesterConnection).send(anyString());

                HttpIngressServer.pushContactPresenceToRequester(
                                presenceRegistry,
                                auditLogger,
                                "req-4",
                                "requester",
                                "contact-offline");

                verify(auditLogger, times(1)).logError(
                                eq("contacts_presence_push_error"),
                                eq("req-4"),
                                eq("requester"),
                                any(Throwable.class),
                                anyMap());
        }

        @Test
        void presence_json_escapes_quotes_and_backslashes() {
                String payload = HttpIngressServer.presenceJson("id\"\\\\x", true);

                org.junit.jupiter.api.Assertions.assertEquals(
                                "{\"type\":\"presence\",\"userId\":\"id\\\"\\\\\\\\x\",\"active\":true,\"hidden\":false}",
                                payload);
        }
}
