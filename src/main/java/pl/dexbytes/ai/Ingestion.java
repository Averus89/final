package pl.dexbytes.ai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import pl.dexbytes.components.EmbeddingTextStore;
import pl.dexbytes.util.FilePatternMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

@Slf4j
@ApplicationScoped
@Getter
public class Ingestion {
    private final EmbeddingStoreIngestor ingestor;

    public Ingestion(@Named("pgvector") EmbeddingTextStore store, EmbeddingModel embeddingModel) {
        ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(store.getStore())
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(1024, 250, new OpenAiTokenCountEstimator("text-embedding-3-large")))
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                .build();
    }

    public void ingest(String path) {
        if (path == null || path.isBlank()) {
            getPaths().forEach(p -> prepareDocumentsInBatches(p).parallelStream().forEach(document -> {
                log.info("Ingesting documents: {}", document.stream()
                        .map(doc -> doc.metadata().getString("file_name"))
                        .collect(Collectors.joining(", "))
                );
                ingestor.ingest(document);
                log.info("Finished ingesting documents from path: {}", p);
            }));
        } else {
            prepareDocumentsInBatches(path).parallelStream().forEach(document -> {
                        log.info("Ingesting documents: {}", document.stream()
                                .map(doc -> doc.metadata().getString("file_name"))
                                .collect(Collectors.joining(", "))
                        );
                        ingestor.ingest(document);
                        log.info("Finished ingesting documents from path: {}", path);
                    }

            );
            log.info("Finished ingesting documents from path: {}", path);
        }
    }

    private List<String> getPaths() {
        String baseDirectory = "/Users/alan/Documents/GitHub";  // You might want to make this configurable
        List<String> paths;

        try (Stream<Path> stream = Files.walk(Path.of(baseDirectory), 1)) {
            paths = stream.filter(Files::isDirectory)
                    .map(Path::toString)
                    .collect(Collectors.toList());

            log.info("Found {} directories to process", paths.size());
            paths.forEach(path -> log.info("Directory found: {}", path));

        } catch (IOException e) {
            log.error("Error while listing directories: {}", e.getMessage(), e);
            return new ArrayList<>();
        }

        return paths;
    }

    private @NotNull List<List<Document>> prepareDocumentsInBatches(String path) {
        if (path == null || path.isBlank()) {
            path = "/Users/alan/Documents/GitHub/final/documents";
        }
        Path dir = Path.of(path);
        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively(dir, new FilePatternMatcher());
        Log.info("Ingesting " + documents.size() + " documents");
        List<Document> trimmed = documents.stream()
                .filter(doc -> doc.text() != null && doc.text().trim().length() > 10)
                .collect(Collectors.toCollection(ArrayList::new));
        Log.info("Ingesting trimmed " + trimmed.size() + " documents");

        for (Document document : trimmed) {
            UUID uuid = UUID.randomUUID();
            document.metadata().put("uuid", uuid);
        }

        int batchSize = Math.min(20, trimmed.size()); // Adjust based on your API limits
        return partitionList(trimmed, batchSize);
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

}
