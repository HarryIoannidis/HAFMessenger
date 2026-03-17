package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Uploads one encrypted chunk for an attachment.
 */
public class AttachmentChunkRequest implements Serializable {
    private int chunkIndex;
    private String chunkDataB64;

    public AttachmentChunkRequest() {
        // Required for JSON deserialization
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getChunkDataB64() {
        return chunkDataB64;
    }

    public void setChunkDataB64(String chunkDataB64) {
        this.chunkDataB64 = chunkDataB64;
    }
}
