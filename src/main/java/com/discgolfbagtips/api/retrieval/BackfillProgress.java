package com.discgolfbagtips.api.retrieval;

import java.time.Duration;
import java.time.Instant;

/**
 * Live state of a full embedding backfill. Held in memory and polled over HTTP, because embedding
 * the whole catalog takes longer than a sensible request timeout.
 *
 * @param remaining discs still lacking a current vector, refreshed after each batch
 */
public record BackfillProgress(
        State state,
        String model,
        int embedded,
        int failed,
        int remaining,
        int batches,
        Instant startedAt,
        Instant finishedAt,
        String message) {

    public enum State {
        IDLE,
        RUNNING,
        COMPLETED,
        /** Stopped early — usually the embedding provider rate-limiting or going down. */
        FAILED,
        DISABLED
    }

    public static BackfillProgress idle(String model) {
        return new BackfillProgress(State.IDLE, model, 0, 0, 0, 0, null, null,
                "No full backfill has run since startup");
    }

    /** Claimed but not yet counted; the worker replaces this as soon as it knows the workload. */
    public static BackfillProgress claimed(String model) {
        return new BackfillProgress(State.RUNNING, model, 0, 0, 0, 0, Instant.now(), null,
                "Backfill claimed, counting work");
    }

    public static BackfillProgress starting(String model, int remaining) {
        return new BackfillProgress(State.RUNNING, model, 0, 0, remaining, 0, Instant.now(), null,
                "Backfill started");
    }

    public BackfillProgress advanced(int justEmbedded, int stillRemaining) {
        return new BackfillProgress(State.RUNNING, model, embedded + justEmbedded, failed, stillRemaining,
                batches + 1, startedAt, null, "Embedding in progress");
    }

    public BackfillProgress completed() {
        return new BackfillProgress(State.COMPLETED, model, embedded, failed, remaining, batches, startedAt,
                Instant.now(), "Every active disc has a current embedding");
    }

    public BackfillProgress failed(String reason, int stillRemaining) {
        return new BackfillProgress(State.FAILED, model, embedded, stillRemaining, stillRemaining, batches,
                startedAt, Instant.now(), reason);
    }

    public boolean running() {
        return state == State.RUNNING;
    }

    /** Wall-clock duration, live while running. */
    public Long elapsedMs() {
        if (startedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt == null ? Instant.now() : finishedAt).toMillis();
    }

    public double percentComplete() {
        int total = embedded + remaining;
        return total == 0 ? 100.0 : Math.round((double) embedded / total * 1000.0) / 10.0;
    }
}
