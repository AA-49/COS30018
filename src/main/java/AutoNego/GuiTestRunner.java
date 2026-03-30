package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 *  GUI Test Runner — NegotiationGUI
 * ============================================================
 *  Run this class directly (no JADE container needed).
 *  It launches a test menu so you can open and interact with
 *  every screen individually, with simulated agent responses.
 *
 *  HOW TO RUN:
 *    Right-click GuiTestRunner.java → Run 'GuiTestRunner.main()'
 *    OR: javac + java NegotiationGUI.GuiTestRunner
 * ============================================================
 */
public class GuiTestRunner {

    // ── Minimal mock Agent so the GUIs can call getLocalName() etc. ──────
    // We subclass Agent but never start a JADE platform.
    static Agent mockAgent(String name) {
        return new Agent() {
            { _name = new jade.core.AID(name, jade.core.AID.ISLOCALNAME); }
            public String getLocalName() { return name; }
            public jade.core.AID getAID() {
                return new jade.core.AID(name, jade.core.AID.ISLOCALNAME);
            }
            public void doDelete() {
                System.out.println("[MockAgent] doDelete() called on " + name);
            }
        };
    }

    // ── Sample test data ──────────────────────────────────────────────────
    static List<BuyerMatchedCarsGui.CarListing> sampleMatches() {
        return Arrays.asList(
                new BuyerMatchedCarsGui.CarListing("Toyota",  "Camry",   95000.0, "DealerAgent1"),
                new BuyerMatchedCarsGui.CarListing("Honda",   "Accord",  88000.0, "DealerAgent2"),
                new BuyerMatchedCarsGui.CarListing("BMW",     "320i",   185000.0, "DealerAgent3")
        );
    }

    static List<DealerBuyerScreen.BuyerInterest> sampleInterests() {
        return new ArrayList<>(Arrays.asList(
                new DealerBuyerScreen.BuyerInterest("BuyerAgent1", "Toyota", "Camry",  80000.0),
                new DealerBuyerScreen.BuyerInterest("BuyerAgent2", "Toyota", "Camry",  75000.0)
        ));
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SCREEN TESTS
    // ═════════════════════════════════════════════════════════════════════

    // ── Screen 1 ──────────────────────────────────────────────────────────
    static void testBuyerInputGui() {
        Agent agent = mockAgent("BuyerAgent1");
        BuyerInputGui gui = new BuyerInputGui(agent);

        gui.setOnConfirmListener((brand, type, maxPrice) -> {
            System.out.println("[TEST Screen 1] Confirm clicked:");
            System.out.println("  Brand    = " + brand);
            System.out.println("  Type     = " + type);
            System.out.println("  MaxPrice = " + maxPrice);

            // Simulate a short delay then re-enable (as if broker responded)
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(gui::resetForm);
                System.out.println("[TEST Screen 1] Simulated broker response — form re-enabled.");
            }).start();
        });

        gui.show();
        System.out.println("[TEST] Screen 1 opened: BuyerInputGui");
        System.out.println("  → Fill in the form and click 'Search for Deals'");
        System.out.println("  → Check console output for callback values");
        System.out.println("  → Button disables then re-enables after 2 seconds (simulated broker)");
    }

    // ── Screen 2 ──────────────────────────────────────────────────────────
    static void testBuyerMatchedCarsGui() {
        Agent agent = mockAgent("BuyerAgent1");
        BuyerMatchedCarsGui gui = new BuyerMatchedCarsGui(agent, sampleMatches());

        gui.setOnActionListener(new BuyerMatchedCarsGui.OnActionListener() {
            public void onNegotiate(BuyerMatchedCarsGui.CarListing listing) {
                System.out.println("[TEST Screen 2] Negotiate clicked:");
                System.out.println("  Car    = " + listing.brand + " " + listing.type);
                System.out.println("  Price  = RM " + listing.price);
                System.out.println("  Dealer = " + listing.dealerName);

                // Auto-open negotiation screen for the selected car
                SwingUtilities.invokeLater(() -> testBuyerNegotiationGui(listing));
            }
            public void onCancel(BuyerMatchedCarsGui.CarListing listing) {
                System.out.println("[TEST Screen 2] Cancel clicked for: " + listing.brand + " " + listing.type);
            }
        });

        gui.show();
        System.out.println("[TEST] Screen 2 opened: BuyerMatchedCarsGui (3 listings)");
        System.out.println("  → Click 'Negotiate' to auto-open Screen 3");
        System.out.println("  → Click 'Cancel' to log and grey out that card");
    }

    // ── Screen 3 ──────────────────────────────────────────────────────────
    static void testBuyerNegotiationGui(BuyerMatchedCarsGui.CarListing listing) {
        Agent agent = mockAgent("BuyerAgent1");
        BuyerNegotiationGui gui = new BuyerNegotiationGui(agent, listing);

        gui.setOnNegotiationListener(new BuyerNegotiationGui.OnNegotiationListener() {
            public void onAccept(double price) {
                System.out.println("[TEST Screen 3] ACCEPT at RM " + price);
            }
            public void onCounterOffer(double counter) {
                System.out.println("[TEST Screen 3] Counter-offer sent: RM " + counter);
                // Simulate dealer responding after 1.5 seconds
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    double dealerCounter = counter * 1.05; // dealer bumps up 5%
                    SwingUtilities.invokeLater(() -> {
                        gui.addDealerOffer(dealerCounter, "Dealer counter-offer");
                        System.out.println("[TEST Screen 3] Simulated dealer counter: RM " + dealerCounter);
                    });
                }).start();
            }
            public void onCancel() {
                System.out.println("[TEST Screen 3] Negotiation cancelled by buyer.");
            }
        });

        // Simulate dealer sending an initial offer after 1 second
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.addDealerOffer(listing.price * 0.97, "Initial offer (3% discount)");
                System.out.println("[TEST Screen 3] Simulated dealer initial offer.");
            });
        }).start();

        gui.show();
        System.out.println("[TEST] Screen 3 opened: BuyerNegotiationGui");
        System.out.println("  → Dealer sends an auto offer in 1 second");
        System.out.println("  → Enter a counter-offer and press Send (or Enter)");
        System.out.println("  → Dealer auto-counters back at +5% in 1.5 seconds");
        System.out.println("  → Click 'Accept Offer' to close the deal");
    }

    static void testBuyerNegotiationGuiStandalone() {
        BuyerMatchedCarsGui.CarListing listing =
                new BuyerMatchedCarsGui.CarListing("Toyota", "Camry", 95000.0, "DealerAgent1");
        testBuyerNegotiationGui(listing);
    }

    // ── Screen 4 ──────────────────────────────────────────────────────────
    static void testDealerInputGui() {
        Agent agent = mockAgent("DealerAgent1");
        DealerInputGui gui = new DealerInputGui(agent);

        // Pre-populate with sample data
        gui.addListing("Toyota",  "Camry",   95000.0);
        gui.addListing("Honda",   "HR-V",    112000.0);

        gui.setOnListingListener(listings -> {
            System.out.println("[TEST Screen 4] Submit listings clicked. Count: " + listings.size());
            for (DealerInputGui.CarListing l : listings) {
                System.out.printf("  → %-10s %-10s  RM %,.2f%n", l.brand, l.type, l.price);
            }
        });

        gui.show();
        System.out.println("[TEST] Screen 4 opened: DealerInputGui (2 pre-loaded)");
        System.out.println("  → Add more cars using the left form");
        System.out.println("  → Click ✕ to remove a row");
        System.out.println("  → Click 'Send Listings to Broker' to log all entries");
    }

    // ── Screen 5 ──────────────────────────────────────────────────────────
    static void testDealerBuyerScreen() {
        Agent agent = mockAgent("DealerAgent1");
        DealerBuyerScreen gui = new DealerBuyerScreen(agent, sampleInterests());

        gui.setOnActionListener(new DealerBuyerScreen.OnActionListener() {
            public void onNegotiate(DealerBuyerScreen.BuyerInterest interest) {
                System.out.println("[TEST Screen 5] Negotiate with: " + interest.buyerName);
                System.out.println("  Car   = " + interest.carBrand + " " + interest.carType);
                System.out.println("  Offer = RM " + interest.buyerInitialOffer);
                // Auto-open dealer negotiation screen
                SwingUtilities.invokeLater(() ->
                        testDealerNegotiationGui(interest.buyerName,
                                interest.carBrand, interest.carType, 95000.0));
            }
            public void onDecline(DealerBuyerScreen.BuyerInterest interest) {
                System.out.println("[TEST Screen 5] Declined buyer: " + interest.buyerName);
            }
        });

        // Simulate a new buyer arriving after 3 seconds
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.addInterest(new DealerBuyerScreen.BuyerInterest(
                        "BuyerAgent3", "Toyota", "Camry", 72000.0));
                System.out.println("[TEST Screen 5] New buyer arrived dynamically!");
            });
        }).start();

        gui.show();
        System.out.println("[TEST] Screen 5 opened: DealerBuyerScreen (2 buyers)");
        System.out.println("  → A 3rd buyer will appear automatically in 3 seconds");
        System.out.println("  → Click 'Negotiate' to open Screen 6");
        System.out.println("  → Click 'Decline' to reject the buyer");
    }

    // ── Screen 6 ──────────────────────────────────────────────────────────
    static void testDealerNegotiationGui(String buyerName,
                                         String brand, String type, double askingPrice) {
        Agent agent = mockAgent("DealerAgent1");
        DealerNegotiationGui gui = new DealerNegotiationGui(agent, buyerName, brand, type, askingPrice);

        gui.setOnNegotiationListener(new DealerNegotiationGui.OnNegotiationListener() {
            public void onAccept(double price) {
                System.out.println("[TEST Screen 6] ACCEPT at RM " + price);
            }
            public void onCounterOffer(double counter) {
                System.out.println("[TEST Screen 6] Counter-offer sent: RM " + counter);
                // Simulate buyer countering back in 1.5 seconds
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    double buyerCounter = counter * 0.97; // buyer drops 3%
                    SwingUtilities.invokeLater(() -> {
                        gui.addBuyerOffer(buyerCounter, "Buyer counter-offer");
                        System.out.println("[TEST Screen 6] Simulated buyer counter: RM " + buyerCounter);
                    });
                }).start();
            }
            public void onReject() {
                System.out.println("[TEST Screen 6] Negotiation rejected by dealer.");
            }
        });

        // Simulate buyer sending first offer after 1 second
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.addBuyerOffer(askingPrice * 0.85, "Buyer's initial offer");
                System.out.println("[TEST Screen 6] Simulated buyer initial offer.");
            });
        }).start();

        gui.show();
        System.out.println("[TEST] Screen 6 opened: DealerNegotiationGui");
        System.out.println("  → Buyer sends an auto offer in 1 second");
        System.out.println("  → Send a counter to see back-and-forth simulation");
    }

    static void testDealerNegotiationGuiStandalone() {
        testDealerNegotiationGui("BuyerAgent1", "Toyota", "Camry", 95000.0);
    }

    // ── Screen 7 ──────────────────────────────────────────────────────────
    static void testBrokerDashboardGui() {
        Agent agent = mockAgent("BrokerAgent");
        BrokerDashboardGui gui = new BrokerDashboardGui(agent);
        gui.show();
        System.out.println("[TEST] Screen 7 opened: BrokerDashboardGui");
        System.out.println("  → Listings and negotiations will populate automatically...");

        // Simulate dealers registering cars one by one
        new Thread(() -> {
            String[][] cars = {
                    {"listing-001", "DealerAgent1", "Toyota",  "Camry",   "95000"},
                    {"listing-002", "DealerAgent2", "Honda",   "Accord",  "88000"},
                    {"listing-003", "DealerAgent1", "Toyota",  "RAV4",   "145000"},
                    {"listing-004", "DealerAgent3", "BMW",     "320i",   "185000"},
                    {"listing-005", "DealerAgent2", "Honda",   "HR-V",   "112000"},
            };
            for (String[] c : cars) {
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() ->
                        gui.addListing(new BrokerDashboardGui.DealerListing(
                                c[0], c[1], c[2], c[3], Double.parseDouble(c[4]))));
                System.out.println("[TEST Screen 7] Listing added: " + c[2] + " " + c[3]);
            }

            // Simulate negotiations starting
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.addNegotiation(new BrokerDashboardGui.NegotiationSession(
                        "session-001", "BuyerAgent1", "DealerAgent1", "Toyota", "Camry", 80000.0));
                System.out.println("[TEST Screen 7] Negotiation 1 started.");
            });

            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.addNegotiation(new BrokerDashboardGui.NegotiationSession(
                        "session-002", "BuyerAgent2", "DealerAgent2", "Honda", "Accord", 75000.0));
                System.out.println("[TEST Screen 7] Negotiation 2 started.");
            });

            // Simulate back-and-forth counter offers
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.updateNegotiationStatus("session-001",
                        BrokerDashboardGui.NegotiationStatus.IN_PROGRESS, 87000.0);
                System.out.println("[TEST Screen 7] Session 1 counter-offer updated.");
            });

            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.updateNegotiationStatus("session-001",
                        BrokerDashboardGui.NegotiationStatus.DEAL_MADE, 91000.0);
                gui.removeListing("listing-001");
                System.out.println("[TEST Screen 7] Session 1 DEAL MADE — listing removed.");
            });

            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                gui.updateNegotiationStatus("session-002",
                        BrokerDashboardGui.NegotiationStatus.FAILED, 75000.0);
                System.out.println("[TEST Screen 7] Session 2 FAILED.");
            });
        }).start();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  MAIN TEST MENU
    // ═════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // Dark theme for test menu
            Color BG      = new Color(15, 17, 26);
            Color PANEL   = new Color(22, 26, 40);
            Color ACCENT  = new Color(99, 179, 237);
            Color TEXT    = new Color(226, 232, 240);
            Color MUTED   = new Color(113, 128, 150);
            Color BORDER  = new Color(45, 55, 80);

            JFrame menu = new JFrame("NegotiationGUI — Test Runner");
            menu.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            menu.setLayout(new BorderLayout());
            menu.getContentPane().setBackground(BG);

            // Header
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(PANEL);
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0,0,2,0,ACCENT),
                    BorderFactory.createEmptyBorder(16,20,16,20)));
            JLabel title = new JLabel("GUI Test Runner");
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            title.setForeground(TEXT);
            JLabel sub = new JLabel("Click a button to open and test each screen independently");
            sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            sub.setForeground(MUTED);
            JPanel tp = new JPanel(); tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS)); tp.setOpaque(false);
            tp.add(title); tp.add(Box.createVerticalStrut(3)); tp.add(sub);
            header.add(tp);
            menu.add(header, BorderLayout.NORTH);

            // Buttons panel
            JPanel grid = new JPanel(new GridLayout(0, 1, 0, 8));
            grid.setBackground(BG);
            grid.setBorder(BorderFactory.createEmptyBorder(20, 24, 8, 24));

            Object[][] screens = {
                    {"BUYER AGENT", null, null},
                    {"Screen 1 — Buyer: Car Input Form",           ACCENT, (Runnable) GuiTestRunner::testBuyerInputGui},
                    {"Screen 2 — Buyer: Matched Cars List",        ACCENT, (Runnable) GuiTestRunner::testBuyerMatchedCarsGui},
                    {"Screen 3 — Buyer: Negotiation (standalone)", ACCENT, (Runnable) GuiTestRunner::testBuyerNegotiationGuiStandalone},
                    {"DEALER AGENT", null, null},
                    {"Screen 4 — Dealer: Car Listing Input",       new Color(167,139,250), (Runnable) GuiTestRunner::testDealerInputGui},
                    {"Screen 5 — Dealer: Interested Buyers",       new Color(167,139,250), (Runnable) GuiTestRunner::testDealerBuyerScreen},
                    {"Screen 6 — Dealer: Negotiation (standalone)",new Color(167,139,250), (Runnable) GuiTestRunner::testDealerNegotiationGuiStandalone},
                    {"BROKER AGENT", null, null},
                    {"Screen 7 — Broker: Live Dashboard",          new Color(56,189,190),  (Runnable) GuiTestRunner::testBrokerDashboardGui},
                    {"ALL SCREENS", null, null},
                    {"▶  Launch All 7 Screens at Once",            new Color(236,201,75),  (Runnable) () -> {
                        testBuyerInputGui();
                        testBuyerMatchedCarsGui();
                        testBuyerNegotiationGuiStandalone();
                        testDealerInputGui();
                        testDealerBuyerScreen();
                        testDealerNegotiationGuiStandalone();
                        testBrokerDashboardGui();
                    }},
            };

            for (Object[] row : screens) {
                String label = (String) row[0];
                Color  color = (Color)  row[1];
                Runnable action = (Runnable) row[2];

                if (action == null) {
                    // Section separator label
                    JLabel sep = new JLabel("  " + label);
                    sep.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    sep.setForeground(MUTED);
                    sep.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(1,0,0,0,BORDER),
                            BorderFactory.createEmptyBorder(10,0,2,0)));
                    grid.add(sep);
                } else {
                    JButton btn = new JButton(label);
                    btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    btn.setBackground(PANEL);
                    btn.setForeground(color);
                    btn.setFocusPainted(false);
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(color, 1, true),
                            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
                    btn.setOpaque(true);
                    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    btn.setHorizontalAlignment(SwingConstants.LEFT);
                    Color hoverBg = new Color(
                            Math.min(color.getRed()+30, 255),
                            Math.min(color.getGreen()+30, 255),
                            Math.min(color.getBlue()+30, 255));
                    btn.addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) {
                            btn.setBackground(new Color(hoverBg.getRed(), hoverBg.getGreen(), hoverBg.getBlue(), 40));
                        }
                        public void mouseExited(MouseEvent e) { btn.setBackground(PANEL); }
                    });
                    Runnable finalAction = action;
                    btn.addActionListener(e -> finalAction.run());
                    grid.add(btn);
                }
            }

            // Console output area
            JTextArea console = new JTextArea(8, 50);
            console.setEditable(false);
            console.setBackground(new Color(10, 12, 20));
            console.setForeground(new Color(150, 255, 150));
            console.setFont(new Font("Monospaced", Font.PLAIN, 11));
            console.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            console.setText("Console output:\n");

            // Redirect System.out to the console area
            java.io.PrintStream ps = new java.io.PrintStream(System.out) {
                public void println(String x) {
                    super.println(x);
                    SwingUtilities.invokeLater(() -> {
                        console.append(x + "\n");
                        console.setCaretPosition(console.getDocument().getLength());
                    });
                }
            };
            System.setOut(ps);

            JScrollPane consoleScroll = new JScrollPane(console);
            consoleScroll.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2,0,0,0,BORDER),
                    BorderFactory.createEmptyBorder(0,0,0,0)));

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setBackground(BG);
            JLabel consoleLabel = new JLabel("  Console Output");
            consoleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            consoleLabel.setForeground(MUTED);
            consoleLabel.setBorder(BorderFactory.createEmptyBorder(6,20,4,0));
            bottom.add(consoleLabel, BorderLayout.NORTH);
            bottom.add(consoleScroll, BorderLayout.CENTER);

            menu.add(new JScrollPane(grid) {{
                setBorder(null);
                getViewport().setBackground(BG);
                setBackground(BG);
            }}, BorderLayout.CENTER);
            menu.add(bottom, BorderLayout.SOUTH);

            menu.setSize(600, 780);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            menu.setLocation((screen.width - 600) / 2, (screen.height - 780) / 2);
            menu.setVisible(true);

            System.out.println("Test Runner ready. Click any button above to open a screen.");
        });
    }
}