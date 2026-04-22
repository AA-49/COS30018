package AutoNego.strategy;

public final class ConcederStrategy implements NegotiationStrategy {

    private static final double E = 5.0;

    @Override
    public double nextOffer(NegotiationContext ctx) {
        double ft = Math.pow(ctx.t(), 1.0 / E);
        return ctx.initialOffer + (ctx.reservePrice - ctx.initialOffer) * ft;
    }

    @Override
    public String getName() {
        return "Conceder (e=5.0)";
    }
}
