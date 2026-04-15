package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI Screen 3 (Buyer): Negotiation Screen
 * Chat-style negotiation view showing offer history, current offer,
 * and controls to accept or counter-offer.
 *
 * Usage from BuyerAgent when a negotiation session begins:
 *   BuyerNegotiationGui gui = new BuyerNegotiationGui(agent, listing);
 *   gui.setOnNegotiationListener(new BuyerNegotiationGui.OnNegotiationListener() { ... });
 *   gui.show();
 *
 * Then call gui.addOffer(...) as messages arrive from the Dealer Agent via the Broker.
 */
public class BuyerNegotiationGui extends JFrame {

    // ── Data model ────────────────────────────────────────────────────────
    public static class OfferEntry {
        public enum Side { BUYER, DEALER, SYSTEM }
        public final Side   side;
        public final double amount;
        public final String label;   // e.g. "Counter-offer", "Initial offer"
        public final String time;

        public OfferEntry(Side side, double amount, String label) {
            this.side   = side;
            this.amount = amount;
            this.label  = label;
            this.time   = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        /** For SYSTEM messages (no price) */
        public OfferEntry(String systemMessage) {
            this.side   = Side.SYSTEM;
            this.amount = 0;
            this.label  = systemMessage;
            this.time   = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        }
    }

    public interface OnNegotiationListener {
        void onAccept(double currentOffer);
        void onCounterOffer(double counterAmount);
        void onCancel();
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final Agent myAgent;
    private final BuyerMatchedCarsGui.CarListing listing;
    private final List<OfferEntry> offerHistory = new ArrayList<>();

    private OnNegotiationListener negotiationListener;

    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JLabel currentOfferLabel;
    private JButton acceptButton;
    private JTextField counterField;
    private JButton sendButton;
    private double currentOffer = 0;

    // ── Palette ──────────────────────────────────────────────────────────
    private static final Color BG        = new Color(15, 17, 26);
    private static final Color PANEL_BG  = new Color(22, 26, 40);
    private static final Color CARD_BG   = new Color(28, 34, 52);
    private static final Color ACCENT    = new Color(99, 179, 237);
    private static final Color ACCENT2   = new Color(236, 201, 75);
    private static final Color DANGER    = new Color(245, 101, 101);
    private static final Color SUCCESS   = new Color(72, 187, 120);
    private static final Color DEALER_BUBBLE = new Color(35, 45, 70);
    private static final Color BUYER_BUBBLE  = new Color(30, 60, 50);
    private static final Color TEXT      = new Color(226, 232, 240);
    private static final Color MUTED     = new Color(113, 128, 150);
    private static final Color BORDER    = new Color(45, 55, 80);
    private static final Color FIELD_BG  = new Color(30, 36, 54);

    public BuyerNegotiationGui(Agent agent, BuyerMatchedCarsGui.CarListing listing) {
        super("Buyer — Negotiation: " + listing.brand + " " + listing.type);
        this.myAgent = agent;
        this.listing = listing;
        this.currentOffer = listing.price;  // start with dealer's asking price
        initUI();
    }

    public void setOnNegotiationListener(OnNegotiationListener listener) {
        this.negotiationListener = listener;
    }

    private void initUI() {
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(),       BorderLayout.NORTH);
        add(buildChatArea(),     BorderLayout.CENTER);
        add(buildControlPanel(), BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(
                        BuyerNegotiationGui.this,
                        "Cancel this negotiation?", "Confirm Cancel",
                        JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    if (negotiationListener != null) negotiationListener.onCancel();
                    dispose();
                }
            }
        });

        setSize(500, 600);
        centerOnScreen();
        setResizable(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, ACCENT),
                new EmptyBorder(14, 20, 14, 20)
        ));

        // Car info
        JPanel carInfo = new JPanel();
        carInfo.setLayout(new BoxLayout(carInfo, BoxLayout.Y_AXIS));
        carInfo.setOpaque(false);

        JLabel carLabel = new JLabel(listing.brand + " " + listing.type);
        carLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        carLabel.setForeground(TEXT);

        JLabel dealerLabel = new JLabel("Negotiating with: " + listing.dealerName);
        dealerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dealerLabel.setForeground(MUTED);

        carInfo.add(carLabel);
        carInfo.add(Box.createVerticalStrut(3));
        carInfo.add(dealerLabel);
        header.add(carInfo, BorderLayout.CENTER);

        // Asking price tag
        JLabel askingPrice = new JLabel("Listed: RM " + String.format("%,.0f", listing.price));
        askingPrice.setFont(new Font("Segoe UI", Font.BOLD, 12));
        askingPrice.setForeground(ACCENT2);
        askingPrice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT2, 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));
        header.add(askingPrice, BorderLayout.EAST);

        return header;
    }

    private JScrollPane buildChatArea() {
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(BG);
        chatPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Initial system message
        addSystemEntry("Negotiation started. Dealer's listed price: RM " + String.format("%,.2f", listing.price));

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBackground(BG);
        chatScroll.getViewport().setBackground(BG);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        return chatScroll;
    }

    private JPanel buildControlPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(PANEL_BG);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(2, 0, 0, 0, BORDER),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Current offer display
        JPanel currentOfferPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        currentOfferPanel.setOpaque(false);

        JLabel currentLabel = new JLabel("Current Offer: ");
        currentLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        currentLabel.setForeground(MUTED);

        currentOfferLabel = new JLabel("RM " + String.format("%,.2f", currentOffer));
        currentOfferLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        currentOfferLabel.setForeground(ACCENT);

        currentOfferPanel.add(currentLabel);
        currentOfferPanel.add(currentOfferLabel);

        // Accept button
        acceptButton = new JButton("✓  Accept Offer");
        acceptButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        acceptButton.setBackground(SUCCESS);
        acceptButton.setForeground(Color.WHITE);
        acceptButton.setFocusPainted(false);
        acceptButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        acceptButton.setOpaque(true);
        acceptButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        acceptButton.addActionListener(e -> handleAccept());

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(currentOfferPanel, BorderLayout.WEST);
        topRow.add(acceptButton, BorderLayout.EAST);

        // Counter-offer row
        JPanel counterRow = new JPanel(new BorderLayout(8, 0));
        counterRow.setOpaque(false);
        counterRow.setBorder(new EmptyBorder(8, 0, 0, 0));

        JLabel counterLabel = new JLabel("Make counter-offer (RM):");
        counterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        counterLabel.setForeground(MUTED);
        counterLabel.setPreferredSize(new Dimension(170, 30));

        counterField = new JTextField();
        counterField.setBackground(FIELD_BG);
        counterField.setForeground(TEXT);
        counterField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        counterField.setCaretColor(TEXT);
        counterField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setBackground(ACCENT);
        sendButton.setForeground(new Color(10, 15, 30));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(8, 16, 8, 16));
        sendButton.setOpaque(true);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> handleCounterOffer());
        counterField.addActionListener(e -> handleCounterOffer()); // Enter key

        counterRow.add(counterLabel,  BorderLayout.WEST);
        counterRow.add(counterField,  BorderLayout.CENTER);
        counterRow.add(sendButton,    BorderLayout.EAST);

        wrapper.add(topRow,    BorderLayout.NORTH);
        wrapper.add(counterRow, BorderLayout.SOUTH);

        return wrapper;
    }

    // ── Public API called by the agent ────────────────────────────────────

    /** Called when a dealer sends a new offer to this buyer */
    public void addDealerOffer(double amount, String label) {
        SwingUtilities.invokeLater(() -> {
            this.currentOffer = amount;
            currentOfferLabel.setText("RM " + String.format("%,.2f", amount));
            addBubble(new OfferEntry(OfferEntry.Side.DEALER, amount, label));
        });
    }

    /** Called when buyer successfully sends a counter (reflected in chat) */
    public void addBuyerOffer(double amount, String label) {
        SwingUtilities.invokeLater(() -> addBubble(new OfferEntry(OfferEntry.Side.BUYER, amount, label)));
    }

    /** For status messages like "Dealer accepted", "Negotiation ended" */
    public void addSystemMessage(String message) {
        SwingUtilities.invokeLater(() -> addSystemEntry(message));
    }

    /** Lock the UI once negotiation concludes */
    public void lockNegotiation(boolean accepted) {
        SwingUtilities.invokeLater(() -> {
            acceptButton.setEnabled(false);
            sendButton.setEnabled(false);
            counterField.setEnabled(false);
            if (accepted) {
                addSystemEntry("✅ Deal confirmed at RM " + String.format("%,.2f", currentOffer));
            } else {
                addSystemEntry("❌ Negotiation ended without agreement.");
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void handleAccept() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Accept offer at RM " + String.format("%,.2f", currentOffer) + "?",
                "Confirm Accept", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            if (negotiationListener != null) negotiationListener.onAccept(currentOffer);
            lockNegotiation(true);
        }
    }

    private void handleCounterOffer() {
        String text = counterField.getText().trim();
        if (text.isEmpty()) return;

        double counter;
        try {
            counter = Double.parseDouble(text);
            if (counter <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid price.", "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }

        counterField.setText("");
        addBuyerOffer(counter, "Counter-offer");
        if (negotiationListener != null) negotiationListener.onCounterOffer(counter);
    }

    private void addBubble(OfferEntry entry) {
        boolean isDealer = entry.side == OfferEntry.Side.DEALER;

        JPanel bubbleRow = new JPanel(new FlowLayout(
                isDealer ? FlowLayout.LEFT : FlowLayout.RIGHT, 0, 0));
        bubbleRow.setOpaque(false);
        bubbleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(isDealer ? DEALER_BUBBLE : BUYER_BUBBLE);
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDealer ? ACCENT : SUCCESS, 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));

        JLabel senderLabel = new JLabel(isDealer ? "🏢 " + listing.dealerName : "👤 You");
        senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        senderLabel.setForeground(isDealer ? ACCENT : SUCCESS);

        JLabel typeLabel = new JLabel(entry.label);
        typeLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        typeLabel.setForeground(MUTED);

        JLabel priceLabel = new JLabel("RM " + String.format("%,.2f", entry.amount));
        priceLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        priceLabel.setForeground(TEXT);

        JLabel timeLabel = new JLabel(entry.time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        timeLabel.setForeground(MUTED);

        bubble.add(senderLabel);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(typeLabel);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(priceLabel);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(timeLabel);

        bubbleRow.add(bubble);
        chatPanel.add(bubbleRow);
        chatPanel.add(Box.createVerticalStrut(10));
        chatPanel.revalidate();
        scrollToBottom();
    }

    private void addSystemEntry(String message) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel lbl = new JLabel(message);
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lbl.setForeground(MUTED);
        lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(4, 12, 4, 12)
        ));
        lbl.setBackground(CARD_BG);
        lbl.setOpaque(true);

        row.add(lbl);
        chatPanel.add(row);
        chatPanel.add(Box.createVerticalStrut(8));
        chatPanel.revalidate();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    public void display() {
        pack();
        setSize(500, 600);
        centerOnScreen();
        setVisible(true);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }
}
