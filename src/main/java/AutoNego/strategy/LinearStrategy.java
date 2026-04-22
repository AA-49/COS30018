package AutoNego.strategy;

// Gives the next offer by decreasing or increasing price at a steady, equal rate each round
public final class LinearStrategy implements NegotiationStrategy {

    @Override
    public double nextOffer(NegotiationContext ctx) {
        return ctx.initialOffer + (ctx.reservePrice - ctx.initialOffer) * ctx.t();
    }

    @Override
    public String getName() { return "Linear"; }
}
