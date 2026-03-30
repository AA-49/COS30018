package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * GUI Screen 2 (Buyer): Matched Car Dealings Screen
 * Displays a list of matched cars/dealers from the Broker.
 * Buyer can choose to negotiate or cancel each listing.
 *
 * Usage from BuyerAgent (after receiving broker match results):
 *   List<CarListing> matches = ...; // parsed from JADE message
 *   BuyerMatchedCarsGui gui = new BuyerMatchedCarsGui(agent, matches);
 *   gui.setOnActionListener((listing, action) -> { ... });
 *   gui.show();
 */
public class BuyerMatchedCarsGui extends JFrame {

    /** Simple data model for a matched car listing */
    public static class CarListing {
        public final String brand;
        public final String type;
        public final double price;
        public final String dealerName;  // JADE agent name of the dealer

        public CarListing(String brand, String type, double price, String dealerName) {
            this.brand      = brand;
            this.type       = type;
            this.price      = price;
            this.dealerName = dealerName;
        }
    }

    public interface OnActionListener {
        void onNegotiate(CarListing listing);
        void onCancel(CarListing listing);
    }

    private final Agent myAgent;
    private final List<CarListing> listings;
    private OnActionListener actionListener;

    // ── Palette ──────────────────────────────────────────────────────────
    private static final Color BG        = new Color(15, 17, 26);
    private static final Color PANEL_BG  = new Color(22, 26, 40);
    private static final Color CARD_BG   = new Color(28, 34, 52);
    private static final Color ACCENT    = new Color(99, 179, 237);
    private static final Color ACCENT2   = new Color(236, 201, 75);
    private static final Color DANGER    = new Color(245, 101, 101);
    private static final Color SUCCESS   = new Color(72, 187, 120);
    private static final Color TEXT      = new Color(226, 232, 240);
    private static final Color MUTED     = new Color(113, 128, 150);
    private static final Color BORDER    = new Color(45, 55, 80);

    public BuyerMatchedCarsGui(Agent agent, List<CarListing> listings) {
        super("Buyer — Matched Deals");
        this.myAgent  = agent;
        this.listings = listings;
        initUI();
    }

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    private void initUI() {
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildListingsPanel(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setSize(560, Math.min(100 + listings.size() * 120, 620));
        centerOnScreen();
        setResizable(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, ACCENT),
                new EmptyBorder(16, 24, 16, 24)
        ));

        JLabel title = new JLabel("Matched Cars & Dealers");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT);

        String countText = listings.size() + " match" + (listings.size() != 1 ? "es" : "") + " found";
        JLabel count = new JLabel(countText);
        count.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        count.setForeground(listings.isEmpty() ? DANGER : SUCCESS);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(count);
        header.add(titlePanel, BorderLayout.CENTER);

        JLabel agentBadge = new JLabel("● " + myAgent.getLocalName());
        agentBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        agentBadge.setForeground(ACCENT);
        header.add(agentBadge, BorderLayout.EAST);

        return header;
    }

    private JScrollPane buildListingsPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(BG);
        container.setBorder(new EmptyBorder(16, 20, 16, 20));

        if (listings.isEmpty()) {
            JLabel emptyLabel = new JLabel("No matching cars found. Try adjusting your search.");
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            emptyLabel.setForeground(MUTED);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            container.add(Box.createVerticalGlue());
            container.add(emptyLabel);
            container.add(Box.createVerticalGlue());
        } else {
            for (int i = 0; i < listings.size(); i++) {
                container.add(buildCarCard(listings.get(i), i + 1));
                container.add(Box.createVerticalStrut(12));
            }
        }

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildCarCard(CarListing listing, int index) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        // Index badge
        JLabel badge = new JLabel(String.valueOf(index));
        badge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        badge.setForeground(ACCENT);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setPreferredSize(new Dimension(28, 28));
        badge.setBorder(BorderFactory.createLineBorder(ACCENT, 1, true));
        card.add(badge, BorderLayout.WEST);

        // Info panel
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel carName = new JLabel(listing.brand + " " + listing.type);
        carName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        carName.setForeground(TEXT);

        JLabel priceLabel = new JLabel(String.format("RM %,.2f", listing.price));
        priceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        priceLabel.setForeground(ACCENT2);

        JLabel dealerLabel = new JLabel("Dealer: " + listing.dealerName);
        dealerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dealerLabel.setForeground(MUTED);

        info.add(carName);
        info.add(Box.createVerticalStrut(3));
        info.add(priceLabel);
        info.add(Box.createVerticalStrut(3));
        info.add(dealerLabel);
        card.add(info, BorderLayout.CENTER);

        // Action buttons
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.setOpaque(false);

        JButton negotiateBtn = buildActionButton("Negotiate", ACCENT, new Color(10, 15, 30));
        JButton cancelBtn    = buildActionButton("Cancel",    DANGER, Color.WHITE);

        negotiateBtn.addActionListener(e -> {
            if (actionListener != null) actionListener.onNegotiate(listing);
            negotiateBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            negotiateBtn.setText("Connecting...");
        });

        cancelBtn.addActionListener(e -> {
            if (actionListener != null) actionListener.onCancel(listing);
            card.setBackground(new Color(40, 28, 35));
            negotiateBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            cancelBtn.setText("Declined");
        });

        buttons.add(negotiateBtn);
        buttons.add(cancelBtn);
        card.add(buttons, BorderLayout.EAST);

        return card;
    }

    private JButton buildActionButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 30));
        return btn;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.setBackground(PANEL_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(2, 0, 0, 0, BORDER),
                new EmptyBorder(10, 20, 10, 20)
        ));
        JLabel hint = new JLabel("ℹ  You may negotiate with up to 3 dealers simultaneously.");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(MUTED);
        footer.add(hint);
        return footer;
    }

    /** Refreshes the list with a new set of matches (call from EDT) */
    public void updateListings(List<CarListing> newListings) {
        SwingUtilities.invokeLater(() -> {
            listings.clear();
            listings.addAll(newListings);
            getContentPane().removeAll();
            initUI();
            revalidate();
            repaint();
        });
    }

    public void show() {
        pack();
        centerOnScreen();
        setVisible(true);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }
}
