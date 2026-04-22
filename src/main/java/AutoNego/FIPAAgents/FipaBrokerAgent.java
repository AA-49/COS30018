package AutoNego.FIPAAgents;

import AutoNego.GUI.BrokerDashboardGui;
import AutoNego.DemoMessageCodec;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FipaBrokerAgent extends Agent {
    private final AtomicInteger listingSequence = new AtomicInteger(1);
    private final AtomicInteger sessionSequence = new AtomicInteger(1);
    private final Map<String, ListingRecord> listings = new LinkedHashMap<>();
    private double totalCommissions = 0.0;

    private BrokerDashboardGui dashboard;

    @Override
    protected void setup() {
        // create UI
        dashboard = new BrokerDashboardGui(this);
        dashboard.display();

        // create behavior
        addBehaviour(new BrokerMessageRouter());
        System.out.println("FIPA Broker Agent " + getLocalName() + " is ready.");
    }

    // get rid of UI when agent is killed
    @Override
    protected void takeDown() {
        if (dashboard != null) {
            dashboard.dispose();
        }
    }

    // behavior of broker agent
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

    // what to do when dealer send listing
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
                    listing.price));
        }
    }

    // what to do when buyer search for car
    private void handleBuyerSearch(ACLMessage message) {
        String[] request = DemoMessageCodec.decodeFields(message.getContent(), 3);
        String brand = request[0];
        String type = request[1];
        double maxPrice = Double.parseDouble(request[2]);

        List<String> matches = new ArrayList<>();
        for (ListingRecord listing : listings.values()) {
            if (listing.brand.equalsIgnoreCase(brand)
                    && listing.type.equalsIgnoreCase(type)
                    && listing.price <= maxPrice) {
                matches.add(DemoMessageCodec.encodeFields(
                        listing.id,
                        listing.brand,
                        listing.type,
                        Double.toString(listing.price),
                        listing.dealerName));
            }
        }

        ACLMessage reply = message.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("buyer-search-result");
        reply.setContent(DemoMessageCodec.encodeRecords(matches.toArray(String[]::new)));
        send(reply);
    }

    // what to do when buyer want to negotiate
    private void handleNegotiationRequest(ACLMessage message) {
        ListingRecord listing = listings.get(message.getContent());
        if (listing == null)
            return;

        ACLMessage notifyDealer = new ACLMessage(ACLMessage.INFORM);
        notifyDealer.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
        notifyDealer.setConversationId("buyer-interest");
        notifyDealer.setContent(DemoMessageCodec.encodeFields(
                listing.id,
                message.getSender().getLocalName(),
                listing.brand,
                listing.type,
                Double.toString(listing.price)));
        send(notifyDealer);
    }

    // what to do when dealer respond to negotiation request
    private void handleDealerInterestResponse(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 2);
        ListingRecord listing = listings.get(parts[0]);
        if (listing == null)
            return;

        String buyerName = parts[1];
        if (message.getPerformative() == ACLMessage.AGREE) {
            String sessionId = "session-" + sessionSequence.getAndIncrement();

            // Notify both to start negotiation
            // In this FIPA version, Dealer will be the Initiator (CFP)
            ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
            toBuyer.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
            toBuyer.setConversationId("negotiation-start");
            toBuyer.setContent(DemoMessageCodec.encodeFields(
                    sessionId, listing.id, listing.brand, listing.type, Double.toString(listing.price),
                    listing.dealerName));
            send(toBuyer);

            ACLMessage toDealer = new ACLMessage(ACLMessage.INFORM);
            toDealer.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
            toDealer.setConversationId("negotiation-start");
            toDealer.setContent(DemoMessageCodec.encodeFields(
                    sessionId, buyerName, listing.brand, listing.type,
                    Double.toString(listing.price), listing.id,
                    Double.toString(listing.minAcceptPrice)));
            send(toDealer);
            System.out.println("Broker: Started negotiation session " + sessionId + " between " + buyerName + " and "
                    + listing.dealerName);
        } else {
            ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
            toBuyer.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
            toBuyer.setConversationId("negotiation-update");
            toBuyer.setContent(DemoMessageCodec.encodeFields("none", "FAILED", "0", "Dealer declined the request."));
            send(toBuyer);
            System.out.println("Broker: Dealer declined negotiation for " + buyerName);
        }
    }

    // what to do when deal is completed
    private void handleDealCompleted(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 4);
        String listingId = parts[0];
        double finalPrice = Double.parseDouble(parts[1]);
        double commission = Double.parseDouble(parts[2]);
        String buyerName = parts[3];

        ListingRecord listing = listings.get(listingId);
        if (listing != null) {
            String carInfo = listing.brand + " " + listing.type;
            javax.swing.SwingUtilities.invokeLater(() -> {
                dashboard.addCompletedDeal(buyerName, listing.dealerName, carInfo, finalPrice, commission);
            });
        }

        totalCommissions += commission;
        listings.remove(listingId);
        javax.swing.SwingUtilities.invokeLater(() -> {
            dashboard.removeListing(listingId);
            dashboard.updateCommission(totalCommissions);
        });
        System.out.println("Broker: Deal completed. Total commissions: " + totalCommissions);
    }

    private record ListingRecord(String id, String dealerName, String brand, String type, double price,
            double minAcceptPrice) {
    }
}
