package com.discgolfbagtips.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * A named bag setup a player can keep and re-analyse.
 *
 * <p>{@code ownerKey} is an opaque string rather than a user foreign key: {@code session:<id>} today,
 * {@code user:<uuid>} once Supabase auth lands. Claiming a session's profiles at sign-up is then an
 * update rather than a schema change, which is what lets profiles ship before accounts do.
 */
@Entity
@Table(name = "bag_profile")
public class BagProfile {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "owner_key", nullable = false, length = 128)
    private String ownerKey;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    /**
     * The request body verbatim, so a profile can be replayed without re-resolving anything.
     *
     * <p>Stored as {@code jsonb} rather than {@code text}: Postgres validates it on write, and a
     * later feature can query inside it without a migration. The explicit JDBC type is required —
     * without it Hibernate binds the String as {@code varchar} and Postgres refuses the implicit
     * cast.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "disc_count", nullable = false)
    private int discCount;

    @Column(name = "bag_model_id")
    private Long bagModelId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    protected BagProfile() {
    }

    public BagProfile(String ownerKey, String name, String description, String payload, int discCount,
            Long bagModelId) {
        this.id = UUID.randomUUID();
        this.ownerKey = ownerKey;
        this.name = name;
        this.description = description;
        this.payload = payload;
        this.discCount = discCount;
        this.bagModelId = bagModelId;
    }

    public void update(String name, String description, String payload, int discCount, Long bagModelId) {
        this.name = name;
        this.description = description;
        this.payload = payload;
        this.discCount = discCount;
        this.bagModelId = bagModelId;
        this.updatedAt = Instant.now();
    }

    public void markViewed() {
        this.lastViewedAt = Instant.now();
    }

    /** Transfers ownership from an anonymous session to a signed-in user. */
    public void claimedBy(String userOwnerKey) {
        this.ownerKey = userOwnerKey;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String ownerKey() {
        return ownerKey;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String payload() {
        return payload;
    }

    public int discCount() {
        return discCount;
    }

    public Long bagModelId() {
        return bagModelId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant lastViewedAt() {
        return lastViewedAt;
    }
}
