package FinalJDBC;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.sql.*;
import java.util.*;
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
    private final JLabel statusBar;

    public MainFrame() {
        super("Crime Incidents Explorer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JMenuBar menuBar = new JMenuBar();
        JMenu reportMenu = new JMenu("Reports");
        JMenuItem topNBlocks = new JMenuItem("Top N Blocks");
        JMenuItem topNOffenses = new JMenuItem("Top N Offenses");
        reportMenu.add(topNOffenses);
        topNOffenses.addActionListener(e -> showTopNOffenses());

        JMenuItem avgDuration = new JMenuItem("Avg Duration by Offense");
        reportMenu.add(avgDuration);
        avgDuration.addActionListener(e -> showAvgDurationByOffense());
        reportMenu.add(topNBlocks);
        menuBar.add(reportMenu);
        setJMenuBar(menuBar);
        topNBlocks.addActionListener(e -> showTopNBlocks());

        filterPanel = new FilterPanel();
        resultsPanel = new ResultsPanel();
        statusBar = new JLabel("Ready"); 

        add(filterPanel, BorderLayout.NORTH);
        add(resultsPanel.getScrollPane(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        filterPanel.addSearchListener(e -> onSearch());

        setSize(1000, 600);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void onSearch() {
        statusBar.setText("Searching...");
        FilterCriteria crit = filterPanel.getCriteria();
        StringBuilder sql = new StringBuilder(
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
            sql.append(" AND f.report_dt >= ?");
            params.add(new Timestamp(crit.getFromDate().getTime()));
        }
        if (crit.getToDate() != null) {
            sql.append(" AND f.report_dt <= ?");
            params.add(new Timestamp(crit.getToDate().getTime()));
        }
        if (!"All".equals(crit.getShift())) {
            sql.append(" AND s.shift_code = ?");
            params.add(crit.getShift());
        }
        if (!"All".equals(crit.getMethod())) {
            sql.append(" AND m.method_code = ?");
            params.add(crit.getMethod());
        }
        if (!"All".equals(crit.getOffense())) {
            sql.append(" AND o.offense_code = ?");
            params.add(crit.getOffense());
        }
        if (!"All".equals(crit.getBlock())) {
            sql.append(" AND b.block = ?");
            params.add(crit.getBlock());
        }
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i+1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                DefaultTableModel model = DBUtil.buildTableModel(rs);
                resultsPanel.setTableModel(model);
                statusBar.setText(model.getRowCount() + " records found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading data: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            statusBar.setText("Error occurred.");
        }
    }

    private void showTopNBlocks() {
        String input = JOptionPane.showInputDialog(this, "Enter N:", "5");
        if (input == null) return;
        int n;
        try { n = Integer.parseInt(input.trim()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid number: " + input,
                "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String sql =
            "SELECT block, cnt FROM (" +
            " SELECT b.block, COUNT(*) cnt, RANK() OVER (ORDER BY COUNT(*) DESC) rnk " +
            " FROM fact_incident f JOIN dim_block b ON f.block=b.block GROUP BY b.block) t " +
            "WHERE rnk <= ? ORDER BY cnt DESC";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, n);
            try (ResultSet rs = ps.executeQuery()) {
                DefaultTableModel model = DBUtil.buildTableModel(rs);
                resultsPanel.setTableModel(model);
                statusBar.setText("Top " + n + " blocks displayed.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error fetching Top N Blocks: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            statusBar.setText("Error occurred.");
        }
    }
    
    private void showTopNOffenses() {
        String input = JOptionPane.showInputDialog(this, "Enter N:", "5");
        if (input == null) return;
        int n;
        try { n = Integer.parseInt(input.trim()); }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid number: " + input,
                "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sql =
          "SELECT offense, cnt FROM (" +
          "  SELECT o.offense_code AS offense," +
          "         COUNT(*) AS cnt," +
          "         RANK() OVER (ORDER BY COUNT(*) DESC) AS rnk" +
          "  FROM fact_incident f" +
          "  JOIN dim_offense o ON f.offense_code = o.offense_code" +
          "  GROUP BY o.offense_code" +
          ") t WHERE rnk <= ? ORDER BY cnt DESC";

        try (Connection conn = DBConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setInt(1, n);
          try (ResultSet rs = ps.executeQuery()) {
            DefaultTableModel model = DBUtil.buildTableModel(rs);
            resultsPanel.setTableModel(model);
            statusBar.setText("Top " + n + " offenses displayed.");
          }
        } catch (Exception e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(this,
            "Error fetching Top N Offenses: " + e.getMessage(),
            "Database Error", JOptionPane.ERROR_MESSAGE);
          statusBar.setText("Error occurred.");
        }
    }

    private void showAvgDurationByOffense() {
        String sql =
          "SELECT o.offense_code    AS Offense, " +
          "       ROUND(AVG(TIMESTAMPDIFF(MINUTE, f.start_dt, f.end_dt)),2) AS AvgDurationMins " +
          "  FROM fact_incident f " +
          "  JOIN dim_offense    o ON f.offense_code = o.offense_code " +
          " GROUP BY o.offense_code " +
          " ORDER BY AvgDurationMins DESC";

        try (Connection conn = DBConnector.getConnection();
             Statement stmt    = conn.createStatement();
             ResultSet rs      = stmt.executeQuery(sql)) {

            DefaultTableModel model = DBUtil.buildTableModel(rs);
            resultsPanel.setTableModel(model);
            statusBar.setText("Average duration by offense displayed.");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error fetching Avg Duration: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
            statusBar.setText("Error occurred.");
        }
    }

}

class FilterPanel extends JPanel {
    private final JSpinner fromDateSpinner, toDateSpinner;
    private final JComboBox<String> shiftCombo, methodCombo, offenseCombo, blockCombo;
    private final JButton searchButton;

    public FilterPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 8));
        add(new JLabel("From:"));
        fromDateSpinner = new JSpinner(new SpinnerDateModel());
        fromDateSpinner.setEditor(new JSpinner.DateEditor(fromDateSpinner, "yyyy-MM-dd"));
        add(fromDateSpinner);
        add(new JLabel("To:"));
        toDateSpinner = new JSpinner(new SpinnerDateModel());
        toDateSpinner.setEditor(new JSpinner.DateEditor(toDateSpinner, "yyyy-MM-dd"));
        add(toDateSpinner);
        add(new JLabel("Shift:"));
        shiftCombo = new JComboBox<>(loadLookup("SELECT shift_code FROM dim_shift")); add(shiftCombo);
        add(new JLabel("Method:"));
        methodCombo = new JComboBox<>(loadLookup("SELECT method_code FROM dim_method")); add(methodCombo);
        add(new JLabel("Offense:"));
        offenseCombo = new JComboBox<>(loadLookup("SELECT offense_code FROM dim_offense")); add(offenseCombo);
        add(new JLabel("Block:"));
        blockCombo = new JComboBox<>(loadLookup("SELECT block FROM dim_block")); add(blockCombo);
        searchButton = new JButton("Search"); add(searchButton);
    }

    public void addSearchListener(ActionListener listener) {
        searchButton.addActionListener(listener);
    }

    public FilterCriteria getCriteria() {
        Date from = ((SpinnerDateModel)fromDateSpinner.getModel()).getDate();
        Date to = ((SpinnerDateModel)toDateSpinner.getModel()).getDate();
        return new FilterCriteria(from, to,
            (String)shiftCombo.getSelectedItem(),
            (String)methodCombo.getSelectedItem(),
            (String)offenseCombo.getSelectedItem(),
            (String)blockCombo.getSelectedItem());
    }

    private String[] loadLookup(String query) {
        List<String> list = new ArrayList<>(); list.add("All");
        try (Connection conn = DBConnector.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (Exception ignored) {}
        return list.toArray(new String[0]);
    }
}

class ResultsPanel extends JPanel {
    private final JTable table; private final JScrollPane scrollPane;
    public ResultsPanel() {
        table = new JTable(); table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scrollPane = new JScrollPane(table);
    }
    public JScrollPane getScrollPane() { return scrollPane; }
    public void setTableModel(DefaultTableModel model) { table.setModel(model); }
}

class FilterCriteria {
    private final Date fromDate, toDate;
    private final String shift, method, offense, block;

    public FilterCriteria(Date f, Date t, String s, String m, String off, String b) {
        this.fromDate = f; this.toDate = t;
        this.shift = s; this.method = m;
        this.offense = off; this.block = b;
    }
    public Date getFromDate() { return fromDate; }
    public Date getToDate() { return toDate; }
    public String getShift() { return shift; }
    public String getMethod() { return method; }
    public String getOffense() { return offense; }
    public String getBlock() { return block; }
}

class DBUtil {
    public static Properties loadDBProperties() throws Exception {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream("db.properties")) { p.load(fis); }
        return p;
    }
    public static DefaultTableModel buildTableModel(ResultSet rs) throws Exception {
        ResultSetMetaData m = rs.getMetaData(); int cols = m.getColumnCount();
        Vector<String> names = new Vector<>(); for (int i=1;i<=cols;i++) names.add(m.getColumnLabel(i));
        Vector<Vector<Object>> data = new Vector<>();
        while(rs.next()){
            Vector<Object> row = new Vector<>();
            for(int i=1;i<=cols;i++) row.add(rs.getObject(i));
            data.add(row);
        }
        return new DefaultTableModel(data,names) {@Override public boolean isCellEditable(int r,int c){return false;}};
    }
}

class DBConnector {
    public static Connection getConnection() throws Exception {
        Properties p = DBUtil.loadDBProperties();
        return DriverManager.getConnection(p.getProperty("db.url"), p.getProperty("db.user"), p.getProperty("db.password"));
    }
}
