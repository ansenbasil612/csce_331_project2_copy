import java.awt.BorderLayout;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Manager tab that lists past orders, newest first, one page at a time.
 * Selecting an order shows its drinks below.
 */
public class OrdersPanel extends JPanel {
  /** Database connection used to load orders. */
  private final Connection conn;
  /** Orders on the current page. */
  private final DefaultTableModel orders = model(new String[] {"Order", "Date", "Employee", "Total", "Payment"});
  /** Drinks in the selected order. */
  private final DefaultTableModel drinks = model(new String[] {"Drink", "Quantity", "Size", "Sweetness", "Ice", "Toppings", "Line total"});
  /** Table showing the orders. */
  private final JTable table = new JTable(orders);
  /** Shows the page number or an error message. */
  private final JLabel status = new JLabel();
  /** Goes to the newer page. */
  private final JButton previous = new JButton("Previous");
  /** Goes to the older page. */
  private final JButton next = new JButton("Next");
  /** Number of orders shown on each page. */
  private static final int PAGE_SIZE = 100;
  /** Current page, starting at 0 for the newest orders. */
  private int page = 0;

  /**
   * Creates the panel and loads the first page of orders.
   *
   * @param conn database connection
   */
  public OrdersPanel(Connection conn) {
    super(new BorderLayout(10, 10));
    this.conn = conn;
    JButton refresh = new JButton("Refresh");
    JPanel controls = new JPanel();
    controls.add(refresh);
    controls.add(previous);
    controls.add(next);
    controls.add(status);
    add(controls, BorderLayout.NORTH);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
        new JScrollPane(table), new JScrollPane(new JTable(drinks)));
    split.setResizeWeight(0.6);
    split.setDividerLocation(300);
    add(split, BorderLayout.CENTER);
    refresh.addActionListener(e -> { page = 0; load(); });
    previous.addActionListener(e -> { if (page > 0) page--; load(); });
    next.addActionListener(e -> { page++; load(); });
    table.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) loadDrinks();
    });
    load();
  }

  /** Loads the current page of orders and enables the page buttons that apply. */
  private void load() {
    orders.setRowCount(0);
    drinks.setRowCount(0);
    previous.setEnabled(page > 0);
    next.setEnabled(false);
    // one extra row tells us whether another page exists
    try (PreparedStatement stmt = conn.prepareStatement(
        "SELECT o.order_id, o.order_date, e.name, o.order_total, COALESCE(o.payment_method, 'Not recorded') FROM orders o "
        + "JOIN employees e USING (employee_id) ORDER BY o.order_id DESC LIMIT ? OFFSET ?")) {
      stmt.setInt(1, PAGE_SIZE + 1);
      stmt.setInt(2, page * PAGE_SIZE);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          if (orders.getRowCount() == PAGE_SIZE) { next.setEnabled(true); break; }
          orders.addRow(new Object[] {rs.getInt(1), rs.getTimestamp(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5)});
        }
      }
      status.setText("Page " + (page + 1) + " — " + orders.getRowCount() + " orders, newest first");
    } catch (SQLException ex) { status.setText("Could not load orders: " + ex.getMessage()); }
  }

  /** Loads the drinks and toppings for the selected order. */
  private void loadDrinks() {
    drinks.setRowCount(0);
    int row = table.getSelectedRow();
    if (row < 0) return;
    try (PreparedStatement stmt = conn.prepareStatement(
        "SELECT m.name, i.quantity, i.size, i.sugar_percent, i.ice_level, "
        + "COALESCE(string_agg(t.name, ', ' ORDER BY t.name), ''), i.item_total "
        + "FROM order_items i JOIN menu_items m USING (menu_item_id) "
        + "LEFT JOIN order_toppings ot USING (order_item_id) LEFT JOIN toppings t USING (topping_id) "
        + "WHERE i.order_id = ? GROUP BY i.order_item_id, m.name ORDER BY i.order_item_id")) {
      stmt.setInt(1, ((Number) orders.getValueAt(row, 0)).intValue());
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) drinks.addRow(new Object[] {rs.getString(1), rs.getInt(2), rs.getString(3),
            rs.getInt(4) + "%", rs.getString(5), rs.getString(6), rs.getBigDecimal(7)});
      }
    } catch (SQLException ex) { status.setText("Could not load drinks: " + ex.getMessage()); }
  }

  /**
   * Creates an empty read-only table model.
   *
   * @param columns the column headers
   * @return the table model
   */
  private static DefaultTableModel model(String[] columns) {
    return new DefaultTableModel(columns, 0) {
      public boolean isCellEditable(int row, int column) { return false; }
    };
  }
}
