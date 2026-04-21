package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI Screen 7 (Broker / KA): Dashboard
 * Visualizes two live panels:
 *   Left  – All dealer listings registered with the Broker
 *   Right – Ongoing negotiations being facilitated by the Broker
 *
 * This is a READ-ONLY visualization dashboard; the Broker agent drives all data.
 *
 * Usage from BrokerAgent.setup():
 *   BrokerDashboardGui dashboard = new BrokerDashboardGui(this);
 *   dashboard.display();
 *
 * Then call:
 *   dashboard.addListing(...)          – when a DA registers a car
 *   dashboard.addNegotiation(...)      – when a negotiation pair starts
 *   dashboard.updateNegotiationStatus(...) – when status changes
 *   dashboard.removeListing(...)       – when a car is sold
 */
public class BrokerDashboardGui extends JFrame {

    // ── Data models ───────────────────────────────────────────────────────
    public static class DealerListing {
        public final String listingId;   // unique key for updates/removal
        public final String dealerName;
        public final String carBrand;
        public final String carType;
        public final double price;

        public DealerListing(String listingId, String dealerName,
                             String carBrand, String carType, double price) {
            this.listingId  = listingId;
            this.dealerName = dealerName;
            this.carBrand   = carBrand;
            this.carType    = carType;
            this.price      = price;
        }
    }

    public enum NegotiationStatus { IN_PROGRESS, DEAL_MADE, FAILED }

    public static class NegotiationSession {
        public final String sessionId;
        public final String buyerName;
        public final String dealerName;
        public final String carBrand;
        public final String carType;
        public NegotiationStatus status;
        public double latestOffer;
        public String lastUpdated;

        public NegotiationSession(String sessionId, String buyerName, String dealerName,
                                  String carBrand, String carType, double initialOffer) {
            this.sessionId   = sessionId;
            this.buyerName   = buyerName;
            this.dealerName  = dealerName;
            this.carBrand    = carBrand;
            this.carType     = carType;
            this.status      = NegotiationStatus.IN_PROGRESS;
            this.latestOffer = initialOffer;
            this.lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }

    // ── State ─────────────────────────────────────────────────────────────
    private final Agent myAgent;
    private final List<DealerListing>     listings     = new ArrayList<>();
    private final List<NegotiationSession> negotiations = new ArrayList<>();

    private DefaultTableModel listingModel;
    private DefaultTableModel negotiationModel;
    private JLabel listingCountLabel;
    private JLabel negotiationCountLabel;
    private JLabel activeNegLabel;

    // ── Palette (teal/cyan — neutral broker theme) ────────────────────────
    private static final Color BG        = new Color(10, 20, 22);
    private static final Color PANEL_BG  = new Color(14, 28, 32);
    private static final Color CARD_BG   = new Color(18, 36, 40);
    private static final Color ACCENT    = new Color(56, 189, 190);   // teal
    private static final Color ACCENT2   = new Color(45, 212, 191);   // cyan-green
    private static final Color SUCCESS   = new Color(72, 187, 120);
    private static final Color DANGER    = new Color(245, 101, 101);
    private static final Color WARN      = new Color(236, 201, 75);
    private static final Color TEXT      = new Color(220, 248, 248);
    private static final Color MUTED     = new Color(100, 160, 165);
    private static final Color BORDER    = new Color(30, 65, 70);
    private static final Color TABLE_HDR = new Color(20, 48, 54);
    private static final Color ROW_ALT   = new Color(15, 32, 36);

    public BrokerDashboardGui(Agent agent) {
        super("Broker (KA) — Live Dashboard");
        this.myAgent = agent;
        initUI();
    }

    private void initUI() {
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(),        BorderLayout.NORTH);
        add(buildMainContent(),   BorderLayout.CENTER);
        add(buildStatusBar(),     BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setSize(1000, 600);
        centerOnScreen();
        setResizable(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, ACCENT),
                new EmptyBorder(14, 24, 14, 24)
        ));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel title = new JLabel("Broker Dashboard");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT);

        JLabel sub = new JLabel("Monitoring dealer listings and active negotiations in real-time");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(MUTED);

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(3));
        titlePanel.add(sub);
        header.add(titlePanel, BorderLayout.CENTER);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        stats.setOpaque(false);

        listingCountLabel     = buildStatLabel("0 Listings",     ACCENT);
        activeNegLabel        = buildStatLabel("0 Active",        WARN);
        negotiationCountLabel = buildStatLabel("0 Total Sessions", MUTED);

        stats.add(listingCountLabel);
        stats.add(buildSeparator());
        stats.add(activeNegLabel);
        stats.add(buildSeparator());
        stats.add(negotiationCountLabel);

        JLabel agentBadge = new JLabel("● " + myAgent.getLocalName());
        agentBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        agentBadge.setForeground(ACCENT2);
        agentBadge.setBorder(new EmptyBorder(0, 16, 0, 0));
        stats.add(agentBadge);

        header.add(stats, BorderLayout.EAST);
        return header;
    }

    private JLabel buildStatLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(color);
        return lbl;
    }

    private JSeparator buildSeparator() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setForeground(BORDER);
        sep.setPreferredSize(new Dimension(1, 16));
        return sep;
    }

    private JSplitPane buildMainContent() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildListingsPanel(), buildNegotiationPanel());
        split.setDividerLocation(480);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(4);
        return split;
    }

    // ── Dealer Listings Panel (left) ──────────────────────────────────────
    private JPanel buildListingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(16, 16, 16, 8));

        // Section header
        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setOpaque(false);
        sectionHeader.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel sectionTitle = new JLabel("Dealer Listings");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sectionTitle.setForeground(ACCENT);

        JLabel sectionSub = new JLabel("Cars available on the platform");
        sectionSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sectionSub.setForeground(MUTED);

        JPanel stp = new JPanel();
        stp.setLayout(new BoxLayout(stp, BoxLayout.Y_AXIS));
        stp.setOpaque(false);
        stp.add(sectionTitle);
        stp.add(Box.createVerticalStrut(2));
        stp.add(sectionSub);
        sectionHeader.add(stp, BorderLayout.CENTER);

        panel.add(sectionHeader, BorderLayout.NORTH);

        // Table
        String[] cols = {"Dealer", "Brand", "Type", "Price (RM)"};
        listingModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = buildStyledTable(listingModel);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);

        JScrollPane scroll = wrapInScroll(table);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ── Negotiation Panel (right) ─────────────────────────────────────────
    private JPanel buildNegotiationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(16, 8, 16, 16));

        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setOpaque(false);
        sectionHeader.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel sectionTitle = new JLabel("Ongoing Negotiations");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sectionTitle.setForeground(ACCENT2);

        JLabel sectionSub = new JLabel("Between Buyer Agents and Dealer Agents");
        sectionSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sectionSub.setForeground(MUTED);

        JPanel stp = new JPanel();
        stp.setLayout(new BoxLayout(stp, BoxLayout.Y_AXIS));
        stp.setOpaque(false);
        stp.add(sectionTitle);
        stp.add(Box.createVerticalStrut(2));
        stp.add(sectionSub);
        sectionHeader.add(stp, BorderLayout.CENTER);

        panel.add(sectionHeader, BorderLayout.NORTH);

        String[] cols = {"Buyer", "Dealer", "Car", "Latest Offer", "Status", "Updated"};
        negotiationModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = buildStyledTable(negotiationModel);

        // Custom cell renderer for Status column (index 4)
        table.getColumnModel().getColumn(4).setCellRenderer((tbl, value, sel, foc, row, col) -> {
            JLabel lbl = new JLabel(value != null ? value.toString() : "");
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lbl.setOpaque(true);
            lbl.setBorder(new EmptyBorder(2, 8, 2, 8));
            lbl.setHorizontalAlignment(SwingConstants.CENTER);

            if ("In Progress".equals(value)) {
                lbl.setBackground(new Color(30, 50, 26));
                lbl.setForeground(SUCCESS);
            } else if ("Deal Made".equals(value)) {
                lbl.setBackground(new Color(22, 40, 55));
                lbl.setForeground(new Color(99, 179, 237));
            } else {
                lbl.setBackground(new Color(50, 22, 22));
                lbl.setForeground(DANGER);
            }
            return lbl;
        });

        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);

        JScrollPane scroll = wrapInScroll(table);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JTable buildStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? CARD_BG : ROW_ALT);
                }
                return c;
            }
        };
        table.setBackground(CARD_BG);
        table.setForeground(TEXT);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setSelectionBackground(new Color(20, 60, 65));
        table.setSelectionForeground(TEXT);

        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HDR);
        header.setForeground(MUTED);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBorder(new MatteBorder(0, 0, 2, 0, BORDER));

        return table;
    }

    private JScrollPane wrapInScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        bar.setBackground(new Color(8, 18, 20));
        bar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JLabel dot = new JLabel("●");
        dot.setForeground(ACCENT2);
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        JLabel status = new JLabel("Broker online — Listening for dealer registrations and buyer requests");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        status.setForeground(MUTED);

        bar.add(dot);
        bar.add(status);
        return bar;
    }

    // ── Public API (called by BrokerAgent from behaviour threads) ─────────

    /** Register a new dealer car listing */
    public void addListing(DealerListing listing) {
        SwingUtilities.invokeLater(() -> {
            listings.add(listing);
            listingModel.addRow(new Object[]{
                    listing.dealerName, listing.carBrand, listing.carType,
                    String.format("%,.2f", listing.price)
            });
            updateStats();
        });
    }

    /** Remove a listing when the car is sold */
    public void removeListing(String listingId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < listings.size(); i++) {
                if (listings.get(i).listingId.equals(listingId)) {
                    listings.remove(i);
                    listingModel.removeRow(i);
                    break;
                }
            }
            updateStats();
        });
    }

    /** Add a new negotiation session */
    public void addNegotiation(NegotiationSession session) {
        SwingUtilities.invokeLater(() -> {
            negotiations.add(session);
            negotiationModel.addRow(new Object[]{
                    session.buyerName, session.dealerName,
                    session.carBrand + " " + session.carType,
                    "RM " + String.format("%,.0f", session.latestOffer),
                    "In Progress",
                    session.lastUpdated
            });
            updateStats();
        });
    }

    /** Update a session's status and latest offer */
    public void updateNegotiationStatus(String sessionId, NegotiationStatus status, double latestOffer) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < negotiations.size(); i++) {
                NegotiationSession s = negotiations.get(i);
                if (s.sessionId.equals(sessionId)) {
                    s.status      = status;
                    s.latestOffer = latestOffer;
                    s.lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                    String statusStr;
                    switch (status) {
                        case IN_PROGRESS:
                            statusStr = "In Progress";
                            break;
                        case DEAL_MADE:
                            statusStr = "Deal Made";
                            break;
                        case FAILED:
                            statusStr = "Failed";
                            break;
                        default:
                            statusStr = "Unknown";
                            break;
                    }

                    negotiationModel.setValueAt("RM " + String.format("%,.0f", latestOffer), i, 3);
                    negotiationModel.setValueAt(statusStr, i, 4);
                    negotiationModel.setValueAt(s.lastUpdated, i, 5);
                    break;
                }
            }
            updateStats();
        });
    }

    private void updateStats() {
        long active = negotiations.stream()
                .filter(n -> n.status == NegotiationStatus.IN_PROGRESS).count();
        listingCountLabel.setText(listings.size() + " Listing" + (listings.size() != 1 ? "s" : ""));
        activeNegLabel.setText(active + " Active");
        negotiationCountLabel.setText(negotiations.size() + " Total Session" + (negotiations.size() != 1 ? "s" : ""));
    }

    public void display() {
        pack();
        setSize(1000, 600);
        centerOnScreen();
        setVisible(true);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }
}
