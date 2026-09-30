package com.discgolfbagtips.api.bag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A physical bag — the backpack, cart or duffle, not the discs inside it.
 *
 * <p>No upstream source publishes this: DiscIt covers molds only, and manufacturers describe bags in
 * marketing copy rather than a feed. Several fields are therefore nullable by design, because a
 * maker that does not publish an empty weight should leave a gap rather than a guess.
 */
@Entity
@Table(name = "bag_model")
public class BagModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand", nullable = false, length = 120)
    private String brand;

    @Column(name = "model", nullable = false, length = 160)
    private String model;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "disc_capacity_min")
    private Integer discCapacityMin;

    @Column(name = "disc_capacity_max", nullable = false)
    private int discCapacityMax;

    /** Null when the manufacturer does not publish one — common outside the premium brands. */
    @Column(name = "empty_weight_grams")
    private Integer emptyWeightGrams;

    @Column(name = "height_in", precision = 5, scale = 2)
    private BigDecimal heightIn;

    @Column(name = "width_in", precision = 5, scale = 2)
    private BigDecimal widthIn;

    @Column(name = "depth_in", precision = 5, scale = 2)
    private BigDecimal depthIn;

    @Column(name = "carry_on_compliant")
    private Boolean carryOnCompliant;

    @Column(name = "price_usd", precision = 8, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "build_tier", nullable = false, length = 16)
    private String buildTier;

    @Column(name = "bag_type", nullable = false, length = 16)
    private String bagType;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "notes", length = 1024)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    protected BagModel() {
    }

    public String displayName() {
        return brand + " " + model;
    }

    /** True when the bag's published capacity cannot hold this many discs. */
    public boolean overpackedBy(int discCount) {
        return discCount > discCapacityMax;
    }

    public boolean hasPublishedWeight() {
        return emptyWeightGrams != null;
    }

    public Long id() {
        return id;
    }

    public String brand() {
        return brand;
    }

    public String model() {
        return model;
    }

    public String slug() {
        return slug;
    }

    public Integer discCapacityMin() {
        return discCapacityMin;
    }

    public int discCapacityMax() {
        return discCapacityMax;
    }

    public Integer emptyWeightGrams() {
        return emptyWeightGrams;
    }

    public BigDecimal heightIn() {
        return heightIn;
    }

    public BigDecimal widthIn() {
        return widthIn;
    }

    public BigDecimal depthIn() {
        return depthIn;
    }

    public Boolean carryOnCompliant() {
        return carryOnCompliant;
    }

    public BigDecimal priceUsd() {
        return priceUsd;
    }

    public String buildTier() {
        return buildTier;
    }

    public String bagType() {
        return bagType;
    }

    public String source() {
        return source;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public String notes() {
        return notes;
    }

    public boolean active() {
        return active;
    }

    public Instant lastSyncedAt() {
        return lastSyncedAt;
    }
}
