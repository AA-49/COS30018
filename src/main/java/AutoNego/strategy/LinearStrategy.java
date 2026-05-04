package AutoNego.strategy;

// Gives the next offer by decreasing or increasing price at a steady, equal rate each round
public final class LinearStrategy implements NegotiationStrategy {

    @Override
    public Offer nextOffer(NegotiationContext ctx) {
        return interpolate(ctx.initialOfferBundle, ctx.reserveOfferBundle, ctx.t());
    }

    @Override
    public String getName() { return "Linear"; }

    static Offer interpolate(Offer from, Offer to, double t) {
        return new Offer(
                lerp(from.price(), to.price(), t),
                lerpInt(from.warrantyMonths(), to.warrantyMonths(), t),
                lerpInt(from.insuranceIncludedMonths(), to.insuranceIncludedMonths(), t),
                lerpInt(from.servicePackageLevel(), to.servicePackageLevel(), t));
    }

    static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    static int lerpInt(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }
}
