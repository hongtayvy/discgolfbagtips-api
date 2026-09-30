package com.discgolfbagtips.api.generation;

/** Minimal chat abstraction — one system prompt, one user prompt, one JSON answer. */
public interface ChatModelClient {

    String complete(String systemPrompt, String userPrompt);

    String model();

    String provider();

    boolean available();
}
