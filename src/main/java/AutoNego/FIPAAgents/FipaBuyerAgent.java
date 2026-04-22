package AutoNego.FIPAAgents;

import AutoNego.GUI.BuyerInputGui;
import AutoNego.GUI.BuyerMatchedCarsGui;
import AutoNego.GUI.BuyerNegotiationGui;
import AutoNego.DemoMessageCodec;
import AutoNego.strategy.LinearStrategy;
import AutoNego.strategy.NegotiationContext;
import AutoNego.strategy.NegotiationStrategy;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SSIteratedContractNetResponder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FipaBuyerAgent extends Agent {
    private BuyerInputGui inputGui;
    private BuyerMatchedCarsGui resultsGui;

    // Pricing params captured from BuyerInputGui
    private double myFirstOffer = 0;
    private double myReservePrice = 0;

    private final Map<String, CompletableFuture<BuyerNegotiationGui>> negotiationWindows = new HashMap<>();

    // Auto-negotiate state
    private final Map<String, Boolean> autoModeByListingId = new HashMap<>();
    private final Set<String> autoSessions = new HashSet<>();
    private final Map<String, NegotiationContext> sessionToAutoCtx = new HashMap<>();
    private final Map<String, NegotiationStrategy> sessionToAutoStrategy = new HashMap<>();

    @Override
    protected void setup() {
        // search for cars gui and send message to broker after user press confirm
        inputGui = new BuyerInputGui(this);
        inputGui.setOnConfirmListener((brand, type, maxPrice, reservePrice) -> {
            // Store pricing for auto-negotiate use
            myFirstOffer = maxPrice;
            myReservePrice = reservePrice;

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
            msg.setConversationId("buyer-search");
            msg.setContent(DemoMessageCodec.encodeFields(brand, type, Double.toString(maxPrice)));
            send(msg);
        });
        inputGui.display();

        addBehaviour(new MessageRouter());
        addBehaviour(new CfpDispatcher());

        System.out.println("FIPA Buyer Agent " + getLocalName() + " is ready.");
    }

    @Override
    protected void takeDown() {
        if (inputGui != null)
            inputGui.dispose();
        if (resultsGui != null)
            resultsGui.dispose();
        for (CompletableFuture<BuyerNegotiationGui> fut : negotiationWindows.values()) {
            if (fut != null)
                fut.thenAccept(gui -> javax.swing.SwingUtilities.invokeLater(gui::dispose));
        }
    }

    private class MessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            jade.lang.acl.MessageTemplate mt = jade.lang.acl.MessageTemplate.or(
                    // template can either be for search result or negotiation related message
                    jade.lang.acl.MessageTemplate.MatchConversationId("buyer-search-result"),
                    jade.lang.acl.MessageTemplate.or(
                            jade.lang.acl.MessageTemplate.MatchConversationId("negotiation-start"),
                            jade.lang.acl.MessageTemplate.MatchConversationId("negotiation-update")));
            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }

            String convId = msg.getConversationId();
            if ("buyer-search-result".equals(convId))
                handleSearchResults(msg);
            else if ("negotiation-start".equals(convId))
                handleNegotiationStart(msg);
        }
    }

    // Watches for incoming CFPs on the Iterated Contract Net protocol and
    private class CfpDispatcher extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET),
                    MessageTemplate.MatchPerformative(ACLMessage.CFP));
            ACLMessage cfp = receive(mt);
            if (cfp == null) {
                block();
                return;
            }

            String sessionId = cfp.getConversationId();

            if (autoSessions.contains(sessionId)) {
                CompletableFuture<BuyerNegotiationGui> guiFuture = negotiationWindows.get(sessionId);
                addBehaviour(new AutoNegotiationSession(myAgent, cfp, sessionId, guiFuture));
            } else {
                CompletableFuture<BuyerNegotiationGui> guiFuture = negotiationWindows.get(sessionId);
                if (guiFuture != null) {
                    addBehaviour(new NegotiationSession(myAgent, cfp, guiFuture));
                }
            }
        }
    }

    private void handleSearchResults(ACLMessage msg) {
        // get the list of matched cars
        List<BuyerMatchedCarsGui.CarListing> matches = new ArrayList<>();
        for (String record : DemoMessageCodec.decodeRecords(msg.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 5);
            matches.add(new BuyerMatchedCarsGui.CarListing(
                    parts[1], parts[2], Double.parseDouble(parts[3]), parts[4]));
        }

        if (resultsGui == null) {
            resultsGui = new BuyerMatchedCarsGui(this, matches);
            resultsGui.setOnActionListener(new BuyerMatchedCarsGui.OnActionListener() {
                // what to do when buyer want to negotiate
                @Override
                public void onNegotiate(BuyerMatchedCarsGui.CarListing listing, boolean autoNegotiate) {
                    String listingId = getListingIdFromResults(msg, listing);
                    autoModeByListingId.put(listingId, autoNegotiate);

                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                    req.addReceiver(new AID("broker", AID.ISLOCALNAME));
                    req.setConversationId("negotiation-request");
                    req.setContent(listingId);
                    send(req);
                }

                @Override
                public void onCancel(BuyerMatchedCarsGui.CarListing listing) {
                }
            });
            resultsGui.display();
        } else {
            resultsGui.updateListings(matches);
        }
        inputGui.resetForm();
    }

    // get listing id for selected cars
    private String getListingIdFromResults(ACLMessage msg, BuyerMatchedCarsGui.CarListing listing) {
        for (String record : DemoMessageCodec.decodeRecords(msg.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 5);
            if (parts[4].equals(listing.dealerName) && parts[1].equals(listing.brand))
                return parts[0];
        }
        return "";
    }

    // what to do when broker reply with negotiation start
    private void handleNegotiationStart(ACLMessage msg) {
        // decode the negotiation start message
        String[] parts = DemoMessageCodec.decodeFields(msg.getContent(), 6);
        String sessionId = parts[0];
        String listingId = parts[1];
        String brand = parts[2];
        String type = parts[3];
        double initialPrice = Double.parseDouble(parts[4]);
        String dealerName = parts[5];

        boolean auto = autoModeByListingId.getOrDefault(listingId, false);
        autoModeByListingId.remove(listingId);

        if (auto) {
            // Auto mode: strategy-driven. GUI opens in read-only mode so the negotiation
            // is still visible, but all input buttons are disabled.
            NegotiationStrategy strategy = new LinearStrategy();
            // Buyer: initialOffer = first (low) offer, reserve = max willing to pay
            NegotiationContext ctx = new NegotiationContext(myFirstOffer, myReservePrice, 10, 0);
            autoSessions.add(sessionId);
            sessionToAutoCtx.put(sessionId, ctx);
            sessionToAutoStrategy.put(sessionId, strategy);

            CompletableFuture<BuyerNegotiationGui> guiFuture = new CompletableFuture<>();
            negotiationWindows.put(sessionId, guiFuture);
            final String strategyName = strategy.getName();
            javax.swing.SwingUtilities.invokeLater(() -> {
                BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(
                        brand, type, initialPrice, dealerName);
                BuyerNegotiationGui gui = new BuyerNegotiationGui(this, listing);
                gui.display();
                gui.lockNegotiation(true); // disable all input — display-only
                gui.addSystemMessage("Auto-negotiating with " + strategyName + " strategy...");
                guiFuture.complete(gui);
            });

            System.out.printf("[AUTO-FIPA-BUYER] Session %s | Strategy: %s | Start: %.2f | Reserve: %.2f%n",
                    sessionId, strategy.getName(), myFirstOffer, myReservePrice);

        } else {
            // ── Manual mode: GUI-driven ───────────────────────────────────
            CompletableFuture<BuyerNegotiationGui> guiFuture = new CompletableFuture<>();
            negotiationWindows.put(sessionId, guiFuture);

            javax.swing.SwingUtilities.invokeLater(() -> {
                BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(brand, type, initialPrice,
                        dealerName);
                BuyerNegotiationGui gui = new BuyerNegotiationGui(this, listing);
                gui.display();
                gui.addSystemMessage("Waiting for Dealer to start the protocol...");
                guiFuture.complete(gui);
                System.out.println("Buyer: Negotiation window opened for dealer " + dealerName);
            });
        }
    }

    // MANUALLL
    private class NegotiationSession extends SSIteratedContractNetResponder {
        private final CompletableFuture<BuyerNegotiationGui> guiFuture;

        NegotiationSession(Agent a, ACLMessage cfp, CompletableFuture<BuyerNegotiationGui> guiFuture) {
            super(a, cfp);
            this.guiFuture = guiFuture;
        }

        // handle cfp, basically offer from dealer
        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            try {
                // wait for the GUI window to be opened and assigned to this session
                BuyerNegotiationGui gui = guiFuture.get();

                String[] parts = DemoMessageCodec.decodeFields(cfp.getContent(), 3);
                double dealerPrice = Double.parseDouble(parts[2]);

                // Show the Dealer's price in our chat (this also unlocks the local Buyer input)
                gui.addDealerOffer(dealerPrice, "Dealer's Ask");

                // Wait for the human user to decide (Accept/Counter/Cancel)
                CompletableFuture<Double> nextOffer = new CompletableFuture<>();
                gui.setOnNegotiationListener(new BuyerNegotiationGui.OnNegotiationListener() {
                    @Override
                    public void onAccept(double currentOffer) {
                        // the buyer agrees to dealer price and send that price as propose message as
                        // part of IteratedContractNet
                        nextOffer.complete(currentOffer);
                    }

                    @Override
                    public void onCounterOffer(double amt) {
                        nextOffer.complete(amt);
                    }

                    @Override
                    public void onCancel() {
                        nextOffer.complete(-1.0);
                    }
                });

                // Blocks the JADE behavior thread until the user clicks a button
                double amount = nextOffer.get();
                if (amount < 0)
                    return cfp.createReply(ACLMessage.REFUSE);

                // Show our own proposal in the chat
                gui.addBuyerOffer(amount, "Your Proposal");

                // send a message for counter offer
                ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
                String sessionId = cfp.getConversationId();
                propose.setContent(DemoMessageCodec.encodeFields(sessionId, "PROPOSE", Double.toString(amount)));
                return propose;

            } catch (Exception e) {
                e.printStackTrace();
                return cfp.createReply(ACLMessage.NOT_UNDERSTOOD);
            }
        }

        // handle when dealer accept proposal
        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            guiFuture.thenAccept(gui -> {
                String[] parts = DemoMessageCodec.decodeFields(propose.getContent(), 3);
                double finalPrice = Double.parseDouble(parts[2]);
                gui.addSystemMessage("Dealer accepted your offer of RM " + String.format("%,.2f", finalPrice));
                gui.lockNegotiation(true);
            });
            ACLMessage inform = accept.createReply(ACLMessage.INFORM);
            inform.setContent("Deal finalized");
            return inform;
        }

        // handle when dealer reject proposal
        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            guiFuture.thenAccept(gui -> gui.addSystemMessage("Dealer rejected. Waiting for counter-offer..."));
        }
    }

    // AUTOOOOO
    private class AutoNegotiationSession extends SSIteratedContractNetResponder {
        private final String sessionId;
        private final CompletableFuture<BuyerNegotiationGui> guiFuture;

        AutoNegotiationSession(Agent a, ACLMessage cfp, String sessionId,
                CompletableFuture<BuyerNegotiationGui> guiFuture) {
            super(a, cfp);
            this.sessionId = sessionId;
            this.guiFuture = guiFuture;
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            String[] parts = DemoMessageCodec.decodeFields(cfp.getContent(), 3);
            double dealerPrice = Double.parseDouble(parts[2]);

            NegotiationContext ctx = sessionToAutoCtx.get(sessionId);
            NegotiationStrategy strategy = sessionToAutoStrategy.get(sessionId);

            if (ctx == null || strategy == null) {
                return cfp.createReply(ACLMessage.REFUSE);
            }

            // Show the dealer's ask in the GUI
            guiFuture.thenAccept(gui -> javax.swing.SwingUtilities
                    .invokeLater(() -> gui.addDealerOffer(dealerPrice, "Dealer's Ask (auto)")));

            // Dealer dropped to or below our reserve — accept at dealer's price
            if (dealerPrice <= ctx.reservePrice) {
                System.out.printf("[AUTO-FIPA-BUYER] Dealer %.2f <= reserve %.2f — ACCEPTING%n",
                        dealerPrice, ctx.reservePrice);
                guiFuture.thenAccept(gui -> javax.swing.SwingUtilities.invokeLater(() -> {
                    gui.addBuyerOffer(dealerPrice, "Auto Accepted");
                    gui.addSystemMessage("Auto accepted the dealer's price.");
                    gui.lockNegotiation(true);
                }));
                cleanupAutoSession();
                ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
                propose.setContent(DemoMessageCodec.encodeFields(sessionId, "PROPOSE", Double.toString(dealerPrice)));
                return propose;
            }

            if (ctx.isExhausted()) {
                System.out.println("[AUTO-FIPA-BUYER] Max rounds reached — REFUSING");
                guiFuture.thenAccept(gui -> javax.swing.SwingUtilities.invokeLater(() -> {
                    gui.addSystemMessage("Max rounds reached. Auto-negotiation ended.");
                    gui.lockNegotiation(true);
                }));
                cleanupAutoSession();
                return cfp.createReply(ACLMessage.REFUSE);
            }

            // Counter with the next scheduled offer
            double myOffer = strategy.nextOffer(ctx);
            System.out.printf("[AUTO-FIPA-BUYER] Round %d/%d — countering with RM %.2f%n",
                    ctx.roundsElapsed, ctx.maxRounds, myOffer);
            sessionToAutoCtx.put(sessionId, ctx.nextRound());

            // Show our counter-offer in the GUI
            final double offer = myOffer;
            guiFuture.thenAccept(gui -> javax.swing.SwingUtilities
                    .invokeLater(() -> gui.addBuyerOffer(offer, "Auto Counter-Offer")));

            ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
            propose.setContent(DemoMessageCodec.encodeFields(sessionId, "PROPOSE", Double.toString(myOffer)));
            return propose;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            String[] parts = DemoMessageCodec.decodeFields(propose.getContent(), 3);
            double finalPrice = Double.parseDouble(parts[2]);
            System.out.printf("[AUTO-FIPA-BUYER] Deal accepted! Final price: RM %.2f%n", finalPrice);
            guiFuture.thenAccept(gui -> javax.swing.SwingUtilities.invokeLater(() -> {
                gui.addSystemMessage("Deal accepted at RM " + String.format("%,.2f", finalPrice));
                gui.lockNegotiation(true);
            }));
            cleanupAutoSession();
            ACLMessage inform = accept.createReply(ACLMessage.INFORM);
            inform.setContent("Deal finalized");
            return inform;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            // Dealer will send a counter-CFP; handleCfp will be invoked again automatically
            System.out.println("[AUTO-FIPA-BUYER] Proposal rejected — waiting for dealer counter...");
            guiFuture.thenAccept(gui -> javax.swing.SwingUtilities
                    .invokeLater(() -> gui.addSystemMessage("Proposal rejected. Waiting for counter...")));
        }

        private void cleanupAutoSession() {
            autoSessions.remove(sessionId);
            sessionToAutoCtx.remove(sessionId);
            sessionToAutoStrategy.remove(sessionId);
        }
    }
}
