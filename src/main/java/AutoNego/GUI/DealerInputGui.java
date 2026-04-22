package AutoNego.GUI;

import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI Screen 4 (Dealer): Car Listing Input Page
 * Allows a Dealer Agent to add car listings and send them to the Broker.
 *
 * Usage from DealerAgent.setup():
 *   DealerInputGui gui = new DealerInputGui(this);
 *   gui.setOnListingListener(listings -> { // send via ACL message });
 *   gui.show();
 */
public class DealerInputGui extends JFrame {

    public static class CarListing {
        public final String brand;
        public final String type;
        public final double price;
        public final double minAcceptPrice;

        public CarListing(String brand, String type, double price, double minAcceptPrice) {
            this.brand = brand;
            this.type  = type;
            this.price = price;
            this.minAcceptPrice = minAcceptPrice;
        }
    }

    public interface OnListingListener {
        void onSubmitListings(List<CarListing> listings);
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final Agent myAgent;
    private OnListingListener listingListener;

    private JTextField brandField;
    private JTextField typeField;
    private JTextField priceField;
    private JTextField minPriceField;
    private DefaultTableModel tableModel;
    private final List<CarListing> listings = new ArrayList<>();

    // ── Palette ──────────────────────────────────────────────────────────
    private static final Color BG        = new Color(18, 12, 30);   // deep purple-dark
    private static final Color PANEL_BG  = new Color(26, 18, 44);
    private static final Color CARD_BG   = new Color(32, 24, 52);
    private static final Color ACCENT    = new Color(167, 139, 250); // violet
    private static final Color ACCENT2   = new Color(236, 72, 153);  // pink
    private static final Color SUCCESS   = new Color(72, 187, 120);
    private static final Color DANGER    = new Color(245, 101, 101);
    private static final Color TEXT      = new Color(237, 233, 254);
    private static final Color MUTED     = new Color(139, 122, 180);
    private static final Color BORDER    = new Color(60, 44, 90);
    private static final Color FIELD_BG  = new Color(38, 28, 60);
    private static final Color TABLE_HDR = new Color(44, 32, 70);

    public DealerInputGui(Agent agent) {
        super("Dealer — Car Listings");
        this.myAgent = agent;
        initUI();
    }

    public void setOnListingListener(OnListingListener listener) {
        this.listingListener = listener;
    }

    private void initUI() {
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(),     BorderLayout.NORTH);
        add(buildFormArea(),   BorderLayout.WEST);
        add(buildTableArea(),  BorderLayout.CENTER);
        add(buildFooter(),     BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setSize(760, 480);
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

        JLabel title = new JLabel("Dealer Listings");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT);

        JLabel sub = new JLabel("Add cars to your inventory and send them to the Broker");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(MUTED);

        JPanel tp = new JPanel();
        tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));
        tp.setOpaque(false);
        tp.add(title);
        tp.add(Box.createVerticalStrut(3));
        tp.add(sub);
        header.add(tp, BorderLayout.CENTER);

        JLabel badge = new JLabel("● " + myAgent.getLocalName());
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setForeground(ACCENT);
        header.add(badge, BorderLayout.EAST);

        return header;
    }

    private JPanel buildFormArea() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 0, 2, BORDER),
                new EmptyBorder(20, 20, 20, 20)
        ));
        panel.setPreferredSize(new Dimension(240, 0));

        JLabel formTitle = new JLabel("Add New Car");
        formTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        formTitle.setForeground(ACCENT);
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        brandField = createField();
        typeField  = createField();
        priceField = createField();
        minPriceField = createField();

        panel.add(formTitle);
        panel.add(Box.createVerticalStrut(16));
        panel.add(buildFormRow("Car Brand", brandField));
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildFormRow("Car Type", typeField));
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildFormRow("Price (RM)", priceField));
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildFormRow("Minimum Accept Price (RM)", minPriceField));
        panel.add(Box.createVerticalStrut(20));

        JButton addBtn = new JButton("+ Add to List");
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        addBtn.setBackground(ACCENT);
        addBtn.setForeground(new Color(18, 12, 30));
        addBtn.setFocusPainted(false);
        addBtn.setBorder(new EmptyBorder(10, 0, 10, 0));
        addBtn.setOpaque(true);
        addBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { addBtn.setBackground(ACCENT2); }
            public void mouseExited(MouseEvent e)  { addBtn.setBackground(ACCENT);  }
        });
        addBtn.addActionListener(e -> handleAddCar());

        panel.add(addBtn);

        JButton clearBtn = new JButton("Clear Form");
        clearBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clearBtn.setBackground(CARD_BG);
        clearBtn.setForeground(MUTED);
        clearBtn.setFocusPainted(false);
        clearBtn.setBorder(new EmptyBorder(8, 0, 8, 0));
        clearBtn.setOpaque(true);
        clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        clearBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> clearForm());
        panel.add(Box.createVerticalStrut(8));
        panel.add(clearBtn);

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel buildTableArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(16, 16, 0, 16));

        JLabel tableTitle = new JLabel("Current Inventory");
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tableTitle.setForeground(MUTED);
        tableTitle.setBorder(new EmptyBorder(0, 0, 8, 0));

        String[] cols = {"#", "Brand", "Type", "Price (RM)", "Min (RM)", ""};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(tableModel);
        table.setBackground(CARD_BG);
        table.setForeground(TEXT);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setSelectionBackground(new Color(50, 38, 80));
        table.setSelectionForeground(TEXT);
        table.setFocusable(false);

        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HDR);
        header.setForeground(MUTED);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);

        // Delete button in last column
        table.getColumnModel().getColumn(5).setCellRenderer((tbl, value, selected, focused, row, col) -> {
            JButton btn = new JButton("✕");
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setBackground(DANGER);
            btn.setForeground(Color.WHITE);
            btn.setBorder(new EmptyBorder(3, 8, 3, 8));
            btn.setOpaque(true);
            return btn;
        });

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (col == 5 && row >= 0 && row < listings.size()) {
                    listings.remove(row);
                    tableModel.removeRow(row);
                    renumberTable();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));

        panel.add(tableTitle, BorderLayout.NORTH);
        panel.add(scroll,     BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(PANEL_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(2, 0, 0, 0, BORDER),
                new EmptyBorder(12, 20, 12, 20)
        ));

        JLabel hint = new JLabel("Cars will be sent to the Broker (KA) when you confirm.");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(MUTED);

        JButton submitBtn = new JButton("✓  Send Listings to Broker");
        submitBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        submitBtn.setBackground(SUCCESS);
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFocusPainted(false);
        submitBtn.setBorder(new EmptyBorder(10, 20, 10, 20));
        submitBtn.setOpaque(true);
        submitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        submitBtn.addActionListener(e -> handleSubmit());

        footer.add(hint,      BorderLayout.WEST);
        footer.add(submitBtn, BorderLayout.EAST);

        return footer;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private JPanel buildFormRow(String labelText, JTextField field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(TEXT);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        row.add(lbl);
        row.add(Box.createVerticalStrut(4));
        row.add(field);
        return row;
    }

    private JTextField createField() {
        JTextField f = new JTextField();
        f.setBackground(FIELD_BG);
        f.setForeground(TEXT);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return f;
    }

    private void handleAddCar() {
        String brand = brandField.getText().trim();
        String type  = typeField.getText().trim();
        String priceStr = priceField.getText().trim();
        String minPriceStr = minPriceField.getText().trim();

        if (brand.isEmpty() || type.isEmpty() || priceStr.isEmpty() || minPriceStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.", "Incomplete", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid price.", "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double minAcceptPrice;
        try {
            minAcceptPrice = Double.parseDouble(minPriceStr);
            if (minAcceptPrice <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid minimum acceptable price.", "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (minAcceptPrice > price) {
            JOptionPane.showMessageDialog(this, "Minimum acceptable price must be less than or equal to listing price.", "Invalid Range", JOptionPane.ERROR_MESSAGE);
            return;
        }

        CarListing listing = new CarListing(brand, type, price, minAcceptPrice);
        listings.add(listing);
        tableModel.addRow(new Object[]{listings.size(), brand, type, String.format("%,.2f", price), String.format("%,.2f", minAcceptPrice), "✕"});
        clearForm();
    }

    private void handleSubmit() {
        if (listings.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one car before submitting.", "Empty List", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (listingListener != null) {
            listingListener.onSubmitListings(new ArrayList<>(listings));
        }
    }

    private void clearForm() {
        brandField.setText("");
        typeField.setText("");
        priceField.setText("");
        minPriceField.setText("");
        brandField.requestFocus();
    }

    private void renumberTable() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(i + 1, i, 0);
        }
    }

    /** Programmatically add a listing (e.g. from config file) */
    public void addListing(String brand, String type, double price, double minAcceptPrice) {
        SwingUtilities.invokeLater(() -> {
            CarListing listing = new CarListing(brand, type, price, minAcceptPrice);
            listings.add(listing);
            tableModel.addRow(new Object[]{listings.size(), brand, type, String.format("%,.2f", price), String.format("%,.2f", minAcceptPrice), "✕"});
        });
    }

    public void display() {
        pack();
        setSize(760, 480);
        centerOnScreen();
        setVisible(true);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }
}
