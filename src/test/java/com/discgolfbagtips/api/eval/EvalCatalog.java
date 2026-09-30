package com.discgolfbagtips.api.eval;

import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.sync.DiscItDisc;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.CollectionType;

/**
 * Fetches the DiscIt catalog once and caches it on disk, so repeated eval runs do not hammer a
 * volunteer-run free API. Deliberately independent of the database: the whole harness runs in
 * memory, which means it needs no Postgres, no Supabase and no Docker.
 */
final class EvalCatalog {

    private static final String URL = "https://discit-api.fly.dev/disc";
    private static final Path CACHE = Path.of(System.getProperty("java.io.tmpdir"), "discit-eval-cache.json");

    private EvalCatalog() {
    }

    static List<Disc> load() throws IOException, InterruptedException {
        String json = cached();
        JsonMapper mapper = JsonMapper.builder().build();
        CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, DiscItDisc.class);
        List<DiscItDisc> raw = mapper.readValue(json, type);

        // Upstream returns every disc twice under one id. Left in, each relevant mold would occupy
        // two of the five precision@5 slots, quietly distorting the comparison.
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Disc> discs = new ArrayList<>(raw.size());
        for (DiscItDisc source : raw) {
            if (!source.usable() || "Disc Golf Sets".equals(source.category()) || !seen.add(source.id())) {
                continue;
            }
            Disc disc = new Disc(source.id());
            disc.setName(source.name());
            disc.setBrand(source.brand());
            disc.setCategory(source.category() == null ? "Unknown" : source.category());
            disc.setSpeed(source.speedValue());
            disc.setGlide(source.glideValue());
            disc.setTurn(source.turnValue());
            disc.setFade(source.fadeValue());
            disc.setStabilityLabel(source.stability());
            discs.add(disc);
        }
        return List.copyOf(discs);
    }

    private static String cached() throws IOException, InterruptedException {
        if (Files.exists(CACHE) && Files.size(CACHE) > 1000) {
            return Files.readString(CACHE);
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("DiscIt returned HTTP " + response.statusCode());
        }
        Files.writeString(CACHE, response.body());
        return response.body();
    }
}
