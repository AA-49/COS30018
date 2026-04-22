package AutoNego.FIPAAgents;

import AutoNego.DealerInputGui;
import AutoNego.DealerNegotiationGui;
import AutoNego.DemoMessageCodec;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

/**
 * FIPA Dealer Agent (Initiator)
 * Uses Iterated Contract Net Protocol where Dealer sends CFP and Buyer sends PROPOSE.
 */
public class FipaDealerAgent extends Agent {
    private DealerInputGui inputGui;
    private final Map<String, CompletableFuture<DealerNegotiationGui>> negotiationWindows = new HashMap<>();
    private final Map<String, String> sessionToBuyer = new HashMap<>();
    private final Map<String, String> sessionToListingId = new HashMap<>();
    private final Map<String, Double> sessionToCurrentOffer = new HashMap<>();

    @Override
    protected void setup() {
        inputGui = new DealerInputGui(this);
        inputGui.setOnListingListener(listings -> {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
            msg.setConversationId("dealer-listings");
            msg.setContent(DemoMessageCodec.encodeRecords(listings.stream()
                .map(l -> DemoMessageCodec.encodeFields(l.brand, l.type, Double.toString(l.price)))
                .toArray(String[]::new)));
            send(msg);
        });
        inputGui.show();

        addBehaviour(new MessageRouter());
        System.out.println("FIPA Dealer Agent " + getLocalName() + " is ready.");
    }

    @Override
    protected void takeDown() {
        if (inputGui != null) inputGui.dispose();
        for (CompletableFuture<DealerNegotiationGui> fut : negotiationWindows.values()) {
            fut.thenAccept(gui -> javax.swing.SwingUtilities.invokeLater(gui::dispose));
        }
    }

    private class MessageRouter extends CyclicBehaviour {
        @Override
        public void action() {
            jade.lang.acl.MessageTemplate mt = jade.lang.acl.MessageTemplate.or(
                jade.lang.acl.MessageTemplate.MatchConversationId("buyer-interest"),
                jade.lang.acl.MessageTemplate.MatchConversationId("negotiation-start")
            );
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

    private void handleBuyerInterest(ACLMessage msg) {
        String[] parts = DemoMessageCodec.decodeFields(msg.getContent(), 5);
        String listingId = parts[0];
        String buyerName = parts[1];
        
        int choice = javax.swing.JOptionPane.showConfirmDialog(null,
                "Buyer " + buyerName + " is interested in your car (" + parts[2] + "). Start negotiation?",
                "Buyer Interest", javax.swing.JOptionPane.YES_NO_OPTION);

        ACLMessage reply = new ACLMessage(choice == javax.swing.JOptionPane.YES_OPTION ? ACLMessage.AGREE : ACLMessage.REFUSE);
        reply.addReceiver(new AID("broker", AID.ISLOCALNAME));
        reply.setConversationId("dealer-interest-response");
        reply.setContent(DemoMessageCodec.encodeFields(listingId, buyerName));
        send(reply);
    }

    private void startNegotiationProtocol(ACLMessage startMsg) {
        String[] parts = DemoMessageCodec.decodeFields(startMsg.getContent(), 6);
        String sessionId = parts[0];
        String buyerName = parts[1];
        String brand = parts[2];
        String type = parts[3];
        double initialPrice = Double.parseDouble(parts[4]);
        String listingId = parts[5];

        sessionToBuyer.put(sessionId, buyerName);
        sessionToListingId.put(sessionId, listingId);
        sessionToCurrentOffer.put(sessionId, initialPrice);

        // Bridge Pattern: Use a CompletableFuture to allow JADE agents to "wait" 
        // for the GUI to be initialized on the Swing Thread (EDT) before 
        // processing messages that need to update said GUI.
        CompletableFuture<DealerNegotiationGui> guiFuture = new CompletableFuture<>();
        negotiationWindows.put(sessionId, guiFuture);

        javax.swing.SwingUtilities.invokeLater(() -> {
            DealerNegotiationGui gui = new DealerNegotiationGui(this, buyerName, brand, type, initialPrice);
            gui.show();
            // Start locked: Protocol begins with Dealer CFP, so Dealer must wait for a PROPOSE.
            gui.setWaitingState(true); 
            guiFuture.complete(gui);
            System.out.println("Dealer: Negotiation window opened for buyer " + buyerName);
        });

        // Prepare initial CFP
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
        cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
        cfp.setConversationId(sessionId);
        cfp.setContent(DemoMessageCodec.encodeFields(sessionId, "INITIAL", Double.toString(initialPrice)));

        // Start Initiator Behaviour - It will wait for guiFuture when needed
        addBehaviour(new HagglingInitiator(this, cfp, sessionId, guiFuture));
    }

    private class HagglingInitiator extends ContractNetInitiator {
        private final String sessionId;
        private final CompletableFuture<DealerNegotiationGui> guiFuture;

        public HagglingInitiator(Agent a, ACLMessage cfp, String sessionId, CompletableFuture<DealerNegotiationGui> guiFuture) {
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
                // Ensure the GUI is fully created before we try to signal it
                DealerNegotiationGui gui = guiFuture.get(); 
                
                // Block the JADE thread while we wait for user input from the GUI
                CompletableFuture<String> decision = new CompletableFuture<>();
                gui.setOnNegotiationListener(new DealerNegotiationGui.OnNegotiationListener() {
                    @Override public void onAccept(double currentOffer)     { decision.complete("ACCEPT"); }
                    @Override public void onCounterOffer(double counterAmt) { decision.complete("COUNTER"); }
                    @Override public void onReject()                        { decision.complete("REJECT"); }
                });

                // Update the UI with the incoming Buyer offer (this also unlocks the UI)
                gui.addBuyerOffer(amount, "Buyer Proposal");

                // Wait for the user to click a button in the GUI
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
                    
                    // Show the sent counter in our own UI history
                    // (Double-message fix: Only the Agent calls addDealerOffer now)
                    gui.addDealerOffer(counterPrice, "Counter-asking price");
                    
                    // FIPA Interaction Logic: Reject previous proposal before starting iteration
                    ACLMessage reject = propose.createReply(ACLMessage.REJECT_PROPOSAL);
                    accs.addElement(reject);

                    // Prepare an Iterated CFP for the next round
                    ACLMessage nextCfp = new ACLMessage(ACLMessage.CFP);
                    nextCfp.addReceiver(propose.getSender());
                    nextCfp.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                    nextCfp.setConversationId(sessionId);
                    nextCfp.setContent(DemoMessageCodec.encodeFields(sessionId, "COUNTER", Double.toString(counterPrice)));
                    
                    Vector<ACLMessage> v = new Vector<>();
                    v.add(nextCfp);
                    newIteration(v); // Loops the protocol back to waiting for a proposal
                } else {
                    ACLMessage reject = propose.createReply(ACLMessage.REJECT_PROPOSAL);
                    accs.addElement(reject);
                    gui.lockNegotiation(false);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            // Negotiation concluded successfully
            guiFuture.thenAccept(gui -> {
                gui.addSystemMessage("Deal confirmed and reported to broker.");
                reportDealToBroker(sessionId, sessionToCurrentOffer.get(sessionId));
            });
        }
    }

    private void reportDealToBroker(String sessionId, double finalPrice) {
        String listingId = sessionToListingId.get(sessionId);
        String buyerName = sessionToBuyer.get(sessionId);
        if (listingId == null || buyerName == null) return;

        double commission = finalPrice * 0.05;
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
        msg.setConversationId("deal-completed");
        msg.setContent(DemoMessageCodec.encodeFields(listingId, Double.toString(finalPrice), Double.toString(commission), buyerName));
        send(msg);
    }
}
