package AutoNego;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DemoMessageCodec {
    private static final String FIELD_SEPARATOR = "\t";
    private static final String RECORD_SEPARATOR = "\n";
    private static final String PROPERTY_SEPARATOR = ";";
    private static final String KEY_VALUE_SEPARATOR = "=";

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

    public static String encodeProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (builder.length() > 0) {
                builder.append(PROPERTY_SEPARATOR);
            }
            builder.append(urlEncode(entry.getKey()));
            builder.append(KEY_VALUE_SEPARATOR);
            builder.append(urlEncode(entry.getValue()));
        }
        return builder.toString();
    }

    public static Map<String, String> decodeProperties(String payload) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) {
            return properties;
        }

        for (String pair : payload.split(PROPERTY_SEPARATOR)) {
            if (pair.isEmpty()) {
                continue;
            }
            int separatorIndex = pair.indexOf(KEY_VALUE_SEPARATOR);
            if (separatorIndex < 0) {
                properties.put(urlDecode(pair), "");
            } else {
                String key = pair.substring(0, separatorIndex);
                String value = pair.substring(separatorIndex + 1);
                properties.put(urlDecode(key), urlDecode(value));
            }
        }
        return properties;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
