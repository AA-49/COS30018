package AutoNego;

/**
 * ============================================================
 *  NegotiationGUI — Usage Guide & Agent Integration Examples
 * ============================================================
 *
 * 7 GUI Screens overview:
 * ┌─────────────────────────────────────────────────────────┐
 * │  BUYER AGENT (BA)                                       │
 * │    Screen 1: BuyerInputGui          – Car search form   │
 * │    Screen 2: BuyerMatchedCarsGui    – Matched deals     │
 * │    Screen 3: BuyerNegotiationGui    – Chat negotiation  │
 * ├─────────────────────────────────────────────────────────┤
 * │  DEALER AGENT (DA)                                      │
 * │    Screen 4: DealerInputGui         – Add car listings  │
 * │    Screen 5: DealerBuyerScreen      – Buyer interests   │
 * │    Screen 6: DealerNegotiationGui   – Chat negotiation  │
 * ├─────────────────────────────────────────────────────────┤
 * │  BROKER AGENT (KA)                                      │
 * │    Screen 7: BrokerDashboardGui     – Live dashboard    │
 * └─────────────────────────────────────────────────────────┘
 *
 * All GUIs follow the same pattern as BookTradingPackage:
 *   - Constructed with the owning Agent as parameter
 *   - Call .show() to display
 *   - Use listener interfaces to react to user actions
 *   - Agent calls update methods (via SwingUtilities) when ACL messages arrive
 */

/*
 * ─────────────────────────────────────────────────────────────
 * EXAMPLE 1: BuyerAgent.java — setup()
 * ─────────────────────────────────────────────────────────────
 *
 * public class BuyerAgent extends Agent {
 *
 *     private BuyerInputGui        inputGui;
 *     private BuyerMatchedCarsGui  matchGui;
 *     private BuyerNegotiationGui  negotiationGui;
 *
 *     protected void setup() {
 *
 *         // ── Screen 1: show the car input form ──────────────────────
 *         inputGui = new BuyerInputGui(this);
 *         inputGui.setOnConfirmListener((brand, type, maxPrice) -> {
 *             // User pressed Confirm → build ACL message to KA
 *             ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
 *             msg.addReceiver(brokerAID);
 *             msg.setContent(brand + "," + type + "," + maxPrice);
 *             msg.setConversationId("car-search");
 *             send(msg);
 *         });
 *         inputGui.show();
 *
 *         // ── Behaviour: wait for broker's match response ─────────────
 *         addBehaviour(new CyclicBehaviour(this) {
 *             public void action() {
 *                 ACLMessage msg = receive(
 *                     MessageTemplate.MatchConversationId("car-search-result"));
 *                 if (msg != null) {
 *                     // Parse listings from msg content
 *                     List<BuyerMatchedCarsGui.CarListing> matches = parseMatches(msg.getContent());
 *
 *                     // ── Screen 2: show matched cars ─────────────────────
 *                     matchGui = new BuyerMatchedCarsGui(myAgent, matches);
 *                     matchGui.setOnActionListener(new BuyerMatchedCarsGui.OnActionListener() {
 *                         public void onNegotiate(BuyerMatchedCarsGui.CarListing listing) {
 *                             // Notify KA that buyer wants to negotiate
 *                             ACLMessage negotiate = new ACLMessage(ACLMessage.PROPOSE);
 *                             negotiate.addReceiver(brokerAID);
 *                             negotiate.setContent("NEGOTIATE," + listing.dealerName);
 *                             negotiate.setConversationId("negotiation-request");
 *                             send(negotiate);
 *
 *                             // ── Screen 3: open negotiation window ──────────
 *                             negotiationGui = new BuyerNegotiationGui(myAgent, listing);
 *                             negotiationGui.setOnNegotiationListener(
 *                                 new BuyerNegotiationGui.OnNegotiationListener() {
 *                                     public void onAccept(double price) {
 *                                         ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
 *                                         accept.addReceiver(brokerAID);
 *                                         accept.setContent("ACCEPT," + price);
 *                                         send(accept);
 *                                     }
 *                                     public void onCounterOffer(double counterPrice) {
 *                                         ACLMessage counter = new ACLMessage(ACLMessage.PROPOSE);
 *                                         counter.addReceiver(brokerAID);
 *                                         counter.setContent("COUNTER," + counterPrice);
 *                                         send(counter);
 *                                     }
 *                                     public void onCancel() {
 *                                         ACLMessage cancel = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
 *                                         cancel.addReceiver(brokerAID);
 *                                         send(cancel);
 *                                     }
 *                                 }
 *                             );
 *                             negotiationGui.show();
 *                         }
 *                         public void onCancel(BuyerMatchedCarsGui.CarListing listing) {
 *                             // user declined — no message needed
 *                         }
 *                     });
 *                     matchGui.show();
 *                 } else {
 *                     block();
 *                 }
 *             }
 *         });
 *
 *         // ── Behaviour: receive dealer offers during negotiation ──────
 *         addBehaviour(new CyclicBehaviour(this) {
 *             public void action() {
 *                 ACLMessage msg = receive(
 *                     MessageTemplate.MatchConversationId("negotiation"));
 *                 if (msg != null && negotiationGui != null) {
 *                     double offer = Double.parseDouble(msg.getContent());
 *                     negotiationGui.addDealerOffer(offer, "Dealer counter-offer");
 *                 } else {
 *                     block();
 *                 }
 *             }
 *         });
 *     }
 * }
 *
 *
 * ─────────────────────────────────────────────────────────────
 * EXAMPLE 2: DealerAgent.java — setup()
 * ─────────────────────────────────────────────────────────────
 *
 * public class DealerAgent extends Agent {
 *
 *     private DealerInputGui        inputGui;
 *     private DealerBuyerScreen     buyerScreen;
 *     private DealerNegotiationGui  negotiationGui;
 *
 *     protected void setup() {
 *
 *         // ── Screen 4: show car listing input ───────────────────────
 *         inputGui = new DealerInputGui(this);
 *         inputGui.setOnListingListener(listings -> {
 *             // Send listings to KA as ACL message
 *             StringBuilder sb = new StringBuilder();
 *             for (DealerInputGui.CarListing l : listings) {
 *                 sb.append(l.brand).append(",").append(l.type)
 *                   .append(",").append(l.price).append(";");
 *             }
 *             ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
 *             msg.addReceiver(brokerAID);
 *             msg.setContent(sb.toString());
 *             msg.setConversationId("dealer-listings");
 *             send(msg);
 *         });
 *         inputGui.show();
 *
 *         // ── Behaviour: receive buyer interests from KA ───────────────
 *         addBehaviour(new CyclicBehaviour(this) {
 *             public void action() {
 *                 ACLMessage msg = receive(
 *                     MessageTemplate.MatchConversationId("buyer-interest"));
 *                 if (msg != null) {
 *                     // Parse buyer info
 *                     String[] parts = msg.getContent().split(",");
 *                     DealerBuyerScreen.BuyerInterest interest =
 *                         new DealerBuyerScreen.BuyerInterest(
 *                             parts[0], parts[1], parts[2], Double.parseDouble(parts[3]));
 *
 *                     if (buyerScreen == null) {
 *                         List<DealerBuyerScreen.BuyerInterest> list = new ArrayList<>();
 *                         list.add(interest);
 *                         // ── Screen 5: show buyer interest screen ───────
 *                         buyerScreen = new DealerBuyerScreen(myAgent, list);
 *                         buyerScreen.setOnActionListener(new DealerBuyerScreen.OnActionListener() {
 *                             public void onNegotiate(DealerBuyerScreen.BuyerInterest bi) {
 *                                 // Tell KA we accept this buyer
 *                                 ACLMessage accept = new ACLMessage(ACLMessage.AGREE);
 *                                 accept.addReceiver(brokerAID);
 *                                 accept.setContent("NEGOTIATE," + bi.buyerName);
 *                                 send(accept);
 *
 *                                 // ── Screen 6: open dealer negotiation window ──
 *                                 negotiationGui = new DealerNegotiationGui(
 *                                     myAgent, bi.buyerName, bi.carBrand, bi.carType,
 *                                     getListedPrice(bi.carBrand, bi.carType));
 *                                 negotiationGui.setOnNegotiationListener(
 *                                     new DealerNegotiationGui.OnNegotiationListener() {
 *                                         public void onAccept(double price) {
 *                                             ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
 *                                             accept.addReceiver(brokerAID);
 *                                             accept.setContent("ACCEPT," + price);
 *                                             send(accept);
 *                                         }
 *                                         public void onCounterOffer(double price) {
 *                                             ACLMessage counter = new ACLMessage(ACLMessage.PROPOSE);
 *                                             counter.addReceiver(brokerAID);
 *                                             counter.setContent("COUNTER," + price);
 *                                             send(counter);
 *                                         }
 *                                         public void onReject() {
 *                                             ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
 *                                             reject.addReceiver(brokerAID);
 *                                             send(reject);
 *                                         }
 *                                     }
 *                                 );
 *                                 negotiationGui.show();
 *                             }
 *                             public void onDecline(DealerBuyerScreen.BuyerInterest bi) {
 *                                 ACLMessage decline = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
 *                                 decline.addReceiver(brokerAID);
 *                                 decline.setContent("DECLINE," + bi.buyerName);
 *                                 send(decline);
 *                             }
 *                         });
 *                         buyerScreen.show();
 *                     } else {
 *                         buyerScreen.addInterest(interest);  // add to existing window
 *                     }
 *                 } else {
 *                     block();
 *                 }
 *             }
 *         });
 *     }
 * }
 *
 *
 * ─────────────────────────────────────────────────────────────
 * EXAMPLE 3: BrokerAgent.java — setup()
 * ─────────────────────────────────────────────────────────────
 *
 * public class BrokerAgent extends Agent {
 *
 *     private BrokerDashboardGui dashboard;
 *
 *     protected void setup() {
 *
 *         // ── Screen 7: show broker dashboard ────────────────────────
 *         dashboard = new BrokerDashboardGui(this);
 *         dashboard.show();
 *
 *         // Register with DF, then add behaviours...
 *
 *         // When a dealer registers a car:
 *         //   dashboard.addListing(new BrokerDashboardGui.DealerListing(
 *         //       "listing-001", "DealerAgent1", "Toyota", "Camry", 95000.0));
 *
 *         // When a negotiation starts:
 *         //   dashboard.addNegotiation(new BrokerDashboardGui.NegotiationSession(
 *         //       "session-001", "BuyerAgent1", "DealerAgent1", "Toyota", "Camry", 80000.0));
 *
 *         // When a counter-offer is sent:
 *         //   dashboard.updateNegotiationStatus("session-001",
 *         //       BrokerDashboardGui.NegotiationStatus.IN_PROGRESS, 87000.0);
 *
 *         // When deal is closed:
 *         //   dashboard.updateNegotiationStatus("session-001",
 *         //       BrokerDashboardGui.NegotiationStatus.DEAL_MADE, 90000.0);
 *         //   dashboard.removeListing("listing-001");
 *     }
 * }
 */
public class GuiUsageGuide {
    // This class is intentionally empty — see comments above.
}
