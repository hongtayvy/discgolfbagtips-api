package com.discgolfbagtips.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A manufacturer plastic blend and, crucially, its effect on flight. The {@code stabilityShift} is
 * applied to a mold's stability index to produce the flight the player actually experiences.
 */
@Entity
@Table(name = "plastic_type")
public class PlasticType {

    /** Used when the caller names a plastic we do not know, or names none at all. */
    public static final String GENERIC_BRAND = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand", nullable = false, length = 120)
    private String brand;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "family", nullable = false, length = 32)
    private PlasticFamily family;

    /** Positive = flies more overstable than the published numbers; negative = more understable. */
    @Column(name = "stability_shift", nullable = false)
    private double stabilityShift;

    /** 1 (beats in within a round) to 5 (holds its flight for years). */
    @Column(name = "durability", nullable = false)
    private int durability;

    /** 1 (slick) to 5 (tacky). */
    @Column(name = "grip", nullable = false)
    private int grip;

    @Column(name = "description", nullable = false, length = 1024)
    private String description;

    protected PlasticType() {
    }

    public Long id() {
        return id;
    }

    public String brand() {
        return brand;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public PlasticFamily family() {
        return family;
    }

    public double stabilityShift() {
        return stabilityShift;
    }

    public int durability() {
        return durability;
    }

    public int grip() {
        return grip;
    }

    public String description() {
        return description;
    }

    public boolean generic() {
        return GENERIC_BRAND.equals(brand);
    }
}
