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
    private final Map<String, NegotiationState> negotiationStates = new HashMap<>();
    private boolean autoNegotiateMode = false; // Tracks if auto-negotiation is enabled
    private double myFirstOffer = 0; // Kept private for auto-negotiation logic

    private double myReservePrice = 0; // Kept private for auto-negotiation logic

    private BuyerInputGui inputGui;
    private BuyerMatchedCarsGui matchedCarsGui;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(() -> {
            inputGui = new BuyerInputGui(this);
            inputGui.setOnConfirmListener((brand, type, maxPrice, reservePrice, autoNegotiate) -> {
                // Store mode and prices on the agent — they survive past the lambda
                autoNegotiateMode = autoNegotiate;
                myFirstOffer      = maxPrice;
                myReservePrice    = reservePrice;

                if (autoNegotiate) {
                    System.out.println("\n[AUTO] " + getLocalName() + " starting auto-negotiation.");
                    System.out.println("[AUTO] First offer: RM " + String.format("%.2f", maxPrice));
                    System.out.println("[AUTO] Reserve price: RM " + String.format("%.2f", reservePrice) + " (private)");
                    System.out.println("[AUTO] Searching for: " + brand + " " + type + " under RM " + maxPrice);
                }

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(broker);
                request.setConversationId("buyer-search");
                // Send first offer as the search price so broker knows what we want to pay
                double searchPrice = autoNegotiate ? maxPrice : maxPrice;
                request.setContent(DemoMessageCodec.encodeFields(brand, type, Double.toString(searchPrice)));
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

    private void handleNegotiationStart(ACLMessage message) {
        String[] parts     = DemoMessageCodec.decodeFields(message.getContent(), 6);
        String sessionId   = parts[0];
        // parts[1] is listing.id — not needed here, skip it
        String brand       = parts[2];
        String type        = parts[3];
        double askingPrice = Double.parseDouble(parts[4]);
        String dealerName  = parts[5];

        BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(
                brand, type, askingPrice, dealerName
        );
        // e = 0.2 → Boulware (holds firm, good for buyers)
        // e = 1.0 → Linear
        // e = 3.0 → Conceder (gives in fast)
        double e = 1.0;
        if (autoNegotiateMode) {
            // Create negotiation state using the buyer's own stored prices
            NegotiationState state = new NegotiationState(
                    sessionId, myFirstOffer, myReservePrice, 10,e);
            negotiationStates.put(sessionId, state);

            System.out.println("\n[AUTO] ========================================");
            System.out.println("[AUTO] Negotiation started — session: " + sessionId);
            System.out.printf("[AUTO] %s %s | Dealer asking: RM %.2f | Dealer: %s%n",
                    brand, type, askingPrice, dealerName);
            System.out.printf("[AUTO] My first offer: RM %.2f | Reserve: RM %.2f%n",
                    myFirstOffer, myReservePrice);
            System.out.println("[AUTO] ========================================");

            // Send first offer immediately — no GUI
            double offer = state.computeNextOffer();
            System.out.printf("[AUTO] Round 0 — Sending first offer: RM %.2f%n", offer);
            sendNegotiationAction(sessionId, "COUNTER", offer, ACLMessage.PROPOSE);

        } else {
            // Manual mode — show GUI as before
            NegotiationState state = new NegotiationState(
                    sessionId, myFirstOffer, myReservePrice, 10, e);
            negotiationStates.put(sessionId, state);

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
    }

    private void handleNegotiationUpdate(ACLMessage message) {
        String[] parts   = DemoMessageCodec.decodeFields(message.getContent(), 4);
        String sessionId = parts[0];
        String event     = parts[1];
        double amount    = Double.parseDouble(parts[2]);
        String note      = parts[3];

        if (autoNegotiateMode) {
            NegotiationState state = negotiationStates.get(sessionId);
            if (state == null) return;

            if ("DEALER_COUNTER".equals(event)) {
                System.out.printf("[AUTO] Dealer counter-offer: RM %.2f%n", amount);

                if (amount <= state.reservePrice) {
                    System.out.printf("[AUTO] Within reserve (RM %.2f). ACCEPTING.%n", state.reservePrice);
                    sendNegotiationAction(sessionId, "ACCEPT", amount, ACLMessage.ACCEPT_PROPOSAL);
                    negotiationStates.remove(sessionId);

                } else if (state.roundsElapsed >= state.maxRounds) {
                    System.out.println("[AUTO] Max rounds reached. CANCELLING.");
                    sendNegotiationAction(sessionId, "CANCEL", 0, ACLMessage.REJECT_PROPOSAL);
                    negotiationStates.remove(sessionId);

                } else {
                    state.roundsElapsed++;
                    double nextOffer = state.computeNextOffer();
                    System.out.printf("[AUTO] Round %d/%d — countering: RM %.2f%n",
                            state.roundsElapsed, state.maxRounds, nextOffer);
                    sendNegotiationAction(sessionId, "COUNTER", nextOffer, ACLMessage.PROPOSE);
                }

            } else if ("ACCEPTED".equals(event)) {
                System.out.println("[AUTO] DEAL ACCEPTED. " + note);
                System.out.printf("[AUTO] Final price: RM %.2f%n", amount);
                System.out.println("[AUTO] ========================================");
                negotiationStates.remove(sessionId);

            } else if ("FAILED".equals(event)) {
                System.out.println("[AUTO] Negotiation FAILED. " + note);
                System.out.println("[AUTO] ========================================");
                negotiationStates.remove(sessionId);
            }

        } else {
            BuyerNegotiationGui gui = negotiationWindows.get(sessionId);
            if (gui == null) return;

            if ("DEALER_COUNTER".equals(event)) {
                gui.addDealerOffer(amount, "Dealer counter-offer");
            } else if ("ACCEPTED".equals(event)) {
                gui.addSystemMessage(note);
                gui.lockNegotiation(true);
                negotiationWindows.remove(sessionId);
            } else if ("FAILED".equals(event)) {
                gui.addSystemMessage(note);
                gui.lockNegotiation(false);
                negotiationWindows.remove(sessionId);
            }
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
