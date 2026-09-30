package com.discgolfbagtips.api.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} per upstream, each with its own timeouts. Timeouts are the first half of
 * the resilience story; the circuit breakers configured in {@code application.yml} are the second.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient discItRestClient(RestClient.Builder builder, BagTipsProperties properties) {
        BagTipsProperties.DiscIt cfg = properties.discit();
        return builder.clone()
                .requestFactory(requestFactory(cfg.connectTimeout(), cfg.readTimeout()))
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "discgolfbagtips-api/0.1 (+https://discgolfbagtips.com)")
                .build();
    }

    @Bean
    public RestClient embeddingRestClient(RestClient.Builder builder, BagTipsProperties properties) {
        BagTipsProperties.Embedding cfg = properties.embedding();
        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory(cfg.connectTimeout(), cfg.readTimeout()))
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Accept", "application/json");
        if (cfg.hasCredentials()) {
            configured.defaultHeader("Authorization", "Bearer " + cfg.apiToken());
        }
        return configured.build();
    }

    @Bean
    public RestClient ollamaRestClient(RestClient.Builder builder, BagTipsProperties properties) {
        BagTipsProperties.Ollama cfg = properties.embedding().ollama();
        return builder.clone()
                .requestFactory(requestFactory(cfg.connectTimeout(), cfg.readTimeout()))
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    public RestClient generationRestClient(RestClient.Builder builder, BagTipsProperties properties) {
        BagTipsProperties.Generation cfg = properties.generation();
        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory(cfg.connectTimeout(), cfg.readTimeout()))
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Accept", "application/json");
        if (cfg.hasCredentials()) {
            configured.defaultHeader("Authorization", "Bearer " + cfg.apiKey());
        }
        return configured.build();
    }

    private JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
