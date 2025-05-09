package FinalJDBC;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

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
        reportMenu.add(createMenuItem("Top N Blocks", e -> fetchTopNBlocks()));
        reportMenu.add(createMenuItem("Top N Offenses", e -> fetchTopNOffenses()));
        reportMenu.add(createMenuItem("Avg Duration by Offense", e -> fetchAvgDuration()));
        menuBar.add(reportMenu);
        setJMenuBar(menuBar);

        // Filter Panel
        filterPanel = new FilterPanel();
        filterPanel.addSearchListener(e -> onSearch());
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

        // Enter key triggers search
        getRootPane().setDefaultButton(filterPanel.getSearchButton());

        setSize(1000, 600);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JMenuItem createMenuItem(String title, ActionListener action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(action);
        return item;
    }

    private void performSearch(String sql, List<Object> params, boolean isPrepared) {
        statusLabel.setText("Searching...");
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        SwingWorker<DefaultTableModel, Void> worker = new SwingWorker<>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                if (isPrepared) {
                    try (Connection conn = DBConnector.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < params.size(); i++) {
                            ps.setObject(i + 1, params.get(i));
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            return DBUtil.buildTableModel(rs);
                        }
                    }
                } else {
                    try (Connection conn = DBConnector.getConnection();
                         Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery(sql)) {
                        return DBUtil.buildTableModel(rs);
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
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Error fetching data: " + e.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Error occurred.");
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                }
            }
        };
        worker.execute();
    }

    private void onSearch() {
        FilterCriteria crit = filterPanel.getCriteria();
        StringBuilder sb = new StringBuilder(
                "SELECT f.ccn, f.report_dt, f.start_dt, f.end_dt, " +
                        "s.shift_code AS Shift, m.method_code AS Method, " +
                        "o.offense_code AS Offense, b.block AS Block, " +
                        "f.x, f.y, f.latitude, f.longitude " +
                        "FROM fact_incident f " +
                        "LEFT JOIN dim_shift s ON f.shift_code = s.shift_code " +
                        "LEFT JOIN dim_method m ON f.method_code = m.method_code " +
                        "LEFT JOIN dim_offense o ON f.offense_code = o.offense_code " +
                        "LEFT JOIN dim_block b ON f.block = b.block " +
                        "WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();
        if (crit.getFromDate() != null) {
            sb.append(" AND f.report_dt >= ?");
            params.add(new Timestamp(crit.getFromDate().getTime()));
        }
        if (crit.getToDate() != null) {
            sb.append(" AND f.report_dt <= ?");
            params.add(new Timestamp(crit.getToDate().getTime()));
        }
        if (!"All".equals(crit.getShift())) {
            sb.append(" AND s.shift_code = ?");
            params.add(crit.getShift());
        }
        if (!"All".equals(crit.getMethod())) {
            sb.append(" AND m.method_code = ?");
            params.add(crit.getMethod());
        }
        if (!"All".equals(crit.getOffense())) {
            sb.append(" AND o.offense_code = ?");
            params.add(crit.getOffense());
        }
        if (!"All".equals(crit.getBlock())) {
            sb.append(" AND b.block = ?");
            params.add(crit.getBlock());
        }
        performSearch(sb.toString(), params, true);
    }

    private void fetchTopNBlocks() {
        int n = filterPanel.promptForN("blocks");
        if (n < 1) return;
        String sql =
                "SELECT block, cnt FROM (" +
                        "  SELECT b.block, COUNT(*) cnt, RANK() OVER (ORDER BY COUNT(*) DESC) rnk " +
                        "  FROM fact_incident f " +
                        "  JOIN dim_block b ON f.block=b.block GROUP BY b.block" +
                        ") t WHERE rnk <= ? ORDER BY cnt DESC";
        performSearch(sql, Collections.singletonList(n), true);
    }

    private void fetchTopNOffenses() {
        int n = filterPanel.promptForN("offenses");
        if (n < 1) return;
        String sql =
                "SELECT offense, cnt FROM (" +
                        "  SELECT o.offense_code AS offense, COUNT(*) cnt, RANK() OVER (ORDER BY COUNT(*) DESC) rnk " +
                        "  FROM fact_incident f " +
                        "  JOIN dim_offense o ON f.offense_code=o.offense_code GROUP BY o.offense_code" +
                        ") t WHERE rnk <= ? ORDER BY cnt DESC";
        performSearch(sql, Collections.singletonList(n), true);
    }

    private void fetchAvgDuration() {
        String sql =
                "SELECT o.offense_code AS Offense, ROUND(AVG(TIMESTAMPDIFF(MINUTE,f.start_dt,f.end_dt)),2) AS AvgDurationMins " +
                "FROM fact_incident f JOIN dim_offense o ON f.offense_code=o.offense_code GROUP BY o.offense_code ORDER BY AvgDurationMins DESC";
        performSearch(sql, Collections.emptyList(), false);
    }
}

class FilterPanel extends JPanel {
    private final JSpinner fromDateSpinner, toDateSpinner;
    private final JComboBox<String> shiftCombo, methodCombo, offenseCombo, blockCombo;
    private final JButton searchButton, clearButton;

    public FilterPanel() {
        setBorder(BorderFactory.createTitledBorder("Filters"));
        setLayout(new FlowLayout(FlowLayout.LEADING, 8, 8));

        fromDateSpinner = createDateSpinner("From:");
        toDateSpinner = createDateSpinner("To:");
        shiftCombo = createLookupCombo("Shift:", "dim_shift", "shift_code");
        methodCombo = createLookupCombo("Method:", "dim_method", "method_code");
        offenseCombo = createLookupCombo("Offense:", "dim_offense", "offense_code");
        blockCombo = createLookupCombo("Block:", "dim_block", "block");

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
                (String) shiftCombo.getSelectedItem(),
                (String) methodCombo.getSelectedItem(),
                (String) offenseCombo.getSelectedItem(),
                (String) blockCombo.getSelectedItem()
        );
    }

    public int promptForN(String label) {
        String input = JOptionPane.showInputDialog(this, "Enter top N " + label + ":", "5");
        try { return Integer.parseInt(input.trim()); } catch (Exception e) { return -1; }
    }

    private void resetFilters() {
        Calendar cal = Calendar.getInstance();
        toDateSpinner.setValue(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, -7);
        fromDateSpinner.setValue(cal.getTime());
        shiftCombo.setSelectedIndex(0);
        methodCombo.setSelectedIndex(0);
        offenseCombo.setSelectedIndex(0);
        blockCombo.setSelectedIndex(0);
    }

    private JSpinner createDateSpinner(String label) {
        add(new JLabel(label));
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
        spinner.setToolTipText(label + " date");
        add(spinner);
        return spinner;
    }

    private JComboBox<String> createLookupCombo(String label, String table, String column) {
        add(new JLabel(label));
        List<String> items = new ArrayList<>();
        items.add("All");
        String sql = String.format("SELECT %s FROM %s ORDER BY %s", column, table, column);
        try (Connection conn = DBConnector.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) items.add(rs.getString(1));
        } catch (Exception ignored) {}
        JComboBox<String> combo = new JComboBox<>(items.toArray(new String[0]));
        combo.setToolTipText(label + " (All for no filter)");
        add(combo);
        return combo;
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
    private final String shift, method, offense, block;

    public FilterCriteria(Date fromDate, Date toDate, String shift, String method, String offense, String block) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.shift = shift;
        this.method = method;
        this.offense = offense;
        this.block = block;
    }

    public Date getFromDate() { return fromDate; }
    public Date getToDate() { return toDate; }
    public String getShift() { return shift; }
    public String getMethod() { return method; }
    public String getOffense() { return offense; }
    public String getBlock() { return block; }
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
