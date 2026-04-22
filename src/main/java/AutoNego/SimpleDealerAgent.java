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

public class SimpleDealerAgent extends Agent {
    private final AID broker = new AID("broker", AID.ISLOCALNAME);
    private final Map<String, String> interestListingIds = new HashMap<>();
    private final Map<String, DealerNegotiationGui> negotiationWindows = new HashMap<>();
    private final List<DealerBuyerScreen.BuyerInterest> interests = new ArrayList<>();
    private final Map<String, String> sessionToBuyer = new HashMap<>();
    private final Map<String, String> sessionToListingId = new HashMap<>();

    private DealerInputGui inputGui;
    private DealerBuyerScreen buyerScreen;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            inputGui = new DealerInputGui(this);
            inputGui.setOnListingListener(this::submitListingsToBroker);
            inputGui.show();
        });
        addBehaviour(new DealerMessageRouter());
    }

    @Override
    protected void takeDown() {
        SwingUtilities.invokeLater(() -> {
            if (inputGui != null) {
                inputGui.dispose();
            }
            if (buyerScreen != null) {
                buyerScreen.dispose();
            }
            negotiationWindows.values().forEach(DealerNegotiationGui::dispose);
        });
    }

    private void submitListingsToBroker(List<DealerInputGui.CarListing> listings) {
        List<String> records = new ArrayList<>();
        for (DealerInputGui.CarListing listing : listings) {
            records.add(DemoMessageCodec.encodeFields(
                    listing.brand,
                    listing.type,
                    Double.toString(listing.price)
            ));
        }

        ACLMessage message = new ACLMessage(ACLMessage.INFORM);
        message.addReceiver(broker);
        message.setConversationId("dealer-listings");
        message.setContent(DemoMessageCodec.encodeRecords(records.toArray(String[]::new)));
        send(message);
    }

    private final class DealerMessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage message = receive();
            if (message == null) {
                block();
                return;
            }

            String conversationId = message.getConversationId();
            if ("buyer-interest".equals(conversationId)) {
                handleBuyerInterest(message);
            } else if ("negotiation-start".equals(conversationId)) {
                handleNegotiationStart(message);
            } else if ("negotiation-action".equals(conversationId)) {
                handleNegotiationAction(message);
            }
        }
    }

    private void handleBuyerInterest(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 5);
        String listingId = parts[0];
        DealerBuyerScreen.BuyerInterest interest = new DealerBuyerScreen.BuyerInterest(
                parts[1],
                parts[2],
                parts[3],
                Double.parseDouble(parts[4])
        );
        interestListingIds.put(interestKey(interest), listingId);

        SwingUtilities.invokeLater(() -> {
            if (buyerScreen == null) {
                interests.add(interest);
                buyerScreen = new DealerBuyerScreen(this, interests);
                buyerScreen.setOnActionListener(new DealerBuyerScreen.OnActionListener() {
                    @Override
                    public void onNegotiate(DealerBuyerScreen.BuyerInterest selected) {
                        sendInterestDecision(selected, ACLMessage.AGREE);
                    }

                    @Override
                    public void onDecline(DealerBuyerScreen.BuyerInterest selected) {
                        sendInterestDecision(selected, ACLMessage.REJECT_PROPOSAL);
                    }
                });
                buyerScreen.show();
            } else {
                buyerScreen.addInterest(interest);
            }
        });
    }

    private void handleNegotiationStart(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 6);
        String sessionId = parts[0];
        String buyerName = parts[1];
        String brand = parts[2];
        String type = parts[3];
        double askingPrice = Double.parseDouble(parts[4]);
        String listingId = parts[5];

        sessionToBuyer.put(sessionId, buyerName);
        sessionToListingId.put(sessionId, listingId);

        SwingUtilities.invokeLater(() -> {
            DealerNegotiationGui gui = new DealerNegotiationGui(this, buyerName, brand, type, askingPrice);
            gui.setOnNegotiationListener(new DealerNegotiationGui.OnNegotiationListener() {
                @Override
                public void onAccept(double currentOffer) {
                    sendNegotiationAction(sessionId, "ACCEPT", currentOffer, ACLMessage.ACCEPT_PROPOSAL);
                }

                @Override
                public void onCounterOffer(double counterAmount) {
                    sendNegotiationAction(sessionId, "COUNTER", counterAmount, ACLMessage.PROPOSE);
                }

                @Override
                public void onReject() {
                    sendNegotiationAction(sessionId, "REJECT", 0, ACLMessage.REJECT_PROPOSAL);
                }
            });
            negotiationWindows.put(sessionId, gui);
            gui.show();
        });
    }

    private void handleNegotiationAction(ACLMessage message) {
        String[] parts;
        try {
            parts = DemoMessageCodec.decodeFields(message.getContent(), 2);
        } catch (IllegalArgumentException e) {
            System.err.println("Dealer received malformed message: " + message.getContent());
            return;
        }
        
        String sessionId = parts[0];
        String action = parts[1];
        double amount = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;

        DealerNegotiationGui gui = negotiationWindows.get(sessionId);
        if (gui == null) return;

        if ("COUNTER".equals(action)) {
            gui.addBuyerOffer(amount, "Buyer counter-offer");
        } else if ("ACCEPT".equals(action)) {
            gui.addSystemMessage("Buyer accepted your offer of RM " + String.format("%,.2f", amount));
            gui.lockNegotiation(true);
            reportDealToBroker(sessionId, amount);
        } else if ("REJECT".equals(action) || "CANCEL".equals(action)) {
            gui.addSystemMessage("Buyer ended the negotiation.");
            gui.lockNegotiation(false);
        }
    }

    private void sendInterestDecision(DealerBuyerScreen.BuyerInterest interest, int performative) {
        String listingId = interestListingIds.get(interestKey(interest));
        if (listingId == null) {
            return;
        }

        ACLMessage message = new ACLMessage(performative);
        message.addReceiver(broker);
        message.setConversationId("dealer-interest-response");
        message.setContent(DemoMessageCodec.encodeFields(listingId, interest.buyerName));
        send(message);
    }

    private void sendNegotiationAction(String sessionId, String action, double amount, int performative) {
        String buyerName = sessionToBuyer.get(sessionId);
        if (buyerName == null) return;

        ACLMessage message = new ACLMessage(performative);
        message.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
        message.setConversationId("negotiation-action");
        message.setContent(DemoMessageCodec.encodeFields(sessionId, action, Double.toString(amount)));
        send(message);

        if ("ACCEPT".equals(action)) {
            reportDealToBroker(sessionId, amount);
        }
    }

    private void reportDealToBroker(String sessionId, double finalPrice) {
        String listingId = sessionToListingId.get(sessionId);
        String buyerName = sessionToBuyer.get(sessionId);
        if (listingId == null || buyerName == null) return;

        double commission = finalPrice * 0.05; // 5% commission
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(broker);
        msg.setConversationId("deal-completed");
        msg.setContent(DemoMessageCodec.encodeFields(
                listingId, 
                Double.toString(finalPrice), 
                Double.toString(commission),
                buyerName
        ));
        send(msg);
    }

    private String interestKey(DealerBuyerScreen.BuyerInterest interest) {
        return DemoMessageCodec.encodeFields(
                interest.buyerName,
                interest.carBrand,
                interest.carType,
                Double.toString(interest.buyerInitialOffer)
        );
    }
}
