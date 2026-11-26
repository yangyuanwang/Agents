package com.example.rag.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ChunkVectorKey implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID chunkId;
    private UUID embeddingProfileId;

    public ChunkVectorKey() { }
    public ChunkVectorKey(UUID chunkId, UUID embeddingProfileId) {
        this.chunkId = chunkId;
        this.embeddingProfileId = embeddingProfileId;
    }
    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }
    public UUID getEmbeddingProfileId() { return embeddingProfileId; }
    public void setEmbeddingProfileId(UUID embeddingProfileId) { this.embeddingProfileId = embeddingProfileId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChunkVectorKey)) return false;
        ChunkVectorKey that = (ChunkVectorKey) other;
        return Objects.equals(chunkId, that.chunkId) && Objects.equals(embeddingProfileId, that.embeddingProfileId);
    }
    @Override public int hashCode() { return Objects.hash(chunkId, embeddingProfileId); }
}
