package AutoNego;

import AutoNego.GUI.*;
import AutoNego.strategy.*;

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
    private final Map<String, NegotiationContext>  autoCtx      = new HashMap<>();
    private final Map<String, NegotiationStrategy> autoStrategy = new HashMap<>();
    private final List<DealerBuyerScreen.BuyerInterest> interests = new ArrayList<>();
    private final Map<String, List<ACLMessage>> pendingUpdates = new HashMap<>();
    private final Map<String, String> sessionToBuyer = new HashMap<>();
    private final Map<String, String> sessionToListingId = new HashMap<>();

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
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 7);
        String sessionId = parts[0];
        String buyerName = parts[1];
        String brand = parts[2];
        String type = parts[3];
        double askingPrice = Double.parseDouble(parts[4]);
        String listingId = parts[5];
        double minAcceptPrice = Double.parseDouble(parts[6]);

        sessionToBuyer.put(sessionId, buyerName);
        sessionToListingId.put(sessionId, listingId);

        boolean autoNegotiate = autoModeByListingId.getOrDefault(listingId, false);
        autoModeByListingId.remove(listingId);

        if (autoNegotiate) {
            NegotiationStrategy strategy = new LinearStrategy();
            // Dealer: initialOffer = askingPrice (high), reservePrice = minAcceptPrice (low)
            NegotiationContext ctx = new NegotiationContext(askingPrice, minAcceptPrice, 10, 0);
            autoStrategy.put(sessionId, strategy);
            autoCtx.put(sessionId, ctx);
            System.out.printf("[AUTO-DEALER] Strategy: %s | Ask: %.2f | Min: %.2f%n",
                    strategy.getName(), askingPrice, minAcceptPrice);
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

        NegotiationContext ctx      = autoCtx.get(sessionId);
        NegotiationStrategy strategy = autoStrategy.get(sessionId);
        if (ctx != null && strategy != null) {
            applyAutoNegotiationUpdate(sessionId, ctx, strategy, action, amount);
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

    private void applyNegotiationUpdate(DealerNegotiationGui gui, ACLMessage message) {
        String[] parts = DemoMessageCodec.decodeFields(message.getContent(), 2);
        String action  = parts[1];
        double amount = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;

        if ("COUNTER".equals(action)) {
            gui.addBuyerOffer(amount, "Buyer counter-offer");
        } else if ("ACCEPT".equals(action)) {
            gui.addSystemMessage("Buyer accepted your offer of RM " + String.format("%,.2f", amount));
            gui.lockNegotiation(true);
            reportDealToBroker(parts[0], amount); // parts[0] is sessionId
        } else if ("REJECT".equals(action) || "CANCEL".equals(action)) {
            gui.addSystemMessage("Buyer ended the negotiation.");
            gui.lockNegotiation(false);
        }
    }

    private void applyAutoNegotiationUpdate(String sessionId, NegotiationContext ctx,
                                             NegotiationStrategy strategy,
                                             String action, double buyerOffer) {
        if ("COUNTER".equals(action)) {
            // Compute the dealer's scheduled price at the current round
            double schedulePrice = strategy.nextOffer(ctx);

            if (buyerOffer >= schedulePrice) {
                // Buyer is already offering at or above our scheduled ask — accept!
                System.out.printf("[AUTO-DEALER] Buyer offer %.2f >= schedule %.2f. ACCEPTING.%n",
                        buyerOffer, schedulePrice);
                sendNegotiationAction(sessionId, "ACCEPT", buyerOffer, ACLMessage.ACCEPT_PROPOSAL);
                autoCtx.remove(sessionId); autoStrategy.remove(sessionId);
                return;
            }

            if (ctx.isExhausted()) {
                System.out.println("[AUTO-DEALER] Max rounds reached. REJECTING.");
                sendNegotiationAction(sessionId, "REJECT", 0, ACLMessage.REJECT_PROPOSAL);
                autoCtx.remove(sessionId); autoStrategy.remove(sessionId);
                return;
            }

            // Counter with the scheduled price and advance
            System.out.printf("[AUTO-DEALER] Round %d/%d — countering with: RM %.2f%n",
                    ctx.roundsElapsed, ctx.maxRounds, schedulePrice);
            autoCtx.put(sessionId, ctx.nextRound());
            sendNegotiationAction(sessionId, "COUNTER", schedulePrice, ACLMessage.PROPOSE);
            return;
        }

        if ("ACCEPT".equals(action) || "REJECT".equals(action) || "CANCEL".equals(action)) {
            autoCtx.remove(sessionId); autoStrategy.remove(sessionId);
            if ("ACCEPT".equals(action)) reportDealToBroker(sessionId, buyerOffer);
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
