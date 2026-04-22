package AutoNego;

import java.util.Arrays;

public final class DemoMessageCodec {
    private static final String FIELD_SEPARATOR = "\t";
    private static final String RECORD_SEPARATOR = "\n";

    private DemoMessageCodec() {
    }

    // would it be better to chagne this to JSON?
    public static String encodeFields(String... values) {
        return String.join(FIELD_SEPARATOR, Arrays.stream(values)
                .map(value -> value == null ? "" : value.replace(FIELD_SEPARATOR, " ").replace(RECORD_SEPARATOR, " "))
                .toArray(String[]::new));
    }

    public static String[] decodeFields(String payload, int minimumParts) {
        String[] parts = payload.split(FIELD_SEPARATOR, -1);
        if (parts.length < minimumParts) {
            throw new IllegalArgumentException("Expected at least " + minimumParts + " parts but got " + parts.length);
        }
        return parts;
    }

    public static String encodeRecords(String... records) {
        return String.join(RECORD_SEPARATOR, records);
    }

    public static String[] decodeRecords(String payload) {
        if (payload == null || payload.isBlank()) {
            return new String[0];
        }
        return payload.split(RECORD_SEPARATOR);
    }
}
