package AutoNego.strategy;

// Calculates the overall score of an offer by weighting each attribute
public final class WeightedSumUtility implements UtilityFunction {

    private final double priceWeight;
    private final double priceBest;
    private final double priceWorst;

    private final double warrantyWeight;
    private final double warrantyBest;
    private final double warrantyWorst;

    private final double insuranceWeight;
    private final double insuranceBest;
    private final double insuranceWorst;

    private final double servicePackageWeight;
    private final double servicePackageBest;
    private final double servicePackageWorst;

    private WeightedSumUtility(Builder b) {
        this.priceWeight = b.priceWeight;
        this.priceBest = b.priceBest;
        this.priceWorst = b.priceWorst;
        this.warrantyWeight = b.warrantyWeight;
        this.warrantyBest = b.warrantyBest;
        this.warrantyWorst = b.warrantyWorst;
        this.insuranceWeight = b.insuranceWeight;
        this.insuranceBest = b.insuranceBest;
        this.insuranceWorst = b.insuranceWorst;
        this.servicePackageWeight = b.servicePackageWeight;
        this.servicePackageBest = b.servicePackageBest;
        this.servicePackageWorst = b.servicePackageWorst;
    }

    @Override
    public double score(Offer offer) {
        double total = 0.0;
        double totalWeight = 0.0;

        if (priceWeight > 0) {
            total += priceWeight * normalise(offer.price(), priceBest, priceWorst);
            totalWeight += priceWeight;
        }
        if (warrantyWeight > 0) {
            total += warrantyWeight * normalise(offer.warrantyMonths(), warrantyBest, warrantyWorst);
            totalWeight += warrantyWeight;
        }
        if (insuranceWeight > 0) {
            total += insuranceWeight * normalise(offer.insuranceIncludedMonths(), insuranceBest, insuranceWorst);
            totalWeight += insuranceWeight;
        }
        if (servicePackageWeight > 0) {
            total += servicePackageWeight * normalise(offer.servicePackageLevel(), servicePackageBest, servicePackageWorst);
            totalWeight += servicePackageWeight;
        }

        return totalWeight == 0 ? 0.0 : Math.max(0.0, Math.min(1.0, total / totalWeight));
    }

    private double normalise(double value, double best, double worst) {
        if (best == worst)
            return 1.0;
        return (value - worst) / (best - worst);
    }

    // Helper builder to configure weights for each attribute
    public static final class Builder {
        private double priceWeight = 0, priceBest = 0, priceWorst = 0;
        private double warrantyWeight = 0, warrantyBest = 0, warrantyWorst = 0;
        private double insuranceWeight = 0, insuranceBest = 0, insuranceWorst = 0;
        private double servicePackageWeight = 0, servicePackageBest = 0, servicePackageWorst = 0;

        // Configure price (for buyers, lower is better. for dealers, higher is better)
        public Builder price(double weight, double best, double worst) {
            this.priceWeight = weight;
            this.priceBest = best;
            this.priceWorst = worst;
            return this;
        }

        // Configure warranty (higher is usually better)
        public Builder warrantyMonths(double weight, double best, double worst) {
            this.warrantyWeight = weight;
            this.warrantyBest = best;
            this.warrantyWorst = worst;
            return this;
        }

        // Configure included insurance months (higher is better)
        public Builder insuranceIncludedMonths(double weight, double best, double worst) {
            this.insuranceWeight = weight;
            this.insuranceBest = best;
            this.insuranceWorst = worst;
            return this;
        }

        // Configure service package level (higher is better)
        public Builder servicePackageLevel(double weight, double best, double worst) {
            this.servicePackageWeight = weight;
            this.servicePackageBest = best;
            this.servicePackageWorst = worst;
            return this;
        }

        public WeightedSumUtility build() {
            return new WeightedSumUtility(this);
        }
    }
}
