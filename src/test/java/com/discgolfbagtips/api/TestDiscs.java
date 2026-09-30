package com.discgolfbagtips.api;

import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.PlasticFamily;
import com.discgolfbagtips.api.catalog.PlasticType;
import java.lang.reflect.Field;

/** Builders for the JPA entities, which have no public constructors by design. */
public final class TestDiscs {

    private TestDiscs() {
    }

    public static Disc disc(String id, String brand, String name, String category,
            double speed, double glide, double turn, double fade) {
        Disc disc = new Disc(id);
        disc.setBrand(brand);
        disc.setName(name);
        disc.setCategory(category);
        disc.setSpeed(speed);
        disc.setGlide(glide);
        disc.setTurn(turn);
        disc.setFade(fade);
        disc.setStabilityLabel("Stable");
        disc.setContentHash("hash-" + id);
        return disc;
    }

    public static PlasticType plastic(String brand, String name, PlasticFamily family, double stabilityShift,
            int durability, int grip, String description) {
        PlasticType plastic = instantiate(PlasticType.class);
        set(plastic, "brand", brand);
        set(plastic, "name", name);
        set(plastic, "slug", name.toLowerCase().replace(' ', '-'));
        set(plastic, "family", family);
        set(plastic, "stabilityShift", stabilityShift);
        set(plastic, "durability", durability);
        set(plastic, "grip", grip);
        set(plastic, "description", description);
        return plastic;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not instantiate " + type.getSimpleName(), ex);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not set " + fieldName, ex);
        }
    }
}
