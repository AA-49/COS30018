package AutoNego.FIPAAgents;

import AutoNego.GUI.BuyerInputGui;
import AutoNego.GUI.BuyerMatchedCarsGui;
import AutoNego.GUI.BuyerNegotiationGui;
import AutoNego.DemoMessageCodec;
import AutoNego.strategy.LinearStrategy;
import AutoNego.strategy.NegotiationContext;
import AutoNego.strategy.NegotiationStrategy;
import AutoNego.strategy.Offer;
import AutoNego.strategy.PreferenceProfile;
import AutoNego.strategy.WeightedSumUtility;
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
import java.util.IdentityHashMap;
import java.lang.reflect.InvocationTargetException;
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

    private final Map<BuyerMatchedCarsGui.CarListing, String> listingIdsByObject = new IdentityHashMap<>();
    private final Map<String, CompletableFuture<BuyerNegotiationGui>> negotiationWindows = new HashMap<>();
    private final Map<String, CompletableFuture<BuyerNegotiationGui>> autoNegotiationWindows = new HashMap<>();

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
            msg.setContent(DemoMessageCodec.encodeFields(brand, type, Double.toString(maxPrice), Double.toString(reservePrice)));
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
        for (CompletableFuture<BuyerNegotiationGui> fut : autoNegotiationWindows.values()) {
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
            else if ("negotiation-update".equals(convId))
                handleNegotiationUpdate(msg);
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
                addBehaviour(new AutoNegotiationSession(myAgent, cfp, sessionId));
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
        listingIdsByObject.clear();
        for (String record : DemoMessageCodec.decodeRecords(msg.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 5);
            BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(
                    parts[1], parts[2], Double.parseDouble(parts[3]), parts[4]);
            matches.add(listing);
            listingIdsByObject.put(listing, parts[0]);
        }

        if (resultsGui == null) {
            resultsGui = new BuyerMatchedCarsGui(this, matches);
            resultsGui.setOnActionListener(new BuyerMatchedCarsGui.OnActionListener() {
                // what to do when buyer want to negotiate
                @Override
                public void onNegotiate(BuyerMatchedCarsGui.CarListing listing, boolean autoNegotiate) {
                    String listingId = listingIdsByObject.get(listing);
                    if (listingId == null) {
                        javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                                resultsGui,
                                "Could not identify the selected listing. Please refresh search results and try again.",
                                "Listing Not Found",
                                javax.swing.JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    autoModeByListingId.put(listingId, autoNegotiate);

                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                    req.addReceiver(new AID("broker", AID.ISLOCALNAME));
                    req.setConversationId("negotiation-request");
                    req.setContent(DemoMessageCodec.encodeFields(
                            listingId, 
                            Double.toString(myFirstOffer), 
                            Double.toString(myReservePrice)
                    ));
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

    private void handleNegotiationUpdate(ACLMessage msg) {
        String[] parts = DemoMessageCodec.decodeFields(msg.getContent(), 4);
        String status = parts[1];
        String reason = parts[3];
        if ("FAILED".equals(status)) {
            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                    resultsGui != null ? resultsGui : inputGui,
                    reason,
                    "Negotiation Unavailable",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE));
            inputGui.resetForm();
        }
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
            // Auto mode: strategy-driven without GUI.
            NegotiationStrategy strategy = new LinearStrategy();
            // Buyer must concede upward in price (low -> high), even if inputs are swapped.
            double buyerStartPrice = Math.min(myFirstOffer, myReservePrice);
            double buyerReservePrice = Math.max(myFirstOffer, myReservePrice);
            Offer initialOffer = buildBuyerInitialOffer(buyerStartPrice);
            Offer reserveOffer = buildBuyerReserveOffer(buyerReservePrice);
            PreferenceProfile profile = buildBuyerPreference(initialOffer, reserveOffer);
            NegotiationContext ctx = new NegotiationContext(initialOffer, reserveOffer, profile, 10, 0);
            autoSessions.add(sessionId);
            sessionToAutoCtx.put(sessionId, ctx);
            sessionToAutoStrategy.put(sessionId, strategy);
            CompletableFuture<BuyerNegotiationGui> guiFuture = new CompletableFuture<>();
            autoNegotiationWindows.put(sessionId, guiFuture);

            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    BuyerMatchedCarsGui.CarListing listing = new BuyerMatchedCarsGui.CarListing(brand, type, initialPrice,
                            dealerName);
                    BuyerNegotiationGui gui = new BuyerNegotiationGui(this, listing);
                    gui.display();
                    gui.setWaitingState(true);
                    gui.addSystemMessage("Auto-negotiation enabled.");
                    gui.addBuyerOffer(initialOffer, "Auto Profile Start");
                    gui.addSystemMessage("Reserve profile: " + reserveOffer.toDisplayString());
                    guiFuture.complete(gui);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[AUTO-FIPA-BUYER] GUI initialization interrupted: " + e.getMessage());
            } catch (InvocationTargetException e) {
                System.err.println("[AUTO-FIPA-BUYER] GUI initialization failed: " + e.getCause());
            }

            System.out.printf("[AUTO-FIPA-BUYER] Session %s | Strategy: %s | Start: %.2f | Reserve: %.2f%n",
                    sessionId, strategy.getName(), buyerStartPrice, buyerReservePrice);

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

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            try {
                BuyerNegotiationGui gui = guiFuture.get();
                OfferMessage offerMsg = decodeOfferMessage(cfp.getContent());
                Offer dealerOffer = offerMsg.offer;

                gui.addDealerOffer(dealerOffer, "Dealer Offer");

                CompletableFuture<Double> nextOffer = new CompletableFuture<>();
                gui.setOnNegotiationListener(new BuyerNegotiationGui.OnNegotiationListener() {
                    @Override
                    public void onAccept(double currentOffer) {
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

                double amount = nextOffer.get();
                if (amount < 0)
                    return cfp.createReply(ACLMessage.REFUSE);

                Offer buyerOffer = dealerOffer.withPrice(amount);
                gui.addBuyerOffer(buyerOffer, "Your Proposal");

                ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
                String sessionId = cfp.getConversationId();
                propose.setContent(encodeOfferPayload(sessionId, "PROPOSE", buyerOffer));
                return propose;

            } catch (Exception e) {
                e.printStackTrace();
                return cfp.createReply(ACLMessage.NOT_UNDERSTOOD);
            }
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            guiFuture.thenAccept(gui -> {
                OfferMessage accepted = decodeOfferMessage(accept.getContent());
                double finalPrice = accepted.offer.price();
                gui.addSystemMessage("Dealer accepted your offer of RM " + String.format("%,.2f", finalPrice));
                gui.lockNegotiation(true);
            });
            ACLMessage inform = accept.createReply(ACLMessage.INFORM);
            inform.setContent("Deal finalized");
            return inform;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            guiFuture.thenAccept(gui -> gui.addSystemMessage("Dealer rejected. Waiting for counter-offer..."));
        }
    }

    // AUTOOOOO
    private class AutoNegotiationSession extends SSIteratedContractNetResponder {
        private final String sessionId;

        AutoNegotiationSession(Agent a, ACLMessage cfp, String sessionId) {
            super(a, cfp);
            this.sessionId = sessionId;
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            OfferMessage offerMsg = decodeOfferMessage(cfp.getContent());
            Offer dealerOffer = offerMsg.offer;
            CompletableFuture<BuyerNegotiationGui> guiFuture = autoNegotiationWindows.get(sessionId);
            if (guiFuture != null) {
                guiFuture.thenAccept(gui -> gui.addDealerOffer(dealerOffer, "Dealer Offer"));
            }

            NegotiationContext ctx = sessionToAutoCtx.get(sessionId);
            NegotiationStrategy strategy = sessionToAutoStrategy.get(sessionId);

            if (ctx == null || strategy == null) {
                return cfp.createReply(ACLMessage.REFUSE);
            }

            double offerUtility = utilityOf(ctx, dealerOffer);
            double acceptanceThreshold = thresholdOf(ctx);
            if (offerUtility >= acceptanceThreshold) {
                System.out.printf("[AUTO-FIPA-BUYER] Utility %.3f >= threshold %.3f - ACCEPTING%n",
                        offerUtility, acceptanceThreshold);
                if (guiFuture != null) {
                    guiFuture.thenAccept(gui -> {
                        gui.addBuyerOffer(dealerOffer, "Auto Accept");
                        gui.addSystemMessage(String.format("Accepted at utility %.3f (threshold %.3f).", offerUtility,
                                acceptanceThreshold));
                    });
                }
                cleanupAutoSession();
                ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
                propose.setContent(encodeOfferPayload(sessionId, "PROPOSE", dealerOffer));
                return propose;
            }

            if (ctx.isExhausted()) {
                System.out.println("[AUTO-FIPA-BUYER] Max rounds reached - REFUSING");
                if (guiFuture != null) {
                    guiFuture.thenAccept(gui -> {
                        gui.addSystemMessage("Auto-negotiation ended: round limit reached.");
                        gui.lockNegotiation(false);
                    });
                }
                cleanupAutoSession();
                return cfp.createReply(ACLMessage.REFUSE);
            }

            // Price-first behavior: buyer adjusts price by strategy but keeps dealer's latest non-price package.
            Offer scheduledBuyerOffer = strategy.nextOffer(ctx);
            Offer myOffer = dealerOffer.withPrice(scheduledBuyerOffer.price());
            System.out.printf("[AUTO-FIPA-BUYER] Round %d/%d - countering with RM %.2f%n",
                    ctx.roundsElapsed, ctx.maxRounds, myOffer.price());
            sessionToAutoCtx.put(sessionId, ctx.nextRound());
            if (guiFuture != null) {
                guiFuture.thenAccept(gui -> {
                    gui.addBuyerOffer(myOffer, "Auto Counter");
                    gui.setWaitingState(true);
                });
            }

            ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
            propose.setContent(encodeOfferPayload(sessionId, "PROPOSE", myOffer));
            return propose;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            OfferMessage accepted = decodeOfferMessage(accept.getContent());
            double finalPrice = accepted.offer.price();
            System.out.printf("[AUTO-FIPA-BUYER] Deal accepted! Final price: RM %.2f%n", finalPrice);
            CompletableFuture<BuyerNegotiationGui> guiFuture = autoNegotiationWindows.get(sessionId);
            if (guiFuture != null) {
                guiFuture.thenAccept(gui -> {
                    gui.addSystemMessage("Dealer confirmed the deal.");
                    gui.lockNegotiation(true);
                });
            }
            cleanupAutoSession();
            ACLMessage inform = accept.createReply(ACLMessage.INFORM);
            inform.setContent("Deal finalized");
            return inform;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("[AUTO-FIPA-BUYER] Proposal rejected - waiting for dealer counter...");
        }

        private void cleanupAutoSession() {
            autoSessions.remove(sessionId);
            sessionToAutoCtx.remove(sessionId);
            sessionToAutoStrategy.remove(sessionId);
            autoNegotiationWindows.remove(sessionId);
        }
    }
    private PreferenceProfile buildBuyerPreference(Offer initialOffer, Offer reserveOffer) {
        double priceBest = Math.min(initialOffer.price(), reserveOffer.price());
        double priceWorst = Math.max(initialOffer.price(), reserveOffer.price());
        int warrantyBest = Math.max(initialOffer.warrantyMonths(), reserveOffer.warrantyMonths());
        int warrantyWorst = Math.min(initialOffer.warrantyMonths(), reserveOffer.warrantyMonths());
        int insuranceBest = Math.max(initialOffer.insuranceIncludedMonths(), reserveOffer.insuranceIncludedMonths());
        int insuranceWorst = Math.min(initialOffer.insuranceIncludedMonths(), reserveOffer.insuranceIncludedMonths());
        int serviceBest = Math.max(initialOffer.servicePackageLevel(), reserveOffer.servicePackageLevel());
        int serviceWorst = Math.min(initialOffer.servicePackageLevel(), reserveOffer.servicePackageLevel());
        return new PreferenceProfile(
                new WeightedSumUtility.Builder()
                        .price(0.60, priceBest, priceWorst)
                        .warrantyMonths(0.15, warrantyBest, warrantyWorst)
                        .insuranceIncludedMonths(0.15, insuranceBest, insuranceWorst)
                        .servicePackageLevel(0.10, serviceBest, serviceWorst)
                        .build(),
                0.95,
                0.60);
    }

    private Offer buildBuyerInitialOffer(double firstOfferPrice) {
        // Buyer starts strict on non-price terms.
        return new Offer(firstOfferPrice, 24, 24, 3);
    }

    private Offer buildBuyerReserveOffer(double reservePrice) {
        // Buyer can relax non-price requirements toward deadline.
        return new Offer(reservePrice, 6, 6, 1);
    }

    private double utilityOf(NegotiationContext ctx, Offer offer) {
        if (ctx.preferenceProfile != null) {
            return ctx.preferenceProfile.score(offer);
        }
        return new WeightedSumUtility.Builder()
                .price(1.0, Math.min(ctx.initialOffer, ctx.reservePrice), Math.max(ctx.initialOffer, ctx.reservePrice))
                .build()
                .score(offer);
    }

    private double thresholdOf(NegotiationContext ctx) {
        if (ctx.preferenceProfile != null) {
            return ctx.preferenceProfile.thresholdAt(ctx.t());
        }
        return 0.0;
    }

    private String encodeOfferPayload(String sessionId, String action, Offer offer) {
        return DemoMessageCodec.encodeFields(
                sessionId,
                action,
                Double.toString(offer.price()),
                Integer.toString(offer.warrantyMonths()),
                Integer.toString(offer.insuranceIncludedMonths()),
                Integer.toString(offer.servicePackageLevel()));
    }

    private OfferMessage decodeOfferMessage(String payload) {
        String[] parts = DemoMessageCodec.decodeFields(payload, 3);
        String sessionId = parts[0];
        String action = parts[1];
        double price = Double.parseDouble(parts[2]);
        int warrantyMonths = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
        int insuranceIncludedMonths = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int servicePackageLevel = parts.length > 5 ? (int) Math.round(Double.parseDouble(parts[5])) : 0;
        return new OfferMessage(sessionId, action, new Offer(price, warrantyMonths, insuranceIncludedMonths, servicePackageLevel));
    }

    private record OfferMessage(String sessionId, String action, Offer offer) {
    }
}

