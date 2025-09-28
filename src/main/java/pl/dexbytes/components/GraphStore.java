package pl.dexbytes.components;

import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraph;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;

import java.util.List;

@Getter
@Slf4j
@Singleton
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
        log.info("Schema {}", toSchemaString(graph.getStructuredSchema()));
        return Neo4jText2CypherRetriever.builder()
                .graph(getGraph())
                .chatModel(model)
                .examples(List.of(
                        "MATCH p=()-[:HAS_RELATIONSHIP_WITH]->() RETURN p",
                        "MATCH (n:Element) RETURN n;",
                        "MATCH (n:Element {name: \"Customer Portal\"})-[r:HAS_RELATIONSHIP_WITH]-(connected:Element) RETURN n.name as Source, r.role as Relationship, connected.name as Target",
                        "MATCH path = (n:Element {name: \"Customer Portal\"})-[r:HAS_RELATIONSHIP_WITH*1..3]-(connected:Element) RETURN path",
                        "MATCH (n:Element {name: \"Customer Portal\"})-[r:HAS_RELATIONSHIP_WITH]-(connected:Element) " +
                                "RETURN n.name as Source," +
                                " n.type as SourceType," +
                                " r.role as Relationship," +
                                " connected.name as Target," +
                                " connected.type as TargetType",
                        "MATCH (n:Element)-[r:HAS_RELATIONSHIP_WITH]->(target:Element {name: \"Customer Portal\"})" +
                                "RETURN n.name as DependentComponent, r.role as Relationship",
                        "MATCH (n:Element {name: \"Customer Portal\"})-[r:HAS_RELATIONSHIP_WITH]->(target:Element)" +
                                "RETURN target.name as DependencyComponent, r.role as Relationship"
                ))
                .build();
    }

    static String toSchemaString(Neo4jGraph.StructuredSchema structuredSchema) {

        final String nodesString = String.join(", ", structuredSchema.nodesProperties());
        final String relationshipsString = String.join(", ", structuredSchema.relationshipsProperties());
        final String patternsString = String.join(", ", structuredSchema.patterns());

        return "Node properties are the following:\n" + nodesString
                + "\n\n" + "Relationship properties are the following:\n"
                + relationshipsString
                + "\n\n" + "The relationships are the following:\n"
                + patternsString;
    }
}
