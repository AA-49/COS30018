package AutoNego.strategy;

// Stores information about the current negotiation details (e.g. max budget, time)
public final class NegotiationContext {

    // Legacy price-only fields (kept for compatibility during transition).
    public final double initialOffer;
    public final double reservePrice;

    // Multi-attribute first and boundary offers.
    public final Offer initialOfferBundle;
    public final Offer reserveOfferBundle;

    // Optional preference model for utility-driven decisions.
    public final PreferenceProfile preferenceProfile;

    // Max number of negotiation rounds allowed
    public final int maxRounds;

    // How many rounds have passed so far
    public final int roundsElapsed;

    public NegotiationContext(double initialOffer, double reservePrice,
            int maxRounds, int roundsElapsed) {
        this.initialOffer = initialOffer;
        this.reservePrice = reservePrice;
        this.initialOfferBundle = Offer.priceOnly(initialOffer);
        this.reserveOfferBundle = Offer.priceOnly(reservePrice);
        this.preferenceProfile = null;
        this.maxRounds = maxRounds;
        this.roundsElapsed = roundsElapsed;
    }

    public NegotiationContext(Offer initialOfferBundle, Offer reserveOfferBundle,
            PreferenceProfile preferenceProfile, int maxRounds, int roundsElapsed) {
        if (initialOfferBundle == null || reserveOfferBundle == null) {
            throw new IllegalArgumentException("Offer bundles must not be null");
        }
        this.initialOfferBundle = initialOfferBundle;
        this.reserveOfferBundle = reserveOfferBundle;
        this.initialOffer = initialOfferBundle.price();
        this.reservePrice = reserveOfferBundle.price();
        this.preferenceProfile = preferenceProfile;
        this.maxRounds = maxRounds;
        this.roundsElapsed = roundsElapsed;
    }

    // Calculate how far along the negotiation is (0.0 means start, 1.0 means end)
    public double t() {
        if (maxRounds <= 0)
            return 1.0;
        // roundsElapsed starts at 0. We want the final scheduled round (maxRounds - 1)
        // to map to t = 1.0 so the strategy can reach reserve/floor before exhaustion.
        if (maxRounds == 1) {
            return 1.0;
        }
        return Math.min(1.0, (double) roundsElapsed / (maxRounds - 1));
    }

    // Go to next round
    public NegotiationContext nextRound() {
        if (preferenceProfile == null) {
            return new NegotiationContext(initialOffer, reservePrice, maxRounds, roundsElapsed + 1);
        }
        return new NegotiationContext(initialOfferBundle, reserveOfferBundle, preferenceProfile, maxRounds,
                roundsElapsed + 1);
    }

    // True when the agent has reached the maximum allowed rounds
    public boolean isExhausted() {
        return roundsElapsed >= maxRounds;
    }
}
