package AutoNego.strategy;

// Information about the car being negotiated
public record Offer(double price, int warrantyMonths, int insuranceIncludedMonths, int servicePackageLevel) {

    // Helper: Create an offer when we only care about price
    public static Offer priceOnly(double price) {
        return new Offer(price, 0, 0, 0);
    }

    // Change the price
    public Offer withPrice(double newPrice) {
        return new Offer(newPrice, warrantyMonths, insuranceIncludedMonths, servicePackageLevel);
    }

    // Change the warranty
    public Offer withWarrantyMonths(int months) {
        return new Offer(price, months, insuranceIncludedMonths, servicePackageLevel);
    }

    // Change insurance coverage months
    public Offer withInsuranceIncludedMonths(int months) {
        return new Offer(price, warrantyMonths, months, servicePackageLevel);
    }

    // Change service package level
    public Offer withServicePackageLevel(int level) {
        return new Offer(price, warrantyMonths, insuranceIncludedMonths, level);
    }

    // Helper to print out the offer details nicely
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("RM %,.2f", price));
        if (warrantyMonths > 0)
            sb.append(String.format(" | Warranty: %dmo", warrantyMonths));
        if (insuranceIncludedMonths > 0)
            sb.append(String.format(" | Insurance: %dmo", insuranceIncludedMonths));
        if (servicePackageLevel > 0)
            sb.append(String.format(" | Service Pkg: L%d", servicePackageLevel));
        return sb.toString();
    }
}
