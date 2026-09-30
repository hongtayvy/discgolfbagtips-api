package com.discgolfbagtips.api.embedding;

import java.util.List;

/** A unit-length embedding plus the provenance the explainability payload reports. */
public record EmbeddingVector(float[] values, String model, boolean stubbed) {

    public int dimensions() {
        return values.length;
    }

    /** pgvector's text input format: {@code [0.1,-0.2,...]}. */
    public String toVectorLiteral() {
        StringBuilder literal = new StringBuilder(values.length * 8 + 2).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(values[i]);
        }
        return literal.append(']').toString();
    }

    public List<Float> toList() {
        List<Float> list = new java.util.ArrayList<>(values.length);
        for (float value : values) {
            list.add(value);
        }
        return List.copyOf(list);
    }

    public static float[] normalize(float[] raw) {
        double sumSquares = 0;
        for (float value : raw) {
            sumSquares += (double) value * value;
        }
        double magnitude = Math.sqrt(sumSquares);
        if (magnitude == 0) {
            return raw;
        }
        float[] normalized = new float[raw.length];
        for (int i = 0; i < raw.length; i++) {
            normalized[i] = (float) (raw[i] / magnitude);
        }
        return normalized;
    }
}
