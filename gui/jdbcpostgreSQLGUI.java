import java.sql.*;
import java.awt.Dimension;
import java.util.Vector;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * CSCE 331 database viewer. Connects to the database and shows the rows of
 * a table picked from a list.
 */
public class jdbcpostgreSQLGUI {
  /** This class only uses static methods. */
  private jdbcpostgreSQLGUI() { }

  /** Most rows shown for a table. */
  private static final int ROW_LIMIT = 100;

  /**
   * Loads the first rows of a table into a read-only table model.
   *
   * @param conn database connection
   * @param table the table to load, from the fixed list in the dropdown
   * @return a table model with the table's columns and up to {@link #ROW_LIMIT} rows
   * @throws SQLException if the query fails
   */
  private static DefaultTableModel loadTable(Connection conn, String table) throws SQLException {
    // table comes from the fixed list in the dropdown.
    String query = "SELECT * FROM " + table + " ORDER BY 1 LIMIT " + ROW_LIMIT;
    try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
      ResultSetMetaData md = rs.getMetaData();
      int cols = md.getColumnCount();
      Vector<String> names = new Vector<>();
      for (int i = 1; i <= cols; i++) {
        names.add(md.getColumnName(i));
      }
      Vector<Vector<Object>> rows = new Vector<>();
      while (rs.next()) {
        Vector<Object> row = new Vector<>();
        for (int i = 1; i <= cols; i++) {
          row.add(rs.getObject(i));
        }
        rows.add(row);
      }
      return new DefaultTableModel(rows, names) {
        public boolean isCellEditable(int r, int c) { return false; }
      };
    }
  }

  /**
   * Connects to the database from DB_URL, DB_USER and DB_PASSWORD, then lets
   * the user view tables until they cancel.
   *
   * @param args not used
   */
  public static void main(String args[]) {
    String url = System.getenv("DB_URL");
    String user = System.getenv("DB_USER");
    String password = System.getenv("DB_PASSWORD");
    if (url == null || user == null || password == null
        || url.trim().isEmpty() || user.trim().isEmpty() || password.isEmpty()) {
      JOptionPane.showMessageDialog(null, "Set DB_URL, DB_USER, and DB_PASSWORD first.");
      return;
    }
    Connection conn = null;
    try {
      Class.forName("org.postgresql.Driver");
      conn = DriverManager.getConnection(url, user, password);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
      return;
    }
    JOptionPane.showMessageDialog(null, "Opened database successfully");

    String[] tables = {"employees", "ingredients", "menu_inventory", "menu_items",
        "order_items", "order_toppings", "orders", "supplies", "toppings"};

    while (true) {
      String choice = (String) JOptionPane.showInputDialog(null, "Pick a table to view:",
          "Tables", JOptionPane.QUESTION_MESSAGE, null, tables, tables[0]);
      if (choice == null) {
        break;
      }

      try {
        int action;
        do {
          JTable grid = new JTable(loadTable(conn, choice));
          grid.setAutoCreateRowSorter(true);
          JScrollPane scroll = new JScrollPane(grid);
          scroll.setPreferredSize(new Dimension(750, 400));
          action = JOptionPane.showOptionDialog(null, scroll,
              choice + " (showing " + grid.getRowCount() + " rows, limit " + ROW_LIMIT + ")",
              JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
              new String[] {"Refresh", "Back"}, "Back");
        } while (action == 0);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(null, "Error accessing Database: " + e.getMessage());
      }
    }

    try {
      conn.close();
      JOptionPane.showMessageDialog(null, "Connection Closed.");
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, "Connection NOT Closed.");
    }
  }
}
