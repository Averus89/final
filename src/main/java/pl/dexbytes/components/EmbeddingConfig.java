package pl.dexbytes.components;

import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Singleton;

public class EmbeddingConfig {

    @Singleton
    public EmbeddingModel getLarge(){
        return AzureOpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("AZURE_AI_API_KEY"))
                .endpoint(System.getenv("AZURE_AI_BASE_URL"))
                .deploymentName("text-embedding-3-large")
                .build();
    }
}
