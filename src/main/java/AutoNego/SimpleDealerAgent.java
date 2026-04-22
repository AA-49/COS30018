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
    private final Map<String, Boolean> autoModeByListingId = new HashMap<>();
    private final Map<String, DealerNegotiationGui> negotiationWindows = new HashMap<>();
    private final Map<String, AutoNegotiationState> autoNegotiationStates = new HashMap<>();
    private final List<DealerBuyerScreen.BuyerInterest> interests = new ArrayList<>();
    private final Map<String, List<ACLMessage>> pendingUpdates = new HashMap<>();
    private DealerInputGui inputGui;
    private DealerBuyerScreen buyerScreen;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            inputGui = new DealerInputGui(this);
            inputGui.setOnListingListener(this::submitListingsToBroker);
            inputGui.display();
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
                    Double.toString(listing.price),
                    Double.toString(listing.minAcceptPrice)
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
            } else if ("negotiation-update".equals(conversationId)) {
                handleNegotiationUpdate(message);
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
                    public void onNegotiate(DealerBuyerScreen.BuyerInterest selected, boolean autoNegotiate) {
                        String listingId = interestListingIds.get(interestKey(selected));
                        if (listingId != null) {
                            autoModeByListingId.put(listingId, autoNegotiate);
                        }
                        sendInterestDecision(selected, ACLMessage.AGREE);
                    }

                    @Override
                    public void onDecline(DealerBuyerScreen.BuyerInterest selected) {
                        sendInterestDecision(selected, ACLMessage.REJECT_PROPOSAL);
                    }
                });
                buyerScreen.display();
            } else {
                buyerScreen.addInterest(interest);
            }
        });
    }

    private void handleNegotiationStart(ACLMessage message) {
        String[] parts    = DemoMessageCodec.decodeFields(message.getContent(), 7);
        String sessionId  = parts[0];
        String listingId  = parts[1];
        String buyerName  = parts[2];
        String brand      = parts[3];
        String type       = parts[4];
        double askingPrice = Double.parseDouble(parts[5]);
        double minAcceptPrice = Double.parseDouble(parts[6]);
        boolean autoNegotiate = autoModeByListingId.getOrDefault(listingId, false);
        autoModeByListingId.remove(listingId);

        if (autoNegotiate) {
            autoNegotiationStates.put(sessionId, AutoNegotiationState.fromAskingPrice(askingPrice, minAcceptPrice));
            return;
        }

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

            // Register the GUI first
            negotiationWindows.put(sessionId, gui);
            gui.display();

            // Flush any updates that arrived before the GUI was ready
            List<ACLMessage> queued = pendingUpdates.remove(sessionId);
            if (queued != null) {
                for (ACLMessage queued_message : queued) {
                    applyNegotiationUpdate(gui, queued_message);
                }
            }
        });
    }

    private void handleNegotiationUpdate(ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 4);
        String sessionId = parts[0];
        String event = parts[1];
        double amount = Double.parseDouble(parts[2]);

        AutoNegotiationState autoState = autoNegotiationStates.get(sessionId);
        if (autoState != null) {
            applyAutoNegotiationUpdate(sessionId, autoState, event, amount);
            return;
        }

        DealerNegotiationGui gui = negotiationWindows.get(sessionId);

        if (gui == null) {
            // GUI not ready yet — queue this update for when it opens
            pendingUpdates.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
            return;
        }

        // GUI is ready — apply immediately via EDT
        SwingUtilities.invokeLater(() -> applyNegotiationUpdate(gui, message));
    }

    // Extracted so both the flush path and the live path use the same logic
    private void applyNegotiationUpdate(DealerNegotiationGui gui, ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 4);
        String event  = parts[1];
        double amount = Double.parseDouble(parts[2]);
        String note   = parts[3];

        if ("BUYER_COUNTER".equals(event)) {
            gui.addBuyerOffer(amount, "Buyer counter-offer");
        } else if ("ACCEPTED".equals(event)) {
            gui.addSystemMessage(note);
            gui.lockNegotiation(true);
        } else if ("FAILED".equals(event)) {
            gui.addSystemMessage(note);
            gui.lockNegotiation(false);
        }
    }

    private void applyAutoNegotiationUpdate(String sessionId, AutoNegotiationState state, String event, double buyerOffer) {
        if ("BUYER_COUNTER".equals(event)) {
            if (buyerOffer >= state.currentAsk) {
                sendNegotiationAction(sessionId, "ACCEPT", buyerOffer, ACLMessage.ACCEPT_PROPOSAL);
                autoNegotiationStates.remove(sessionId);
                return;
            }

            if (state.roundsElapsed >= state.maxRounds) {
                sendNegotiationAction(sessionId, "REJECT", 0, ACLMessage.REJECT_PROPOSAL);
                autoNegotiationStates.remove(sessionId);
                return;
            }

            double nextAsk = Math.max(state.minAsk, state.currentAsk - state.step);
            state.roundsElapsed++;

            if (buyerOffer >= nextAsk) {
                sendNegotiationAction(sessionId, "ACCEPT", buyerOffer, ACLMessage.ACCEPT_PROPOSAL);
                autoNegotiationStates.remove(sessionId);
                return;
            }

            if (nextAsk <= state.minAsk) {
                sendNegotiationAction(sessionId, "REJECT", 0, ACLMessage.REJECT_PROPOSAL);
                autoNegotiationStates.remove(sessionId);
                return;
            }

            state.currentAsk = nextAsk;
            sendNegotiationAction(sessionId, "COUNTER", nextAsk, ACLMessage.PROPOSE);
            return;
        }

        if ("ACCEPTED".equals(event) || "FAILED".equals(event)) {
            autoNegotiationStates.remove(sessionId);
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
        ACLMessage message = new ACLMessage(performative);
        message.addReceiver(broker);
        message.setConversationId("negotiation-action");
        message.setContent(DemoMessageCodec.encodeFields(sessionId, action, Double.toString(amount)));
        send(message);
    }

    private String interestKey(DealerBuyerScreen.BuyerInterest interest) {
        return DemoMessageCodec.encodeFields(
                interest.buyerName,
                interest.carBrand,
                interest.carType,
                Double.toString(interest.buyerInitialOffer)
        );
    }

    private static final class AutoNegotiationState {
        private final double minAsk;
        private final double step;
        private final int maxRounds;
        private int roundsElapsed;
        private double currentAsk;

        private AutoNegotiationState(double minAsk, double step, int maxRounds, double currentAsk) {
            this.minAsk = minAsk;
            this.step = step;
            this.maxRounds = maxRounds;
            this.roundsElapsed = 0;
            this.currentAsk = currentAsk;
        }

        private static AutoNegotiationState fromAskingPrice(double askingPrice, double minAcceptPrice) {
            double minAsk = minAcceptPrice;
            int maxRounds = 10;
            double step = (askingPrice - minAsk) / maxRounds;
            return new AutoNegotiationState(minAsk, step, maxRounds, askingPrice);
        }
    }
}
