package FinalJDBC;

import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;

import java.awt.*;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.sql.*;
import java.text.DateFormatSymbols;
import java.util.*;
import java.util.List;
import java.util.Date;

// JFreeChart imports (ensure jars on classpath)
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * Entry point for the Crime Incidents Explorer application. Sets up the system
 * look-and-feel and initializes the main frame UI.
 */
public class CrimeIncidentsApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager
                        .getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
                UIManager.put("control", new Color(60, 63, 65));
                UIManager.put("info", new Color(60, 63, 65));
                UIManager.put("nimbusBase", new Color(18, 30, 49));
                UIManager.put("nimbusBlueGrey", new Color(50, 50, 50));
                UIManager.put("nimbusLightBackground", new Color(43, 43, 43));
                UIManager.put("text", new Color(230, 230, 230));
                UIManager.put("nimbusSelectionBackground",
                        new Color(104, 93, 156));
                UIManager.put("nimbusSelectedText", new Color(255, 255, 255));
            } catch (Exception ignored) {
            }
            new MainFrame().initUI();
        });
    }

}

/**
 * Main application window containing menu, filter panel, results panel, and
 * status bar.
 */
class MainFrame extends JFrame {
    private FilterPanel filterPanel;
    private ResultsPanel resultsPanel;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    public MainFrame() {
        super("Crime Incidents Explorer"); // Window title
    }

    /**
     * Builds and displays the UI components.
     */
    public void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // Apply a dark bg to the whole window
        getContentPane().setBackground(new Color(45, 45, 45));

        setJMenuBar(createMenuBar());

        filterPanel = new FilterPanel();
        filterPanel.setBackground(new Color(60, 63, 65)); // dark grey
        filterPanel.addSearchListener(e -> onSearch());
        add(filterPanel, BorderLayout.NORTH);

        resultsPanel = new ResultsPanel();
        // give the table area a matching bg
        resultsPanel.getScrollPane().getViewport()
                .setBackground(new Color(43, 43, 43));
        add(resultsPanel.getScrollPane(), BorderLayout.CENTER);

        // Status bar styling
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(60, 63, 65));
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        setSize(1000, 600);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Creates the application menu bar with report and history menus.
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Reports menu
        JMenu reportMenu = new JMenu("Reports");
        reportMenu.setForeground(Color.WHITE);
        reportMenu.add(createMenuItem("Top N Blocks", e -> fetchTopNBlocks()));
        reportMenu.add(
                createMenuItem("Top N Offenses", e -> fetchTopNOffenses()));
        reportMenu.add(createMenuItem("Avg Duration by Offense",
                e -> fetchAvgDuration()));
        reportMenu.add(createMenuItem("Incidents per Month Chart",
                e -> showIncidentsPerMonthChart()));
        menuBar.add(reportMenu);

        // History menu
        JMenu historyMenu = new JMenu("History");
        historyMenu.setForeground(Color.WHITE);
        historyMenu.add(
                createMenuItem("View Query History", e -> fetchQueryHistory()));
        menuBar.add(historyMenu);

        return menuBar;
    }

    /**
     * Helper to create menu items.
     */
    private JMenuItem createMenuItem(String title, ActionListener action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(action);
        return item;
    }

    /**
     * Performs a database search asynchronously and updates the results table.
     * 
     * @param sql      The SQL query or prepared statement
     * @param params   Parameters for prepared statements
     * @param prepared Whether to use PreparedStatement
     */
    private void performSearch(String sql, List<Object> params,
            boolean prepared) {
        logQuery(sql); // Save query to history table
        statusLabel.setText("Searching...");
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                try (Connection conn = DBConnector.getConnection()) {
                    if (prepared) {
                        try (PreparedStatement ps = conn
                                .prepareStatement(sql)) {
                            for (int i = 0; i < params.size(); i++) {
                                ps.setObject(i + 1, params.get(i));
                            }
                            try (ResultSet rs = ps.executeQuery()) {
                                return DBUtil.buildTableModel(rs);
                            }
                        }
                    } else {
                        try (Statement st = conn.createStatement();
                                ResultSet rs = st.executeQuery(sql)) {
                            return DBUtil.buildTableModel(rs);
                        }
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    DefaultTableModel model = get();
                    resultsPanel.setTableModel(model);
                    statusLabel
                            .setText(model.getRowCount() + " records found.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Error: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                }
            }
        }.execute();
    }

    /**
     * Logs the executed SQL text and timestamp into the query_history table.
     */
    private void logQuery(String sql) {
        String insert = "INSERT INTO query_history(sql_text, executed_at) VALUES(?, ?)";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, sql);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception ignored) {
            // Non-critical logging failure
        }
    }

    /**
     * Builds and executes the main search query based on filter criteria.
     */
    private void onSearch() {
        FilterCriteria c = filterPanel.getCriteria();
        StringBuilder sb = new StringBuilder(
                "SELECT f.ccn, f.report_dt, f.start_dt, f.end_dt, " +
                        "s.shift_code Shift, m.method_code Method, " +
                        "o.offense_code Offense, b.block Block, f.x, f.y, f.latitude, f.longitude "
                        +
                        "FROM fact_incident f " +
                        "LEFT JOIN dim_shift s ON f.shift_code=s.shift_code " +
                        "LEFT JOIN dim_method m ON f.method_code=m.method_code "
                        +
                        "LEFT JOIN dim_offense o ON f.offense_code=o.offense_code "
                        +
                        "LEFT JOIN dim_block b ON f.block=b.block WHERE 1=1");
        List<Object> params = new ArrayList<>();
        // Date range filtering
        if (c.getFromDate() != null) {
            sb.append(" AND f.report_dt>=?");
            params.add(new Timestamp(c.getFromDate().getTime()));
        }
        if (c.getToDate() != null) {
            sb.append(" AND f.report_dt<=?");
            params.add(new Timestamp(c.getToDate().getTime()));
        }
        // Multi-select filters (shift, method, offense, block)
        applyMultiFilter(sb, params, c.getShifts(), "s.shift_code");
        applyMultiFilter(sb, params, c.getMethods(), "m.method_code");
        applyMultiFilter(sb, params, c.getOffenses(), "o.offense_code");
        applyMultiFilter(sb, params, c.getBlocks(), "b.block");
        performSearch(sb.toString(), params, true);
    }

    /**
     * Prompts the user for a number N and retrieves the top N blocks by
     * incident count. Uses a prepared statement with a LIMIT ? parameter.
     */
    private void fetchTopNBlocks() {
        int n = promptForN("blocks");
        if (n < 1)
            return;
        String sql = "SELECT b.block, COUNT(*) cnt " +
                "FROM fact_incident f JOIN dim_block b ON f.block=b.block " +
                "GROUP BY b.block ORDER BY cnt DESC LIMIT ?";
        performSearch(sql, Collections.singletonList(n), true);
    }

    /**
     * Prompts the user for a number N and retrieves the top N offense codes by
     * incident count. Uses a prepared statement with a LIMIT ? parameter.
     */
    private void fetchTopNOffenses() {
        int n = promptForN("offenses");
        if (n < 1)
            return;
        String sql = "SELECT o.offense_code offense, COUNT(*) cnt " +
                "FROM fact_incident f JOIN dim_offense o ON f.offense_code=o.offense_code "
                +
                "GROUP BY o.offense_code ORDER BY cnt DESC LIMIT ?";
        performSearch(sql, Collections.singletonList(n), true);
    }

    /**
     * Calculates the average incident duration (in minutes) for each offense
     * type. Executes a SELECT with AVG(TIMESTAMPDIFF(...)) to compute the
     * average.
     */
    private void fetchAvgDuration() {
        String sql = "SELECT o.offense_code offense, " +
                "ROUND(AVG(TIMESTAMPDIFF(MINUTE,f.start_dt,f.end_dt)),2) avg_duration "
                +
                "FROM fact_incident f JOIN dim_offense o ON f.offense_code=o.offense_code "
                +
                "GROUP BY o.offense_code ORDER BY avg_duration DESC";
        performSearch(sql, Collections.emptyList(), false);
    }

    /**
     * Fetches the history of all previously executed queries from the
     * query_history table. Displays the most recent entries first.
     */
    private void fetchQueryHistory() {
        performSearch(
                "SELECT id, sql_text, executed_at FROM query_history ORDER BY executed_at DESC",
                Collections.emptyList(), false);
    }

    /**
     * Launches a line chart showing monthly incident counts per offense.
     */
    private void showIncidentsPerMonthChart() {
        FilterCriteria c = filterPanel.getCriteria();
        Timestamp t1 = new Timestamp(c.getFromDate().getTime());
        Timestamp t2 = new Timestamp(c.getToDate().getTime());
        new SwingWorker<DefaultCategoryDataset, Void>() {
            @Override
            protected DefaultCategoryDataset doInBackground() throws Exception {
                DefaultCategoryDataset ds = new DefaultCategoryDataset();
                String q = "SELECT o.offense_code offense, MONTH(f.report_dt) m, COUNT(*) cnt "
                        +
                        "FROM fact_incident f JOIN dim_offense o ON f.offense_code=o.offense_code "
                        +
                        "WHERE f.report_dt>=? AND f.report_dt<=? " +
                        "GROUP BY o.offense_code, MONTH(f.report_dt) ORDER BY m";
                try (Connection conn = DBConnector.getConnection();
                        PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setTimestamp(1, t1);
                    ps.setTimestamp(2, t2);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String off = rs.getString("offense");
                            int m = rs.getInt("m");
                            long cnt = rs.getLong("cnt");
                            String month = new DateFormatSymbols().getMonths()[m
                                    - 1];
                            ds.addValue(cnt, off, month);
                        }
                    }
                }
                return ds;
            }

            @Override
            protected void done() {
                try {
                    DefaultCategoryDataset ds = get();
                    JFreeChart ch = ChartFactory.createLineChart(
                            "Incidents per Month by Offense",
                            "Month", "Count", ds,
                            PlotOrientation.VERTICAL, true, true, false);
                    JFrame f = new JFrame("Monthly Trends");
                    f.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    f.add(new ChartPanel(ch));
                    f.pack();
                    f.setLocationRelativeTo(MainFrame.this);
                    f.setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Chart error: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Adds an "IN (...)" clause for multi-select filters.
     */
    private void applyMultiFilter(StringBuilder sb,
            List<Object> params,
            List<String> vals,
            String column) {
        if (vals.size() > 1 || !"All".equals(vals.get(0))) {
            String ph = String.join(",", Collections.nCopies(vals.size(), "?"));
            sb.append(" AND ").append(column)
                    .append(" IN (").append(ph).append(")");
            params.addAll(vals);
        }
    }

    /**
     * Prompts for a Top-N value.
     */
    private int promptForN(String label) {
        String in = JOptionPane.showInputDialog(
                this, "Enter top N " + label + ":", "5");
        try {
            return Integer.parseInt(in);
        } catch (Exception e) {
            return -1;
        }
    }
}

/**
 * Panel providing date and multi-select filters for queries.
 */
class FilterPanel extends JPanel {
    private final JSpinner fromDateSpinner, toDateSpinner;
    private final JButton shiftButton, methodButton, offenseButton, blockButton;
    private List<String> selectedShifts = new ArrayList<>(
            Collections.singletonList("All"));
    private List<String> selectedMethods = new ArrayList<>(
            Collections.singletonList("All"));
    private List<String> selectedOffenses = new ArrayList<>(
            Collections.singletonList("All"));
    private List<String> selectedBlocks = new ArrayList<>(
            Collections.singletonList("All"));
    private final JButton searchButton, clearButton;

    /**
     * Constructs filter inputs: two date spinners and four multi-select
     * buttons.
     */
    public FilterPanel() {
        setBackground(new Color(60, 63, 65));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(78, 93, 148)),
                "Filters", 0, 0, null, Color.LIGHT_GRAY));

        setBorder(BorderFactory.createTitledBorder("Filters"));
        setLayout(new FlowLayout(FlowLayout.LEADING, 8, 8));

        fromDateSpinner = createDateSpinner("From:");
        toDateSpinner = createDateSpinner("To:");

        shiftButton = createMultiSelectButton("Shift", "dim_shift",
                "shift_code", selectedShifts);
        methodButton = createMultiSelectButton("Method", "dim_method",
                "method_code", selectedMethods);
        offenseButton = createMultiSelectButton("Offense", "dim_offense",
                "offense_code", selectedOffenses);
        blockButton = createMultiSelectButton("Block", "dim_block", "block",
                selectedBlocks);

        searchButton = new JButton("Search");
        clearButton = new JButton("Clear");

        // Tinted buttons
        searchButton.setBackground(new Color(77, 150, 255));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFocusPainted(false);
        clearButton.setBackground(new Color(200, 77, 255));
        clearButton.setForeground(Color.WHITE);
        clearButton.setFocusPainted(false);

        // Initialize default date range: last 7 days
        Calendar cal = Calendar.getInstance();
        toDateSpinner.setValue(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, -7);
        fromDateSpinner.setValue(cal.getTime());

        add(searchButton);
        add(clearButton);

        // Reset filters action
        clearButton.addActionListener(e -> resetFilters());
    }

    /**
     * Attaches a listener to the Search button.
     */
    public void addSearchListener(ActionListener listener) {
        searchButton.addActionListener(listener);
    }

    /**
     * Returns the Search button (used for default Enter key binding).
     */
    public JButton getSearchButton() {
        return searchButton;
    }

    /**
     * Collects current filter criteria into a data object.
     */
    public FilterCriteria getCriteria() {
        return new FilterCriteria(
                (Date) fromDateSpinner.getValue(),
                (Date) toDateSpinner.getValue(),
                selectedShifts,
                selectedMethods,
                selectedOffenses,
                selectedBlocks);
    }

    /**
     * Resets all filters to defaults.
     */
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

    /**
     * Creates a button that opens a dialog with checkboxes for multi-select.
     */
    private JButton createMultiSelectButton(String label, String table,
            String column, List<String> selectionList) {
        JButton button = new JButton(label + ": All");
        button.addActionListener(e -> {
            JDialog dialog = new JDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this),
                    "Select " + label, true);
            dialog.setLayout(new BorderLayout());
            JPanel checkPanel = new JPanel();
            checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));
            List<JCheckBox> checkBoxes = new ArrayList<>();
            try (Connection conn = DBConnector.getConnection();
                    Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(
                            String.format("SELECT %s FROM %s ORDER BY %s",
                                    column, table, column))) {
                while (rs.next()) {
                    String val = rs.getString(1);
                    JCheckBox cb = new JCheckBox(val,
                            selectionList.contains(val));
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
                    if (cb.isSelected())
                        selectionList.add(cb.getText());
                }
                if (selectionList.isEmpty())
                    selectionList.add("All");
                button.setText(
                        label + ": " + (selectionList.contains("All") ? "All"
                                : selectionList.size() + " selected"));
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

    /**
     * Helper to create a date spinner for From/To filters.
     */
    private JSpinner createDateSpinner(String label) {
        add(new JLabel(label));
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
        spinner.setToolTipText(label + " date");
        add(spinner);
        return spinner;
    }

    /**
     * Prompts user for Top-N values via dialog.
     */
    public int promptForN(String label) {
        String input = JOptionPane.showInputDialog(this,
                "Enter top N " + label + ":", "5");
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
        super(new BorderLayout());
        table = new JTable();
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Scans the table’s columns by header name and installs our wrapping
     * renderer on the “sql_text” column (if it exists).
     */
    private void wrapSqlColumn() {
        TableColumnModel colModel = table.getColumnModel();
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            String hdr = colModel.getColumn(i).getHeaderValue().toString();
            if ("sql_text".equalsIgnoreCase(hdr)) {
                TableColumn tc = colModel.getColumn(i);
                tc.setCellRenderer(new TextAreaRenderer());
                tc.setPreferredWidth(400);
                break;
            }
        }
    }

    /**
     * Call this whenever you set a new model so wrapped row‑heights still
     * apply:
     */
    public void setTableModel(DefaultTableModel model) {
        table.setModel(model);
        wrapSqlColumn(); // now that the model has columns, we can safely scan
                         // them
    }

    /**
     * Renders each cell as a wrapping JTextArea and auto‑adjusts row height to
     * fit the text.
     */
    private static class TextAreaRenderer extends JTextArea
            implements TableCellRenderer {
        public TextAreaRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setForeground(Color.WHITE); // default text color
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            setText(value == null ? "" : value.toString());

            setSize(table.getColumnModel().getColumn(column).getWidth(),
                    Short.MAX_VALUE);
            int prefHeight = getPreferredSize().height;
            if (table.getRowHeight(row) != prefHeight) {
                table.setRowHeight(row, prefHeight);
            }

            // selection colors
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(Color.WHITE); //  non‑selected text color
            }
            return this;
        }
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

}

/**
 * Simple data holder for filter criteria from the UI.
 */
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

    public Date getFromDate() {
        return fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public List<String> getShifts() {
        return shifts;
    }

    public List<String> getMethods() {
        return methods;
    }

    public List<String> getOffenses() {
        return offenses;
    }

    public List<String> getBlocks() {
        return blocks;
    }
}

/**
 * Utility to convert a JDBC ResultSet into a Swing TableModel.
 */
class DBUtil {
    public static DefaultTableModel buildTableModel(ResultSet rs)
            throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        Vector<String> colNames = new Vector<>();
        for (int i = 1; i <= cols; i++) {
            colNames.add(md.getColumnLabel(i));
        }
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= cols; i++) {
                row.add(rs.getObject(i));
            }
            data.add(row);
        }
        return new DefaultTableModel(data, colNames) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
    }
}

/**
 * Manages acquiring JDBC Connection using properties from file.
 */
class DBConnector {
    public static Connection getConnection() throws Exception {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            props.load(fis);
        }
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password"));
    }
}
