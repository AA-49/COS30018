package AutoNego.io;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

/**
 * Exports negotiation sessions to CSV for ML training (long + summary).
 */
public final class NegotiationSessionExport {

    public record OfferStep(int sequence, String actor, double amount, String note) {
    }

    public record SessionSnapshot(
            String sessionId,
            String buyer,
            String dealer,
            String brand,
            String type,
            double buyerStart,
            double buyerMax,
            double sellerAsk,
            double sellerMin,
            String buyerTactic,
            String dealerTactic,
            String result,
            double finalPrice,
            int rounds,
            boolean dealInsideAgreement,
            List<OfferStep> offers) {
    }

    private NegotiationSessionExport() {
    }

    public static void writeLongCsv(Appendable out, List<SessionSnapshot> sessions) throws IOException {
        out.append("# Long-format negotiation steps: next_dealer_amount is the following offer amount only when next actor is the session dealer.\n");
        String header = String.join(",",
                "session_id", "step", "actor", "amount", "note",
                "buyer_tactic", "dealer_tactic", "brand", "type",
                "buyer_start", "buyer_max", "seller_ask", "seller_min",
                "amount_norm_zopa", "next_actor", "next_amount", "next_note", "next_dealer_amount",
                "result", "final_price", "rounds", "deal_inside_agreement");
        out.append(header).append('\n');
        for (SessionSnapshot s : sessions) {
            double zopaLow = Math.max(Math.min(s.buyerStart, s.buyerMax), Math.min(s.sellerMin, s.sellerAsk));
            double zopaHigh = Math.min(Math.max(s.buyerStart, s.buyerMax), Math.max(s.sellerMin, s.sellerAsk));
            double zopaWidth = zopaHigh - zopaLow;
            List<OfferStep> offers = s.offers;
            for (int i = 0; i < offers.size(); i++) {
                OfferStep cur = offers.get(i);
                OfferStep next = i + 1 < offers.size() ? offers.get(i + 1) : null;
                String norm = normZopa(cur.amount, zopaLow, zopaHigh, zopaWidth);
                String nextActor = next == null ? "" : escapeCsv(next.actor);
                String nextAmount = next == null ? "" : String.format(Locale.US, "%.4f", next.amount);
                String nextNote = next == null ? "" : escapeCsv(next.note);
                String nextDealerAmt = "";
                if (next != null && next.actor.equals(s.dealer)) {
                    nextDealerAmt = String.format(Locale.US, "%.4f", next.amount);
                }
                String line = String.join(",",
                        escapeCsv(s.sessionId),
                        Integer.toString(cur.sequence),
                        escapeCsv(cur.actor),
                        String.format(Locale.US, "%.4f", cur.amount),
                        escapeCsv(cur.note),
                        escapeCsv(s.buyerTactic),
                        escapeCsv(s.dealerTactic),
                        escapeCsv(s.brand),
                        escapeCsv(s.type),
                        String.format(Locale.US, "%.4f", s.buyerStart),
                        String.format(Locale.US, "%.4f", s.buyerMax),
                        String.format(Locale.US, "%.4f", s.sellerAsk),
                        String.format(Locale.US, "%.4f", s.sellerMin),
                        norm,
                        nextActor,
                        nextAmount,
                        nextNote,
                        nextDealerAmt,
                        escapeCsv(s.result),
                        String.format(Locale.US, "%.4f", s.finalPrice),
                        Integer.toString(s.rounds),
                        s.dealInsideAgreement ? "1" : "0");
                out.append(line).append('\n');
            }
        }
    }

    public static void writeSummaryCsv(Appendable out, List<SessionSnapshot> sessions) throws IOException {
        String header = String.join(",",
                "session_id", "buyer", "dealer", "brand", "type",
                "buyer_start", "buyer_max", "seller_ask", "seller_min",
                "buyer_tactic", "dealer_tactic", "result", "final_price", "rounds",
                "deal_inside_agreement", "num_offers");
        out.append(header).append('\n');
        for (SessionSnapshot s : sessions) {
            String line = String.join(",",
                    escapeCsv(s.sessionId),
                    escapeCsv(s.buyer),
                    escapeCsv(s.dealer),
                    escapeCsv(s.brand),
                    escapeCsv(s.type),
                    String.format(Locale.US, "%.4f", s.buyerStart),
                    String.format(Locale.US, "%.4f", s.buyerMax),
                    String.format(Locale.US, "%.4f", s.sellerAsk),
                    String.format(Locale.US, "%.4f", s.sellerMin),
                    escapeCsv(s.buyerTactic),
                    escapeCsv(s.dealerTactic),
                    escapeCsv(s.result),
                    String.format(Locale.US, "%.4f", s.finalPrice),
                    Integer.toString(s.rounds),
                    s.dealInsideAgreement ? "1" : "0",
                    Integer.toString(s.offers.size()));
            out.append(line).append('\n');
        }
    }

    private static String normZopa(double amount, double zopaLow, double zopaHigh, double zopaWidth) {
        if (zopaWidth <= 1e-9) {
            return "0.5";
        }
        double v = (amount - zopaLow) / zopaWidth;
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return String.format(Locale.US, "%.6f", v);
    }

    private static String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String t = s.replace("\"", "\"\"");
        if (needQuote) {
            return "\"" + t + "\"";
        }
        return t;
    }
}
