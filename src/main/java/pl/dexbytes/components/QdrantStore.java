package pl.dexbytes.components;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import jakarta.inject.Singleton;

@Singleton
public class QdrantStore {

    public final EmbeddingStore<TextSegment> small;
    public final EmbeddingStore<TextSegment> large;


    public QdrantStore() {
        small = QdrantEmbeddingStore.builder()
                // Ensure the collection is configured with the appropriate dimensions
                // of the embedding model.
                // Reference https://qdrant.tech/documentation/concepts/collections/
                .collectionName("final")
                .host(System.getenv("QDRANT_HOST") != null ? System.getenv("QDRANT_HOST") : "localhost")
                // GRPC port of the Qdrant server
                .port(6334)
                .apiKey(System.getenv("QDRANT_API_KEY") != null ? System.getenv("QDRANT_API_KEY") : "")
                .build();
        large = QdrantEmbeddingStore.builder()
                // Ensure the collection is configured with the appropriate dimensions
                // of the embedding model.
                // Reference https://qdrant.tech/documentation/concepts/collections/
                .collectionName("myvaillant-large")
                .host(System.getenv("QDRANT_HOST") != null ? System.getenv("QDRANT_HOST") : "localhost")
                // GRPC port of the Qdrant server
                .port(6334)
                .apiKey(System.getenv("QDRANT_API_KEY") != null ? System.getenv("QDRANT_API_KEY") : "")
                .build();
    }
}
