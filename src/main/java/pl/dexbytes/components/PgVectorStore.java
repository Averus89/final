package pl.dexbytes.components;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.inject.Singleton;

/**
 * jdbc:postgresql://iotdocumentationanalysispgvector.postgres.database.azure.com:5432/postgres?user=iotadmin&password={your_password}&sslmode=require
 */
@Singleton
public class PgVectorStore {
    EmbeddingStore<TextSegment> pgVectorStore;

    public PgVectorStore() {
        pgVectorStore = PgVectorEmbeddingStore.builder()
                // Connection and table parameters
                .host(System.getenv("AZURE_PG_URL"))
                .port(5432)
                .database(System.getenv("AZURE_PG_DATABASE"))
                .user(System.getenv("AZURE_PG_USERNAME"))
                .password(System.getenv("AZURE_PG_PASSWORD"))
                .table(System.getenv("AZURE_PG_TABLE"))

                // Embedding dimension
                .dimension(embeddingModel.dimension())      // Required: Must match the embedding model’s output dimension

                // Indexing and performance options
                .useIndex(true)                             // Enable IVFFlat index
                .indexListSize(100)                         // Number of lists for IVFFlat index

                // Table creation options
                .createTable(true)                          // Automatically create the table if it doesn’t exist
                .dropTableFirst(false)                      // Don’t drop the table first (set to true if you want a fresh start)

                // Metadata storage format
                .metadataStorageConfig(MetadataStorageConfig.combinedJsonb()) // Store metadata as a combined JSONB column

                .build();
    }
}
