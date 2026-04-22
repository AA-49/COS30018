package AutoNego;

import AutoNego.GUI.*;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleBrokerAgent extends Agent {
    private final AtomicInteger listingSequence = new AtomicInteger(1);
    private final AtomicInteger sessionSequence = new AtomicInteger(1);
    private final Map<String, ListingRecord> listings = new LinkedHashMap<>();
    private double totalCommissions = 0.0;

    private BrokerDashboardGui dashboard;

    @Override
    protected void setup() {
        dashboard = new BrokerDashboardGui(this);
        dashboard.display();
        addBehaviour(new BrokerMessageRouter());
    }

    @Override
    protected void takeDown() {
        if (dashboard != null) {
            dashboard.dispose();
        }
    }

    private final class BrokerMessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage message = receive();
            if (message == null) {
                block();
                return;
            }

            String conversationId = message.getConversationId();
            if ("dealer-listings".equals(conversationId)) {
                handleDealerListings(message);
            } else if ("buyer-search".equals(conversationId)) {
                handleBuyerSearch(message);
            } else if ("negotiation-request".equals(conversationId)) {
                handleNegotiationRequest(message);
            } else if ("dealer-interest-response".equals(conversationId)) {
                handleDealerInterestResponse(message);
            } else if ("deal-completed".equals(conversationId)) {
                handleDealCompleted(message);
            }
        }
    }

    private void handleDealerListings(ACLMessage message) {
        String dealerName = message.getSender().getLocalName();
        for (String record : DemoMessageCodec.decodeRecords(message.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 4);
            String listingId = "listing-" + listingSequence.getAndIncrement();
            double price = Double.parseDouble(parts[2]);
            double minAcceptPrice = Double.parseDouble(parts[3]);

            ListingRecord listing = new ListingRecord(listingId, dealerName, parts[0], parts[1], price, minAcceptPrice);
            listings.put(listingId, listing);
            dashboard.addListing(new BrokerDashboardGui.DealerListing(
                    listingId,
                    dealerName,
                    listing.brand,
                    listing.type,
                    listing.price
            ));
        }
    }

    private void handleBuyerSearch(ACLMessage message) {
        String[] request = DemoMessageCodec.decodeFields(message.getContent(), 4);
        String brand = request[0];
        String type = request[1];
        double buyerFirstOffer = Double.parseDouble(request[2]);
        double buyerReservePrice = Double.parseDouble(request[3]);

        List<String> matches = new ArrayList<>();
        for (ListingRecord listing : listings.values()) {
            if (listing.brand.equalsIgnoreCase(brand)
                    && listing.type.equalsIgnoreCase(type)
                    && rangesOverlap(buyerFirstOffer, buyerReservePrice, listing.minAcceptPrice, listing.price)
            ) {
                matches.add(DemoMessageCodec.encodeFields(
                        listing.id,
                        listing.brand,
                        listing.type,
                        Double.toString(listing.price),
                        listing.dealerName
                ));
            }
        }

        ACLMessage reply = message.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("buyer-search-result");
        reply.setContent(DemoMessageCodec.encodeRecords(matches.toArray(String[]::new)));
        send(reply);
    }

    private void handleNegotiationRequest(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 1);
        ListingRecord listing = listings.get(parts[0]);
        if (listing == null) {
            return;
        }

        String buyerName = message.getSender().getLocalName();
        double buyerFirstOffer = listing.price;
        if (parts.length >= 3) {
            buyerFirstOffer = Double.parseDouble(parts[1]);
            double buyerReservePrice = Double.parseDouble(parts[2]);
            boolean hasAgreementRange = rangesOverlap(
                    buyerFirstOffer,
                    buyerReservePrice,
                    listing.minAcceptPrice,
                    listing.price
            );
            if (!hasAgreementRange) {
                ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
                toBuyer.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
                toBuyer.setConversationId("negotiation-update");
                toBuyer.setContent(DemoMessageCodec.encodeFields(
                        "none",
                        "FAILED",
                        "0",
                        "No agreement range between buyer and dealer."
                ));
                send(toBuyer);
                return;
            }
        }

        ACLMessage notifyDealer = new ACLMessage(ACLMessage.INFORM);
        notifyDealer.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
        notifyDealer.setConversationId("buyer-interest");
        notifyDealer.setContent(DemoMessageCodec.encodeFields(
                listing.id,
                buyerName,
                listing.brand,
                listing.type,
                Double.toString(buyerFirstOffer)
        ));
        send(notifyDealer);
    }

    private void handleDealerInterestResponse(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 2);
        ListingRecord listing = listings.get(parts[0]);
        if (listing == null) {
            return;
        }

        String buyerName = parts[1];
        if (message.getPerformative() == ACLMessage.AGREE) {
            String sessionId = "session-" + sessionSequence.getAndIncrement();

            ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
            toBuyer.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
            toBuyer.setConversationId("negotiation-start");
            toBuyer.setContent(DemoMessageCodec.encodeFields(
                    sessionId,
                    listing.id,
                    listing.brand,
                    listing.type,
                    Double.toString(listing.price),
                    listing.dealerName
            ));
            send(toBuyer);

            ACLMessage toDealer = new ACLMessage(ACLMessage.INFORM);
            toDealer.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
            toDealer.setConversationId("negotiation-start");
            toDealer.setContent(DemoMessageCodec.encodeFields(
                    sessionId,
                    buyerName,
                    listing.brand,
                    listing.type,
                    Double.toString(listing.price),
                    listing.id,
                    Double.toString(listing.minAcceptPrice)
            ));
            send(toDealer);
        } else {
            ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
            toBuyer.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
            toBuyer.setConversationId("negotiation-update");
            toBuyer.setContent(DemoMessageCodec.encodeFields("none", "FAILED", "0", "Dealer declined the request."));
            send(toBuyer);
        }
    }

    private void handleDealCompleted(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 4);
        String listingId = parts[0];
        double finalPrice = Double.parseDouble(parts[1]);
        double commission = Double.parseDouble(parts[2]);
        String buyerName = parts[3];

        ListingRecord listing = listings.get(listingId);
        if (listing != null) {
            String carInfo = listing.brand + " " + listing.type;
            dashboard.addCompletedDeal(buyerName, listing.dealerName, carInfo, finalPrice, commission);
        }

        totalCommissions += commission;
        listings.remove(listingId);
        dashboard.removeListing(listingId);
        dashboard.updateCommission(totalCommissions);
    }

    private boolean rangesOverlap(double buyerLow, double buyerHigh, double dealerLow, double dealerHigh) {
        double normalizedBuyerLow = Math.min(buyerLow, buyerHigh);
        double normalizedBuyerHigh = Math.max(buyerLow, buyerHigh);
        double normalizedDealerLow = Math.min(dealerLow, dealerHigh);
        double normalizedDealerHigh = Math.max(dealerLow, dealerHigh);
        return Math.max(normalizedBuyerLow, normalizedDealerLow) <= Math.min(normalizedBuyerHigh, normalizedDealerHigh);
    }

    private record ListingRecord(String id, String dealerName, String brand, String type, double price, double minAcceptPrice) {
    }
}
