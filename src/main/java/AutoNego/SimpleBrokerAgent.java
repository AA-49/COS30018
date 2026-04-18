package AutoNego;

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
    private final Map<String, SessionRecord> sessions = new LinkedHashMap<>();

    private BrokerDashboardGui dashboard;

    @Override
    protected void setup() {
        dashboard = new BrokerDashboardGui(this);
        dashboard.show();
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
            } else if ("negotiation-action".equals(conversationId)) {
                handleNegotiationAction(message);
            }
        }
    }

    private void handleDealerListings(ACLMessage message) {
        String dealerName = message.getSender().getLocalName();
        for (String record : DemoMessageCodec.decodeRecords(message.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 3);
            String listingId = "listing-" + listingSequence.getAndIncrement();
            double price = Double.parseDouble(parts[2]);

            ListingRecord listing = new ListingRecord(listingId, dealerName, parts[0], parts[1], price);
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
        String[] request = DemoMessageCodec.decodeFields(message.getContent(), 3);
        String brand = request[0];
        String type = request[1];

        List<String> matches = new ArrayList<>();
        for (ListingRecord listing : listings.values()) {
            if (listing.brand.equalsIgnoreCase(brand)
                    && listing.type.equalsIgnoreCase(type)
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
        ListingRecord listing = listings.get(message.getContent());
        if (listing == null) {
            return;
        }

        ACLMessage notifyDealer = new ACLMessage(ACLMessage.INFORM);
        notifyDealer.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
        notifyDealer.setConversationId("buyer-interest");
        notifyDealer.setContent(DemoMessageCodec.encodeFields(
                listing.id,
                message.getSender().getLocalName(),
                listing.brand,
                listing.type,
                Double.toString(listing.price)
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
            SessionRecord session = new SessionRecord(sessionId, listing, buyerName);
            sessions.put(sessionId, session);

            dashboard.addNegotiation(new BrokerDashboardGui.NegotiationSession(
                    sessionId,
                    buyerName,
                    listing.dealerName,
                    listing.brand,
                    listing.type,
                    listing.price
            ));

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
                    Double.toString(listing.price)
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

    private void handleNegotiationAction(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 2);
        SessionRecord session = sessions.get(parts[0]);
        if (session == null) {
            return;
        }

        String sender = message.getSender().getLocalName();
        String action = parts[1];
        double amount = parts.length > 2 ? Double.parseDouble(parts[2]) : session.latestOffer;
        boolean fromBuyer = sender.equals(session.buyerName);

        if ("COUNTER".equals(action)) {
            session.latestOffer = amount;
            dashboard.updateNegotiationStatus(session.id, BrokerDashboardGui.NegotiationStatus.IN_PROGRESS, amount);
            ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
            forward.addReceiver(new AID(fromBuyer ? session.dealerName : session.buyerName, AID.ISLOCALNAME));
            forward.setConversationId("negotiation-update");
            forward.setContent(DemoMessageCodec.encodeFields(
                    session.id,
                    fromBuyer ? "BUYER_COUNTER" : "DEALER_COUNTER",
                    Double.toString(amount),
                    ""
            ));
            send(forward);
            return;
        }

        if ("ACCEPT".equals(action)) {
            session.latestOffer = amount;
            dashboard.updateNegotiationStatus(session.id, BrokerDashboardGui.NegotiationStatus.DEAL_MADE, amount);
            listings.remove(session.listingId);
            dashboard.removeListing(session.listingId);
            notifyNegotiationEnd(session, "ACCEPTED", amount, "Deal confirmed.");
            return;
        }

        if ("REJECT".equals(action) || "CANCEL".equals(action)) {
            dashboard.updateNegotiationStatus(session.id, BrokerDashboardGui.NegotiationStatus.FAILED, session.latestOffer);
            notifyNegotiationEnd(
                    session,
                    "FAILED",
                    session.latestOffer,
                    fromBuyer ? "Buyer cancelled the negotiation." : "Dealer ended the negotiation."
            );
        }
    }

    private void notifyNegotiationEnd(SessionRecord session, String status, double amount, String note) {
        ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
        toBuyer.addReceiver(new AID(session.buyerName, AID.ISLOCALNAME));
        toBuyer.setConversationId("negotiation-update");
        toBuyer.setContent(DemoMessageCodec.encodeFields(session.id, status, Double.toString(amount), note));
        send(toBuyer);

        ACLMessage toDealer = new ACLMessage(ACLMessage.INFORM);
        toDealer.addReceiver(new AID(session.dealerName, AID.ISLOCALNAME));
        toDealer.setConversationId("negotiation-update");
        toDealer.setContent(DemoMessageCodec.encodeFields(session.id, status, Double.toString(amount), note));
        send(toDealer);
    }

    private record ListingRecord(String id, String dealerName, String brand, String type, double price) {
    }

    private static final class SessionRecord {
        private final String id;
        private final String listingId;
        private final String buyerName;
        private final String dealerName;
        private double latestOffer;

        private SessionRecord(String id, ListingRecord listing, String buyerName) {
            this.id = id;
            this.listingId = listing.id;
            this.buyerName = buyerName;
            this.dealerName = listing.dealerName;
            this.latestOffer = listing.price;
        }
    }
}
