package AutoNego.strategy;

// Information about the car being negotiated
public record Offer(double price, int warrantyMonths, int deliveryDays, double conditionScore) {

    // Helper: Create an offer when we only care about price
    public static Offer priceOnly(double price) {
        return new Offer(price, 0, 0, 0.0);
    }

    // Change the price
    public Offer withPrice(double newPrice) {
        return new Offer(newPrice, warrantyMonths, deliveryDays, conditionScore);
    }

    // Change the warranty
    public Offer withWarrantyMonths(int months) {
        return new Offer(price, months, deliveryDays, conditionScore);
    }

    // Change the condition score
    public Offer withConditionScore(double score) {
        return new Offer(price, warrantyMonths, deliveryDays, score);
    }

    // Helper to print out the offer details nicely
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("RM %,.2f", price));
        if (warrantyMonths > 0)
            sb.append(String.format(" | Warranty: %dmo", warrantyMonths));
        if (conditionScore > 0)
            sb.append(String.format(" | Condition: %.1f/5", conditionScore));
        return sb.toString();
    }
}
