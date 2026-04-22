package AutoNego.strategy;

// Gives a score to an offer (0.0 to 1.0) where 1.0 is the best possible deal
public interface UtilityFunction {

    double score(Offer offer);
}
