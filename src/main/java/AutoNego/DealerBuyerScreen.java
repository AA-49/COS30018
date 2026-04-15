package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * GUI Screen 5 (Dealer): Interested Buyer Screen
 * Shows which buyers are interested in which of the dealer's cars.
 * Dealer can choose to Negotiate or decline each buyer.
 *
 * Usage from DealerAgent (after broker sends buyer interest notifications):
 *   List<BuyerInterest> interests = ...;
 *   DealerBuyerScreen gui = new DealerBuyerScreen(agent, interests);
 *   gui.setOnActionListener((interest, accept) -> { ... });
 *   gui.show();
 */
public class DealerBuyerScreen extends JFrame {

    public static class BuyerInterest {
        public final String buyerName;    // BA agent name
        public final String carBrand;
        public final String carType;
        public final double buyerInitialOffer; // buyer's first offer price

        public BuyerInterest(String buyerName, String carBrand, String carType, double buyerInitialOffer) {
            this.buyerName        = buyerName;
            this.carBrand         = carBrand;
            this.carType          = carType;
            this.buyerInitialOffer = buyerInitialOffer;
        }
    }

    public interface OnActionListener {
        void onNegotiate(BuyerInterest interest);
        void onDecline(BuyerInterest interest);
    }

    private final Agent myAgent;
    private final List<BuyerInterest> interests;
    private OnActionListener actionListener;

    // ── Palette ──────────────────────────────────────────────────────────
    private static final Color BG        = new Color(18, 12, 30);
    private static final Color PANEL_BG  = new Color(26, 18, 44);
    private static final Color CARD_BG   = new Color(32, 24, 52);
    private static final Color ACCENT    = new Color(167, 139, 250);
    private static final Color ACCENT2   = new Color(236, 72, 153);
    private static final Color SUCCESS   = new Color(72, 187, 120);
    private static final Color DANGER    = new Color(245, 101, 101);
    private static final Color WARN      = new Color(236, 201, 75);
    private static final Color TEXT      = new Color(237, 233, 254);
    private static final Color MUTED     = new Color(139, 122, 180);
    private static final Color BORDER    = new Color(60, 44, 90);

    public DealerBuyerScreen(Agent agent, List<BuyerInterest> interests) {
        super("Dealer — Interested Buyers");
        this.myAgent   = agent;
        this.interests = interests;
        initUI();
    }

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    private void initUI() {
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildCards(),    BorderLayout.CENTER);
        add(buildFooter(),   BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setSize(560, Math.min(120 + interests.size() * 130, 640));
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

        JLabel sub = new JLabel(interests.size() + " buyer" + (interests.size() != 1 ? "s" : "") + " interested in your cars");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(MUTED);

        JPanel tp = new JPanel();
        tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));
        tp.setOpaque(false);
        tp.add(title);
        tp.add(Box.createVerticalStrut(4));
        tp.add(sub);
        header.add(tp, BorderLayout.CENTER);

        JLabel badge = new JLabel("● " + myAgent.getLocalName());
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setForeground(ACCENT);
        header.add(badge, BorderLayout.EAST);

        return header;
    }

    private JScrollPane buildCards() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(BG);
        container.setBorder(new EmptyBorder(16, 20, 16, 20));

        if (interests.isEmpty()) {
            JLabel empty = new JLabel("No buyers have expressed interest yet.");
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            empty.setForeground(MUTED);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            container.add(Box.createVerticalGlue());
            container.add(empty);
            container.add(Box.createVerticalGlue());
        } else {
            for (BuyerInterest interest : interests) {
                container.add(buildCard(interest));
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

    private JPanel buildCard(BuyerInterest interest) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        // Buyer avatar / icon
        JLabel avatar = new JLabel("👤");
        avatar.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        avatar.setHorizontalAlignment(SwingConstants.CENTER);
        avatar.setPreferredSize(new Dimension(40, 40));
        card.add(avatar, BorderLayout.WEST);

        // Info
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel buyerLabel = new JLabel("Buyer: " + interest.buyerName);
        buyerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        buyerLabel.setForeground(TEXT);

        JLabel carLabel = new JLabel("Interested in: " + interest.carBrand + " " + interest.carType);
        carLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        carLabel.setForeground(MUTED);

        JLabel offerLabel = new JLabel("Initial offer: RM " + String.format("%,.2f", interest.buyerInitialOffer));
        offerLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        offerLabel.setForeground(WARN);

        info.add(buyerLabel);
        info.add(Box.createVerticalStrut(3));
        info.add(carLabel);
        info.add(Box.createVerticalStrut(3));
        info.add(offerLabel);
        card.add(info, BorderLayout.CENTER);

        // Action buttons
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.setOpaque(false);

        JButton negotiateBtn = buildBtn("Negotiate", ACCENT, new Color(18, 12, 30));
        JButton declineBtn   = buildBtn("Decline",   DANGER, Color.WHITE);

        negotiateBtn.addActionListener(e -> {
            if (actionListener != null) actionListener.onNegotiate(interest);
            negotiateBtn.setEnabled(false);
            declineBtn.setEnabled(false);
            negotiateBtn.setText("Starting...");
            card.setBackground(new Color(30, 40, 35));
        });

        declineBtn.addActionListener(e -> {
            if (actionListener != null) actionListener.onDecline(interest);
            negotiateBtn.setEnabled(false);
            declineBtn.setEnabled(false);
            declineBtn.setText("Declined");
            card.setBackground(new Color(40, 28, 35));
        });

        buttons.add(negotiateBtn);
        buttons.add(declineBtn);
        card.add(buttons, BorderLayout.EAST);

        return card;
    }

    private JButton buildBtn(String text, Color bg, Color fg) {
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
        JLabel hint = new JLabel("ℹ  Broker will be notified of your decisions and will start the negotiation process.");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(MUTED);
        footer.add(hint);
        return footer;
    }

    /** Add a new buyer interest dynamically (agent thread must call via SwingUtilities) */
    public void addInterest(BuyerInterest interest) {
        SwingUtilities.invokeLater(() -> {
            interests.add(interest);
            getContentPane().removeAll();
            initUI();
            revalidate();
            repaint();
        });
    }

    public void display() {
        pack();
        centerOnScreen();
        setVisible(true);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }
}
