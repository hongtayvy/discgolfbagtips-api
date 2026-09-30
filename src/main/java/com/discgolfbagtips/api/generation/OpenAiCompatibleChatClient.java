package com.discgolfbagtips.api.generation;

import com.discgolfbagtips.api.common.UpstreamServiceException;
import com.discgolfbagtips.api.config.BagTipsProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Groq and OpenRouter both expose the OpenAI chat-completions shape, so one client covers either
 * free tier; {@code bagtips.generation.base-url} and {@code .model} decide which is in play.
 */
@Component
public class OpenAiCompatibleChatClient implements ChatModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatClient.class);

    private final RestClient restClient;
    private final BagTipsProperties.Generation config;

    public OpenAiCompatibleChatClient(RestClient generationRestClient, BagTipsProperties properties) {
        this.restClient = generationRestClient;
        this.config = properties.generation();
    }

    @Override
    @CircuitBreaker(name = "generation", fallbackMethod = "completeFallback")
    @Retry(name = "generation")
    public String complete(String systemPrompt, String userPrompt) {
        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", config.model(),
                        "temperature", config.temperature(),
                        "max_tokens", config.maxTokens(),
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt))))
                .retrieve()
                .body(ChatCompletionResponse.class);

        String content = response == null ? null : response.firstContent();
        if (content == null || content.isBlank()) {
            throw new UpstreamServiceException(config.provider(), "Reasoning model returned an empty completion");
        }
        return content;
    }

    @SuppressWarnings("unused")
    private String completeFallback(String systemPrompt, String userPrompt, Throwable cause) {
        if (cause instanceof UpstreamServiceException upstream) {
            throw upstream;
        }
        String reason = cause instanceof RestClientException ? cause.getMessage() : cause.toString();
        log.warn("Reasoning model call failed: {}", reason);
        throw new UpstreamServiceException(config.provider(), "Reasoning model unavailable: " + reason, cause);
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public String provider() {
        return config.provider();
    }

    @Override
    public boolean available() {
        return config.hasCredentials();
    }

    /** Only the fields we actually read; unknown properties are ignored by the converter. */
    record ChatCompletionResponse(List<Choice> choices) {

        String firstContent() {
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Choice choice = choices.getFirst();
            return choice == null || choice.message() == null ? null : choice.message().content();
        }

        record Choice(Message message) {
        }

        record Message(String role, String content) {
        }
    }
}
