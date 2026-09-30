package com.discgolfbagtips.api.retrieval;

import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.embedding.EmbeddingVector;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pgvector half of the pipeline. Hibernate does not map the {@code vector} type, so the vector
 * table is reached with plain SQL and the embedding is passed as a literal that Postgres casts.
 */
@Repository
public class DiscVectorStore {

    private static final String SELECT_COLUMNS = """
            d.id, d.name, d.brand, d.category, d.speed, d.glide, d.turn, d.fade,
            d.stability_label, d.image_url, d.source_url, e.passage, e.descriptors, e.model
            """;

    private final JdbcClient jdbcClient;

    public DiscVectorStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public void upsert(EmbeddingService.DiscEmbedding embedding, String sourceHash) {
        jdbcClient.sql("""
                insert into disc_embedding
                    (disc_id, model, dimensions, source_hash, passage, descriptors, embedding, updated_at)
                values
                    (:discId, :model, :dimensions, :sourceHash, :passage, :descriptors, cast(:embedding as vector), :updatedAt)
                on conflict (disc_id) do update set
                    model = excluded.model,
                    dimensions = excluded.dimensions,
                    source_hash = excluded.source_hash,
                    passage = excluded.passage,
                    descriptors = excluded.descriptors,
                    embedding = excluded.embedding,
                    updated_at = excluded.updated_at
                """)
                .param("discId", embedding.discId())
                .param("model", embedding.vector().model())
                .param("dimensions", embedding.vector().dimensions())
                .param("sourceHash", sourceHash)
                .param("passage", embedding.text())
                .param("descriptors", String.join(" | ", embedding.descriptors()))
                .param("embedding", embedding.vector().toVectorLiteral())
                .param("updatedAt", java.sql.Timestamp.from(Instant.now()))
                .update();
    }

    /**
     * Hybrid retrieval: a structured speed window keeps the candidates in the right slot, and cosine
     * distance over the descriptive passage ranks them within it.
     */
    @Transactional(readOnly = true)
    public List<RetrievedDisc> search(EmbeddingVector query, double minSpeed, double maxSpeed,
            RetrievalFilters filters, int limit) {

        var statement = jdbcClient.sql("""
                select %s, (e.embedding <=> cast(:query as vector)) as distance
                from disc_embedding e
                join disc d on d.id = e.disc_id
                where d.active = true
                  and e.model = :model
                  and d.speed between :minSpeed and :maxSpeed
                  %s
                order by e.embedding <=> cast(:query as vector)
                limit :limit
                """.formatted(SELECT_COLUMNS, brandClauses(filters)))
                .param("query", query.toVectorLiteral())
                .param("model", query.model())
                .param("minSpeed", minSpeed)
                .param("maxSpeed", effectiveMaxSpeed(maxSpeed, filters))
                .param("limit", limit);
        return bindFilters(statement, filters).query((rs, rowNum) -> map(rs, rowNum + 1)).list();
    }

    /**
     * Only the shape of the clause is built from the filter; every value is still a bound parameter,
     * so a brand name can never reach the parser as SQL.
     */
    private static String brandClauses(RetrievalFilters filters) {
        StringBuilder clauses = new StringBuilder();
        if (filters.restrictsBrands()) {
            clauses.append(" and lower(d.brand) in (:brands)");
        }
        if (!filters.excludeBrands().isEmpty()) {
            clauses.append(" and lower(d.brand) not in (:excludeBrands)");
        }
        return clauses.toString();
    }

    private static org.springframework.jdbc.core.simple.JdbcClient.StatementSpec bindFilters(
            org.springframework.jdbc.core.simple.JdbcClient.StatementSpec statement,
            RetrievalFilters filters) {
        if (filters.restrictsBrands()) {
            statement = statement.param("brands", filters.brands());
        }
        if (!filters.excludeBrands().isEmpty()) {
            statement = statement.param("excludeBrands", filters.excludeBrands());
        }
        return statement;
    }

    private static double effectiveMaxSpeed(double slotMax, RetrievalFilters filters) {
        return filters.maxSpeed() == null ? slotMax : Math.min(slotMax, filters.maxSpeed());
    }

    /**
     * The no-vector path. Used when the embedding provider is down or the catalog has not been
     * embedded yet: rank by straight-line distance in flight-number space instead.
     */
    @Transactional(readOnly = true)
    public List<RetrievedDisc> searchByFlightNumbers(double speed, double glide, double turn, double fade,
            double minSpeed, double maxSpeed, RetrievalFilters filters, int limit) {
        var statement = jdbcClient.sql("""
                select d.id, d.name, d.brand, d.category, d.speed, d.glide, d.turn, d.fade,
                       d.stability_label, d.image_url, d.source_url,
                       null as passage, null as descriptors, 'flight-numbers' as model,
                       sqrt(power(d.speed - :speed, 2) + power(d.glide - :glide, 2)
                            + power(d.turn - :turn, 2) + power(d.fade - :fade, 2)) as distance
                from disc d
                where d.active = true and d.speed between :minSpeed and :maxSpeed
                  %s
                order by distance
                limit :limit
                """.formatted(brandClauses(filters)))
                .param("speed", speed)
                .param("glide", glide)
                .param("turn", turn)
                .param("fade", fade)
                .param("minSpeed", minSpeed)
                .param("maxSpeed", effectiveMaxSpeed(maxSpeed, filters))
                .param("limit", limit);
        return bindFilters(statement, filters).query((rs, rowNum) -> map(rs, rowNum + 1)).list();
    }

    /** Brands present in the catalog, for populating a filter control. */
    @Transactional(readOnly = true)
    public List<String> activeBrands() {
        return jdbcClient.sql("select distinct brand from disc where active = true order by brand")
                .query(String.class).list();
    }

    /** Discs whose vector is missing or was built from a now-stale passage. */
    @Transactional(readOnly = true)
    public List<String> findDiscIdsNeedingEmbedding(String model, int limit) {
        return jdbcClient.sql("""
                select d.id
                from disc d
                left join disc_embedding e on e.disc_id = d.id
                where d.active = true
                  and (e.disc_id is null or e.source_hash is distinct from d.content_hash or e.model <> :model)
                order by d.brand, d.name
                limit :limit
                """)
                .param("model", model)
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    @Transactional(readOnly = true)
    public VectorStoreStats stats(String model) {
        return jdbcClient.sql("""
                select
                    (select count(*) from disc where active = true) as active_discs,
                    (select count(*) from disc_embedding e join disc d on d.id = e.disc_id
                        where d.active = true and e.model = :model
                          and e.source_hash is not distinct from d.content_hash) as embedded_discs,
                    (select max(updated_at) from disc_embedding) as last_embedded_at
                """)
                .param("model", model)
                .query((rs, rowNum) -> new VectorStoreStats(
                        rs.getLong("active_discs"),
                        rs.getLong("embedded_discs"),
                        rs.getTimestamp("last_embedded_at") == null
                                ? null
                                : rs.getTimestamp("last_embedded_at").toInstant()))
                .single();
    }

    private RetrievedDisc map(java.sql.ResultSet rs, int rank) throws java.sql.SQLException {
        double distance = rs.getDouble("distance");
        String descriptors = rs.getString("descriptors");
        return new RetrievedDisc(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("brand"),
                rs.getString("category"),
                rs.getDouble("speed"),
                rs.getDouble("glide"),
                rs.getDouble("turn"),
                rs.getDouble("fade"),
                rs.getString("stability_label"),
                rs.getString("image_url"),
                rs.getString("source_url"),
                rs.getString("passage"),
                descriptors == null || descriptors.isBlank()
                        ? List.of()
                        : Arrays.stream(descriptors.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                rs.getString("model"),
                round(1.0 - distance),
                round(distance),
                rank);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record VectorStoreStats(long activeDiscs, long embeddedDiscs, Instant lastEmbeddedAt) {

        public boolean ready() {
            return embeddedDiscs > 0;
        }

        public double coverage() {
            return activeDiscs == 0 ? 0.0 : Math.round((double) embeddedDiscs / activeDiscs * 1000.0) / 1000.0;
        }
    }
}
