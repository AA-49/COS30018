package AutoNego;

import jade.wrapper.ContainerController;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LauncherControlGui extends JFrame {
    private final ContainerController container;
    private final AtomicInteger dealerCount = new AtomicInteger(1);
    private final AtomicInteger buyerCount = new AtomicInteger(1);

    private static final Color BG = new Color(15, 17, 26);
    private static final Color PANEL_BG = new Color(22, 26, 40);
    private static final Color ACCENT = new Color(99, 179, 237);

    public LauncherControlGui(ContainerController container) {
        super("AutoNego System Control Console");
        this.container = container;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG);
        setLayout(new GridLayout(1, 3, 20, 0));
        ((JPanel)getContentPane()).setBorder(new EmptyBorder(40, 40, 40, 40));

        add(createModule("Broker (KA)", "Central Market Facilitator", "Start Broker", e -> {
            try {
                container.createNewAgent("broker", SimpleBrokerAgent.class.getName(), null).start();
                ((JButton)e.getSource()).setEnabled(false); // Broker 通常只有一个
                ((JButton)e.getSource()).setText("Broker Online");
            } catch (Exception ex) { ex.printStackTrace(); }
        }));

        add(createModule("Dealer (DA)", "Add Car Sellers", "Add New Dealer", e -> {
            try {
                String name = "dealer_" + dealerCount.getAndIncrement();
                container.createNewAgent(name, SimpleDealerAgent.class.getName(), null).start();
            } catch (Exception ex) { ex.printStackTrace(); }
        }));

        add(createModule("Buyer (BA)", "Add Car Seekers", "Add New Buyer", e -> {
            try {
                String name = "buyer_" + buyerCount.getAndIncrement();
                container.createNewAgent(name, SimpleBuyerAgent.class.getName(), null).start();
            } catch (Exception ex) { ex.printStackTrace(); }
        }));

        setSize(900, 300);
        setLocationRelativeTo(null);
    }

    private JPanel createModule(String title, String desc, String btnText, java.awt.event.ActionListener action) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 55, 80), 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel t = new JLabel(title);
        t.setForeground(ACCENT);
        t.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel d = new JLabel("<html>" + desc + "</html>");
        d.setForeground(new Color(113, 128, 150));
        d.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton btn = new JButton(btnText);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.addActionListener(action);

        p.add(t); p.add(Box.createVerticalStrut(10));
        p.add(d); p.add(Box.createVerticalGlue());
        p.add(btn);
        return p;
    }
}