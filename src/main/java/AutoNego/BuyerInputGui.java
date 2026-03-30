package AutoNego;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * GUI Screen 1 (Buyer): Car Input Page
 * Allows the Buyer Agent to input desired car specifications and submit to the Broker.
 *
 * Usage from BuyerAgent.setup():
 *   BuyerInputGui gui = new BuyerInputGui(this);
 *   gui.show();
 */
public class BuyerInputGui extends JFrame {

    private final Agent myAgent;

    private JTextField brandField;
    private JTextField typeField;
    private JTextField priceField;
    private JButton confirmButton;

    // Callback interface so the agent can react to the confirm action
    public interface OnConfirmListener {
        void onConfirm(String brand, String type, double maxPrice);
    }

    private OnConfirmListener listener;

    public BuyerInputGui(Agent agent) {
        super("Buyer — Car Request");
        this.myAgent = agent;
        initUI();
    }

    public void setOnConfirmListener(OnConfirmListener listener) {
        this.listener = listener;
    }

    private void initUI() {
        // ── Styling constants ──────────────────────────────────────────────
        Color BG        = new Color(15, 17, 26);
        Color PANEL_BG  = new Color(22, 26, 40);
        Color ACCENT    = new Color(99, 179, 237);   // sky blue
        Color ACCENT2   = new Color(236, 201, 75);   // amber highlight
        Color TEXT      = new Color(226, 232, 240);
        Color MUTED     = new Color(113, 128, 150);
        Color FIELD_BG  = new Color(30, 36, 54);
        Color BORDER    = new Color(45, 55, 80);

        Font TITLE_FONT  = new Font("Segoe UI", Font.BOLD, 22);
        Font LABEL_FONT  = new Font("Segoe UI", Font.PLAIN, 13);
        Font FIELD_FONT  = new Font("Segoe UI", Font.PLAIN, 14);
        Font BTN_FONT    = new Font("Segoe UI", Font.BOLD, 14);

        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // ── Header ────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, ACCENT),
                new EmptyBorder(18, 24, 18, 24)
        ));

        JLabel titleLabel = new JLabel("Find Your Car");
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT);

        JLabel subtitleLabel = new JLabel("Enter your requirements and we'll find the best deals");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(MUTED);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitleLabel);
        header.add(titlePanel, BorderLayout.CENTER);

        // Agent badge
        JLabel agentBadge = new JLabel("● " + myAgent.getLocalName());
        agentBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        agentBadge.setForeground(ACCENT);
        agentBadge.setBorder(new EmptyBorder(4, 10, 4, 10));
        header.add(agentBadge, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // ── Form ──────────────────────────────────────────────────────────
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(BG);
        form.setBorder(new EmptyBorder(28, 32, 16, 32));

        brandField = createField(FIELD_BG, TEXT, FIELD_FONT, BORDER);
        typeField  = createField(FIELD_BG, TEXT, FIELD_FONT, BORDER);
        priceField = createField(FIELD_BG, TEXT, FIELD_FONT, BORDER);

        form.add(buildFieldRow("Car Brand", "e.g. Toyota, BMW, Honda", brandField, LABEL_FONT, MUTED, TEXT));
        form.add(Box.createVerticalStrut(16));
        form.add(buildFieldRow("Car Type", "e.g. Sedan, SUV, Hatchback", typeField, LABEL_FONT, MUTED, TEXT));
        form.add(Box.createVerticalStrut(16));
        form.add(buildFieldRow("Maximum Price (RM)", "e.g. 85000.00", priceField, LABEL_FONT, MUTED, TEXT));

        add(form, BorderLayout.CENTER);

        // ── Footer / Button ───────────────────────────────────────────────
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(PANEL_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(2, 0, 0, 0, BORDER),
                new EmptyBorder(16, 32, 16, 32)
        ));

        confirmButton = new JButton("Search for Deals  →");
        confirmButton.setFont(BTN_FONT);
        confirmButton.setBackground(ACCENT);
        confirmButton.setForeground(new Color(10, 15, 30));
        confirmButton.setFocusPainted(false);
        confirmButton.setBorder(new EmptyBorder(12, 28, 12, 28));
        confirmButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        confirmButton.setOpaque(true);

        // Hover effect
        confirmButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { confirmButton.setBackground(ACCENT2); }
            public void mouseExited(MouseEvent e)  { confirmButton.setBackground(ACCENT); }
        });

        confirmButton.addActionListener(e -> handleConfirm());

        footer.add(confirmButton, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        // ── Window settings ───────────────────────────────────────────────
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setSize(480, 380);
        centerOnScreen();
        setResizable(false);
    }

    private JPanel buildFieldRow(String label, String hint,
                                 JTextField field, Font labelFont,
                                 Color muteColor, Color textColor) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JLabel lbl = new JLabel(label);
        lbl.setFont(labelFont.deriveFont(Font.BOLD));
        lbl.setForeground(textColor);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hintLbl = new JLabel(hint);
        hintLbl.setFont(labelFont.deriveFont(Font.PLAIN, 11f));
        hintLbl.setForeground(muteColor);
        hintLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        row.add(lbl);
        row.add(Box.createVerticalStrut(3));
        row.add(hintLbl);
        row.add(Box.createVerticalStrut(6));
        row.add(field);
        return row;
    }

    private JTextField createField(Color bg, Color fg, Font font, Color border) {
        JTextField f = new JTextField();
        f.setBackground(bg);
        f.setForeground(fg);
        f.setFont(font);
        f.setCaretColor(fg);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        return f;
    }

    private void handleConfirm() {
        String brand = brandField.getText().trim();
        String type  = typeField.getText().trim();
        String priceText = priceField.getText().trim();

        if (brand.isEmpty() || type.isEmpty() || priceText.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all fields before searching.",
                    "Incomplete Form", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceText);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid price (e.g. 85000.00).",
                    "Invalid Price", JOptionPane.ERROR_MESSAGE);
            return;
        }

        confirmButton.setEnabled(false);
        confirmButton.setText("Searching...");

        if (listener != null) {
            listener.onConfirm(brand, type, price);
        }
    }

    /** Call this from outside after agent receives a response, to re-enable the button */
    public void resetForm() {
        SwingUtilities.invokeLater(() -> {
            confirmButton.setEnabled(true);
            confirmButton.setText("Search for Deals  →");
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
