package AutoNego.GUI;

import jade.wrapper.ContainerController;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import AutoNego.FIPAAgents.FipaBrokerAgent;
import AutoNego.FIPAAgents.FipaBuyerAgent;
import AutoNego.FIPAAgents.FipaDealerAgent;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Control Console for the FIPA-compliant version.
 */
public class FipaLauncherControlGui extends JFrame {
    private final ContainerController container;
    private final AtomicInteger dealerCount = new AtomicInteger(1);
    private final AtomicInteger buyerCount = new AtomicInteger(1);

    private static final Color BG = new Color(15, 17, 26);
    private static final Color PANEL_BG = new Color(22, 26, 40);
    private static final Color ACCENT = new Color(167, 139, 250); // Violet accent for FIPA version

    public FipaLauncherControlGui(ContainerController container) {
        super("FIPA Iterated Contract Net System");
        this.container = container;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG);
        setLayout(new GridLayout(1, 3, 20, 0));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(40, 40, 40, 40));

        add(createModule("FIPA Broker", "Matchmaker & Ledger", "Start Broker", e -> {
            try {
                container.createNewAgent("broker", FipaBrokerAgent.class.getName(), null).start();
                ((JButton) e.getSource()).setEnabled(false);
                ((JButton) e.getSource()).setText("Broker Online");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }));

        add(createModule("FIPA Dealer (DA)", "Initiator (Sends CFP)", "Add FIPA Dealer", e -> {
            try {
                String name = "fipa_dealer_" + dealerCount.getAndIncrement();
                container.createNewAgent(name, FipaDealerAgent.class.getName(), null).start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }));

        add(createModule("FIPA Buyer (BA)", "Responder (Proposes Price)", "Add FIPA Buyer", e -> {
            try {
                String name = "fipa_buyer_" + buyerCount.getAndIncrement();
                container.createNewAgent(name, FipaBuyerAgent.class.getName(), null).start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
                new EmptyBorder(20, 20, 20, 20)));

        JLabel t = new JLabel(title);
        t.setForeground(ACCENT);
        t.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel d = new JLabel("<html>" + desc + "</html>");
        d.setForeground(new Color(113, 128, 150));
        d.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton btn = new JButton(btnText);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.addActionListener(action);

        p.add(t);
        p.add(Box.createVerticalStrut(10));
        p.add(d);
        p.add(Box.createVerticalGlue());
        p.add(btn);
        return p;
    }
}
