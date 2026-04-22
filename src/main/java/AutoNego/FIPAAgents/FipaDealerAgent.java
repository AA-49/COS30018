package AutoNego.FIPAAgents;

import AutoNego.GUI.DealerInputGui;
import AutoNego.GUI.DealerNegotiationGui;
import AutoNego.DemoMessageCodec;
import AutoNego.strategy.LinearStrategy;
import AutoNego.strategy.NegotiationContext;
import AutoNego.strategy.NegotiationStrategy;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

public class FipaDealerAgent extends Agent {
    private DealerInputGui inputGui;
    // map negotiation session to related information
    private final Map<String, CompletableFuture<DealerNegotiationGui>> negotiationWindows = new HashMap<>();
    private final Map<String, String> sessionToBuyer = new HashMap<>();
    private final Map<String, String> sessionToListingId = new HashMap<>();
    private final Map<String, Double> sessionToCurrentOffer = new HashMap<>();

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
        String buyerName = parts[1];
        String carSummary = parts[2] + " " + parts[3];

        // Custom panel: confirm + auto-negotiate checkbox
        JCheckBox autoBox = new JCheckBox("Auto-Negotiate");
        autoBox.setFont(autoBox.getFont().deriveFont(12f));
        Object[] dialogContent = {
                "Buyer " + buyerName + " is interested in your " + carSummary + ". Start negotiation?",
                autoBox
        };

        int choice = JOptionPane.showConfirmDialog(
                null, dialogContent, "Buyer Interest", JOptionPane.YES_NO_OPTION);

        // reply to broker
        ACLMessage reply = new ACLMessage(
                choice == JOptionPane.YES_OPTION ? ACLMessage.AGREE : ACLMessage.REFUSE);
        reply.addReceiver(new AID("broker", AID.ISLOCALNAME));
        reply.setConversationId("dealer-interest-response");
        reply.setContent(DemoMessageCodec.encodeFields(listingId, buyerName));
        send(reply);

        if (choice == JOptionPane.YES_OPTION) {
            autoModeByListingId.put(listingId, autoBox.isSelected());
        }
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
        cfp.setContent(DemoMessageCodec.encodeFields(sessionId, "INITIAL", Double.toString(initialPrice)));

        if (auto) {
            // Auto mode: strategy-driven. GUI opens in read-only mode so the negotiation
            // is still visible, but all input buttons are disabled.
            NegotiationStrategy strategy = new LinearStrategy();
            // Dealer: initialOffer = asking price (high), reserve = minAcceptPrice (low)
            NegotiationContext ctx = new NegotiationContext(initialPrice, minAcceptPrice, 10, 0);
            sessionToAutoCtx.put(sessionId, ctx);
            sessionToAutoStrategy.put(sessionId, strategy);

            CompletableFuture<DealerNegotiationGui> guiFuture = new CompletableFuture<>();
            negotiationWindows.put(sessionId, guiFuture);
            final String strategyName = strategy.getName();
            SwingUtilities.invokeLater(() -> {
                DealerNegotiationGui gui = new DealerNegotiationGui(this, buyerName, brand, type, initialPrice);
                gui.display();
                gui.lockNegotiation(true); // disable all input — display-only
                gui.addSystemMessage("Auto-negotiating " + strategyName + " strategy...");
                guiFuture.complete(gui);
            });

            System.out.printf("[AUTO-FIPA-DEALER] Session %s | Strategy: %s | Ask: %.2f | Floor: %.2f%n",
                    sessionId, strategy.getName(), initialPrice, minAcceptPrice);
            addBehaviour(new AutoHagglingInitiator(this, cfp, sessionId, guiFuture));

        } else {
            CompletableFuture<DealerNegotiationGui> guiFuture = new CompletableFuture<>();
            negotiationWindows.put(sessionId, guiFuture);

            SwingUtilities.invokeLater(() -> {
                DealerNegotiationGui gui = new DealerNegotiationGui(this, buyerName, brand, type, initialPrice);
                gui.display();
                gui.setWaitingState(true);
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
            String[] parts = DemoMessageCodec.decodeFields(propose.getContent(), 3);
            double amount = Double.parseDouble(parts[2]);
            sessionToCurrentOffer.put(sessionId, amount);

            try {
                DealerNegotiationGui gui = guiFuture.get();

                CompletableFuture<String> decision = new CompletableFuture<>();
                gui.setOnNegotiationListener(new DealerNegotiationGui.OnNegotiationListener() {
                    @Override
                    public void onAccept(double currentOffer) {
                        decision.complete("ACCEPT");
                    }

                    @Override
                    public void onCounterOffer(double counterAmt) {
                        decision.complete("COUNTER");
                    }

                    @Override
                    public void onReject() {
                        decision.complete("REJECT");
                    }
                });

                gui.addBuyerOffer(amount, "Buyer Proposal");

                String action = decision.get();
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> accs = (Vector<ACLMessage>) acceptances;

                if ("ACCEPT".equals(action)) {
                    ACLMessage accept = propose.createReply(ACLMessage.ACCEPT_PROPOSAL);
                    accept.setContent(propose.getContent());
                    accs.addElement(accept);
                    gui.addSystemMessage("You accepted the offer.");
                    gui.lockNegotiation(true);

                } else if ("COUNTER".equals(action)) {
                    double counterPrice = sessionToCurrentOffer.get(sessionId);
                    gui.addDealerOffer(counterPrice, "Counter-asking price");

                    accs.addElement(propose.createReply(ACLMessage.REJECT_PROPOSAL));

                    ACLMessage nextCfp = new ACLMessage(ACLMessage.CFP);
                    nextCfp.addReceiver(propose.getSender());
                    nextCfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                    nextCfp.setConversationId(sessionId);
                    nextCfp.setContent(
                            DemoMessageCodec.encodeFields(sessionId, "COUNTER", Double.toString(counterPrice)));

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
        private final CompletableFuture<DealerNegotiationGui> guiFuture;

        AutoHagglingInitiator(Agent a, ACLMessage cfp, String sessionId,
                CompletableFuture<DealerNegotiationGui> guiFuture) {
            super(a, cfp);
            this.sessionId = sessionId;
            this.guiFuture = guiFuture;
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            String[] parts = DemoMessageCodec.decodeFields(propose.getContent(), 3);
            double buyerOffer = Double.parseDouble(parts[2]);
            sessionToCurrentOffer.put(sessionId, buyerOffer);

            NegotiationContext ctx = sessionToAutoCtx.get(sessionId);
            NegotiationStrategy strategy = sessionToAutoStrategy.get(sessionId);

            if (ctx == null || strategy == null) {
                ((Vector<ACLMessage>) acceptances).add(propose.createReply(ACLMessage.REJECT_PROPOSAL));
                return;
            }

            double scheduled = strategy.nextOffer(ctx);

            // Mirror the buyer's incoming offer into the GUI
            guiFuture.thenAccept(
                    gui -> SwingUtilities.invokeLater(() -> gui.addBuyerOffer(buyerOffer, "Buyer Proposal (auto)")));

            if (buyerOffer >= scheduled) {
                // Buyer meets or beats our scheduled ask — accept
                System.out.printf("[AUTO-FIPA-DEALER] Buyer %.2f >= scheduled %.2f — ACCEPTING%n",
                        buyerOffer, scheduled);
                ACLMessage accept = propose.createReply(ACLMessage.ACCEPT_PROPOSAL);
                accept.setContent(propose.getContent());
                ((Vector<ACLMessage>) acceptances).add(accept);
                guiFuture.thenAccept(gui -> SwingUtilities
                        .invokeLater(() -> gui.addSystemMessage("Auto accepted the buyer's offer.")));
                cleanupAutoSession();

            } else if (ctx.isExhausted()) {
                System.out.println("[AUTO-FIPA-DEALER] Max rounds reached — REJECTING");
                ((Vector<ACLMessage>) acceptances).add(propose.createReply(ACLMessage.REJECT_PROPOSAL));
                guiFuture.thenAccept(gui -> SwingUtilities
                        .invokeLater(() -> gui.addSystemMessage("Max rounds reached. Auto-negotiation ended.")));
                cleanupAutoSession();

            } else {
                // Counter with the scheduled price and advance one round
                System.out.printf("[AUTO-FIPA-DEALER] Round %d/%d — countering with RM %.2f%n",
                        ctx.roundsElapsed, ctx.maxRounds, scheduled);
                sessionToAutoCtx.put(sessionId, ctx.nextRound());

                // Show our auto counter-offer in the GUI
                final double counter = scheduled;
                guiFuture.thenAccept(
                        gui -> SwingUtilities.invokeLater(() -> gui.addDealerOffer(counter, "Auto Counter-Offer")));

                ((Vector<ACLMessage>) acceptances).add(propose.createReply(ACLMessage.REJECT_PROPOSAL));

                ACLMessage nextCfp = new ACLMessage(ACLMessage.CFP);
                nextCfp.addReceiver(propose.getSender());
                nextCfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                nextCfp.setConversationId(sessionId);
                nextCfp.setContent(DemoMessageCodec.encodeFields(sessionId, "COUNTER", Double.toString(scheduled)));

                Vector<ACLMessage> v = new Vector<>();
                v.add(nextCfp);
                newIteration(v);
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("[AUTO-FIPA-DEALER] Deal confirmed by buyer.");
            double finalPrice = sessionToCurrentOffer.get(sessionId);
            guiFuture.thenAccept(gui -> SwingUtilities.invokeLater(() -> {
                gui.addSystemMessage("Deal confirmed at RM " + String.format("%,.2f", finalPrice));
                gui.lockNegotiation(true);
            }));
            reportDealToBroker(sessionId, finalPrice);
            cleanupAutoSession();
        }

        private void cleanupAutoSession() {
            sessionToAutoCtx.remove(sessionId);
            sessionToAutoStrategy.remove(sessionId);
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
}
