package AutoNego.strategy;

public interface NegotiationStrategy {

    double nextOffer(NegotiationContext ctx);

    String getName();
}
