package pl.dexbytes.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import pl.dexbytes.components.EmbeddingTextStore;

import java.util.function.Supplier;

import static java.util.Arrays.asList;

@ApplicationScoped
public class Retriever implements Supplier<RetrievalAugmentor> {
    private final DefaultRetrievalAugmentor augmentor;

    public Retriever(@Named("pgvector") EmbeddingTextStore store, EmbeddingModel embeddingModel) {
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store.getStore())
                .maxResults(10) // Large segments
                .minScore(0.6D)
                .build();

        ContentInjector contentInjector = DefaultContentInjector.builder()
                .metadataKeysToInclude(asList("file_name", "index", "keywords"))
                .promptTemplate(PromptTemplate.from("""
                        {{userMessage}}
                        
                        <context>
                        {{contents}}
                        </context>
                        """))
                .build();

        /*ContentAggregator contentAggregator = queryToContents -> {
            List<Content> aggregatedContents = new ArrayList<>();
            queryToContents.forEach((key, value) -> value
                    .forEach(list -> list
                            .forEach(content -> aggregatedContents.add(Content.from(String.valueOf(content.metadata().get("summary")))))
                    ));
            return aggregatedContents;
        };*/

        /*ScoringModel scoringModel = CohereScoringModel.builder()
                .apiKey(System.getenv("COHERE_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .modelName("rerank-v3.5")
                .build();

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .maxResults(5)
                .minScore(0.8) // we want to present the LLM with only the truly relevant segments for the user's query
                .build();*/

        augmentor = DefaultRetrievalAugmentor
                .builder()
                .contentInjector(contentInjector)
                //.contentAggregator(contentAggregator)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return augmentor;
    }

}