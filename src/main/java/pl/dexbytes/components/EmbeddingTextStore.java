package pl.dexbytes.components;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

public interface EmbeddingTextStore {
    EmbeddingStore<TextSegment> getStore();
}
