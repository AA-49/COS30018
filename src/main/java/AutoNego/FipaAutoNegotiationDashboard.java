package AutoNego;

import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class FipaAutoNegotiationDashboard extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile FipaAutoNegotiationDashboard activeDashboard;

    private final JButton autoNegotiationButton = new JButton("Auto Negotiation");
    private final DefaultTableModel dealerTableModel = new DefaultTableModel(
            new String[]{"Dealer Agent", "Brand", "Type", "Selling Price", "Minimum Price", "Tactic"}, 0);
    private final DefaultTableModel buyerTableModel = new DefaultTableModel(
            new String[]{"Buyer Agent", "Find Brand", "Find Type", "Starting Price", "Maximum Price", "Tactic"}, 0);
    private final DefaultTableModel resultTableModel = new DefaultTableModel(
            new String[]{"Session", "Buyer", "Dealer", "Car", "Result", "Final Price", "Commission", "Rounds", "Buyer Tactic", "Dealer Tactic"}, 0);
    private final JTextArea logArea = new JTextArea();
    private final JComboBox<String> sessionSelector = new JComboBox<>();
    private final NegotiationGraphPanel graphPanel = new NegotiationGraphPanel();
    private final Map<String, SessionView> sessions = new LinkedHashMap<>();
    private final List<DealerProfile> configuredDealers = new ArrayList<>();
    private final List<BuyerProfile> configuredBuyers = new ArrayList<>();
    private final JButton addDealerButton = new JButton("Add Dealer");
    private final JButton addBuyerButton = new JButton("Add Buyer");
    private final JButton addCarButton = new JButton("Add Car");
    private final JButton editTacticButton = new JButton("Edit Tactic");
    private final JButton launchFipaLauncherButton = new JButton("Launch FIPA Launcher");
    private final Map<String, String> listingSessionIds = new HashMap<>();
    private JTable dealerTable;
    private JTable buyerTable;

    private ContainerController container;
    private int expectedResults;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FipaAutoNegotiationDashboard dashboard = new FipaAutoNegotiationDashboard();
            activeDashboard = dashboard;
            dashboard.setVisible(true);
        });
    }

    public FipaAutoNegotiationDashboard() {
        super("FIPA Auto Negotiation Dashboard");
        initUi();
        initializePlan();
    }

    private void initUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("AutoNego FIPA Auto Negotiation");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.add(title, BorderLayout.WEST);

        autoNegotiationButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        autoNegotiationButton.addActionListener(event -> startAutoNegotiation());
        header.add(autoNegotiationButton, BorderLayout.EAST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        addDealerButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addBuyerButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addCarButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        editTacticButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        launchFipaLauncherButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        actionPanel.add(addDealerButton);
        actionPanel.add(addCarButton);
        actionPanel.add(addBuyerButton);
        actionPanel.add(editTacticButton);
        actionPanel.add(launchFipaLauncherButton);

        addDealerButton.addActionListener(event -> addDealer());
        addBuyerButton.addActionListener(event -> addBuyer());
        addCarButton.addActionListener(event -> addCarToDealer());
        editTacticButton.addActionListener(event -> editSelectedAgent());
        launchFipaLauncherButton.addActionListener(event -> launchFipaLauncher());

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(header, BorderLayout.NORTH);
        northPanel.add(actionPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        dealerTable = createTable(dealerTableModel);
        buyerTable = createTable(buyerTableModel);
        JTable resultTable = createTable(resultTableModel);

        JTabbedPane setupTabs = new JTabbedPane();
        setupTabs.addTab("Dealer Inventory", new JScrollPane(dealerTable));
        setupTabs.addTab("Buyer Searches", new JScrollPane(buyerTable));

        JPanel graphContainer = new JPanel(new BorderLayout(8, 8));
        graphContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        graphContainer.add(sessionSelector, BorderLayout.NORTH);
        graphContainer.add(graphPanel, BorderLayout.CENTER);
        sessionSelector.addActionListener(event -> refreshGraph());

        JTabbedPane outputTabs = new JTabbedPane();
        outputTabs.addTab("Negotiation Results", new JScrollPane(resultTable));
        outputTabs.addTab("Negotiation Graph", graphContainer);

        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 170));

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, setupTabs, outputTabs);
        topSplit.setResizeWeight(0.45);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, logScroll);
        mainSplit.setResizeWeight(0.75);
        add(mainSplit, BorderLayout.CENTER);

        setSize(1180, 760);
        setLocationRelativeTo(null);
    }

    private JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        return table;
    }

    private void initializePlan() {
        configuredDealers.clear();
        configuredBuyers.clear();
        configuredDealers.addAll(buildDefaultDealers());
        configuredBuyers.addAll(buildDefaultBuyers());
        refreshPlanTables();
    }

    private List<DealerProfile> buildDefaultDealers() {
        return Arrays.asList(
                new DealerProfile("dealer1", 500, Arrays.asList(
                        new CarSpec("Toyota", "SUVs", 98000, 90000),
                        new CarSpec("Perodua", "Axia", 34000, 30000),
                        new CarSpec("Tesla", "Model 3", 185000, 172000)
                ), Map.of("tactic", "none")),
                new DealerProfile("dealer2", 650, Arrays.asList(
                        new CarSpec("Toyota", "SUVs", 102000, 92000),
                        new CarSpec("Perodua", "Axia", 36000, 31500),
                        new CarSpec("Tesla", "Model 3", 190000, 175000)
                ), Map.of("tactic", "none")),
                new DealerProfile("dealer3", 800, Arrays.asList(
                        new CarSpec("Toyota", "SUVs", 108000, 95000),
                        new CarSpec("Perodua", "Axia", 38000, 33000),
                        new CarSpec("Tesla", "Model 3", 198000, 182000)
                ), Map.of("tactic", "none"))
        );
    }

    private List<BuyerProfile> buildDefaultBuyers() {
        return Arrays.asList(
                new BuyerProfile("buyer1", "Toyota", "SUVs", 88000, 97000, 0, 1700, Map.of("tactic", "none")),
                new BuyerProfile("buyer2", "Toyota", "SUVs", 90000, 101000, 1, 1900, Map.of("tactic", "none")),
                new BuyerProfile("buyer3", "Perodua", "Axia", 29000, 33500, 0, 2100, Map.of("tactic", "none")),
                new BuyerProfile("buyer4", "Perodua", "Axia", 31000, 35000, 1, 2300, Map.of("tactic", "none")),
                new BuyerProfile("buyer5", "Tesla", "Model 3", 170000, 184000, 0, 2500, Map.of("tactic", "none"))
        );
    }

    private void refreshPlanTables() {
        dealerTableModel.setRowCount(0);
        buyerTableModel.setRowCount(0);
        for (DealerProfile dealer : configuredDealers) {
            for (CarSpec car : dealer.cars) {
                dealerTableModel.addRow(new Object[]{
                        dealer.agentName,
                        car.brand,
                        car.type,
                        formatMoney(car.sellingPrice),
                        formatMoney(car.minimumPrice),
                        dealer.variables.getOrDefault("tactic", "none")
                });
            }
        }
        for (BuyerProfile buyer : configuredBuyers) {
            buyerTableModel.addRow(new Object[]{
                    buyer.agentName,
                    buyer.brand,
                    buyer.type,
                    formatMoney(buyer.startingPrice),
                    formatMoney(buyer.maximumPrice),
                    buyer.variables.getOrDefault("tactic", "none")
            });
        }
    }

    private void addDealer() {
        String agentName = JOptionPane.showInputDialog(this, "Dealer agent name:", "dealer" + (configuredDealers.size() + 1));
        if (agentName == null || agentName.isBlank()) {
            return;
        }
        String brand = JOptionPane.showInputDialog(this, "Default car brand:", "Toyota");
        if (brand == null || brand.isBlank()) {
            return;
        }
        String type = JOptionPane.showInputDialog(this, "Default car type:", "SUVs");
        if (type == null || type.isBlank()) {
            return;
        }
        String sellingPriceText = JOptionPane.showInputDialog(this, "Selling price:", "100000");
        String minimumPriceText = JOptionPane.showInputDialog(this, "Minimum price:", "90000");
        if (sellingPriceText == null || minimumPriceText == null) {
            return;
        }
        try {
            double sellingPrice = Double.parseDouble(sellingPriceText);
            double minimumPrice = Double.parseDouble(minimumPriceText);
            configuredDealers.add(new DealerProfile(agentName, 500,
                    Arrays.asList(new CarSpec(brand, type, sellingPrice, minimumPrice)), Map.of("tactic", "none")));
            refreshPlanTables();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric values for prices.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addBuyer() {
        String agentName = JOptionPane.showInputDialog(this, "Buyer agent name:", "buyer" + (configuredBuyers.size() + 1));
        if (agentName == null || agentName.isBlank()) {
            return;
        }
        String brand = JOptionPane.showInputDialog(this, "Search brand:", "Toyota");
        if (brand == null || brand.isBlank()) {
            return;
        }
        String type = JOptionPane.showInputDialog(this, "Search type:", "SUVs");
        if (type == null || type.isBlank()) {
            return;
        }
        String startingPriceText = JOptionPane.showInputDialog(this, "Starting price:", "80000");
        String maximumPriceText = JOptionPane.showInputDialog(this, "Maximum price:", "100000");
        if (startingPriceText == null || maximumPriceText == null) {
            return;
        }
        try {
            double startingPrice = Double.parseDouble(startingPriceText);
            double maximumPrice = Double.parseDouble(maximumPriceText);
            configuredBuyers.add(new BuyerProfile(agentName, brand, type, startingPrice, maximumPrice, 0, 1500, Map.of("tactic", "none")));
            refreshPlanTables();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric values for prices.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addCarToDealer() {
        int selectedRow = dealerTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a dealer row first.", "No Dealer Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DealerProfile dealer = getDealerProfileByRow(selectedRow);
        if (dealer == null) {
            return;
        }
        String brand = JOptionPane.showInputDialog(this, "New car brand:", "Toyota");
        if (brand == null || brand.isBlank()) {
            return;
        }
        String type = JOptionPane.showInputDialog(this, "New car type:", "SUVs");
        if (type == null || type.isBlank()) {
            return;
        }
        String sellingPriceText = JOptionPane.showInputDialog(this, "Selling price:", "100000");
        String minimumPriceText = JOptionPane.showInputDialog(this, "Minimum price:", "90000");
        if (sellingPriceText == null || minimumPriceText == null) {
            return;
        }
        try {
            double sellingPrice = Double.parseDouble(sellingPriceText);
            double minimumPrice = Double.parseDouble(minimumPriceText);
            dealer.cars.add(new CarSpec(brand, type, sellingPrice, minimumPrice));
            refreshPlanTables();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric values for prices.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void editSelectedAgent() {
        int dealerRow = dealerTable.getSelectedRow();
        int buyerRow = buyerTable.getSelectedRow();
        if (dealerRow >= 0) {
            DealerProfile dealer = getDealerProfileByRow(dealerRow);
            if (dealer == null) {
                return;
            }
            String tactic = JOptionPane.showInputDialog(this, "Dealer tactic:", dealer.variables.getOrDefault("tactic", "none"));
            if (tactic != null) {
                dealer.variables.put("tactic", tactic.isBlank() ? "none" : tactic);
            }
            refreshPlanTables();
            return;
        }
        if (buyerRow >= 0) {
            BuyerProfile buyer = configuredBuyers.get(buyerRow);
            String tactic = JOptionPane.showInputDialog(this, "Buyer tactic:", buyer.variables.getOrDefault("tactic", "none"));
            if (tactic != null) {
                buyer.variables.put("tactic", tactic.isBlank() ? "none" : tactic);
            }
            refreshPlanTables();
            return;
        }
        JOptionPane.showMessageDialog(this, "Select a dealer or buyer row to edit its tactics.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
    }

    private void launchFipaLauncher() {
        SwingUtilities.invokeLater(() -> {
            try {
                FipaLauncher.main(new String[0]);
            } catch (Exception e) {
                appendLog("Failed to launch FIPA Launcher: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private DealerProfile getDealerProfileByRow(int rowIndex) {
        int current = 0;
        for (DealerProfile dealer : configuredDealers) {
            int groupSize = Math.max(1, dealer.cars.size());
            if (rowIndex >= current && rowIndex < current + groupSize) {
                return dealer;
            }
            current += groupSize;
        }
        return null;
    }

    private void startAutoNegotiation() {
        SimulationPlan plan = buildPlan();
        resetDashboard(plan.buyers.size());
        loadPlanPreview(plan);

        autoNegotiationButton.setEnabled(false);
        autoNegotiationButton.setText("Running...");

        try {
            if (container != null) {
                try {
                    container.kill();
                } catch (Exception ignored) {
                }
                container = null;
            }
            jade.core.Runtime runtime = jade.core.Runtime.instance();
            runtime.setCloseVM(true);
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.GUI, "false");
            container = runtime.createMainContainer(profile);

            startAgent("broker", DashboardBrokerAgent.class, this);
            for (DealerProfile dealer : plan.dealers) {
                startAgent(dealer.agentName, DashboardDealerAgent.class, this, dealer);
            }
            for (BuyerProfile buyer : plan.buyers) {
                startAgent(buyer.agentName, DashboardBuyerAgent.class, this, buyer);
            }

            appendLog(String.format("Started 1 broker, %d dealer agents, and %d buyer agents using dashboard FIPA agents.",
                    plan.dealers.size(), plan.buyers.size()));
        } catch (StaleProxyException e) {
            appendLog("Could not start JADE agents: " + e.getMessage());
            autoNegotiationButton.setEnabled(true);
            autoNegotiationButton.setText("Auto Negotiation");
        }
    }

    private void startAgent(String name, Class<? extends Agent> agentClass, Object... args) throws StaleProxyException {
        AgentController controller = container.createNewAgent(name, agentClass.getName(), args);
        controller.start();
    }

    private static Class<? extends Agent> loadAgentClass(String className, Class<? extends Agent> fallback) {
        try {
            return Class.forName(className).asSubclass(Agent.class);
        } catch (ClassNotFoundException | ClassCastException e) {
            return fallback;
        }
    }

    private void resetDashboard(int buyerCount) {
        expectedResults = buyerCount;
        dealerTableModel.setRowCount(0);
        buyerTableModel.setRowCount(0);
        resultTableModel.setRowCount(0);
        logArea.setText("");
        sessions.clear();
        listingSessionIds.clear();
        sessionSelector.removeAllItems();
        graphPanel.setSession(null);
    }

    private void loadPlanPreview(SimulationPlan plan) {
        for (DealerProfile dealer : plan.dealers) {
            for (CarSpec car : dealer.cars) {
                dealerTableModel.addRow(new Object[]{
                        dealer.agentName,
                        car.brand,
                        car.type,
                        formatMoney(car.sellingPrice),
                        formatMoney(car.minimumPrice),
                        dealer.variables.getOrDefault("tactic", "none")
                });
            }
        }

        for (BuyerProfile buyer : plan.buyers) {
            buyerTableModel.addRow(new Object[]{
                    buyer.agentName,
                    buyer.brand,
                    buyer.type,
                    formatMoney(buyer.startingPrice),
                    formatMoney(buyer.maximumPrice),
                    buyer.variables.getOrDefault("tactic", "none")
            });
        }
    }

    private SimulationPlan buildPlan() {
        if (configuredDealers.isEmpty() && configuredBuyers.isEmpty()) {
            initializedFallbackPlan();
        }
        return new SimulationPlan(new ArrayList<>(configuredDealers), new ArrayList<>(configuredBuyers));
    }

    private void initializedFallbackPlan() {
        configuredDealers.clear();
        configuredBuyers.clear();
        configuredDealers.addAll(buildDefaultDealers());
        configuredBuyers.addAll(buildDefaultBuyers());
        refreshPlanTables();
    }

    public void recordSessionStart(String sessionId, String buyer, String dealer, String brand, String type,
                                   double buyerStart, double buyerMax, double sellerAsk, double sellerMin,
                                   Map<String, String> buyerVariables, Map<String, String> dealerVariables) {
        SwingUtilities.invokeLater(() -> {
            String buyerTactic = buyerVariables.getOrDefault("tactic", "none");
            String dealerTactic = dealerVariables.getOrDefault("tactic", "none");
            SessionView session = new SessionView(
                    sessionId,
                    buyer,
                    dealer,
                    brand,
                    type,
                    buyerStart,
                    buyerMax,
                    sellerAsk,
                    sellerMin,
                    buyerVariables,
                    dealerVariables,
                    buyerTactic,
                    dealerTactic
            );
            sessions.put(sessionId, session);
            sessionSelector.addItem(session.label());
            if (sessionSelector.getSelectedIndex() < 0) {
                sessionSelector.setSelectedIndex(0);
            }
            appendLogDirect("Session " + sessionId + " started: " + buyer + " and " + dealer
                    + " negotiate " + brand + " " + type + ".");
            refreshGraph();
        });
    }

    public void recordOffer(String sessionId, String actor, double amount, String note) {
        SwingUtilities.invokeLater(() -> {
            SessionView session = sessions.get(sessionId);
            if (session == null) {
                return;
            }
            session.points.add(new OfferPoint(session.points.size() + 1, actor, amount, note));
            appendLogDirect(sessionId + " | " + actor + " -> " + formatMoney(amount) + " | " + note);
            refreshGraph();
        });
    }

    public void recordResult(String sessionId, String buyer, String dealer, String brand, String type,
                             String result, double finalPrice, double commission, int rounds) {
        SwingUtilities.invokeLater(() -> {
            SessionView session = sessions.get(sessionId);
            String displayResult = result;
            if (session != null) {
                session.result = result;
                session.finalPrice = finalPrice;
                if ("DEAL".equals(result)) {
                    session.dealInsideAgreement = isInAgreementRange(
                            finalPrice,
                            session.buyerStart,
                            session.buyerMax,
                            session.sellerMin,
                            session.sellerAsk
                    );
                    displayResult = session.dealInsideAgreement
                            ? "DEAL (Agreement Range)"
                            : "INVALID DEAL (Outside Agreement Range)";
                }
            }

            resultTableModel.addRow(new Object[]{
                    sessionId,
                    buyer,
                    dealer,
                    brand + " " + type,
                    displayResult,
                    finalPrice > 0 ? formatMoney(finalPrice) : "-",
                    commission > 0 ? formatMoney(commission) : "-",
                    rounds,
                    session != null ? session.buyerTactic : "none",
                    session != null ? session.dealerTactic : "none"
            });
            appendLogDirect(sessionId + " result: " + displayResult
                    + (finalPrice > 0 ? " at " + formatMoney(finalPrice) : "") + ".");
            refreshGraph();
            if (resultTableModel.getRowCount() >= expectedResults) {
                autoNegotiationButton.setText("Auto Negotiation Complete");
                autoNegotiationButton.setEnabled(true);
            }
        });
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> appendLogDirect(message));
    }

    private void appendLogDirect(String message) {
        logArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void refreshGraph() {
        String selected = (String) sessionSelector.getSelectedItem();
        if (selected == null) {
            graphPanel.setSession(null);
            return;
        }
        int separator = selected.indexOf(" | ");
        String sessionId = separator >= 0 ? selected.substring(0, separator) : selected;
        graphPanel.setSession(sessions.get(sessionId));
    }

    private static FipaAutoNegotiationDashboard dashboardFromArgs(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof FipaAutoNegotiationDashboard) {
            return (FipaAutoNegotiationDashboard) args[0];
        }
        return activeDashboard;
    }

    private static String formatMoney(double amount) {
        return String.format(Locale.US, "RM %,.2f", amount);
    }

    private static String formatVariableSummary(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private static String encodeProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (builder.length() > 0) {
                builder.append(";");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private static Map<String, String> decodeProperties(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        String[] pairs = payload.split(";");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                result.put(parts[0], parts[1]);
            }
        }
        return result;
    }

    private static final class MessageCodec {
        private static final String FIELD_SEPARATOR = "\t";
        private static final String RECORD_SEPARATOR = "\n";

        private static String encodeFields(String... values) {
            return String.join(FIELD_SEPARATOR, Arrays.stream(values)
                    .map(value -> value == null ? "" : value.replace(FIELD_SEPARATOR, " ").replace(RECORD_SEPARATOR, " "))
                    .toArray(String[]::new));
        }

        private static String[] decodeFields(String payload, int minimumParts) {
            String[] parts = payload.split(FIELD_SEPARATOR, -1);
            if (parts.length < minimumParts) {
                throw new IllegalArgumentException("Expected at least " + minimumParts + " parts but got " + parts.length);
            }
            return parts;
        }

        private static String encodeRecords(String... records) {
            return String.join(RECORD_SEPARATOR, records);
        }

        private static String[] decodeRecords(String payload) {
            if (payload == null || payload.isBlank()) {
                return new String[0];
            }
            return payload.split(RECORD_SEPARATOR);
        }

        private static String encodeProperties(Map<String, String> properties) {
            if (properties == null || properties.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (builder.length() > 0) {
                    builder.append(";");
                }
                builder.append(entry.getKey()).append("=").append(entry.getValue());
            }
            return builder.toString();
        }

        private static Map<String, String> decodeProperties(String payload) {
            if (payload == null || payload.isBlank()) {
                return Map.of();
            }
            Map<String, String> result = new HashMap<>();
            String[] pairs = payload.split(";");
            for (String pair : pairs) {
                if (pair.isBlank()) {
                    continue;
                }
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    result.put(parts[0], parts[1]);
                }
            }
            return result;
        }
    }

    private static double agreementLow(double buyerStart, double buyerMax, double sellerMin, double sellerAsk) {
        return Math.max(Math.min(buyerStart, buyerMax), Math.min(sellerMin, sellerAsk));
    }

    private static double agreementHigh(double buyerStart, double buyerMax, double sellerMin, double sellerAsk) {
        return Math.min(Math.max(buyerStart, buyerMax), Math.max(sellerMin, sellerAsk));
    }

    private static boolean isInAgreementRange(double amount, double buyerStart, double buyerMax,
                                              double sellerMin, double sellerAsk) {
        double low = agreementLow(buyerStart, buyerMax, sellerMin, sellerAsk);
        double high = agreementHigh(buyerStart, buyerMax, sellerMin, sellerAsk);
        return low <= high && amount >= low && amount <= high;
    }

    public static final class DashboardBrokerAgent extends Agent {
        private static final long serialVersionUID = 1L;

        private final AtomicInteger listingSequence = new AtomicInteger(1);
        private final AtomicInteger sessionSequence = new AtomicInteger(1);
        private final Map<String, ListingRecord> listings = new LinkedHashMap<>();
        private final Map<String, PendingNegotiation> pendingNegotiations = new HashMap<>();
        private final Set<String> lockedListings = new HashSet<>();
        private FipaAutoNegotiationDashboard dashboard;

        @Override
        protected void setup() {
            dashboard = dashboardFromArgs(getArguments());
            dashboard.appendLog("Broker agent ready.");
            addBehaviour(new BrokerRouter());
        }

        private final class BrokerRouter extends CyclicBehaviour {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                ACLMessage message = receive();
                if (message == null) {
                    block();
                    return;
                }

                String conversationId = message.getConversationId();
                if ("dealer-listings".equals(conversationId)) {
                    handleDealerListings(message);
                } else if ("buyer-search".equals(conversationId)) {
                    handleBuyerSearch(message);
                } else if ("negotiation-request".equals(conversationId)) {
                    handleNegotiationRequest(message);
                } else if ("dealer-interest-response".equals(conversationId)) {
                    handleDealerInterestResponse(message);
                } else if ("deal-completed".equals(conversationId)) {
                    handleDealCompleted(message);
                } else if ("negotiation-failed".equals(conversationId)) {
                    handleNegotiationFailed(message);
                }
            }
        }

        private void handleDealerListings(ACLMessage message) {
            String dealerName = message.getSender().getLocalName();
            for (String record : MessageCodec.decodeRecords(message.getContent())) {
                String[] parts = MessageCodec.decodeFields(record, 4);
                String listingId = "listing-" + listingSequence.getAndIncrement();
                String propertyPayload = parts.length > 4 ? parts[4] : "";
                Map<String, String> dealerVars = decodeProperties(propertyPayload);
                ListingRecord listing = new ListingRecord(
                        listingId,
                        dealerName,
                        parts[0],
                        parts[1],
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        dealerVars
                );
                listings.put(listingId, listing);
                dashboard.appendLog("Broker received " + listing.brand + " " + listing.type
                        + " from " + dealerName + " at " + formatMoney(listing.price) + ".");
            }
        }

        private void handleBuyerSearch(ACLMessage message) {
            String[] request = MessageCodec.decodeFields(message.getContent(), 4);
            String brand = request[0];
            String type = request[1];
            double buyerStart = Double.parseDouble(request[2]);
            double buyerMax = Double.parseDouble(request[3]);
            String propertyPayload = request.length > 4 ? request[4] : "";
            Map<String, String> buyerVars = decodeProperties(propertyPayload);

            List<ListingRecord> matches = new ArrayList<>();
            for (ListingRecord listing : listings.values()) {
                if (!lockedListings.contains(listing.id)
                        && listing.brand.equalsIgnoreCase(brand)
                        && listing.type.equalsIgnoreCase(type)
                        && rangesOverlap(buyerStart, buyerMax, listing.minAcceptPrice, listing.price)) {
                    matches.add(listing);
                }
            }
            matches.sort(Comparator.comparingDouble(listing -> listing.price));

            List<String> encodedMatches = new ArrayList<>();
            for (ListingRecord listing : matches) {
                encodedMatches.add(MessageCodec.encodeFields(
                        listing.id,
                        listing.brand,
                        listing.type,
                        Double.toString(listing.price),
                        listing.dealerName,
                        Double.toString(listing.minAcceptPrice),
                        encodeProperties(listing.dealerVariables)
                ));
            }

            ACLMessage reply = message.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("buyer-search-result");
            reply.setContent(MessageCodec.encodeRecords(encodedMatches.toArray(new String[0])));
            send(reply);
        }

        private void handleNegotiationRequest(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 3);
            String listingId = parts[0];
            ListingRecord listing = listings.get(listingId);
            String buyerName = message.getSender().getLocalName();
            String propertyPayload = parts.length > 3 ? parts[3] : "";
            Map<String, String> buyerVars = decodeProperties(propertyPayload);

            if (listing == null || lockedListings.contains(listingId)) {
                sendBuyerUpdate(buyerName, "none", "FAILED", "0", "Listing is no longer available.");
                dashboard.recordResult("no-session-" + buyerName + "-" + listingId, buyerName,
                        listing != null ? listing.dealerName : "none",
                        listing != null ? listing.brand : "unknown",
                        listing != null ? listing.type : "unknown",
                        "FAILED: Listing unavailable", 0, 0, 0);
                return;
            }

            double buyerStart = Double.parseDouble(parts[1]);
            double buyerMax = Double.parseDouble(parts[2]);
            if (!rangesOverlap(buyerStart, buyerMax, listing.minAcceptPrice, listing.price)) {
                sendBuyerUpdate(buyerName, "none", "FAILED", "0", "No agreement range.");
                dashboard.recordResult("no-session-" + buyerName + "-" + listingId, buyerName,
                        listing.dealerName,
                        listing.brand,
                        listing.type,
                        "FAILED: No agreement range", 0, 0, 0);
                return;
            }

            lockedListings.add(listingId);
            pendingNegotiations.put(pendingKey(listingId, buyerName),
                    new PendingNegotiation(listingId, buyerName, buyerStart, buyerMax, buyerVars));

            ACLMessage interest = new ACLMessage(ACLMessage.INFORM);
            interest.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
            interest.setConversationId("buyer-interest");
            interest.setContent(MessageCodec.encodeFields(
                    listingId,
                    buyerName,
                    listing.brand,
                    listing.type,
                    Double.toString(buyerStart),
                    Double.toString(buyerMax)
            ));
            send(interest);
        }

        private void handleDealerInterestResponse(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 2);
            String listingId = parts[0];
            String buyerName = parts[1];
            PendingNegotiation pending = pendingNegotiations.remove(pendingKey(listingId, buyerName));
            ListingRecord listing = listings.get(listingId);

            if (pending == null || listing == null) {
                return;
            }

            if (message.getPerformative() != ACLMessage.AGREE) {
                lockedListings.remove(listingId);
                sendBuyerUpdate(buyerName, "none", "FAILED", "0", "Dealer declined.");
                dashboard.recordResult("no-session-" + buyerName + "-" + listingId, buyerName,
                        listing.dealerName,
                        listing.brand,
                        listing.type,
                        "FAILED: Dealer declined", 0, 0, 0);
                return;
            }

            String sessionId = "session-" + sessionSequence.getAndIncrement();
            dashboard.listingSessionIds.put(listingId, sessionId);
            dashboard.recordSessionStart(
                    sessionId,
                    buyerName,
                    listing.dealerName,
                    listing.brand,
                    listing.type,
                    pending.buyerStart,
                    pending.buyerMax,
                    listing.price,
                    listing.minAcceptPrice,
                    pending.buyerVariables,
                    listing.dealerVariables
            );

            ACLMessage toBuyer = new ACLMessage(ACLMessage.INFORM);
            toBuyer.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
            toBuyer.setConversationId("negotiation-start");
            toBuyer.setContent(MessageCodec.encodeFields(
                    sessionId,
                    listingId,
                    listing.brand,
                    listing.type,
                    Double.toString(listing.price),
                    listing.dealerName,
                    Double.toString(listing.minAcceptPrice),
                    encodeProperties(pending.buyerVariables),
                    encodeProperties(listing.dealerVariables)
            ));
            send(toBuyer);

            ACLMessage toDealer = new ACLMessage(ACLMessage.INFORM);
            toDealer.addReceiver(new AID(listing.dealerName, AID.ISLOCALNAME));
            toDealer.setConversationId("negotiation-start");
            toDealer.setContent(MessageCodec.encodeFields(
                    sessionId,
                    buyerName,
                    listing.brand,
                    listing.type,
                    Double.toString(listing.price),
                    listingId,
                    Double.toString(listing.minAcceptPrice),
                    Double.toString(pending.buyerStart),
                    Double.toString(pending.buyerMax),
                    encodeProperties(pending.buyerVariables),
                    encodeProperties(listing.dealerVariables)
            ));
            send(toDealer);
        }

        private void handleDealCompleted(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 6);
            String sessionId;
            String listingId;
            double finalPrice;
            double commission;
            String buyerName;
            int rounds = 0;
            if (parts.length == 6) {
                sessionId = parts[0];
                listingId = parts[1];
                finalPrice = Double.parseDouble(parts[2]);
                commission = Double.parseDouble(parts[3]);
                buyerName = parts[4];
                rounds = Integer.parseInt(parts[5]);
            } else if (parts.length == 4) {
                listingId = parts[0];
                finalPrice = Double.parseDouble(parts[1]);
                commission = Double.parseDouble(parts[2]);
                buyerName = parts[3];
                sessionId = dashboard.listingSessionIds.getOrDefault(listingId, "session-" + listingId);
            } else {
                return;
            }

            ListingRecord listing = listings.remove(listingId);
            lockedListings.remove(listingId);
            dashboard.listingSessionIds.remove(listingId);
            if (listing != null) {
                dashboard.recordResult(sessionId, buyerName, listing.dealerName, listing.brand, listing.type,
                        "DEAL", finalPrice, commission, rounds);
            }
        }

        private void handleNegotiationFailed(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 5);
            String sessionId = parts[0];
            String listingId = parts[1];
            String buyerName = parts[2];
            String reason = parts[3];
            int rounds = Integer.parseInt(parts[4]);

            ListingRecord listing = listings.get(listingId);
            lockedListings.remove(listingId);
            if (listing != null) {
                dashboard.recordResult(sessionId, buyerName, listing.dealerName, listing.brand, listing.type,
                        "FAILED: " + reason, 0, 0, rounds);
            }
        }

        private void sendBuyerUpdate(String buyerName, String sessionId, String status, String amount, String note) {
            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            message.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
            message.setConversationId("negotiation-update");
            message.setContent(MessageCodec.encodeFields(sessionId, status, amount, note));
            send(message);
        }

        private String pendingKey(String listingId, String buyerName) {
            return listingId + "|" + buyerName;
        }

        private boolean rangesOverlap(double buyerLow, double buyerHigh, double dealerLow, double dealerHigh) {
            double normalizedBuyerLow = Math.min(buyerLow, buyerHigh);
            double normalizedBuyerHigh = Math.max(buyerLow, buyerHigh);
            double normalizedDealerLow = Math.min(dealerLow, dealerHigh);
            double normalizedDealerHigh = Math.max(dealerLow, dealerHigh);
            return Math.max(normalizedBuyerLow, normalizedDealerLow)
                    <= Math.min(normalizedBuyerHigh, normalizedDealerHigh);
        }
    }

    public static final class DashboardDealerAgent extends Agent {
        private static final long serialVersionUID = 1L;

        private final AID broker = new AID("broker", AID.ISLOCALNAME);
        private final Map<String, DealerSession> sessions = new HashMap<>();
        private FipaAutoNegotiationDashboard dashboard;
        private DealerProfile profile;

        @Override
        protected void setup() {
            Object[] args = getArguments();
            dashboard = dashboardFromArgs(args);
            profile = (DealerProfile) args[1];

            addBehaviour(new DealerRouter());
            addBehaviour(new WakerBehaviour(this, profile.startDelayMillis) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onWake() {
                    submitListings();
                }
            });
        }

        private void submitListings() {
            List<String> records = new ArrayList<>();
            for (CarSpec car : profile.cars) {
                records.add(MessageCodec.encodeFields(
                        car.brand,
                        car.type,
                        Double.toString(car.sellingPrice),
                        Double.toString(car.minimumPrice),
                        encodeProperties(profile.variables)
                ));
            }

            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            message.addReceiver(broker);
            message.setConversationId("dealer-listings");
            message.setContent(MessageCodec.encodeRecords(records.toArray(new String[0])));
            send(message);
            dashboard.appendLog(getLocalName() + " submitted " + records.size() + " car listings.");
        }

        private final class DealerRouter extends CyclicBehaviour {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                ACLMessage message = receive();
                if (message == null) {
                    block();
                    return;
                }

                if ("buyer-interest".equals(message.getConversationId())) {
                    handleBuyerInterest(message);
                } else if ("negotiation-start".equals(message.getConversationId())) {
                    handleNegotiationStart(message);
                } else if ("negotiation-action".equals(message.getConversationId())) {
                    handleNegotiationAction(message);
                }
            }
        }

        private void handleBuyerInterest(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 6);
            String listingId = parts[0];
            String buyerName = parts[1];

            ACLMessage response = new ACLMessage(ACLMessage.AGREE);
            response.addReceiver(broker);
            response.setConversationId("dealer-interest-response");
            response.setContent(MessageCodec.encodeFields(listingId, buyerName));
            send(response);
            dashboard.appendLog(getLocalName() + " accepted buyer interest from " + buyerName + ".");
        }

        private void handleNegotiationStart(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 9);
            String sessionId = parts[0];
            String buyerName = parts[1];
            double askingPrice = Double.parseDouble(parts[4]);
            String listingId = parts[5];
            double minimumPrice = Double.parseDouble(parts[6]);
            double buyerStart = Double.parseDouble(parts[7]);
            double buyerMax = Double.parseDouble(parts[8]);

            DealerSession session = new DealerSession(
                    sessionId,
                    listingId,
                    buyerName,
                    askingPrice,
                    minimumPrice,
                    buyerStart,
                    buyerMax
            );
            sessions.put(sessionId, session);

            addBehaviour(new WakerBehaviour(this, 300) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onWake() {
                    sendOffer(session, askingPrice, "Initial dealer ask");
                }
            });
        }

        private void handleNegotiationAction(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 3);
            String sessionId = parts[0];
            String action = parts[1];
            double amount = Double.parseDouble(parts[2]);
            DealerSession session = sessions.get(sessionId);
            if (session == null) {
                return;
            }

            session.events++;
            if ("ACCEPT".equals(action)) {
                if (isInAgreementRange(amount, session.buyerStart, session.buyerMax,
                        session.minimumPrice, session.askingPrice)) {
                    reportDeal(session, amount);
                } else {
                    reportFailure(session, "Accepted price outside agreement range");
                }
                sessions.remove(sessionId);
                return;
            }

            if ("CANCEL".equals(action) || "REJECT".equals(action)) {
                reportFailure(session, "Buyer cancelled");
                sessions.remove(sessionId);
                return;
            }

            if (!"COUNTER".equals(action)) {
                return;
            }

            double scheduled = nextDealerPrice(session);
            if (amount >= scheduled && isInAgreementRange(amount, session.buyerStart, session.buyerMax,
                    session.minimumPrice, session.askingPrice)) {
                dashboard.recordOffer(sessionId, getLocalName(), amount, "Dealer accepts buyer offer");

                ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                accept.addReceiver(new AID(session.buyerName, AID.ISLOCALNAME));
                accept.setConversationId("negotiation-action");
                accept.setContent(MessageCodec.encodeFields(sessionId, "ACCEPT", Double.toString(amount)));
                send(accept);

                reportDeal(session, amount);
                sessions.remove(sessionId);
                return;
            }

            if (session.dealerOffersSent >= session.maxRounds) {
                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                reject.addReceiver(new AID(session.buyerName, AID.ISLOCALNAME));
                reject.setConversationId("negotiation-action");
                reject.setContent(MessageCodec.encodeFields(sessionId, "REJECT", "0"));
                send(reject);

                reportFailure(session, "Dealer minimum not reached");
                sessions.remove(sessionId);
                return;
            }

            sendOffer(session, scheduled, "Dealer counter offer");
        }

        private void sendOffer(DealerSession session, double amount, String note) {
            ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);
            message.addReceiver(new AID(session.buyerName, AID.ISLOCALNAME));
            message.setConversationId("negotiation-action");
            message.setContent(MessageCodec.encodeFields(session.sessionId, "COUNTER", Double.toString(amount)));
            send(message);

            session.dealerOffersSent++;
            session.events++;
            dashboard.recordOffer(session.sessionId, getLocalName(), amount, note);
        }

        private double nextDealerPrice(DealerSession session) {
            double t = Math.min(1.0, (double) session.dealerOffersSent / session.maxRounds);
            return session.askingPrice + (session.minimumPrice - session.askingPrice) * t;
        }

        private void reportDeal(DealerSession session, double finalPrice) {
            double commission = finalPrice * 0.05;
            ACLMessage report = new ACLMessage(ACLMessage.INFORM);
            report.addReceiver(broker);
            report.setConversationId("deal-completed");
            report.setContent(MessageCodec.encodeFields(
                    session.sessionId,
                    session.listingId,
                    Double.toString(finalPrice),
                    Double.toString(commission),
                    session.buyerName,
                    Integer.toString(session.events)
            ));
            send(report);
        }

        private void reportFailure(DealerSession session, String reason) {
            ACLMessage report = new ACLMessage(ACLMessage.INFORM);
            report.addReceiver(broker);
            report.setConversationId("negotiation-failed");
            report.setContent(MessageCodec.encodeFields(
                    session.sessionId,
                    session.listingId,
                    session.buyerName,
                    reason,
                    Integer.toString(session.events)
            ));
            send(report);
        }
    }

    public static final class DashboardBuyerAgent extends Agent {
        private static final long serialVersionUID = 1L;

        private final AID broker = new AID("broker", AID.ISLOCALNAME);
        private final Map<String, BuyerSession> sessions = new HashMap<>();
        private FipaAutoNegotiationDashboard dashboard;
        private BuyerProfile profile;

        @Override
        protected void setup() {
            Object[] args = getArguments();
            dashboard = dashboardFromArgs(args);
            profile = (BuyerProfile) args[1];

            addBehaviour(new BuyerRouter());
            addBehaviour(new WakerBehaviour(this, profile.startDelayMillis) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onWake() {
                    sendSearchRequest();
                }
            });
        }

        private void sendSearchRequest() {
            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.addReceiver(broker);
            request.setConversationId("buyer-search");
            request.setContent(MessageCodec.encodeFields(
                    profile.brand,
                    profile.type,
                    Double.toString(profile.startingPrice),
                    Double.toString(profile.maximumPrice),
                    encodeProperties(profile.variables)
            ));
            send(request);
            dashboard.appendLog(getLocalName() + " searches for " + profile.brand + " " + profile.type + ".");
        }

        private final class BuyerRouter extends CyclicBehaviour {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                ACLMessage message = receive();
                if (message == null) {
                    block();
                    return;
                }

                if ("buyer-search-result".equals(message.getConversationId())) {
                    handleSearchResult(message);
                } else if ("negotiation-start".equals(message.getConversationId())) {
                    handleNegotiationStart(message);
                } else if ("negotiation-action".equals(message.getConversationId())) {
                    handleNegotiationAction(message);
                } else if ("negotiation-update".equals(message.getConversationId())) {
                    dashboard.appendLog(getLocalName() + " update: " + message.getContent());
                }
            }
        }

        private void handleSearchResult(ACLMessage message) {
            String[] records = MessageCodec.decodeRecords(message.getContent());
            if (records.length == 0) {
                dashboard.appendLog(getLocalName() + " found no matching cars.");
                dashboard.recordResult("no-search-" + getLocalName(), getLocalName(), "none",
                        profile.brand, profile.type,
                        "FAILED: No matching listings", 0, 0, 0);
                return;
            }

            int selectedIndex = Math.min(profile.choiceIndex, records.length - 1);
            String[] parts = MessageCodec.decodeFields(records[selectedIndex], 6);
            String listingId = parts[0];
            String dealerName = parts[4];

            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.addReceiver(broker);
            request.setConversationId("negotiation-request");
            request.setContent(MessageCodec.encodeFields(
                    listingId,
                    Double.toString(profile.startingPrice),
                    Double.toString(profile.maximumPrice)
            ));
            send(request);
            dashboard.appendLog(getLocalName() + " selected " + parts[1] + " " + parts[2]
                    + " from " + dealerName + ".");
        }

        private void handleNegotiationStart(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 7);
            String sessionId = parts[0];
            String dealerName = parts[5];
            double sellerAsk = Double.parseDouble(parts[4]);
            double sellerMin = Double.parseDouble(parts[6]);
            BuyerSession session = new BuyerSession(
                    sessionId,
                    dealerName,
                    profile.startingPrice,
                    profile.maximumPrice,
                    sellerAsk,
                    sellerMin
            );
            sessions.put(sessionId, session);
        }

        private void handleNegotiationAction(ACLMessage message) {
            String[] parts = MessageCodec.decodeFields(message.getContent(), 3);
            String sessionId = parts[0];
            String action = parts[1];
            double amount = Double.parseDouble(parts[2]);
            BuyerSession session = sessions.get(sessionId);
            if (session == null) {
                return;
            }

            session.events++;
            if ("ACCEPT".equals(action)) {
                dashboard.appendLog(getLocalName() + " deal accepted at " + formatMoney(amount) + ".");
                sessions.remove(sessionId);
                return;
            }

            if ("REJECT".equals(action) || "CANCEL".equals(action)) {
                dashboard.appendLog(getLocalName() + " negotiation ended without deal.");
                sessions.remove(sessionId);
                return;
            }

            if (!"COUNTER".equals(action)) {
                return;
            }

            double offer = nextBuyerPrice(session);
            if (amount <= offer && isInAgreementRange(amount, session.startingPrice, session.maximumPrice,
                    session.sellerMin, session.sellerAsk)) {
                sendAction(session, "ACCEPT", amount, ACLMessage.ACCEPT_PROPOSAL, "Buyer accepts dealer offer");
                sessions.remove(sessionId);
                return;
            }

            if (session.buyerOffersSent >= session.maxRounds) {
                sendAction(session, "CANCEL", 0, ACLMessage.REJECT_PROPOSAL, "Buyer cancels at maximum rounds");
                sessions.remove(sessionId);
                return;
            }

            sendAction(session, "COUNTER", offer, ACLMessage.PROPOSE, "Buyer counter offer");
        }

        private void sendAction(BuyerSession session, String action, double amount, int performative, String note) {
            ACLMessage message = new ACLMessage(performative);
            message.addReceiver(new AID(session.dealerName, AID.ISLOCALNAME));
            message.setConversationId("negotiation-action");
            message.setContent(MessageCodec.encodeFields(session.sessionId, action, Double.toString(amount)));
            send(message);

            session.events++;
            if ("COUNTER".equals(action)) {
                session.buyerOffersSent++;
                dashboard.recordOffer(session.sessionId, getLocalName(), amount, note);
            } else if ("ACCEPT".equals(action)) {
                dashboard.recordOffer(session.sessionId, getLocalName(), amount, note);
            }
        }

        private double nextBuyerPrice(BuyerSession session) {
            double t = Math.min(1.0, (double) session.buyerOffersSent / session.maxRounds);
            return session.startingPrice + (session.maximumPrice - session.startingPrice) * t;
        }
    }

    private static final class NegotiationGraphPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private SessionView session;

        private NegotiationGraphPanel() {
            setPreferredSize(new Dimension(520, 420));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
        }

        private void setSession(SessionView session) {
            this.session = session;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int left = 90;
            int right = 150;
            int top = 52;
            int bottom = 70;

            g.setColor(new Color(40, 40, 40));
            g.setFont(new Font("Segoe UI", Font.BOLD, 16));
            g.drawString(session == null ? "Automated Negotiation" : session.label(), left, 24);

            if (session == null || session.points.isEmpty()) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                g.drawString("The graph will appear after auto negotiation starts.", left, height / 2);
                g.dispose();
                return;
            }

            double min = Math.min(Math.min(session.buyerStart, session.buyerMax),
                    Math.min(session.sellerMin, session.sellerAsk));
            double max = Math.max(Math.max(session.buyerStart, session.buyerMax),
                    Math.max(session.sellerMin, session.sellerAsk));
            for (OfferPoint point : session.points) {
                min = Math.min(min, point.amount);
                max = Math.max(max, point.amount);
            }
            if (max <= min) {
                max = min + 1;
            }
            double padding = (max - min) * 0.12;
            min -= padding;
            max += padding;

            int chartWidth = width - left - right;
            int chartHeight = height - top - bottom;
            double agreementLow = agreementLow(session.buyerStart, session.buyerMax, session.sellerMin, session.sellerAsk);
            double agreementHigh = agreementHigh(session.buyerStart, session.buyerMax, session.sellerMin, session.sellerAsk);

            g.setColor(new Color(230, 230, 230));
            for (int i = 0; i <= 4; i++) {
                int y = top + (chartHeight * i / 4);
                g.drawLine(left, y, width - right, y);
            }

            if (agreementLow <= agreementHigh) {
                int yTop = priceToY(agreementHigh, top, chartHeight, min, max);
                int yBottom = priceToY(agreementLow, top, chartHeight, min, max);
                g.setColor(new Color(255, 219, 173));
                g.fillRect(left, yTop, chartWidth, Math.max(2, yBottom - yTop));
                g.setColor(new Color(180, 100, 24));
                g.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g.drawString("Agreement Range", left + chartWidth / 2 - 58, yTop + Math.max(18, (yBottom - yTop) / 2));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g.drawString(formatMoney(agreementLow) + " - " + formatMoney(agreementHigh),
                        left + chartWidth / 2 - 72, yTop + Math.max(34, (yBottom - yTop) / 2 + 16));
            }

            g.setColor(new Color(60, 60, 60));
            g.drawLine(left, top, left, top + chartHeight);
            g.drawLine(left, top + chartHeight, width - right, top + chartHeight);

            drawRangeLine(g, session.buyerMax, "RV Buyer", new Color(42, 120, 194),
                    left, top, chartWidth, chartHeight, min, max, true);
            drawRangeLine(g, session.sellerMin, "RV Seller", new Color(190, 70, 70),
                    left, top, chartWidth, chartHeight, min, max, true);
            drawRangeLine(g, session.sellerAsk, "Seller Ask", new Color(190, 70, 70),
                    left, top, chartWidth, chartHeight, min, max, false);
            drawRangeLine(g, session.buyerStart, "Buyer Start", new Color(42, 120, 194),
                    left, top, chartWidth, chartHeight, min, max, false);

            drawSeries(g, "dealer", new Color(213, 72, 72), left, top, chartWidth, chartHeight, min, max);
            drawSeries(g, "buyer", new Color(48, 112, 196), left, top, chartWidth, chartHeight, min, max);
            drawFinalDeal(g, left, top, chartWidth, chartHeight, min, max);

            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.setColor(new Color(60, 60, 60));
            g.drawString(formatMoney(max), 8, top + 4);
            g.drawString(formatMoney(min), 8, top + chartHeight);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.drawString("Rounds", left + chartWidth / 2 - 20, height - 16);
            g.drawString("Price", 18, top - 10);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.drawString("Seller Acceptance Range: " + formatMoney(session.sellerMin)
                    + " - " + formatMoney(session.sellerAsk), left, height - 42);
            g.drawString("Buyer Acceptance Range: " + formatMoney(session.buyerStart)
                    + " - " + formatMoney(session.buyerMax), left, height - 26);
            g.drawString("Buyer tactic: " + session.buyerTactic + " | Dealer tactic: " + session.dealerTactic,
                    left, height - 56);

            if (!session.buyerVariables.isEmpty() || !session.dealerVariables.isEmpty()) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g.drawString("Buyer vars: " + formatVariableSummary(session.buyerVariables), left, height - 76);
                g.drawString("Dealer vars: " + formatVariableSummary(session.dealerVariables), left, height - 64);
            }

            g.setColor(new Color(213, 72, 72));
            g.fillRect(width - 136, height - 56, 14, 8);
            g.setColor(new Color(60, 60, 60));
            g.drawString("Dealer curve", width - 116, height - 48);
            g.setColor(new Color(48, 112, 196));
            g.fillRect(width - 136, height - 38, 14, 8);
            g.setColor(new Color(60, 60, 60));
            g.drawString("Buyer curve", width - 116, height - 30);
            g.setColor(new Color(255, 219, 173));
            g.fillRect(width - 136, height - 20, 14, 8);
            g.setColor(new Color(60, 60, 60));
            g.drawString("Agreement", width - 116, height - 12);

            g.dispose();
        }

        private void drawSeries(Graphics2D g, String actorToken, Color color, int left, int top,
                                int chartWidth, int chartHeight, double min, double max) {
            List<OfferPoint> points = new ArrayList<>();
            for (OfferPoint point : session.points) {
                if (point.actor.toLowerCase(Locale.ROOT).contains(actorToken)) {
                    points.add(point);
                }
            }
            if (points.isEmpty()) {
                return;
            }

            g.setColor(color);
            g.setStroke(new BasicStroke(2f));
            int previousX = -1;
            int previousY = -1;
            int totalEvents = Math.max(1, session.points.size() - 1);

            for (OfferPoint point : points) {
                int x = left + (int) Math.round(chartWidth * ((double) point.sequence - 1) / totalEvents);
                int y = priceToY(point.amount, top, chartHeight, min, max);

                if (previousX >= 0) {
                    g.drawLine(previousX, previousY, x, y);
                }
                g.fillOval(x - 4, y - 4, 8, 8);
                g.drawString(String.format(Locale.US, "%,.0f", point.amount), x + 6, y - 6);

                previousX = x;
                previousY = y;
            }
        }

        private void drawFinalDeal(Graphics2D g, int left, int top, int chartWidth, int chartHeight,
                                   double min, double max) {
            if (!"DEAL".equals(session.result) || session.finalPrice <= 0) {
                return;
            }

            int x = left + chartWidth;
            int y = priceToY(session.finalPrice, top, chartHeight, min, max);
            g.setColor(session.dealInsideAgreement ? new Color(34, 150, 84) : new Color(190, 40, 40));
            g.setStroke(new BasicStroke(2.5f));
            g.drawLine(x - 7, y - 7, x + 7, y + 7);
            g.drawLine(x - 7, y + 7, x + 7, y - 7);
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g.drawString(session.dealInsideAgreement ? "Final deal" : "Outside range", x - 72, y - 10);
        }

        private void drawRangeLine(Graphics2D g, double price, String label, Color color, int left, int top,
                                   int chartWidth, int chartHeight, double min, double max, boolean reservation) {
            int y = priceToY(price, top, chartHeight, min, max);
            Stroke oldStroke = g.getStroke();
            g.setColor(color);
            if (reservation) {
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        0, new float[]{5f, 5f}, 0));
            } else {
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        0, new float[]{2f, 5f}, 0));
            }
            g.drawLine(left, y, left + chartWidth, y);
            g.setStroke(oldStroke);
            g.setFont(new Font("Segoe UI", reservation ? Font.BOLD : Font.PLAIN, 11));
            g.drawString(label + " " + formatMoney(price), left + chartWidth + 10, y + 4);
        }

        private int priceToY(double price, int top, int chartHeight, double min, double max) {
            return top + (int) Math.round(chartHeight * (1.0 - ((price - min) / (max - min))));
        }
    }

    private static final class SimulationPlan {
        private final List<DealerProfile> dealers;
        private final List<BuyerProfile> buyers;

        private SimulationPlan(List<DealerProfile> dealers, List<BuyerProfile> buyers) {
            this.dealers = dealers;
            this.buyers = buyers;
        }
    }

    private static final class DealerProfile implements Serializable {
        private static final long serialVersionUID = 1L;
        private String agentName;
        private final long startDelayMillis;
        private final List<CarSpec> cars;
        private final Map<String, String> variables;

        private DealerProfile(String agentName, long startDelayMillis, List<CarSpec> cars,
                              Map<String, String> variables) {
            this.agentName = agentName;
            this.startDelayMillis = startDelayMillis;
            this.cars = new ArrayList<>(cars == null ? List.of() : cars);
            this.variables = new HashMap<>(variables == null ? Map.of() : variables);
        }
    }

    private static final class BuyerProfile implements Serializable {
        private static final long serialVersionUID = 1L;
        private String agentName;
        private String brand;
        private String type;
        private final double startingPrice;
        private final double maximumPrice;
        private final int choiceIndex;
        private final long startDelayMillis;
        private final Map<String, String> variables;

        private BuyerProfile(String agentName, String brand, String type, double startingPrice,
                             double maximumPrice, int choiceIndex, long startDelayMillis,
                             Map<String, String> variables) {
            this.agentName = agentName;
            this.brand = brand;
            this.type = type;
            this.startingPrice = startingPrice;
            this.maximumPrice = maximumPrice;
            this.choiceIndex = choiceIndex;
            this.startDelayMillis = startDelayMillis;
            this.variables = new HashMap<>(variables == null ? Map.of() : variables);
        }
    }

    private static final class CarSpec implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String brand;
        private final String type;
        private final double sellingPrice;
        private final double minimumPrice;

        private CarSpec(String brand, String type, double sellingPrice, double minimumPrice) {
            this.brand = brand;
            this.type = type;
            this.sellingPrice = sellingPrice;
            this.minimumPrice = minimumPrice;
        }
    }

    private static final class ListingRecord {
        private final String id;
        private final String dealerName;
        private final String brand;
        private final String type;
        private final double price;
        private final double minAcceptPrice;
        private final Map<String, String> dealerVariables;

        private ListingRecord(String id, String dealerName, String brand, String type,
                              double price, double minAcceptPrice, Map<String, String> dealerVariables) {
            this.id = id;
            this.dealerName = dealerName;
            this.brand = brand;
            this.type = type;
            this.price = price;
            this.minAcceptPrice = minAcceptPrice;
            this.dealerVariables = dealerVariables == null ? Map.of() : dealerVariables;
        }
    }

    private static final class PendingNegotiation {
        private final String listingId;
        private final String buyerName;
        private final double buyerStart;
        private final double buyerMax;
        private final Map<String, String> buyerVariables;

        private PendingNegotiation(String listingId, String buyerName, double buyerStart, double buyerMax,
                                   Map<String, String> buyerVariables) {
            this.listingId = listingId;
            this.buyerName = buyerName;
            this.buyerStart = buyerStart;
            this.buyerMax = buyerMax;
            this.buyerVariables = buyerVariables == null ? Map.of() : buyerVariables;
        }
    }

    private static final class DealerSession {
        private final String sessionId;
        private final String listingId;
        private final String buyerName;
        private final double askingPrice;
        private final double minimumPrice;
        private final double buyerStart;
        private final double buyerMax;
        private final int maxRounds = 6;
        private int dealerOffersSent;
        private int events;

        private DealerSession(String sessionId, String listingId, String buyerName,
                              double askingPrice, double minimumPrice, double buyerStart, double buyerMax) {
            this.sessionId = sessionId;
            this.listingId = listingId;
            this.buyerName = buyerName;
            this.askingPrice = askingPrice;
            this.minimumPrice = minimumPrice;
            this.buyerStart = buyerStart;
            this.buyerMax = buyerMax;
        }
    }

    private static final class BuyerSession {
        private final String sessionId;
        private final String dealerName;
        private final double startingPrice;
        private final double maximumPrice;
        private final double sellerAsk;
        private final double sellerMin;
        private final int maxRounds = 6;
        private int buyerOffersSent;
        private int events;

        private BuyerSession(String sessionId, String dealerName, double startingPrice, double maximumPrice,
                             double sellerAsk, double sellerMin) {
            this.sessionId = sessionId;
            this.dealerName = dealerName;
            this.startingPrice = startingPrice;
            this.maximumPrice = maximumPrice;
            this.sellerAsk = sellerAsk;
            this.sellerMin = sellerMin;
        }
    }

    private static final class SessionView {
        private final String sessionId;
        private final String buyer;
        private final String dealer;
        private final String brand;
        private final String type;
        private final double buyerStart;
        private final double buyerMax;
        private final double sellerAsk;
        private final double sellerMin;
        private final Map<String, String> buyerVariables;
        private final Map<String, String> dealerVariables;
        private final String buyerTactic;
        private final String dealerTactic;
        private final List<OfferPoint> points = new ArrayList<>();
        private String result = "";
        private double finalPrice;
        private boolean dealInsideAgreement;

        private SessionView(String sessionId, String buyer, String dealer, String brand, String type,
                            double buyerStart, double buyerMax, double sellerAsk, double sellerMin,
                            Map<String, String> buyerVariables, Map<String, String> dealerVariables,
                            String buyerTactic, String dealerTactic) {
            this.sessionId = sessionId;
            this.buyer = buyer;
            this.dealer = dealer;
            this.brand = brand;
            this.type = type;
            this.buyerStart = buyerStart;
            this.buyerMax = buyerMax;
            this.sellerAsk = sellerAsk;
            this.sellerMin = sellerMin;
            this.buyerVariables = buyerVariables == null ? Map.of() : buyerVariables;
            this.dealerVariables = dealerVariables == null ? Map.of() : dealerVariables;
            this.buyerTactic = buyerTactic == null ? "none" : buyerTactic;
            this.dealerTactic = dealerTactic == null ? "none" : dealerTactic;
        }

        private String label() {
            return sessionId + " | " + buyer + " vs " + dealer + " | " + brand + " " + type;
        }
    }

    private static final class OfferPoint {
        private final int sequence;
        private final String actor;
        private final double amount;
        private final String note;

        private OfferPoint(int sequence, String actor, double amount, String note) {
            this.sequence = sequence;
            this.actor = actor;
            this.amount = amount;
            this.note = note;
        }
    }
}
