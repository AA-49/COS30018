package AutoNego.strategy;

import java.util.Objects;

// Captures how an agent evaluates offers in a negotiation session.
public final class PreferenceProfile {

    // Utility evaluator for this agent (e.g., weighted sum across attributes).
    public final UtilityFunction utilityFunction;

    // Minimum acceptable utility at deadline (0..1).
    public final double reservationUtility;

    // Target utility at the start of negotiation (0..1).
    public final double aspirationUtility;

    public PreferenceProfile(UtilityFunction utilityFunction, double aspirationUtility, double reservationUtility) {
        this.utilityFunction = Objects.requireNonNull(utilityFunction, "utilityFunction");
        this.aspirationUtility = clamp01(aspirationUtility);
        this.reservationUtility = clamp01(reservationUtility);
        if (this.reservationUtility > this.aspirationUtility) {
            throw new IllegalArgumentException("reservationUtility cannot be higher than aspirationUtility");
        }
    }

    public double score(Offer offer) {
        return utilityFunction.score(offer);
    }

    // Time-based acceptance threshold. Starts strict, relaxes toward reservation utility.
    public double thresholdAt(double t) {
        double clampedT = clamp01(t);
        return aspirationUtility - (aspirationUtility - reservationUtility) * clampedT;
    }

    private static double clamp01(double value) {
        if (value < 0.0)
            return 0.0;
        if (value > 1.0)
            return 1.0;
        return value;
    }
}

