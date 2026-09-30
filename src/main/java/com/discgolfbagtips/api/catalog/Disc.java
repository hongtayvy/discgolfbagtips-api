package com.discgolfbagtips.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A disc mold synced from the DiscIt catalog. Plastic is a separate axis; see {@link PlasticType}. */
@Entity
@Table(name = "disc")
public class Disc {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "brand", nullable = false, length = 120)
    private String brand;

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "speed", nullable = false)
    private double speed;

    @Column(name = "glide", nullable = false)
    private double glide;

    @Column(name = "turn", nullable = false)
    private double turn;

    @Column(name = "fade", nullable = false)
    private double fade;

    /** The upstream stability label, kept verbatim so the embedding passage can quote the source. */
    @Column(name = "stability_label", length = 60)
    private String stabilityLabel;

    @Column(name = "name_slug", length = 200)
    private String nameSlug;

    @Column(name = "brand_slug", length = 120)
    private String brandSlug;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Hash of the fields that feed the embedding; a change here invalidates the stored vector. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    protected Disc() {
    }

    public Disc(String id) {
        this.id = id;
    }

    public double stabilityIndex() {
        return turn + fade;
    }

    public StabilityClass stabilityClass() {
        return StabilityClass.forIndex(stabilityIndex());
    }

    public DiscSlot slot() {
        return DiscSlot.forSpeed(speed);
    }

    public String displayName() {
        return brand + " " + name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String brand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String category() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double glide() {
        return glide;
    }

    public void setGlide(double glide) {
        this.glide = glide;
    }

    public double turn() {
        return turn;
    }

    public void setTurn(double turn) {
        this.turn = turn;
    }

    public double fade() {
        return fade;
    }

    public void setFade(double fade) {
        this.fade = fade;
    }

    public String stabilityLabel() {
        return stabilityLabel;
    }

    public void setStabilityLabel(String stabilityLabel) {
        this.stabilityLabel = stabilityLabel;
    }

    public String nameSlug() {
        return nameSlug;
    }

    public void setNameSlug(String nameSlug) {
        this.nameSlug = nameSlug;
    }

    public String brandSlug() {
        return brandSlug;
    }

    public void setBrandSlug(String brandSlug) {
        this.brandSlug = brandSlug;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String contentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant lastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
