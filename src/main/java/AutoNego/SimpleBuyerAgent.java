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

public class SimpleBuyerAgent extends Agent {
    private final AID broker = new AID("broker", AID.ISLOCALNAME);
    private final Map<String, String> listingIdsByKey = new HashMap<>();
    private final Map<String, BuyerNegotiationGui> negotiationWindows = new HashMap<>();
    private final Map<String, NegotiationContext>  autoCtx      = new HashMap<>();
    private final Map<String, NegotiationStrategy> autoStrategy = new HashMap<>();
    private final Map<String, Boolean> autoModeByListingId = new HashMap<>();
    private final Map<String, String> sessionToDealer = new HashMap<>();
    
    private double myFirstOffer = 0; // Kept private for auto-negotiation logic
    private double myReservePrice = 0; // Kept private for auto-negotiation logic

    private BuyerInputGui inputGui;
    private BuyerMatchedCarsGui matchedCarsGui;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            inputGui = new BuyerInputGui(this);
            inputGui.setOnConfirmListener((brand, type, maxPrice, reservePrice) -> {
                myFirstOffer      = maxPrice;
                myReservePrice    = reservePrice;

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(broker);
                request.setConversationId("buyer-search");
                request.setContent(DemoMessageCodec.encodeFields(
                        brand,
                        type,
                        Double.toString(maxPrice),
                        Double.toString(reservePrice)
                ));
                send(request);
            });
            inputGui.display();
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
            } else if ("negotiation-action".equals(conversationId)) {
                handleNegotiationAction(message);
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
                public void onNegotiate(BuyerMatchedCarsGui.CarListing listing, boolean autoNegotiate) {
                    String listingId = listingIdsByKey.get(keyOf(listing));
                    if (listingId == null) {
                        return;
                    }
                    autoModeByListingId.put(listingId, autoNegotiate);

                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(broker);
                    request.setConversationId("negotiation-request");
                    request.setContent(DemoMessageCodec.encodeFields(
                            listingId,
                            Double.toString(myFirstOffer),
                            Double.toString(myReservePrice)
                    ));
                    send(request);
                }

                @Override
                public void onCancel(BuyerMatchedCarsGui.CarListing listing) {
                }
            });
            matchedCarsGui.display();
        });
    }

    // NegotiationState removed — tactic logic now lives in AutoNego.strategy.*

    private void handleNegotiationStart(ACLMessage message) {
        String[] parts     = DemoMessageCodec.decodeFields(message.getContent(), 6);
        String sessionId   = parts[0];
        String listingId   = parts[1];
        String brand       = parts[2];
        String type        = parts[3];
        double askingPrice = Double.parseDouble(parts[4]);
        String dealerName  = parts[5];
        
        sessionToDealer.put(sessionId, dealerName);
        
        boolean autoNegotiate = autoModeByListingId.getOrDefault(listingId, false);
        autoModeByListingId.remove(listingId);

        BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(
                brand, type, askingPrice, dealerName
        );

        if (autoNegotiate) {
            NegotiationStrategy strategy = new LinearStrategy();
            NegotiationContext ctx = new NegotiationContext(myFirstOffer, myReservePrice, 10, 0);
            autoStrategy.put(sessionId, strategy);
            autoCtx.put(sessionId, ctx);

            System.out.println("\n[AUTO] ========================================");
            System.out.println("[AUTO] Strategy: " + strategy.getName());
            System.out.println("[AUTO] Negotiation started — session: " + sessionId);
            System.out.printf("[AUTO] %s %s | Dealer asking: RM %.2f | Dealer: %s%n",
                    brand, type, askingPrice, dealerName);
            System.out.printf("[AUTO] My first offer: RM %.2f | Reserve: RM %.2f%n",
                    myFirstOffer, myReservePrice);
            System.out.println("[AUTO] ========================================");

            double firstOffer = strategy.nextOffer(ctx);
            autoCtx.put(sessionId, ctx.nextRound());
            System.out.printf("[AUTO] Round 0 — Sending first offer: RM %.2f%n", firstOffer);
            sendNegotiationAction(sessionId, "COUNTER", firstOffer, ACLMessage.PROPOSE);

        } else {
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
                gui.display();
            });
        }
    }

    private void handleNegotiationAction(ACLMessage message) {
        String[] parts;
        try {
            parts = DemoMessageCodec.decodeFields(message.getContent(), 2);
        } catch (IllegalArgumentException e) {
            System.err.println("Buyer received malformed message: " + message.getContent());
            return;
        }

        String sessionId = parts[0];
        String action = parts[1];
        double amount = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;

        NegotiationContext ctx      = autoCtx.get(sessionId);
        NegotiationStrategy strategy = autoStrategy.get(sessionId);
        if (ctx != null && strategy != null) {
            if ("COUNTER".equals(action)) {
                System.out.printf("[AUTO] Dealer counter-offer: RM %.2f%n", amount);

                if (amount <= ctx.reservePrice) {
                    System.out.printf("[AUTO] Within reserve (RM %.2f). ACCEPTING.%n", ctx.reservePrice);
                    sendNegotiationAction(sessionId, "ACCEPT", amount, ACLMessage.ACCEPT_PROPOSAL);
                    autoCtx.remove(sessionId); autoStrategy.remove(sessionId);

                } else if (ctx.isExhausted()) {
                    System.out.println("[AUTO] Max rounds reached. CANCELLING.");
                    sendNegotiationAction(sessionId, "CANCEL", 0, ACLMessage.REJECT_PROPOSAL);
                    autoCtx.remove(sessionId); autoStrategy.remove(sessionId);

                } else {
                    double nextOffer = strategy.nextOffer(ctx);
                    autoCtx.put(sessionId, ctx.nextRound());
                    System.out.printf("[AUTO] Round %d/%d — countering: RM %.2f%n",
                            ctx.roundsElapsed, ctx.maxRounds, nextOffer);
                    sendNegotiationAction(sessionId, "COUNTER", nextOffer, ACLMessage.PROPOSE);
                }

            } else if ("ACCEPT".equals(action)) {
                System.out.println("[AUTO] DEAL ACCEPTED.");
                System.out.printf("[AUTO] Final price: RM %.2f%n", amount);
                System.out.println("[AUTO] ========================================");
                autoCtx.remove(sessionId); autoStrategy.remove(sessionId);

            } else if ("REJECT".equals(action) || "CANCEL".equals(action)) {
                System.out.println("[AUTO] Negotiation FAILED.");
                System.out.println("[AUTO] ========================================");
                autoCtx.remove(sessionId); autoStrategy.remove(sessionId);
            }
        } else {
            BuyerNegotiationGui gui = negotiationWindows.get(sessionId);
            if (gui == null) return;

            if ("COUNTER".equals(action)) {
                gui.addDealerOffer(amount, "Dealer counter-offer");
            } else if ("ACCEPT".equals(action)) {
                gui.addSystemMessage("Dealer accepted your offer of RM " + String.format("%,.2f", amount));
                gui.lockNegotiation(true);
            } else if ("REJECT".equals(action) || "CANCEL".equals(action)) {
                gui.addSystemMessage("Dealer ended the negotiation.");
                gui.lockNegotiation(false);
            }
        }
    }

    private void sendNegotiationAction(String sessionId, String action, double amount, int performative) {
        String dealerName = sessionToDealer.get(sessionId);
        if (dealerName == null) return;

        ACLMessage request = new ACLMessage(performative);
        request.addReceiver(new AID(dealerName, AID.ISLOCALNAME));
        request.setConversationId("negotiation-action");
        request.setContent(DemoMessageCodec.encodeFields(sessionId, action, Double.toString(amount)));
        send(request);
    }

    }

    private static final class NegotiationState {
        final String sessionId;
        final double firstOffer;
        final double reservePrice;
        final int maxRounds;
        int roundsElapsed;

        // e controls the curve:
        // 0.2 = Boulware, 1.0 = Linear, 3.0 = Conceder
        final double e;

        NegotiationState(String sessionId, double firstOffer, double reservePrice, int maxRounds, double e) {
            this.sessionId    = sessionId;
            this.firstOffer   = firstOffer;
            this.reservePrice = reservePrice;
            this.maxRounds    = maxRounds;
            this.roundsElapsed = 0;
            this.e            = e;
        }

        double computeNextOffer() {
            double t = (double) roundsElapsed / maxRounds;   // progress 0.0 → 1.0
            double ft = Math.pow(t, 1.0 / e);               // concession curve
            return firstOffer + (reservePrice - firstOffer) * ft;
        }
    }
}
