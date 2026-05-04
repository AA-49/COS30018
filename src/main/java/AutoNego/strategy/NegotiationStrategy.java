package AutoNego.strategy;

public interface NegotiationStrategy {

    Offer nextOffer(NegotiationContext ctx);

    String getName();
}
