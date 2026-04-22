package AutoNego.FIPAAgents;

import AutoNego.BuyerInputGui;
import AutoNego.BuyerMatchedCarsGui;
import AutoNego.BuyerNegotiationGui;
import AutoNego.DemoMessageCodec;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SSIteratedContractNetResponder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * FIPA Buyer Agent (Responder)
 * Uses SSIteratedContractNetResponder so it correctly handles multiple
 * rounds: CFP -> PROPOSE -> REJECT_PROPOSAL -> new CFP -> PROPOSE -> ACCEPT
 */
public class FipaBuyerAgent extends Agent {
    private BuyerInputGui inputGui;
    private BuyerMatchedCarsGui resultsGui;
    private final Map<String, CompletableFuture<BuyerNegotiationGui>> negotiationWindows = new HashMap<>();

    @Override
    protected void setup() {
        inputGui = new BuyerInputGui(this);
        inputGui.setOnConfirmListener((brand, type, maxPrice) -> {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
            msg.setConversationId("buyer-search");
            msg.setContent(DemoMessageCodec.encodeFields(brand, type, Double.toString(maxPrice)));
            send(msg);
        });
        inputGui.show();

        addBehaviour(new MessageRouter());

        // FIPA Protocol Listener:
        // We listen for the *initial* CFP (Call For Proposals) with a CyclicBehaviour.
        // Once a CFP arrives, we spawn a specialized SSIteratedContractNetResponder
        // that handles that specific negotiation session until it ends.
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET),
                    MessageTemplate.MatchPerformative(ACLMessage.CFP)
                );
                ACLMessage cfp = receive(mt);
                if (cfp != null) {
                    String sessionId = cfp.getConversationId();
                    CompletableFuture<BuyerNegotiationGui> guiFuture = negotiationWindows.get(sessionId);
                    
                    // Critical Fix: Use SSIteratedContractNetResponder instead of standard ContractNetResponder.
                    // This behavior stays alive across multiple Reject->CFP iterations.
                    if (guiFuture != null) {
                        addBehaviour(new NegotiationSession(myAgent, cfp, guiFuture));
                    }
                } else {
                    block();
                }
            }
        });

        System.out.println("FIPA Buyer Agent " + getLocalName() + " is ready.");
    }

    @Override
    protected void takeDown() {
        if (inputGui != null) inputGui.dispose();
        if (resultsGui != null) resultsGui.dispose();
        for (CompletableFuture<BuyerNegotiationGui> fut : negotiationWindows.values()) {
            fut.thenAccept(gui -> javax.swing.SwingUtilities.invokeLater(gui::dispose));
        }
    }

    // ── Message routing for non-FIPA messages ────────────────────────────────

    private class MessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            jade.lang.acl.MessageTemplate mt = jade.lang.acl.MessageTemplate.or(
                jade.lang.acl.MessageTemplate.MatchConversationId("buyer-search-result"),
                jade.lang.acl.MessageTemplate.or(
                    jade.lang.acl.MessageTemplate.MatchConversationId("negotiation-start"),
                    jade.lang.acl.MessageTemplate.MatchConversationId("negotiation-update")
                )
            );
            ACLMessage msg = receive(mt);
            if (msg == null) { block(); return; }

            String convId = msg.getConversationId();
            if ("buyer-search-result".equals(convId)) handleSearchResults(msg);
            else if ("negotiation-start".equals(convId)) handleNegotiationStart(msg);
        }
    }

    private void handleSearchResults(ACLMessage msg) {
        List<BuyerMatchedCarsGui.CarListing> matches = new ArrayList<>();
        for (String record : DemoMessageCodec.decodeRecords(msg.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 5);
            matches.add(new BuyerMatchedCarsGui.CarListing(parts[1], parts[2], Double.parseDouble(parts[3]), parts[4]));
        }
        if (resultsGui == null) {
            resultsGui = new BuyerMatchedCarsGui(this, matches);
            resultsGui.setOnActionListener(new BuyerMatchedCarsGui.OnActionListener() {
                @Override public void onNegotiate(BuyerMatchedCarsGui.CarListing listing) {
                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                    req.addReceiver(new AID("broker", AID.ISLOCALNAME));
                    req.setConversationId("negotiation-request");
                    req.setContent(getListingIdFromResults(msg, listing));
                    send(req);
                }
                @Override public void onCancel(BuyerMatchedCarsGui.CarListing listing) {}
            });
            resultsGui.show();
        } else {
            resultsGui.updateListings(matches);
        }
        inputGui.resetForm();
    }

    private String getListingIdFromResults(ACLMessage msg, BuyerMatchedCarsGui.CarListing listing) {
        for (String record : DemoMessageCodec.decodeRecords(msg.getContent())) {
            String[] parts = DemoMessageCodec.decodeFields(record, 5);
            if (parts[4].equals(listing.dealerName) && parts[1].equals(listing.brand)) return parts[0];
        }
        return "";
    }

    private void handleNegotiationStart(ACLMessage msg) {
        String[] parts = DemoMessageCodec.decodeFields(msg.getContent(), 6);
        String sessionId = parts[0];
        String brand     = parts[2];
        String type      = parts[3];
        double initialPrice = Double.parseDouble(parts[4]);
        String dealerName   = parts[5];

        CompletableFuture<BuyerNegotiationGui> guiFuture = new CompletableFuture<>();
        negotiationWindows.put(sessionId, guiFuture);

        javax.swing.SwingUtilities.invokeLater(() -> {
            BuyerMatchedCarsGui.CarListing listing =
                    new BuyerMatchedCarsGui.CarListing(brand, type, initialPrice, dealerName);
            BuyerNegotiationGui gui = new BuyerNegotiationGui(this, listing);
            gui.show();
            gui.addSystemMessage("Waiting for Dealer to start the protocol...");
            guiFuture.complete(gui);
            System.out.println("Buyer: Negotiation window opened for dealer " + dealerName);
        });
    }

    // ── Per-session iterated responder ────────────────────────────────────────

    /**
     * Per-session iterated responder.
     * Uses JADE's Single-Session (SS) Iterated protocol logic.
     */
    private class NegotiationSession extends SSIteratedContractNetResponder {
        private final CompletableFuture<BuyerNegotiationGui> guiFuture;

        NegotiationSession(Agent a, ACLMessage cfp, CompletableFuture<BuyerNegotiationGui> guiFuture) {
            super(a, cfp);
            this.guiFuture = guiFuture;
        }

        /** 
         * Called for EVERY CFP in the iteration (initial + each counter from Dealer).
         * This logic triggers whenever the Dealer sends a new price.
         */
        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            try {
                // Bridge: Wait for the GUI window to be opened and assigned to this session
                BuyerNegotiationGui gui = guiFuture.get(); 

                String[] parts = DemoMessageCodec.decodeFields(cfp.getContent(), 3);
                double dealerPrice = Double.parseDouble(parts[2]);
                
                // Show the Dealer's price in our chat (this also unlocks the local Buyer input)
                gui.addDealerOffer(dealerPrice, "Dealer's Ask"); 

                // Wait for the human user to decide (Accept/Counter/Cancel)
                CompletableFuture<Double> nextOffer = new CompletableFuture<>();
                gui.setOnNegotiationListener(new BuyerNegotiationGui.OnNegotiationListener() {
                    @Override public void onAccept(double currentOffer) { nextOffer.complete(currentOffer); }
                    @Override public void onCounterOffer(double amt)    { nextOffer.complete(amt); }
                    @Override public void onCancel()                     { nextOffer.complete(-1.0); }
                });

                // Blocks the JADE behavior thread until the user clicks a button
                double amount = nextOffer.get();
                if (amount < 0) return cfp.createReply(ACLMessage.REFUSE);

                // Show our own proposal in the chat
                // (Double-message fix: Only the Agent calls addBuyerOffer now)
                gui.addBuyerOffer(amount, "Your Proposal");
                
                ACLMessage propose = cfp.createReply(ACLMessage.PROPOSE);
                String sessionId = cfp.getConversationId();
                propose.setContent(DemoMessageCodec.encodeFields(sessionId, "PROPOSE", Double.toString(amount)));
                return propose;

            } catch (Exception e) {
                e.printStackTrace();
                return cfp.createReply(ACLMessage.NOT_UNDERSTOOD);
            }
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            guiFuture.thenAccept(gui -> {
                String[] parts = DemoMessageCodec.decodeFields(propose.getContent(), 3);
                double finalPrice = Double.parseDouble(parts[2]);
                gui.addSystemMessage("✅ Dealer accepted your offer of RM " + String.format("%,.2f", finalPrice));
                gui.lockNegotiation(true);
            });
            ACLMessage inform = accept.createReply(ACLMessage.INFORM);
            inform.setContent("Deal finalized");
            return inform;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            guiFuture.thenAccept(gui ->
                gui.addSystemMessage("Dealer rejected. Waiting for counter-offer...")
            );
            // NOTE: We don't exit here. SSIteratedContractNetResponder automatically
            // returns to its internal state to wait for the next iteration's CFP.
        }
    }
}
