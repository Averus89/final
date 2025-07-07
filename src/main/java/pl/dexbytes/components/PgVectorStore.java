package pl.dexbytes.components;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("pgvector")
public class PgVectorStore implements EmbeddingTextStore {
    EmbeddingStore<TextSegment> pgVectorStore;

    public PgVectorStore(EmbeddingModel embeddingModel) {
        pgVectorStore = new PgHalfvecEmbeddingStore(
                System.getenv("AZURE_PG_URL"),
                5432,
                System.getenv("AZURE_PG_USERNAME"),
                System.getenv("AZURE_PG_PASSWORD"),
                System.getenv("AZURE_PG_DATABASE"),
                System.getenv("AZURE_PG_TABLE"),
                embeddingModel.dimension(),
                true,
                true,
                false
        );
    }

    @Override
    public EmbeddingStore<TextSegment> getStore() {
        return pgVectorStore;
    }
}
