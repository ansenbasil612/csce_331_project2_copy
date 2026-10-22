import java.math.BigDecimal;
import java.sql.*;
import java.util.Random;

/**
 * creates simulated orders for the demo.
 * @author Joyce You
 */
public class OrderStream {
  /** this class only uses static methods. */
  private OrderStream() { }

  /** picks demo drinks and options. */
  private static final Random random = new Random();

  /**
   * starts the order stream.
   * @param args order count and seconds between orders
   */
  public static void main(String[] args) {
    int count = 10;
    int seconds = 3;
    try {
      if (args.length > 2) throw new IllegalArgumentException();//bounds checking
      if (args.length > 0) count = Integer.parseInt(args[0]);
      if (args.length > 1) seconds = Integer.parseInt(args[1]);
      if (count < 1 || seconds < 1) throw new IllegalArgumentException();
    } catch (IllegalArgumentException ex) {
      System.err.println("Usage: OrderStream [order count > 0] [seconds between orders > 0]");
      System.exit(1);
    }

    String url = System.getenv("DB_URL");
    if (url == null || System.getenv("DB_USER") == null || System.getenv("DB_PASSWORD") == null) {
      System.err.println("Set DB_URL, DB_USER, and DB_PASSWORD first.");
      System.exit(1);
    }
    System.out.println("Saving " + count + " simulated orders to the configured database. Ctrl+C stops the stream.");
    try (Connection conn = DriverManager.getConnection(url,
        System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))) {
      conn.setAutoCommit(false);
      for (int i = 0; i < count; i++) {
        saveOrder(conn);
        if (i + 1 < count) Thread.sleep(seconds * 1000L);
      }
    } catch (SQLException ex) {
      System.err.println("Stream stopped: " + ex.getMessage());
      System.exit(1);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * saves one order and deducts stock, or rolls back on failure.
   * @param conn database connection with auto-commit disabled
   * @throws SQLException if saving the order or updating stock fails
   */
  private static void saveOrder(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      // cashier checkout takes the same lock before choosing ids
      stmt.execute("LOCK TABLE orders, order_items, order_toppings IN SHARE ROW EXCLUSIVE MODE");
      int menuId;
      String name;
      BigDecimal price;
      try (ResultSet rs = stmt.executeQuery(
          "SELECT menu_item_id, name, base_price FROM menu_items ORDER BY random() LIMIT 1")) {
        if (!rs.next()) throw new SQLException("Add a menu item before starting the stream.");
        menuId = rs.getInt(1);
        name = rs.getString(2);
        price = rs.getBigDecimal(3);
      }
      int employeeId;
      try (ResultSet rs = stmt.executeQuery("SELECT employee_id FROM employees ORDER BY random() LIMIT 1")) {
        if (!rs.next()) throw new SQLException("Add an employee before starting the stream.");
        employeeId = rs.getInt(1);
      }
      int toppingId = 0;
      boolean hasTopping = false;
      if (random.nextBoolean()) {
        try (ResultSet rs = stmt.executeQuery("SELECT topping_id, cost FROM toppings ORDER BY random() LIMIT 1")) {
          if (rs.next()) {
            hasTopping = true;
            toppingId = rs.getInt(1);
            price = price.add(rs.getBigDecimal(2));
          }
        }
      }
      String size = random.nextBoolean() ? "medium" : "large";
      try (PreparedStatement sizePrice = conn.prepareStatement("SELECT surcharge FROM drink_sizes WHERE size = ?")) {
        sizePrice.setString(1, size);
        try (ResultSet rs = sizePrice.executeQuery()) {
          if (!rs.next()) throw new SQLException("Missing size price: " + size);
          price = price.add(rs.getBigDecimal(1));
        }
      }
      int quantity = 1 + random.nextInt(3);
      BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
      int orderId = nextId(stmt, "orders", "order_id");
      int itemId = nextId(stmt, "order_items", "order_item_id");
      try (PreparedStatement order = conn.prepareStatement(
          "INSERT INTO orders (order_id, order_date, order_total, employee_id, payment_method) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)")) {
        order.setInt(1, orderId);
        order.setBigDecimal(2, total);
        order.setInt(3, employeeId);
        order.setString(4, new String[] {"Cash", "Card", "Gift Card"}[random.nextInt(3)]);
        order.executeUpdate();
      }
      try (PreparedStatement item = conn.prepareStatement(
          "INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, size, sugar_percent, ice_level, item_total) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
        item.setInt(1, itemId);
        item.setInt(2, orderId);
        item.setInt(3, menuId);
        item.setInt(4, quantity);
        item.setString(5, size);
        item.setInt(6, random.nextInt(5) * 25);
        item.setString(7, new String[] {"no ice", "less", "normal"}[random.nextInt(3)]);
        item.setBigDecimal(8, total);
        item.executeUpdate();
      }
      if (hasTopping) {
        try (PreparedStatement topping = conn.prepareStatement(
            "INSERT INTO order_toppings (order_topping_id, order_item_id, topping_id) VALUES (?, ?, ?)")) {
          topping.setInt(1, nextId(stmt, "order_toppings", "order_topping_id"));
          topping.setInt(2, itemId);
          topping.setInt(3, toppingId);
          topping.executeUpdate();
        }
      }
      OrderInventory.deduct(conn, orderId);
      conn.commit();
      System.out.printf("Saved demo order %d: %d x %s, $%.2f%n", orderId, quantity, name, total);
    } catch (SQLException ex) {
      conn.rollback();
      throw ex;
    }
  }

  /**
   * finds the next unused id while the order tables are locked.
   * @param stmt statement used to query the database
   * @param table table to check
   * @param column id column to check
   * @return one more than the largest id, or 1 for an empty table
   * @throws SQLException if the query fails
   */
  private static int nextId(Statement stmt, String table, String column) throws SQLException {
    try (ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM " + table)) {
      rs.next();
      return rs.getInt(1);
    }
  }
}
