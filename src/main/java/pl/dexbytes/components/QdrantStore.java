package pl.dexbytes.components;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("qdrant")
public class QdrantStore implements EmbeddingTextStore {
    public final EmbeddingStore<TextSegment> large;

    public QdrantStore() {
        large = QdrantEmbeddingStore.builder()
                .collectionName("myvaillant-large")
                .host(System.getenv("QDRANT_HOST") != null ? System.getenv("QDRANT_HOST") : "localhost")
                .port(6334)
                .apiKey(System.getenv("QDRANT_API_KEY") != null ? System.getenv("QDRANT_API_KEY") : "")
                .build();
    }

    @Override
    public EmbeddingStore<TextSegment> getStore() {
        return large;
    }
}
