package com.haf.shared.keystore;

import com.haf.shared.exceptions.KeyNotFoundException;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.PublicKey;
import static org.junit.jupiter.api.Assertions.*;

class KeyProviderTest {

    /**
     * Mock KeyProvider for testing.
     */
    static class MockKeyProvider implements KeyProvider {
        private final String senderId;
        private final PublicKey publicKey;

        MockKeyProvider(String senderId, PublicKey publicKey) {
            this.senderId = senderId;
            this.publicKey = publicKey;
        }

        @Override
        public PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException {
            if ("known-recipient".equals(recipientId)) {
                return publicKey;
            }
            throw new KeyNotFoundException("Recipient not found: " + recipientId);
        }

        @Override
        public String getSenderId() {
            return senderId;
        }
    }

    @Test
    void getSenderId_returns_sender_id() throws Exception {
        KeyPair kp = com.haf.shared.utils.RsaKeyIO.generate(2048);
        KeyProvider provider = new MockKeyProvider("sender-123", kp.getPublic());
        
        assertEquals("sender-123", provider.getSenderId());
    }

    @Test
    void getRecipientPublicKey_returns_key_for_known_recipient() throws Exception {
        KeyPair kp = com.haf.shared.utils.RsaKeyIO.generate(2048);
        KeyProvider provider = new MockKeyProvider("sender-123", kp.getPublic());
        
        PublicKey recipientKey = provider.getRecipientPublicKey("known-recipient");
        assertNotNull(recipientKey);
        assertEquals(kp.getPublic(), recipientKey);
    }

    @Test
    void getRecipientPublicKey_throws_for_unknown_recipient() throws Exception {
        KeyPair kp = com.haf.shared.utils.RsaKeyIO.generate(2048);
        KeyProvider provider = new MockKeyProvider("sender-123", kp.getPublic());
        
        assertThrows(KeyNotFoundException.class, () -> {
            provider.getRecipientPublicKey("unknown-recipient");
        });
    }
}

