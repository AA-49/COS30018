package AutoNego.strategy;

public final class BoulwareStrategy implements NegotiationStrategy {

    /** Concession exponent. Lower = more stubborn. */
    private static final double E = 0.2;

    @Override
    public double nextOffer(NegotiationContext ctx) {
        double ft = Math.pow(ctx.t(), 1.0 / E); // = t^5
        return ctx.initialOffer + (ctx.reservePrice - ctx.initialOffer) * ft;
    }

    @Override
    public String getName() {
        return "Boulware (e=0.2)";
    }
}
