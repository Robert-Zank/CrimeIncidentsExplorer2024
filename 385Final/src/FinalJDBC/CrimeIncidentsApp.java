package FinalJDBC;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Date;

public class CrimeIncidentsApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new MainFrame();
        });
    }
}

class MainFrame extends JFrame {
    private final FilterPanel filterPanel;
    private final ResultsPanel resultsPanel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public MainFrame() {
        super("Crime Incidents Explorer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // Menu Bar
        JMenuBar menuBar = new JMenuBar();
        JMenu reportMenu = new JMenu("Reports");
        reportMenu.add(createMenuItem("Top N Blocks", e -> {
            try {
                fetchTopNBlocks();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }));
        reportMenu.add(createMenuItem("Top N Offenses", e -> {
            try {
                fetchTopNOffenses();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }));
        reportMenu.add(createMenuItem("Avg Duration by Offense", e -> {
            try {
                fetchAvgDuration();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }));
        menuBar.add(reportMenu);

        JMenu historyMenu = new JMenu("History");
        historyMenu.add(createMenuItem("View Query History", e -> {
            try {
                fetchQueryHistory();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }));
        menuBar.add(historyMenu);

        setJMenuBar(menuBar);

        // Filter Panel
        filterPanel = new FilterPanel();
        filterPanel.addSearchListener(e -> {
            try {
                onSearch();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        });
        add(filterPanel, BorderLayout.NORTH);

        // Results Panel
        resultsPanel = new ResultsPanel();
        add(resultsPanel.getScrollPane(), BorderLayout.CENTER);

        // Status Bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready");
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        setSize(1000, 600);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JMenuItem createMenuItem(String title, ActionListener action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(action);
        return item;
    }

    private void performSearch(String sql, List<Object> params, boolean isPrepared) throws Exception {
        logQuery(sql, params);
        statusLabel.setText("Searching...");
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        SwingWorker<DefaultTableModel, Void> worker = new SwingWorker<>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                try (Connection conn = DBConnector.getConnection()) {
                    PreparedStatement ps = null;
                    if (isPrepared) {
                        ps = conn.prepareStatement(sql);
                        for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                        return DBUtil.buildTableModel(ps.executeQuery());
                    } else {
                        Statement st = conn.createStatement();
                        return DBUtil.buildTableModel(st.executeQuery(sql));
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    DefaultTableModel model = get();
                    resultsPanel.setTableModel(model);
                    statusLabel.setText(model.getRowCount() + " records found.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Error: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                }
            }
        };
        worker.execute();
    }

    private void logQuery(String sql, List<Object> params) throws Exception {
        String insert = "INSERT INTO query_history (sql_text, executed_at) VALUES (?, ?)";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, sql);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // logging failure should not block user
        }
    }

    private void onSearch() throws Exception {
        FilterCriteria crit = filterPanel.getCriteria();
        StringBuilder sb = new StringBuilder("SELECT f.ccn, f.report_dt, f.start_dt, f.end_dt, " +
                "s.shift_code AS Shift, m.method_code AS Method, " +
                "o.offense_code AS Offense, b.block AS Block, f.x, f.y, f.latitude, f.longitude " +
                "FROM fact_incident f " +
                "LEFT JOIN dim_shift s ON f.shift_code=s.shift_code " +
                "LEFT JOIN dim_method m ON f.method_code=m.method_code " +
                "LEFT JOIN dim_offense o ON f.offense_code=o.offense_code " +
                "LEFT JOIN dim_block b ON f.block=b.block WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (crit.getFromDate() != null) {
            sb.append(" AND f.report_dt>=?"); params.add(new Timestamp(crit.getFromDate().getTime()));
        }
        if (crit.getToDate() != null) {
            sb.append(" AND f.report_dt<=?"); params.add(new Timestamp(crit.getToDate().getTime()));
        }
        applyMultiFilter(sb, params, crit.getShifts(), "s.shift_code");
        applyMultiFilter(sb, params, crit.getMethods(), "m.method_code");
        applyMultiFilter(sb, params, crit.getOffenses(), "o.offense_code");
        applyMultiFilter(sb, params, crit.getBlocks(), "b.block");

        performSearch(sb.toString(), params, true);
    }

    private void fetchTopNBlocks() throws Exception {
        int n = JOptionPane.showInputDialog(this, "Enter top N blocks:", "5").isEmpty() ? -1 : Integer.parseInt(JOptionPane.showInputDialog(this, "Enter top N blocks:", "5"));
        if (n<1) return;
        String sql = "SELECT b.block, COUNT(*) cnt FROM fact_incident f JOIN dim_block b ON f.block=b.block " +
            "GROUP BY b.block ORDER BY cnt DESC LIMIT ?";
        performSearch(sql, Collections.singletonList(n), true);
    }

    private void fetchTopNOffenses() throws Exception {
        int n = JOptionPane.showInputDialog(this, "Enter top N offenses:", "5").isEmpty() ? -1 : Integer.parseInt(JOptionPane.showInputDialog(this, "Enter top N offenses:", "5"));
        if (n<1) return;
        String sql = "SELECT o.offense_code AS offense, COUNT(*) cnt FROM fact_incident f JOIN dim_offense o ON f.offense_code=o.offense_code " +
            "GROUP BY o.offense_code ORDER BY cnt DESC LIMIT ?";
        performSearch(sql, Collections.singletonList(n), true);
    }

    private void fetchAvgDuration() throws Exception {
        String sql = "SELECT o.offense_code AS Offense, ROUND(AVG(TIMESTAMPDIFF(MINUTE, f.start_dt, f.end_dt)),2) AS AvgDurationMins " +
                     "FROM fact_incident f JOIN dim_offense o ON f.offense_code=o.offense_code " +
                     "GROUP BY o.offense_code ORDER BY AvgDurationMins DESC";
        performSearch(sql, Collections.emptyList(), false);
    }

    private void fetchQueryHistory() throws Exception {
        String sql = "SELECT id, sql_text, executed_at FROM query_history ORDER BY executed_at DESC";
        performSearch(sql, Collections.emptyList(), false);
    }

    private void applyMultiFilter(StringBuilder sb, List<Object> params, List<String> vals, String column) {
        if (vals.size()>0 && !(vals.size()==1 && "All".equals(vals.get(0)))) {
            String ph = String.join(",", Collections.nCopies(vals.size(), "?"));
            sb.append(" AND ").append(column).append(" IN (").append(ph).append(")");
            params.addAll(vals);
        }
    }
}

// FilterPanel, ResultsPanel, FilterCriteria, DBUtil, DBConnector unchanged...


class FilterPanel extends JPanel {
    private final JSpinner fromDateSpinner, toDateSpinner;
    private final JButton shiftButton, methodButton, offenseButton, blockButton;
    private List<String> selectedShifts = new ArrayList<>(Collections.singletonList("All"));
    private List<String> selectedMethods = new ArrayList<>(Collections.singletonList("All"));
    private List<String> selectedOffenses = new ArrayList<>(Collections.singletonList("All"));
    private List<String> selectedBlocks = new ArrayList<>(Collections.singletonList("All"));
    private final JButton searchButton, clearButton;

    public FilterPanel() {
        setBorder(BorderFactory.createTitledBorder("Filters"));
        setLayout(new FlowLayout(FlowLayout.LEADING, 8, 8));

        fromDateSpinner = createDateSpinner("From:");
        toDateSpinner = createDateSpinner("To:");

        shiftButton = createMultiSelectButton("Shift", "dim_shift", "shift_code", selectedShifts);
        methodButton = createMultiSelectButton("Method", "dim_method", "method_code", selectedMethods);
        offenseButton = createMultiSelectButton("Offense", "dim_offense", "offense_code", selectedOffenses);
        blockButton = createMultiSelectButton("Block", "dim_block", "block", selectedBlocks);

        searchButton = new JButton("Search");
        clearButton = new JButton("Clear");

        Calendar cal = Calendar.getInstance();
        toDateSpinner.setValue(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, -7);
        fromDateSpinner.setValue(cal.getTime());

        add(searchButton);
        add(clearButton);

        clearButton.addActionListener(e -> resetFilters());
    }

    public void addSearchListener(ActionListener listener) {
        searchButton.addActionListener(listener);
    }

    public JButton getSearchButton() {
        return searchButton;
    }

    public FilterCriteria getCriteria() {
        return new FilterCriteria(
            (Date) fromDateSpinner.getValue(),
            (Date) toDateSpinner.getValue(),
            selectedShifts,
            selectedMethods,
            selectedOffenses,
            selectedBlocks
        );
    }

    private void resetFilters() {
        Calendar cal = Calendar.getInstance();
        toDateSpinner.setValue(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, -7);
        fromDateSpinner.setValue(cal.getTime());

        selectedShifts = new ArrayList<>(Collections.singletonList("All"));
        selectedMethods = new ArrayList<>(Collections.singletonList("All"));
        selectedOffenses = new ArrayList<>(Collections.singletonList("All"));
        selectedBlocks = new ArrayList<>(Collections.singletonList("All"));

        shiftButton.setText("Shift: All");
        methodButton.setText("Method: All");
        offenseButton.setText("Offense: All");
        blockButton.setText("Block: All");
    }

    private JButton createMultiSelectButton(String label, String table, String column, List<String> selectionList) {
        JButton button = new JButton(label + ": All");
        button.addActionListener(e -> {
            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select " + label, true);
            dialog.setLayout(new BorderLayout());
            JPanel checkPanel = new JPanel();
            checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));
            List<JCheckBox> checkBoxes = new ArrayList<>();
            try (Connection conn = DBConnector.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(String.format("SELECT %s FROM %s ORDER BY %s", column, table, column))) {
                while (rs.next()) {
                    String val = rs.getString(1);
                    JCheckBox cb = new JCheckBox(val, selectionList.contains(val));
                    checkBoxes.add(cb);
                    checkPanel.add(cb);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            JScrollPane scroll = new JScrollPane(checkPanel);
            scroll.setPreferredSize(new Dimension(200, 300));
            dialog.add(scroll, BorderLayout.CENTER);

            JPanel south = new JPanel();
            JButton ok = new JButton("OK");
            ok.addActionListener(ae -> {
                selectionList.clear();
                for (JCheckBox cb : checkBoxes) {
                    if (cb.isSelected()) selectionList.add(cb.getText());
                }
                if (selectionList.isEmpty()) selectionList.add("All");
                button.setText(label + ": " + (selectionList.contains("All") ? "All" : selectionList.size() + " selected"));
                dialog.dispose();
            });
            south.add(ok);
            dialog.add(south, BorderLayout.SOUTH);

            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        add(button);
        return button;
    }

    private JSpinner createDateSpinner(String label) {
        add(new JLabel(label));
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
        spinner.setToolTipText(label + " date");
        add(spinner);
        return spinner;
    }

    public int promptForN(String label) {
        String input = JOptionPane.showInputDialog(this, "Enter top N " + label + ":", "5");
        try {
            return Integer.parseInt(input.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}

class ResultsPanel extends JPanel {
    private final JTable table;
    private final JScrollPane scrollPane;

    public ResultsPanel() {
        setLayout(new BorderLayout());
        table = new JTable();
        table.setAutoCreateRowSorter(true);
        scrollPane = new JScrollPane(table);
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void setTableModel(DefaultTableModel model) {
        table.setModel(model);
    }
}

class FilterCriteria {
    private final Date fromDate, toDate;
    private final List<String> shifts, methods, offenses, blocks;

    public FilterCriteria(Date fromDate, Date toDate,
                          List<String> shifts, List<String> methods,
                          List<String> offenses, List<String> blocks) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.shifts = shifts;
        this.methods = methods;
        this.offenses = offenses;
        this.blocks = blocks;
    }

    public Date getFromDate() { return fromDate; }
    public Date getToDate() { return toDate; }
    public List<String> getShifts() { return shifts; }
    public List<String> getMethods() { return methods; }
    public List<String> getOffenses() { return offenses; }
    public List<String> getBlocks() { return blocks; }
}

class DBUtil {
    public static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        Vector<String> colNames = new Vector<>();
        for (int i = 1; i <= cols; i++) colNames.add(md.getColumnLabel(i));
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= cols; i++) row.add(rs.getObject(i));
            data.add(row);
        }
        return new DefaultTableModel(data, colNames) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }
}

class DBConnector {
    public static Connection getConnection() throws Exception {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            props.load(fis);
        }
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password")
        );
    }
}
