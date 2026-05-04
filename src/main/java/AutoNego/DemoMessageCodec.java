package AutoNego;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class DemoMessageCodec {
    private static final Gson GSON = new Gson();
    private static final String FIELD_SEPARATOR = "\t";
    private static final String RECORD_SEPARATOR = "\n";

    private DemoMessageCodec() {
    }

    // Encodes fields as a JSON string-array for robust transport.
    public static String encodeFields(String... values) {
        String[] safe = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            safe[i] = values[i] == null ? "" : values[i];
        }
        return GSON.toJson(safe);
    }

    public static String[] decodeFields(String payload, int minimumParts) {
        String[] parts = tryDecodeJsonArray(payload);
        if (parts == null) {
            // Backward-compatibility for pre-JSON payloads.
            parts = payload.split(FIELD_SEPARATOR, -1);
        }
        if (parts.length < minimumParts) {
            throw new IllegalArgumentException("Expected at least " + minimumParts + " parts but got " + parts.length);
        }
        return parts;
    }

    public static String encodeRecords(String... records) {
        return encodeFields(records);
    }

    public static String[] decodeRecords(String payload) {
        if (payload == null || payload.isBlank()) {
            return new String[0];
        }
        String[] records = tryDecodeJsonArray(payload);
        if (records != null) {
            return records;
        }
        // Backward-compatibility for pre-JSON payloads.
        return payload.split(RECORD_SEPARATOR);
    }

    private static String[] tryDecodeJsonArray(String payload) {
        if (payload == null || payload.isBlank())
            return null;
        String trimmed = payload.trim();
        if (!trimmed.startsWith("[")) {
            return null;
        }
        try {
            return GSON.fromJson(trimmed, String[].class);
        } catch (JsonSyntaxException ex) {
            return null;
        }
    }
}
