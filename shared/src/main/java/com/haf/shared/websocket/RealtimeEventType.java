package com.haf.shared.websocket;

/**
 * Event names used by the secure real-time WebSocket transport.
 */
public enum RealtimeEventType {
    SEND_MESSAGE,
    SEND_ACCEPTED,
    NEW_MESSAGE,
    MESSAGE_DELIVERED,
    MESSAGE_READ,
    TYPING_START,
    TYPING_STOP,
    PRESENCE_UPDATE,
    ERROR,
    HEARTBEAT
}
