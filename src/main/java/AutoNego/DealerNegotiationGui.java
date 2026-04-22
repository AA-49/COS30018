package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * GUI Screen 6 (Dealer): Negotiation Screen
 * Chat-style offer history, current offer display, accept and counter-offer controls.
 * Mirrors BuyerNegotiationGui but from the dealer's perspective.
 *
 * Usage from DealerAgent when negotiation with a buyer begins:
 *   DealerNegotiationGui gui = new DealerNegotiationGui(agent, buyerName, carBrand, carType, askingPrice);
 *   gui.setOnNegotiationListener(new DealerNegotiationGui.OnNegotiationListener() { ... });
 *   gui.show();
 *   // Then call gui.addBuyerOffer(...) when buyer's ACL messages arrive.
 */
public class DealerNegotiationGui extends JFrame {

    /**
     * Interface to pass user actions (Accept, Counter, Reject) from the GUI 
     * back to the Agent.
     */
    public interface OnNegotiationListener {
        void onAccept(double currentOffer);
        void onCounterOffer(double counterAmount);
        void onReject();
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final Agent  myAgent;
    private final String buyerName;
    private final String carBrand;
    private final String carType;
    private final double askingPrice;

    private OnNegotiationListener negotiationListener;

    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JLabel currentOfferLabel;
    private JButton acceptButton;
    private JButton exitButton;
    private JTextField counterField;
    private JButton sendButton;
    private double currentOffer;

    // ── Palette (dealer theme: warm amber/gold) ───────────────────────────
    private static final Color BG           = new Color(18, 14, 10);
    private static final Color PANEL_BG     = new Color(26, 20, 14);
    private static final Color CARD_BG      = new Color(34, 26, 18);
    private static final Color ACCENT       = new Color(251, 191, 36);  // gold
    private static final Color ACCENT2      = new Color(249, 115, 22);  // orange
    private static final Color SUCCESS      = new Color(72, 187, 120);
    private static final Color DANGER       = new Color(245, 101, 101);
    private static final Color DEALER_BUBBLE = new Color(50, 38, 18);
    private static final Color BUYER_BUBBLE  = new Color(22, 36, 50);
    private static final Color TEXT         = new Color(255, 247, 230);
    private static final Color MUTED        = new Color(160, 140, 100);
    private static final Color BORDER       = new Color(70, 52, 30);
    private static final Color FIELD_BG     = new Color(38, 30, 18);

    public DealerNegotiationGui(Agent agent, String buyerName,
                                String carBrand, String carType, double askingPrice) {
        super("Dealer — Negotiation: " + carBrand + " " + carType + " ↔ " + buyerName);
        this.myAgent     = agent;
        this.buyerName   = buyerName;
        this.carBrand    = carBrand;
        this.carType     = carType;
        this.askingPrice = askingPrice;
        this.currentOffer = 0;
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
                if (exitButton != null && exitButton.isEnabled()) {
                    dispose();
                    return;
                }
                JOptionPane.showMessageDialog(
                        DealerNegotiationGui.this,
                        "You can close this chat after the negotiation ends.",
                        "Negotiation In Progress",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });

        setSize(520, 600);
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

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel carLabel = new JLabel(carBrand + " " + carType);
        carLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        carLabel.setForeground(TEXT);

        JLabel buyerLabel = new JLabel("Buyer: " + buyerName);
        buyerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        buyerLabel.setForeground(MUTED);

        info.add(carLabel);
        info.add(Box.createVerticalStrut(3));
        info.add(buyerLabel);
        header.add(info, BorderLayout.CENTER);

        JLabel askingTag = new JLabel("Your Ask: RM " + String.format("%,.0f", askingPrice));
        askingTag.setFont(new Font("Segoe UI", Font.BOLD, 12));
        askingTag.setForeground(ACCENT);
        askingTag.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));
        header.add(askingTag, BorderLayout.EAST);

        return header;
    }

    private JScrollPane buildChatArea() {
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(BG);
        chatPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        addSystemEntry("Negotiation started. Your asking price: RM " + String.format("%,.2f", askingPrice));

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

        // Current offer row
        JPanel offerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        offerRow.setOpaque(false);

        JLabel currentLabel = new JLabel("Buyer's Offer: ");
        currentLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        currentLabel.setForeground(MUTED);

        currentOfferLabel = new JLabel("Waiting for buyer's offer...");
        currentOfferLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        currentOfferLabel.setForeground(ACCENT);

        offerRow.add(currentLabel);
        offerRow.add(currentOfferLabel);

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

        exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        exitButton.setBackground(BORDER);
        exitButton.setForeground(TEXT);
        exitButton.setFocusPainted(false);
        exitButton.setBorder(new EmptyBorder(8, 16, 8, 16));
        exitButton.setOpaque(true);
        exitButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exitButton.setEnabled(false);
        exitButton.addActionListener(e -> dispose());

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);
        actionButtons.add(acceptButton);
        actionButtons.add(exitButton);

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(offerRow,     BorderLayout.WEST);
        topRow.add(actionButtons, BorderLayout.EAST);

        // Counter-offer row
        JPanel counterRow = new JPanel(new BorderLayout(8, 0));
        counterRow.setOpaque(false);
        counterRow.setBorder(new EmptyBorder(8, 0, 0, 0));

        JLabel counterLabel = new JLabel("Counter-offer (RM):");
        counterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        counterLabel.setForeground(MUTED);
        counterLabel.setPreferredSize(new Dimension(150, 30));

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
        sendButton.setForeground(new Color(18, 14, 10));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(8, 16, 8, 16));
        sendButton.setOpaque(true);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> handleCounterOffer());
        counterField.addActionListener(e -> handleCounterOffer());

        counterRow.add(counterLabel, BorderLayout.WEST);
        counterRow.add(counterField, BorderLayout.CENTER);
        counterRow.add(sendButton,   BorderLayout.EAST);

        wrapper.add(topRow,    BorderLayout.NORTH);
        wrapper.add(counterRow, BorderLayout.SOUTH);

        return wrapper;
    }

    // ── Public API called by the agent ────────────────────────────────────

    /** 
     * Called when buyer sends a new offer (from JADE message processing).
     * This method re-enables the UI so the dealer can now respond.
     */
    public void addBuyerOffer(double amount, String label) {
        SwingUtilities.invokeLater(() -> {
            this.currentOffer = amount;
            currentOfferLabel.setText("RM " + String.format("%,.2f", amount));
            addBubble(amount, label, false);
            // Critical Fix: Unlock the UI now that it's the Dealer's turn
            setWaitingState(false); 
        });
    }

    /** Reflected in chat when dealer sends a counter */
    public void addDealerOffer(double amount, String label) {
        SwingUtilities.invokeLater(() -> addBubble(amount, label, true));
    }

    /** System/status messages */
    public void addSystemMessage(String message) {
        SwingUtilities.invokeLater(() -> addSystemEntry(message));
    }

    /** Lock UI on negotiation end */
    public void lockNegotiation(boolean accepted) {
        SwingUtilities.invokeLater(() -> {
            acceptButton.setEnabled(false);
            sendButton.setEnabled(false);
            counterField.setEnabled(false);
            exitButton.setEnabled(true);
            addSystemEntry(accepted
                    ? "✅ Deal confirmed at RM " + String.format("%,.2f", currentOffer)
                    : "❌ Negotiation ended without agreement.");
        });
    }

    /** 
     * View state management.
     * Prevents the user from making "out-of-turn" offers.
     * When isWaiting is true, text fields and buttons are disabled.
     */
    public void setWaitingState(boolean isWaiting) {
        SwingUtilities.invokeLater(() -> {
            acceptButton.setEnabled(!isWaiting);
            sendButton.setEnabled(!isWaiting);
            counterField.setEnabled(!isWaiting);
            if (isWaiting) {
                counterField.setText("Waiting for response...");
            } else {
                counterField.setText("");
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void handleAccept() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Accept buyer's offer at RM " + String.format("%,.2f", currentOffer) + "?",
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

        setWaitingState(true);
        if (negotiationListener != null) negotiationListener.onCounterOffer(counter);
    }

    private void addBubble(double amount, String label, boolean isDealer) {
        JPanel row = new JPanel(new FlowLayout(isDealer ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(isDealer ? DEALER_BUBBLE : BUYER_BUBBLE);
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDealer ? ACCENT : new Color(99, 179, 237), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));

        JLabel senderLbl = new JLabel(isDealer ? "🏢 You (Dealer)" : "👤 " + buyerName);
        senderLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        senderLbl.setForeground(isDealer ? ACCENT : new Color(99, 179, 237));

        JLabel typeLbl = new JLabel(label);
        typeLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        typeLbl.setForeground(MUTED);

        JLabel priceLbl = new JLabel("RM " + String.format("%,.2f", amount));
        priceLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        priceLbl.setForeground(TEXT);

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        JLabel timeLbl = new JLabel(time);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        timeLbl.setForeground(MUTED);

        bubble.add(senderLbl);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(typeLbl);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(priceLbl);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(timeLbl);

        row.add(bubble);
        chatPanel.add(row);
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
            if (chatScroll != null) {
                JScrollBar bar = chatScroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    public void display() {
        pack();
        setSize(520, 800);
        centerOnScreen();
        setVisible(true);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }
}
