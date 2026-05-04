package AutoNego.FIPAAgents;

import AutoNego.GUI.DealerBuyerScreen;
import AutoNego.GUI.DealerInputGui;
import AutoNego.GUI.DealerNegotiationGui;
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
import jade.proto.ContractNetInitiator;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

public class FipaDealerAgent extends Agent {
    private DealerInputGui inputGui;
    private DealerBuyerScreen buyerScreen;
    // map negotiation session to related information
    private final Map<String, CompletableFuture<DealerNegotiationGui>> negotiationWindows = new HashMap<>();
    private final Map<String, CompletableFuture<DealerNegotiationGui>> autoNegotiationWindows = new HashMap<>();
    private final Map<String, String> sessionToBuyer = new HashMap<>();
    private final Map<String, String> sessionToListingId = new HashMap<>();
    private final Map<String, Double> sessionToCurrentOffer = new HashMap<>();

    private final List<DealerBuyerScreen.BuyerInterest> interests = new ArrayList<>();
    private final Map<String, String> interestListingIds = new HashMap<>();

    // Auto-negotiate state
    private final Map<String, Boolean> autoModeByListingId = new HashMap<>();
    private final Map<String, NegotiationContext> sessionToAutoCtx = new HashMap<>();
    private final Map<String, NegotiationStrategy> sessionToAutoStrategy = new HashMap<>();

    @Override
    protected void setup() {
        inputGui = new DealerInputGui(this);
        // dealer send listing
        inputGui.setOnListingListener(listings -> {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
            msg.setConversationId("dealer-listings");
            msg.setContent(DemoMessageCodec.encodeRecords(listings.stream()
                    .map(l -> DemoMessageCodec.encodeFields(
                            l.brand, l.type,
                            Double.toString(l.price),
                            Double.toString(l.minAcceptPrice)))
                    .toArray(String[]::new)));
            send(msg);
        });
        inputGui.display();

        addBehaviour(new MessageRouter());
        System.out.println("FIPA Dealer Agent " + getLocalName() + " is ready.");
    }

    @Override
    protected void takeDown() {
        if (inputGui != null)
            inputGui.dispose();
        for (CompletableFuture<DealerNegotiationGui> fut : negotiationWindows.values()) {
            fut.thenAccept(gui -> SwingUtilities.invokeLater(gui::dispose));
        }
        for (CompletableFuture<DealerNegotiationGui> fut : autoNegotiationWindows.values()) {
            fut.thenAccept(gui -> SwingUtilities.invokeLater(gui::dispose));
        }
    }

    // what to do in case of each message
    private class MessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            // message template for buyer interest and negotiation start
            jade.lang.acl.MessageTemplate mt = jade.lang.acl.MessageTemplate.or(
                    jade.lang.acl.MessageTemplate.MatchConversationId("buyer-interest"),
                    jade.lang.acl.MessageTemplate.MatchConversationId("negotiation-start"));
            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }

            if ("buyer-interest".equals(msg.getConversationId())) {
                handleBuyerInterest(msg);
            } else if ("negotiation-start".equals(msg.getConversationId())) {
                startNegotiationProtocol(msg);
            }
        }
    }

    // what to do when buyer is interested in dealer's car
    private void handleBuyerInterest(ACLMessage msg) {
        String[] parts = DemoMessageCodec.decodeFields(msg.getContent(), 5);
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
                        sendInterestDecision(selected, ACLMessage.REFUSE);
                    }
                });
                buyerScreen.display();
            } else {
                buyerScreen.addInterest(interest);
            }
        });
    }

    private void sendInterestDecision(DealerBuyerScreen.BuyerInterest interest, int performative) {
        String listingId = interestListingIds.get(interestKey(interest));
        if (listingId == null) {
            return;
        }

        ACLMessage reply = new ACLMessage(performative);
        reply.addReceiver(new AID("broker", AID.ISLOCALNAME));
        reply.setConversationId("dealer-interest-response");
        reply.setContent(DemoMessageCodec.encodeFields(listingId, interest.buyerName));
        send(reply);
    }

    private String interestKey(DealerBuyerScreen.BuyerInterest interest) {
        return String.format("%s|%s|%s|%s",
                interest.buyerName,
                interest.carBrand,
                interest.carType,
                Double.toString(interest.buyerInitialOffer)
        );
    }

    // what to do when negotiation start
    private void startNegotiationProtocol(ACLMessage startMsg) {
        // Broker sends 7 fields: sessionId, buyerName, brand, type, price, listingId,
        // minAcceptPrice
        String[] parts = DemoMessageCodec.decodeFields(startMsg.getContent(), 7);
        String sessionId = parts[0];
        String buyerName = parts[1];
        String brand = parts[2];
        String type = parts[3];
        double initialPrice = Double.parseDouble(parts[4]);
        String listingId = parts[5];
        double minAcceptPrice = Double.parseDouble(parts[6]);

        sessionToBuyer.put(sessionId, buyerName);
        sessionToListingId.put(sessionId, listingId);
        sessionToCurrentOffer.put(sessionId, initialPrice);

        boolean auto = autoModeByListingId.getOrDefault(listingId, false);
        autoModeByListingId.remove(listingId);

        // Build the initial CFP (same for both modes)
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
        cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
        cfp.setConversationId(sessionId);
        Offer initialAutoOffer = buildDealerInitialOffer(initialPrice);
        cfp.setContent(encodeOfferPayload(sessionId, "INITIAL", initialAutoOffer));

        if (auto) {
            // Auto mode: strategy-driven without GUI.
            NegotiationStrategy strategy = new LinearStrategy();
            // Dealer must concede downward in price (high -> low), even if inputs are swapped.
            double dealerStartPrice = Math.max(initialPrice, minAcceptPrice);
            double dealerFloorPrice = Math.min(initialPrice, minAcceptPrice);
            Offer initialOffer = buildDealerInitialOffer(dealerStartPrice);
            Offer reserveOffer = buildDealerReserveOffer(dealerFloorPrice);
            PreferenceProfile profile = buildDealerPreference(initialOffer, reserveOffer);
            NegotiationContext ctx = new NegotiationContext(initialOffer, reserveOffer, profile, 10, 0);
            sessionToAutoCtx.put(sessionId, ctx);
            sessionToAutoStrategy.put(sessionId, strategy);
            CompletableFuture<DealerNegotiationGui> autoGuiFuture = new CompletableFuture<>();
            autoNegotiationWindows.put(sessionId, autoGuiFuture);

            try {
                SwingUtilities.invokeAndWait(() -> {
                    DealerNegotiationGui gui = new DealerNegotiationGui(this, buyerName, brand, type, initialPrice);
                    gui.display();
                    gui.setWaitingState(true);
                    gui.addSystemMessage("Auto-negotiation enabled.");
                    gui.addDealerOffer(initialOffer, "Auto Profile Start");
                    gui.addSystemMessage("Reserve profile: " + reserveOffer.toDisplayString());
                    autoGuiFuture.complete(gui);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[AUTO-FIPA-DEALER] GUI initialization interrupted: " + e.getMessage());
            } catch (InvocationTargetException e) {
                System.err.println("[AUTO-FIPA-DEALER] GUI initialization failed: " + e.getCause());
            }

            System.out.printf("[AUTO-FIPA-DEALER] Session %s | Strategy: %s | Ask: %.2f | Floor: %.2f%n",
                    sessionId, strategy.getName(), dealerStartPrice, dealerFloorPrice);
            addBehaviour(new AutoHagglingInitiator(this, cfp, sessionId));

        } else {
            CompletableFuture<DealerNegotiationGui> guiFuture = new CompletableFuture<>();
            negotiationWindows.put(sessionId, guiFuture);

            SwingUtilities.invokeLater(() -> {
                DealerNegotiationGui gui = new DealerNegotiationGui(this, buyerName, brand, type, initialPrice);
                gui.display();
                gui.setWaitingState(true);
                gui.addDealerOffer(initialPrice, "Your Initial Ask");
                guiFuture.complete(gui);
                System.out.println("Dealer: Negotiation window opened for buyer " + buyerName);
            });

            addBehaviour(new HagglingInitiator(this, cfp, sessionId, guiFuture));
        }
    }

    // MANUALLLLL
    private class HagglingInitiator extends ContractNetInitiator {
        private final String sessionId;
        private final CompletableFuture<DealerNegotiationGui> guiFuture;

        HagglingInitiator(Agent a, ACLMessage cfp, String sessionId,
                CompletableFuture<DealerNegotiationGui> guiFuture) {
            super(a, cfp);
            this.sessionId = sessionId;
            this.guiFuture = guiFuture;
        }

        @Override
        @SuppressWarnings("rawtypes")
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            OfferMessage buyerProposal = decodeOfferMessage(propose.getContent());
            double amount = buyerProposal.offer.price();
            sessionToCurrentOffer.put(sessionId, amount);

            try {
                DealerNegotiationGui gui = guiFuture.get();

                final class Decision {
                    String action;
                    double price;
                }
                final Decision decision = new Decision();
                final CompletableFuture<Decision> decisionFuture = new CompletableFuture<>();

                gui.setOnNegotiationListener(new DealerNegotiationGui.OnNegotiationListener() {
                    @Override
                    public void onAccept(double currentOffer) {
                        decision.action = "ACCEPT";
                        decision.price = currentOffer;
                        decisionFuture.complete(decision);
                    }

                    @Override
                    public void onCounterOffer(double counterAmt) {
                        decision.action = "COUNTER";
                        decision.price = counterAmt;
                        decisionFuture.complete(decision);
                    }

                    @Override
                    public void onReject() {
                        decision.action = "REJECT";
                        decisionFuture.complete(decision);
                    }
                });

                gui.addBuyerOffer(amount, "Buyer Proposal");

                Decision result = decisionFuture.get();
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> accs = (Vector<ACLMessage>) acceptances;

                if ("ACCEPT".equals(result.action)) {
                    ACLMessage accept = propose.createReply(ACLMessage.ACCEPT_PROPOSAL);
                    accept.setContent(propose.getContent());
                    accs.addElement(accept);
                    gui.addSystemMessage("You accepted the offer.");
                    gui.lockNegotiation(true);

                } else if ("COUNTER".equals(result.action)) {
                    double counterPrice = result.price;
                    sessionToCurrentOffer.put(sessionId, counterPrice);
                    gui.addDealerOffer(counterPrice, "Your counter-offer");

                    accs.addElement(propose.createReply(ACLMessage.REJECT_PROPOSAL));

                    ACLMessage nextCfp = new ACLMessage(ACLMessage.CFP);
                    nextCfp.addReceiver(propose.getSender());
                    nextCfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                    nextCfp.setConversationId(sessionId);
                    nextCfp.setContent(encodeOfferPayload(sessionId, "COUNTER", Offer.priceOnly(counterPrice)));

                    Vector<ACLMessage> v = new Vector<>();
                    v.add(nextCfp);
                    newIteration(v);

                } else {
                    accs.addElement(propose.createReply(ACLMessage.REJECT_PROPOSAL));
                    gui.lockNegotiation(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            guiFuture.thenAccept(gui -> {
                gui.addSystemMessage("Deal confirmed and reported to broker.");
                reportDealToBroker(sessionId, sessionToCurrentOffer.get(sessionId));
            });
        }
    }

    // AUTOOOOO
    private class AutoHagglingInitiator extends ContractNetInitiator {
        private final String sessionId;

        AutoHagglingInitiator(Agent a, ACLMessage cfp, String sessionId) {
            super(a, cfp);
            this.sessionId = sessionId;
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            OfferMessage buyerProposal = decodeOfferMessage(propose.getContent());
            Offer buyerOfferBundle = buyerProposal.offer;
            double buyerOffer = buyerOfferBundle.price();
            sessionToCurrentOffer.put(sessionId, buyerOffer);
            CompletableFuture<DealerNegotiationGui> guiFuture = autoNegotiationWindows.get(sessionId);
            if (guiFuture != null) {
                guiFuture.thenAccept(gui -> gui.addBuyerOffer(buyerOfferBundle, "Buyer Offer"));
            }

            NegotiationContext ctx = sessionToAutoCtx.get(sessionId);
            NegotiationStrategy strategy = sessionToAutoStrategy.get(sessionId);

            if (ctx == null || strategy == null) {
                ((Vector<ACLMessage>) acceptances).add(propose.createReply(ACLMessage.REJECT_PROPOSAL));
                return;
            }

            Offer scheduledOffer = strategy.nextOffer(ctx);
            double scheduled = scheduledOffer.price();

            double offerUtility = utilityOf(ctx, buyerOfferBundle);
            double acceptanceThreshold = thresholdOf(ctx);
            if (offerUtility >= acceptanceThreshold) {
                System.out.printf("[AUTO-FIPA-DEALER] Utility %.3f >= threshold %.3f - ACCEPTING%n",
                        offerUtility, acceptanceThreshold);
                if (guiFuture != null) {
                    guiFuture.thenAccept(gui -> gui.addSystemMessage(String.format(
                            "Accepted at utility %.3f (threshold %.3f).", offerUtility, acceptanceThreshold)));
                }
                ACLMessage accept = propose.createReply(ACLMessage.ACCEPT_PROPOSAL);
                accept.setContent(propose.getContent());
                ((Vector<ACLMessage>) acceptances).add(accept);
                cleanupAutoSession();

            } else if (ctx.isExhausted()) {
                System.out.println("[AUTO-FIPA-DEALER] Max rounds reached - REJECTING");
                if (guiFuture != null) {
                    guiFuture.thenAccept(gui -> {
                        gui.addSystemMessage("Auto-negotiation ended: round limit reached.");
                        gui.lockNegotiation(false);
                    });
                }
                ((Vector<ACLMessage>) acceptances).add(propose.createReply(ACLMessage.REJECT_PROPOSAL));
                cleanupAutoSession();

            } else {
                System.out.printf("[AUTO-FIPA-DEALER] Round %d/%d - countering with RM %.2f%n",
                        ctx.roundsElapsed, ctx.maxRounds, scheduled);
                sessionToAutoCtx.put(sessionId, ctx.nextRound());
                if (guiFuture != null) {
                    guiFuture.thenAccept(gui -> {
                        gui.addDealerOffer(scheduledOffer, "Auto Counter");
                        gui.setWaitingState(true);
                    });
                }

                ((Vector<ACLMessage>) acceptances).add(propose.createReply(ACLMessage.REJECT_PROPOSAL));

                ACLMessage nextCfp = new ACLMessage(ACLMessage.CFP);
                nextCfp.addReceiver(propose.getSender());
                nextCfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                nextCfp.setConversationId(sessionId);
                nextCfp.setContent(encodeOfferPayload(sessionId, "COUNTER", scheduledOffer));

                Vector<ACLMessage> v = new Vector<>();
                v.add(nextCfp);
                newIteration(v);
            }
        }
        @Override
        protected void handleInform(ACLMessage inform) {
            double finalPrice = sessionToCurrentOffer.get(sessionId);
            System.out.printf("[AUTO-FIPA-DEALER] Deal confirmed by buyer. Final price: %.2f%n", finalPrice);
            CompletableFuture<DealerNegotiationGui> guiFuture = autoNegotiationWindows.get(sessionId);
            if (guiFuture != null) {
                guiFuture.thenAccept(gui -> {
                    gui.addSystemMessage("Buyer confirmed the deal.");
                    gui.lockNegotiation(true);
                });
            }
            reportDealToBroker(sessionId, finalPrice);
            cleanupAutoSession();
        }

        private void cleanupAutoSession() {
            sessionToAutoCtx.remove(sessionId);
            sessionToAutoStrategy.remove(sessionId);
            autoNegotiationWindows.remove(sessionId);
        }
    }

    private void reportDealToBroker(String sessionId, double finalPrice) {
        String listingId = sessionToListingId.get(sessionId);
        String buyerName = sessionToBuyer.get(sessionId);
        if (listingId == null || buyerName == null)
            return;

        double commission = finalPrice * 0.05;
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
        msg.setConversationId("deal-completed");
        msg.setContent(DemoMessageCodec.encodeFields(
                listingId, Double.toString(finalPrice),
                Double.toString(commission), buyerName));
        send(msg);
    }

    private PreferenceProfile buildDealerPreference(Offer initialOffer, Offer reserveOffer) {
        double priceBest = Math.max(initialOffer.price(), reserveOffer.price());
        double priceWorst = Math.min(initialOffer.price(), reserveOffer.price());
        // Dealer economics: richer extras are costlier, so lower extras are better for dealer utility.
        int warrantyBest = Math.min(initialOffer.warrantyMonths(), reserveOffer.warrantyMonths());
        int warrantyWorst = Math.max(initialOffer.warrantyMonths(), reserveOffer.warrantyMonths());
        int insuranceBest = Math.min(initialOffer.insuranceIncludedMonths(), reserveOffer.insuranceIncludedMonths());
        int insuranceWorst = Math.max(initialOffer.insuranceIncludedMonths(), reserveOffer.insuranceIncludedMonths());
        int serviceBest = Math.min(initialOffer.servicePackageLevel(), reserveOffer.servicePackageLevel());
        int serviceWorst = Math.max(initialOffer.servicePackageLevel(), reserveOffer.servicePackageLevel());
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

    private Offer buildDealerInitialOffer(double askPrice) {
        // Dealer starts with a high price and lean (low-cost) extras.
        return new Offer(askPrice, 6, 6, 1);
    }

    private Offer buildDealerReserveOffer(double floorPrice) {
        // Dealer concession path: lower price and sweeter extras near deadline.
        return new Offer(floorPrice, 24, 24, 3);
    }

    private double utilityOf(NegotiationContext ctx, Offer offer) {
        if (ctx.preferenceProfile != null) {
            return ctx.preferenceProfile.score(offer);
        }
        double best = Math.max(ctx.initialOffer, ctx.reservePrice);
        double worst = Math.min(ctx.initialOffer, ctx.reservePrice);
        return new WeightedSumUtility.Builder()
                .price(1.0, best, worst)
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
