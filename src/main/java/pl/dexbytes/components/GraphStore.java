package pl.dexbytes.components;

import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraph;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.model.chat.ChatModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;

@Getter
@Slf4j
public class GraphStore {
    private final Driver driver;
    private final Neo4jGraph graph;

    public GraphStore(Driver driver) {
        this.driver = driver;
        this.graph = Neo4jGraph.builder()
                .driver(driver)
                .build();
    }

    public Neo4jText2CypherRetriever getRetriever(ChatModel model) {
        return Neo4jText2CypherRetriever.builder()
                .graph(getGraph())
                .chatModel(model)
                .build();
    }
}
