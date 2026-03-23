package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Uploads one encrypted chunk for an attachment.
 */
public class AttachmentChunkRequest implements Serializable {
    private int chunkIndex;
    private String chunkDataB64;

    /**
     * Creates an empty chunk request for JSON deserialization.
     */
    public AttachmentChunkRequest() {
        // Required for JSON deserialization
    }

    /**
     * Returns the zero-based index of the chunk being uploaded.
     *
     * @return chunk index
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * Sets the zero-based index of the chunk being uploaded.
     *
     * @param chunkIndex chunk index
     */
    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    /**
     * Returns the Base64-encoded encrypted chunk data.
     *
     * @return Base64 chunk payload
     */
    public String getChunkDataB64() {
        return chunkDataB64;
    }

    /**
     * Sets the Base64-encoded encrypted chunk data.
     *
     * @param chunkDataB64 Base64 chunk payload
     */
    public void setChunkDataB64(String chunkDataB64) {
        this.chunkDataB64 = chunkDataB64;
    }
}
