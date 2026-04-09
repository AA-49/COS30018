package AutoNego;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleBuyerAgent extends Agent {
    private final AID broker = new AID("broker", AID.ISLOCALNAME);
    private final Map<String, String> listingIdsByKey = new HashMap<>();
    private final Map<String, BuyerNegotiationGui> negotiationWindows = new HashMap<>();

    private BuyerInputGui inputGui;
    private BuyerMatchedCarsGui matchedCarsGui;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            inputGui = new BuyerInputGui(this);
            inputGui.setOnConfirmListener((brand, type, maxPrice) -> {
                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(broker);
                request.setConversationId("buyer-search");
                request.setContent(DemoMessageCodec.encodeFields(brand, type, Double.toString(maxPrice)));
                send(request);
            });
            inputGui.show();
        });

        addBehaviour(new BuyerMessageRouter());
    }

    @Override
    protected void takeDown() {
        SwingUtilities.invokeLater(() -> {
            if (inputGui != null) {
                inputGui.dispose();
            }
            if (matchedCarsGui != null) {
                matchedCarsGui.dispose();
            }
            negotiationWindows.values().forEach(BuyerNegotiationGui::dispose);
        });
    }

    private final class BuyerMessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage message = receive();
            if (message == null) {
                block();
                return;
            }

            String conversationId = message.getConversationId();
            if ("buyer-search-result".equals(conversationId)) {
                handleSearchResult(message);
            } else if ("negotiation-start".equals(conversationId)) {
                handleNegotiationStart(message);
            } else if ("negotiation-update".equals(conversationId)) {
                handleNegotiationUpdate(message);
            }
        }
    }

    private void handleSearchResult(ACLMessage message) {
        List<BuyerMatchedCarsGui.CarListing> listings = new ArrayList<>();
        listingIdsByKey.clear();

        for (String record : DemoMessageCodec.decodeRecords(message.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 5);
            BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(
                    parts[1],
                    parts[2],
                    Double.parseDouble(parts[3]),
                    parts[4]
            );
            listings.add(listing);
            listingIdsByKey.put(keyOf(listing), parts[0]);
        }

        SwingUtilities.invokeLater(() -> {
            if (inputGui != null) {
                inputGui.resetForm();
            }

            if (matchedCarsGui != null) {
                matchedCarsGui.dispose();
            }

            matchedCarsGui = new BuyerMatchedCarsGui(this, listings);
            matchedCarsGui.setOnActionListener(new BuyerMatchedCarsGui.OnActionListener() {
                @Override
                public void onNegotiate(BuyerMatchedCarsGui.CarListing listing) {
                    String listingId = listingIdsByKey.get(keyOf(listing));
                    if (listingId == null) {
                        return;
                    }

                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(broker);
                    request.setConversationId("negotiation-request");
                    request.setContent(listingId);
                    send(request);
                }

                @Override
                public void onCancel(BuyerMatchedCarsGui.CarListing listing) {
                }
            });
            matchedCarsGui.show();
        });
    }

    private void handleNegotiationStart(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 6);
        String sessionId = parts[0];
        BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(
                parts[2],
                parts[3],
                Double.parseDouble(parts[4]),
                parts[5]
        );

        SwingUtilities.invokeLater(() -> {
            BuyerNegotiationGui gui = new BuyerNegotiationGui(this, listing);
            gui.setOnNegotiationListener(new BuyerNegotiationGui.OnNegotiationListener() {
                @Override
                public void onAccept(double currentOffer) {
                    sendNegotiationAction(sessionId, "ACCEPT", currentOffer, ACLMessage.ACCEPT_PROPOSAL);
                }

                @Override
                public void onCounterOffer(double counterAmount) {
                    sendNegotiationAction(sessionId, "COUNTER", counterAmount, ACLMessage.PROPOSE);
                }

                @Override
                public void onCancel() {
                    sendNegotiationAction(sessionId, "CANCEL", 0, ACLMessage.REJECT_PROPOSAL);
                }
            });
            negotiationWindows.put(sessionId, gui);
            gui.show();
        });
    }

    private void handleNegotiationUpdate(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 4);
        BuyerNegotiationGui gui = negotiationWindows.get(parts[0]);
        if (gui == null) {
            return;
        }

        String event = parts[1];
        double amount = Double.parseDouble(parts[2]);
        String note = parts[3];

        if ("DEALER_COUNTER".equals(event)) {
            gui.addDealerOffer(amount, "Dealer counter-offer");
        } else if ("ACCEPTED".equals(event)) {
            gui.addSystemMessage(note);
            gui.lockNegotiation(true);
        } else if ("FAILED".equals(event)) {
            gui.addSystemMessage(note);
            gui.lockNegotiation(false);
        }
    }

    private void sendNegotiationAction(String sessionId, String action, double amount, int performative) {
        ACLMessage request = new ACLMessage(performative);
        request.addReceiver(broker);
        request.setConversationId("negotiation-action");
        request.setContent(DemoMessageCodec.encodeFields(sessionId, action, Double.toString(amount)));
        send(request);
    }

    private String keyOf(BuyerMatchedCarsGui.CarListing listing) {
        return DemoMessageCodec.encodeFields(
                listing.brand,
                listing.type,
                Double.toString(listing.price),
                listing.dealerName
        );
    }
}
