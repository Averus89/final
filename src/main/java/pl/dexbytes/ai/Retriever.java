package pl.dexbytes.ai;

import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import pl.dexbytes.components.EmbeddingTextStore;
import pl.dexbytes.components.GraphStore;
import pl.dexbytes.services.ChatService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Arrays.asList;

@ApplicationScoped
public class Retriever implements Supplier<RetrievalAugmentor> {
    private final DefaultRetrievalAugmentor augmentor;

    public Retriever(@Named("pgvector") EmbeddingTextStore store, EmbeddingModel embeddingModel, GraphStore graphStore) {
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store.getStore())
                .maxResults(10) // Large segments
                .minScore(0.6D)
                .build();

        Neo4jText2CypherRetriever retriever = graphStore.getRetriever(ChatService.getGpt41Mini());

        LanguageModelQueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(ChatService.getGpt41Mini())
                .retrieverToDescription(Map.of(
                        contentRetriever, "Retrieve relevant information about code from repositories in GitHub",
                        retriever, "Retrieve Vaillant infrastructure information, architecture, service connections, etc. from graph database Neo4j"
                        ))
                .fallbackStrategy(LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL)
                .build();

        OnnxScoringModel scoringModel = new OnnxScoringModel(
                "/Users/alan/Documents/models/rerank/bge-reranker-base/model.onnx",
                "/Users/alan/Documents/models/rerank/bge-reranker-base/tokenizer.json"
        );

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
                .build();*/

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .querySelector(ReRankingContentAggregator.DEFAULT_QUERY_SELECTOR)
                .maxResults(5)
                .minScore(0.75) // we want to present the LLM with only the truly relevant segments for the user's query
                .build();

        ContentInjector contentInjector = DefaultContentInjector.builder()
                .metadataKeysToInclude(asList("file_name", "index"))
                .promptTemplate(PromptTemplate.from("""
                        {{userMessage}}
                        
                        <context>
                        {{contents}}
                        </context>
                        """))
                .build();

        augmentor = DefaultRetrievalAugmentor
                .builder()
                .queryRouter(router)
                .contentAggregator(contentAggregator)
                //.contentRetriever(contentRetriever) //not used when query router is available
                .contentInjector(contentInjector)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return augmentor;
    }

}