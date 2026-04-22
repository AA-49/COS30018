package AutoNego.strategy;

// Stores information about the current negotiation details (e.g. max budget, time)
public final class NegotiationContext {

    // The agent's first offer
    public final double initialOffer;

    // The agent's absolute limit before walking away
    public final double reservePrice;

    // Max number of negotiation rounds allowed
    public final int maxRounds;

    // How many rounds have passed so far
    public final int roundsElapsed;

    public NegotiationContext(double initialOffer, double reservePrice,
            int maxRounds, int roundsElapsed) {
        this.initialOffer = initialOffer;
        this.reservePrice = reservePrice;
        this.maxRounds = maxRounds;
        this.roundsElapsed = roundsElapsed;
    }

    // Calculate how far along the negotiation is (0.0 means start, 1.0 means end)
    public double t() {
        if (maxRounds <= 0)
            return 1.0;
        return Math.min(1.0, (double) roundsElapsed / maxRounds);
    }

    // Go to next round
    public NegotiationContext nextRound() {
        return new NegotiationContext(initialOffer, reservePrice, maxRounds, roundsElapsed + 1);
    }

    // True when the agent has reached the maximum allowed rounds
    public boolean isExhausted() {
        return roundsElapsed > maxRounds;
    }
}
