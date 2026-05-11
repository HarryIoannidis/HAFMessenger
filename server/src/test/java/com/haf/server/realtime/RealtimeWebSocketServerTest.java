package com.haf.server.realtime;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.Contact;
import com.haf.server.db.Session;
import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.websocket.RealtimeEvent;
import com.haf.shared.websocket.RealtimeEventType;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLContext;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketServerTest {

    private static final String TEST_REALTIME_PATH = String.join("/", "", "ws", "v1", "realtime");

    @Mock
    private MailboxRouter mailboxRouter;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private User userDAO;
    @Mock
    private Session sessionDAO;
    @Mock
    private Contact contactDAO;

    @Test
    void rejects_handshake_with_invalid_path() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = mock(WebSocket.class);
        ClientHandshake handshake = mock(ClientHandshake.class);
        when(handshake.getResourceDescriptor()).thenReturn(TEST_REALTIME_PATH + "?token=secret");

        server.onOpen(socket, handshake);

        verify(socket).close(1008, "invalid realtime path");
    }

    @Test
    void rejects_handshake_with_invalid_token() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = mock(WebSocket.class);
        ClientHandshake handshake = authenticatedHandshake("bad-token");
        when(sessionDAO.getUserIdForSessionAndTouch("bad-token")).thenReturn(null);

        server.onOpen(socket, handshake);

        verify(socket).close(1008, "invalid session");
    }

    @Test
    void accepts_authenticated_handshake_and_subscribes_mailbox() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = mock(WebSocket.class);
        ClientHandshake handshake = authenticatedHandshake("good-token");
        when(sessionDAO.getUserIdForSessionAndTouch("good-token")).thenReturn("user-1");
        when(mailboxRouter.subscribe(eq("user-1"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("user-1", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("user-1", 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("user-1", 500)).thenReturn(java.util.List.of());
        when(contactDAO.getWatcherUserIds("user-1")).thenReturn(java.util.List.of());

        server.onOpen(socket, handshake);

        verify(mailboxRouter).subscribe(eq("user-1"), any());
        verify(mailboxRouter).fetchUndelivered("user-1", 100);
    }

    @Test
    void rejects_unauthorized_typing_event() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = mock(WebSocket.class);
        when(socket.isOpen()).thenReturn(true);
        ClientHandshake handshake = authenticatedHandshake("good-token");
        when(sessionDAO.getUserIdForSessionAndTouch("good-token")).thenReturn("user-1");
        when(mailboxRouter.subscribe(eq("user-1"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("user-1", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("user-1", 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("user-1", 500)).thenReturn(java.util.List.of());
        when(contactDAO.getWatcherUserIds("user-1")).thenReturn(java.util.List.of());
        when(contactDAO.canReach("user-1", "user-2")).thenReturn(false);
        when(contactDAO.canReach("user-2", "user-1")).thenReturn(false);

        server.onOpen(socket, handshake);
        RealtimeEvent event = RealtimeEvent.outbound(RealtimeEventType.TYPING_START);
        event.setRecipientId("user-2");
        server.onMessage(socket, JsonCodec.toJson(event));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(socket).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("unauthorized", error.getCode());
        assertTrue(error.getError().contains("typing"));
    }

    @Test
    void closes_socket_when_session_is_revoked_after_connect() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1", false);
        when(sessionDAO.getUserIdForSessionAndTouch("good-token")).thenReturn(null);

        server.onMessage(socket, JsonCodec.toJson(RealtimeEvent.outbound(RealtimeEventType.HEARTBEAT)));

        verify(socket).close(1008, "invalid session");
    }

    @Test
    void heartbeat_revalidates_session_and_replies_with_correlation() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1");
        RealtimeEvent heartbeat = RealtimeEvent.outbound(RealtimeEventType.HEARTBEAT);

        server.onMessage(socket, JsonCodec.toJson(heartbeat));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(socket).send(payload.capture());
        RealtimeEvent response = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.HEARTBEAT, response.eventType());
        assertEquals(heartbeat.getEventId(), response.getCorrelationId());
    }

    @Test
    void rejects_invalid_event_payload() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1");

        server.onMessage(socket, "{}");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(socket).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("invalid_type", error.getCode());
    }

    @Test
    void rejects_replayed_event_id_and_nonce() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1");
        RealtimeEvent heartbeat = RealtimeEvent.outbound(RealtimeEventType.HEARTBEAT);
        String payloadJson = JsonCodec.toJson(heartbeat);

        server.onMessage(socket, payloadJson);
        server.onMessage(socket, payloadJson);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(socket, times(2)).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getAllValues().get(1), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("replay_rejected", error.getCode());
    }

    @Test
    void rate_limits_events_per_socket_and_type() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1");
        when(contactDAO.canReach("user-1", "user-2")).thenReturn(true);

        for (int i = 0; i < 121; i++) {
            RealtimeEvent typing = RealtimeEvent.outbound(RealtimeEventType.TYPING_START);
            typing.setRecipientId("user-2");
            server.onMessage(socket, JsonCodec.toJson(typing));
        }

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(socket).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("rate_limit", error.getCode());
    }

    @Test
    void rejects_read_receipt_for_unowned_envelope() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "recipient-1");
        when(mailboxRouter.markReadOwnedAndReturn("recipient-1", java.util.List.of("env-1")))
                .thenReturn(java.util.List.of());
        RealtimeEvent receipt = RealtimeEvent.outbound(RealtimeEventType.MESSAGE_READ);
        receipt.setEnvelopeIds(java.util.List.of("env-1"));

        server.onMessage(socket, JsonCodec.toJson(receipt));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(socket).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("unauthorized", error.getCode());
    }

    @Test
    void delivery_receipt_marks_owned_envelope_and_routes_to_sender() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket senderSocket = openAuthenticatedSocket(server, "sender-token", "sender-1");
        WebSocket recipientSocket = openAuthenticatedSocket(server, "recipient-token", "recipient-1", false);
        EncryptedMessage message = new EncryptedMessage();
        message.setSenderId("sender-1");
        message.setRecipientId("recipient-1");
        QueuedEnvelope envelope = new QueuedEnvelope("env-1", message, 10L, 20L);
        when(mailboxRouter.acknowledgeOwnedAndReturn("recipient-1", java.util.List.of("env-1")))
                .thenReturn(java.util.List.of(envelope));
        when(contactDAO.canReach("recipient-1", "sender-1")).thenReturn(true);
        RealtimeEvent receipt = RealtimeEvent.outbound(RealtimeEventType.MESSAGE_DELIVERED);
        receipt.setEnvelopeIds(java.util.List.of("env-1"));

        server.onMessage(recipientSocket, JsonCodec.toJson(receipt));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(senderSocket).send(payload.capture());
        RealtimeEvent routed = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.MESSAGE_DELIVERED, routed.eventType());
        assertEquals("recipient-1", routed.getSenderId());
        assertEquals(java.util.List.of("env-1"), routed.getEnvelopeIds());
    }

    @Test
    void disconnect_cleans_up_subscription_and_presence() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1", false);

        server.onClose(socket, 1000, "done", false);

        verify(mailboxRouter).unsubscribe(any());
        verify(socket, never()).send(any(String.class));
    }

    @Test
    void replacement_session_does_not_emit_false_offline_presence() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket watcherSocket = openAuthenticatedSocket(server, "watcher-token", "watcher-1");
        WebSocket firstUserSocket = mock(WebSocket.class);
        WebSocket secondUserSocket = mock(WebSocket.class);
        ClientHandshake firstHandshake = authenticatedHandshake("user-token-1");
        ClientHandshake secondHandshake = authenticatedHandshake("user-token-2");
        when(sessionDAO.getUserIdForSessionAndTouch("user-token-1")).thenReturn("user-1");
        when(sessionDAO.getUserIdForSessionAndTouch("user-token-2")).thenReturn("user-1");
        when(mailboxRouter.subscribe(eq("user-1"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("user-1", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("user-1", 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("user-1", 500)).thenReturn(java.util.List.of());
        when(contactDAO.getWatcherUserIds("user-1")).thenReturn(java.util.List.of("watcher-1"));
        when(contactDAO.canReach("watcher-1", "user-1")).thenReturn(true);

        server.onOpen(firstUserSocket, firstHandshake);
        server.onOpen(secondUserSocket, secondHandshake);
        server.onClose(firstUserSocket, 1000, "old socket closed late", false);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(watcherSocket, times(1)).send(payload.capture());
        RealtimeEvent presence = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.PRESENCE_UPDATE, presence.eventType());
        assertTrue(presence.isActive());
        verify(sessionDAO).revokeSession("user-token-1");
        verify(firstUserSocket).close(1008, "session replaced");
    }

    @Test
    void same_token_session_replacement_does_not_revoke_shared_session() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket firstUserSocket = mock(WebSocket.class);
        WebSocket secondUserSocket = mock(WebSocket.class);
        ClientHandshake firstHandshake = authenticatedHandshake("user-token");
        ClientHandshake secondHandshake = authenticatedHandshake("user-token");
        when(sessionDAO.getUserIdForSessionAndTouch("user-token")).thenReturn("user-1");
        when(mailboxRouter.subscribe(eq("user-1"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("user-1", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("user-1", 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("user-1", 500)).thenReturn(java.util.List.of());
        when(contactDAO.getWatcherUserIds("user-1")).thenReturn(java.util.List.of());

        server.onOpen(firstUserSocket, firstHandshake);
        server.onOpen(secondUserSocket, secondHandshake);

        verify(sessionDAO, never()).revokeSession("user-token");
        verify(firstUserSocket).close(1008, "session replaced");
    }

    @Test
    void active_session_close_emits_offline_presence_once() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket watcherSocket = openAuthenticatedSocket(server, "watcher-token", "watcher-1");
        WebSocket userSocket = mock(WebSocket.class);
        ClientHandshake userHandshake = authenticatedHandshake("user-token");
        when(sessionDAO.getUserIdForSessionAndTouch("user-token")).thenReturn("user-1");
        when(mailboxRouter.subscribe(eq("user-1"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("user-1", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("user-1", 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("user-1", 500)).thenReturn(java.util.List.of());
        when(contactDAO.getWatcherUserIds("user-1")).thenReturn(java.util.List.of("watcher-1"));
        when(contactDAO.canReach("watcher-1", "user-1")).thenReturn(true);

        server.onOpen(userSocket, userHandshake);
        server.onClose(userSocket, 1000, "done", false);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(watcherSocket, times(2)).send(payload.capture());
        RealtimeEvent online = JsonCodec.fromJson(payload.getAllValues().get(0), RealtimeEvent.class);
        RealtimeEvent offline = JsonCodec.fromJson(payload.getAllValues().get(1), RealtimeEvent.class);
        assertTrue(online.isActive());
        assertEquals(false, offline.isActive());
    }

    @Test
    void replay_guard_survives_reconnect_for_same_user() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket firstSocket = openAuthenticatedSocket(server, "good-token", "user-1");
        RealtimeEvent heartbeat = RealtimeEvent.outbound(RealtimeEventType.HEARTBEAT);
        String payloadJson = JsonCodec.toJson(heartbeat);

        server.onMessage(firstSocket, payloadJson);
        server.onClose(firstSocket, 1000, "reconnect", false);

        WebSocket secondSocket = openAuthenticatedSocket(server, "good-token-2", "user-1");
        server.onMessage(secondSocket, payloadJson);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(secondSocket).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("replay_rejected", error.getCode());
    }

    @Test
    void per_user_rate_limit_survives_reconnect() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket firstSocket = openAuthenticatedSocket(server, "good-token", "user-1", false);
        when(contactDAO.canReach("user-1", "user-2")).thenReturn(true);

        for (int i = 0; i < 120; i++) {
            RealtimeEvent typing = RealtimeEvent.outbound(RealtimeEventType.TYPING_START);
            typing.setRecipientId("user-2");
            server.onMessage(firstSocket, JsonCodec.toJson(typing));
        }
        server.onClose(firstSocket, 1000, "reconnect", false);

        WebSocket secondSocket = openAuthenticatedSocket(server, "good-token-2", "user-1");
        RealtimeEvent typing = RealtimeEvent.outbound(RealtimeEventType.TYPING_START);
        typing.setRecipientId("user-2");
        server.onMessage(secondSocket, JsonCodec.toJson(typing));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(secondSocket).send(payload.capture());
        RealtimeEvent error = JsonCodec.fromJson(payload.getValue(), RealtimeEvent.class);
        assertEquals(RealtimeEventType.ERROR, error.eventType());
        assertEquals("rate_limit", error.getCode());
    }

    @Test
    void reconnect_replays_persisted_receipts_to_sender() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket senderSocket = mock(WebSocket.class);
        when(senderSocket.isOpen()).thenReturn(true);
        ClientHandshake handshake = authenticatedHandshake("sender-token");
        when(sessionDAO.getUserIdForSessionAndTouch("sender-token")).thenReturn("sender-1");
        when(mailboxRouter.subscribe(eq("sender-1"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("sender-1", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("sender-1", 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("sender-1", 500)).thenReturn(java.util.List.of(
                new MailboxRouter.ReceiptReplay("env-delivered", "recipient-1", false),
                new MailboxRouter.ReceiptReplay("env-read", "recipient-1", true)));
        when(contactDAO.getWatcherUserIds("sender-1")).thenReturn(java.util.List.of());
        when(contactDAO.canReach("sender-1", "recipient-1")).thenReturn(true);

        server.onOpen(senderSocket, handshake);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(senderSocket, times(2)).send(payload.capture());
        RealtimeEvent delivered = JsonCodec.fromJson(payload.getAllValues().get(0), RealtimeEvent.class);
        RealtimeEvent read = JsonCodec.fromJson(payload.getAllValues().get(1), RealtimeEvent.class);
        assertEquals(RealtimeEventType.MESSAGE_DELIVERED, delivered.eventType());
        assertEquals(java.util.List.of("env-delivered"), delivered.getEnvelopeIds());
        assertEquals(RealtimeEventType.MESSAGE_READ, read.eventType());
        assertEquals(java.util.List.of("env-read"), read.getEnvelopeIds());
    }

    @Test
    void rejects_binary_frames() throws Exception {
        RealtimeWebSocketServer server = newServer();
        WebSocket socket = openAuthenticatedSocket(server, "good-token", "user-1");

        server.onMessage(socket, ByteBuffer.wrap(new byte[] { 1, 2, 3 }));

        verify(socket).close(1008, "binary frames unsupported");
    }

    private RealtimeWebSocketServer newServer() throws Exception {
        ServerConfig config = mock(ServerConfig.class);
        when(config.getWsPort()).thenReturn(0);
        when(config.getWsPath()).thenReturn(TEST_REALTIME_PATH);
        return new RealtimeWebSocketServer(
                config,
                SSLContext.getDefault(),
                mailboxRouter,
                rateLimiterService,
                auditLogger,
                new EncryptedMessageValidator(),
                userDAO,
                sessionDAO,
                contactDAO);
    }

    private static ClientHandshake authenticatedHandshake(String token) {
        ClientHandshake handshake = mock(ClientHandshake.class);
        when(handshake.getResourceDescriptor()).thenReturn(TEST_REALTIME_PATH);
        when(handshake.getFieldValue("Authorization")).thenReturn("Bearer " + token);
        return handshake;
    }

    private WebSocket openAuthenticatedSocket(RealtimeWebSocketServer server, String token, String userId) {
        return openAuthenticatedSocket(server, token, userId, true);
    }

    private WebSocket openAuthenticatedSocket(
            RealtimeWebSocketServer server,
            String token,
            String userId,
            boolean expectServerSend) {
        WebSocket socket = mock(WebSocket.class);
        if (expectServerSend) {
            when(socket.isOpen()).thenReturn(true);
        }
        ClientHandshake handshake = authenticatedHandshake(token);
        when(sessionDAO.getUserIdForSessionAndTouch(token)).thenReturn(userId);
        when(mailboxRouter.subscribe(eq(userId), any())).thenReturn(
                new MailboxRouter.MailboxSubscription(userId, envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered(userId, 100)).thenReturn(java.util.List.of());
        when(mailboxRouter.fetchReceiptReplayForSender(userId, 500)).thenReturn(java.util.List.of());
        when(contactDAO.getWatcherUserIds(userId)).thenReturn(java.util.List.of());
        server.onOpen(socket, handshake);
        return socket;
    }
}
