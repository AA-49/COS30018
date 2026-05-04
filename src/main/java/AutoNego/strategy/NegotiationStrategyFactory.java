package AutoNego.strategy;

import java.util.Locale;

/**
 * Resolves tactic names from profiles / CSV to strategy instances.
 * Unknown, blank, or "none" defaults to {@link LinearStrategy} for deterministic auto-negotiation.
 */
public final class NegotiationStrategyFactory {

    private NegotiationStrategyFactory() {
    }

    /**
     * Normalizes user input to a stable key: linear, boulware, conceder, or none.
     */
    public static String normalizeTacticKey(String raw) {
        if (raw == null) {
            return "none";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "none";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("boulware")) {
            return "boulware";
        }
        if (lower.contains("conceder")) {
            return "conceder";
        }
        if (lower.contains("linear")) {
            return "linear";
        }
        if ("none".equals(lower)) {
            return "none";
        }
        return "linear";
    }

    public static NegotiationStrategy fromTacticName(String raw) {
        return switch (normalizeTacticKey(raw)) {
            case "boulware" -> new BoulwareStrategy();
            case "conceder" -> new ConcederStrategy();
            default -> new LinearStrategy();
        };
    }
}
