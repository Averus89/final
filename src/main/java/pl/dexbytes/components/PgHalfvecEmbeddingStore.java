package pl.dexbytes.components;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGhalfvec;
import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import dev.langchain4j.store.embedding.pgvector.*;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PGobject;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Slf4j
public class PgHalfvecEmbeddingStore implements EmbeddingStore<TextSegment> {
    protected final DataSource datasource;
    protected final String table;
    final JSONBMetadataHandler metadataHandler;

    public PgHalfvecEmbeddingStore(DataSource datasource,
                                   String table,
                                   Integer dimension,
                                   Boolean useIndex,
                                   Integer indexListSize,
                                   Boolean createTable,
                                   Boolean dropTableFirst) {
        this.datasource = ensureNotNull(datasource, "datasource");
        this.table = ensureNotBlank(table, "table");
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .columnDefinitions(List.of("metadata JSONB NULL"))
                .build();
        this.metadataHandler = new JSONBMetadataHandler(config);
        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);

        initTable(dropTableFirst, createTable, useIndex, dimension, indexListSize);
    }

    public PgHalfvecEmbeddingStore(
            String host,
            Integer port,
            String user,
            String password,
            String database,
            String table,
            Integer dimension,
            Boolean useIndex,
            Boolean createTable,
            Boolean dropTableFirst
    ) {
        this(createDataSource(host, port, user, password, database),
                table, dimension, useIndex, 100, createTable, dropTableFirst);
    }

    private static DataSource createDataSource(String host, Integer port, String user, String password, String database) {
        host = ensureNotBlank(host, "host");
        port = ensureGreaterThanZero(port, "port");
        user = ensureNotBlank(user, "user");
        password = ensureNotBlank(password, "password");
        database = ensureNotBlank(database, "database");

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[]{host});
        source.setPortNumbers(new int[]{port});
        source.setDatabaseName(database);
        source.setUser(user);
        source.setPassword(password);

        return source;
    }

    protected void initTable(Boolean dropTableFirst, Boolean createTable, Boolean useIndex, Integer dimension,
                             Integer indexListSize) {
        String query = "init";
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (dropTableFirst) {
                statement.executeUpdate(String.format("DROP TABLE IF EXISTS %s", table));
            }
            if (createTable) {
                query = String.format("CREATE TABLE IF NOT EXISTS %s (embedding_id UUID PRIMARY KEY, " +
                                "embedding halfvec(%s), text TEXT NULL, %s )",
                        table, ensureGreaterThanZero(dimension, "dimension"),
                        metadataHandler.columnDefinitionsString());
                statement.executeUpdate(query);
                metadataHandler.createMetadataIndexes(statement, table);
            }
            if (useIndex) {
                final String indexName = table + "_hnsw_index";
                query = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s USING hnsw (embedding halfvec_cosine_ops)",
                        indexName, table, ensureGreaterThanZero(indexListSize, "indexListSize"));
                statement.executeUpdate(query);
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Failed to execute '%s'", query), e);
        }
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id        The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding   The embedding to be added to the store.
     * @param textSegment Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        String sql = String.format("DELETE FROM %s WHERE embedding_id = ANY (?)", table);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Array array = connection.createArrayOf("uuid", ids.stream().map(UUID::fromString).toArray());
            statement.setArray(1, array);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        String whereClause = metadataHandler.whereClause(filter);
        String sql = String.format("DELETE FROM %s WHERE %s", table, whereClause);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(String.format("TRUNCATE TABLE %s", table));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addAll(
            List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (Connection connection = getConnection()) {
            String query = String.format(
                    "INSERT INTO %s (embedding_id, embedding, text, %s) VALUES (?, ?, ?, %s)" +
                            "ON CONFLICT (embedding_id) DO UPDATE SET " +
                            "embedding = EXCLUDED.embedding," +
                            "text = EXCLUDED.text," +
                            "%s;",
                    table, join(",", metadataHandler.columnsNames()),
                    join(",", nCopies(metadataHandler.columnsNames().size(), "?")),
                    metadataHandler.insertClause());
            try (PreparedStatement upsertStmt = connection.prepareStatement(query)) {
                for (int i = 0; i < ids.size(); ++i) {
                    upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                    upsertStmt.setObject(2, new PGhalfvec(embeddings.get(i).vector()));

                    if (embedded != null && embedded.get(i) != null) {
                        upsertStmt.setObject(3, embedded.get(i).text());
                        metadataHandler.setMetadata(upsertStmt, 4, embedded.get(i).metadata());
                    } else {
                        upsertStmt.setNull(3, Types.VARCHAR);
                        IntStream.range(4, 4 + metadataHandler.columnsNames().size()).forEach(
                                j -> {
                                    try {
                                        upsertStmt.setNull(j, Types.OTHER);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                    upsertStmt.addBatch();
                }
                upsertStmt.executeBatch();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    protected Connection getConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        // Find a way to do the following code in connection initialization.
        // Here we assume the datasource could handle a connection pool
        // and we should add the vector type on each connection
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
        }
        connection.unwrap(PGConnection.class).addDataType("halfvec", PGhalfvec.class);
        return connection;
    }

    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String whereClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
            whereClause = (whereClause.isEmpty()) ? "" : "AND " + whereClause;
            String query = String.format(
                    "SELECT (2 - (embedding <=> '%s')) / 2 AS score, embedding_id, embedding, text, %s FROM %s " +
                            "WHERE round(cast(float8 (embedding <=> '%s') as numeric), 8) <= round(2 - 2 * %s, 8) %s " + "ORDER BY embedding <=> '%s' LIMIT %s;",
                    referenceVector, join(",", metadataHandler.columnsNames()), table, referenceVector,
                    minScore, whereClause, referenceVector, maxResults
            );
            try (PreparedStatement selectStmt = connection.prepareStatement(query)) {
                try (ResultSet resultSet = selectStmt.executeQuery()) {
                    while (resultSet.next()) {
                        double score = resultSet.getDouble("score");
                        String embeddingId = resultSet.getString("embedding_id");

                        PGhalfvec vector = (PGhalfvec) resultSet.getObject("embedding");
                        Embedding embedding = new Embedding(vector.toArray());

                        String text = resultSet.getString("text");
                        TextSegment textSegment = null;
                        if (isNotNullOrBlank(text)) {
                            Metadata metadata = metadataHandler.fromResultSet(resultSet);
                            textSegment = TextSegment.from(text, metadata);
                        }
                        result.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new EmbeddingSearchResult<>(result);
    }

    static class JSONBMetadataHandler extends JSONMetadataHandler {

        final String indexType;

        /**
         * MetadataHandler constructor
         *
         * @param config {@link MetadataStorageConfig} configuration
         */
        public JSONBMetadataHandler(MetadataStorageConfig config) {
            super(config);
            if (!this.columnDefinition.getType().equals("jsonb")) {
                throw new RuntimeException("Your column definition type should be JSONB");
            }
            indexType = config.indexType();
        }

        @Override
        public void createMetadataIndexes(Statement statement, String table) {
            try {
                String indexSql = "CREATE INDEX IF NOT EXISTS embeddings_embedding_idx ON public.embeddings USING hnsw (embedding halfvec_cosine_ops)";
                statement.executeUpdate(indexSql);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Cannot create index: %s", e));
            }
        }
    }

    static class JSONMetadataHandler {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .enable(INDENT_OUTPUT);

        final MetadataColumDefinition columnDefinition;
        final String columnName;
        final JSONFilterMapper filterMapper;
        final List<String> indexes;

        /**
         * MetadataHandler constructor
         *
         * @param config {@link MetadataStorageConfig} configuration
         */
        public JSONMetadataHandler(MetadataStorageConfig config) {
            List<String> definition = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
            if (definition.size() > 1) {
                throw new IllegalArgumentException("Metadata definition should be an unique column definition, " +
                        "example: metadata JSON NULL");
            }
            this.columnDefinition = MetadataColumDefinition.from(definition.get(0));
            this.columnName = this.columnDefinition.getName();
            this.filterMapper = new JSONFilterMapper(columnName);
            this.indexes = getOrDefault(config.indexes(), Collections.emptyList());
        }

        public String columnDefinitionsString() {
            return columnDefinition.getFullDefinition();
        }

        public List<String> columnsNames() {
            return Collections.singletonList(this.columnName);
        }

        public void createMetadataIndexes(Statement statement, String table) {
            if (!this.indexes.isEmpty()) {
                throw new RuntimeException("Indexes are not allowed for JSON metadata, use JSONB instead");
            }
        }

        public String whereClause(Filter filter) {
            return filterMapper.map(filter);
        }

        @SuppressWarnings("unchecked")
        public Metadata fromResultSet(ResultSet resultSet) {
            try {
                String metadataJson = getOrDefault(resultSet.getString(columnsNames().get(0)), "{}");
                return new Metadata(OBJECT_MAPPER.readValue(metadataJson, Map.class));
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public String insertClause() {
            return String.format("%s = EXCLUDED.%s", this.columnName, this.columnName);
        }

        public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
            try {
                upsertStmt.setObject(parameterInitialIndex,
                        OBJECT_MAPPER.writeValueAsString(toStringValueMap(metadata.toMap())), Types.OTHER);
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class JSONFilterMapper extends PgVectorFilterMapper {
        final String metadataColumn;

        public JSONFilterMapper(String metadataColumn) {
            this.metadataColumn = metadataColumn;
        }

        String formatKey(String key, Class<?> valueType) {
            return String.format("(%s->>'%s')::%s", metadataColumn, key, SQL_TYPE_MAP.get(valueType));
        }

        String formatKeyAsString(String key) {
            return metadataColumn + "->>'" + key + "'";
        }

    }

    abstract static class PgVectorFilterMapper {

        static final Map<Class<?>, String> SQL_TYPE_MAP = Stream.of(
                        new AbstractMap.SimpleEntry<>(Integer.class, "int"),
                        new AbstractMap.SimpleEntry<>(Long.class, "bigint"),
                        new AbstractMap.SimpleEntry<>(Float.class, "float"),
                        new AbstractMap.SimpleEntry<>(Double.class, "float8"),
                        new AbstractMap.SimpleEntry<>(String.class, "text"),
                        new AbstractMap.SimpleEntry<>(UUID.class, "uuid"),
                        new AbstractMap.SimpleEntry<>(Boolean.class, "boolean"),
                        // Default
                        new AbstractMap.SimpleEntry<>(Object.class, "text"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        public String map(Filter filter) {
            if (filter instanceof ContainsString containsString) {
                return mapContains(containsString);
            } else if (filter instanceof IsEqualTo isEqualTo) {
                return mapEqual(isEqualTo);
            } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
                return mapNotEqual(isNotEqualTo);
            } else if (filter instanceof IsGreaterThan isGreaterThan) {
                return mapGreaterThan(isGreaterThan);
            } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
                return mapGreaterThanOrEqual(isGreaterThanOrEqualTo);
            } else if (filter instanceof IsLessThan isLessThan) {
                return mapLessThan(isLessThan);
            } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
                return mapLessThanOrEqual(isLessThanOrEqualTo);
            } else if (filter instanceof IsIn isIn) {
                return mapIn(isIn);
            } else if (filter instanceof IsNotIn isNotIn) {
                return mapNotIn(isNotIn);
            } else if (filter instanceof And and) {
                return mapAnd(and);
            } else if (filter instanceof Not not) {
                return mapNot(not);
            } else if (filter instanceof Or or) {
                return mapOr(or);
            } else {
                throw new UnsupportedOperationException(
                        "Unsupported filter type: " + filter.getClass().getName());
            }
        }

        private String mapContains(ContainsString containsString) {
            String key =
                    formatKey(containsString.key(), containsString.comparisonValue().getClass());
            return format("%s is not null and %s ~ %s", key, key, formatValue(containsString.comparisonValue()));
        }

        private String mapEqual(IsEqualTo isEqualTo) {
            String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
            return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue()));
        }

        private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
            String key =
                    formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
            return format("%s is null or %s != %s", key, key, formatValue(isNotEqualTo.comparisonValue()));
        }

        private String mapGreaterThan(IsGreaterThan isGreaterThan) {
            return format(
                    "%s > %s",
                    formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                    formatValue(isGreaterThan.comparisonValue()));
        }

        private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return format(
                    "%s >= %s",
                    formatKey(
                            isGreaterThanOrEqualTo.key(),
                            isGreaterThanOrEqualTo.comparisonValue().getClass()),
                    formatValue(isGreaterThanOrEqualTo.comparisonValue()));
        }

        private String mapLessThan(IsLessThan isLessThan) {
            return format(
                    "%s < %s",
                    formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                    formatValue(isLessThan.comparisonValue()));
        }

        private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return format(
                    "%s <= %s",
                    formatKey(
                            isLessThanOrEqualTo.key(),
                            isLessThanOrEqualTo.comparisonValue().getClass()),
                    formatValue(isLessThanOrEqualTo.comparisonValue()));
        }

        private String mapIn(IsIn isIn) {
            return format("%s in %s", formatKeyAsString(isIn.key()), formatValuesAsString(isIn.comparisonValues()));
        }

        private String mapNotIn(IsNotIn isNotIn) {
            String key = formatKeyAsString(isNotIn.key());
            return format("%s is null or %s not in %s", key, key, formatValuesAsString(isNotIn.comparisonValues()));
        }

        private String mapAnd(And and) {
            return format("%s and %s", map(and.left()), map(and.right()));
        }

        private String mapNot(Not not) {
            return format("not(%s)", map(not.expression()));
        }

        private String mapOr(Or or) {
            return format("(%s or %s)", map(or.left()), map(or.right()));
        }

        abstract String formatKey(String key, Class<?> valueType);

        abstract String formatKeyAsString(String key);

        String formatValue(Object value) {
            if (value instanceof String stringValue) {
                final String escapedValue = stringValue.replace("'", "''");
                return "'" + escapedValue + "'";
            } else if (value instanceof UUID) {
                return "'" + value + "'";
            } else {
                return value.toString();
            }
        }

        String formatValuesAsString(Collection<?> values) {
            return "(" + values.stream().map(v -> format("'%s'", v)).collect(Collectors.joining(",")) + ")";
        }
    }
}
