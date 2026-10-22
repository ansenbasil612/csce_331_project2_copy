import java.math.BigDecimal;
import java.sql.*;

/**
 * Removes the ingredients, toppings and supplies an order uses from stock.
 */
public class OrderInventory {
  /** This class only uses static methods. */
  private OrderInventory() { }

  /**
   * Deducts stock for every drink in an order. Each drink needs a recipe, and
   * each cup size needs cup and seal supplies. Fails if anything would go below zero.
   *
   * @param conn database connection with auto-commit disabled, so the order can be rolled back
   * @param orderId the order to deduct stock for
   * @throws SQLException if auto-commit is on, a recipe or supply is missing,
   *     there is not enough stock, or an update fails
   */
  public static void deduct(Connection conn, int orderId) throws SQLException {
    if (conn.getAutoCommit()) throw new SQLException("Stock updates need an order transaction.");
    // every drink needs a recipe before it can use stock
    try (PreparedStatement stmt = conn.prepareStatement(
        "SELECT m.name FROM order_items oi JOIN menu_items m USING (menu_item_id) "
        + "LEFT JOIN menu_inventory mi USING (menu_item_id) WHERE oi.order_id = ? "
        + "GROUP BY m.menu_item_id, m.name "
        + "HAVING COUNT(mi.inventory_id) = 0 OR MIN(mi.quantity_per_serving) <= 0")) {
      stmt.setInt(1, orderId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) throw new SQLException("Set up a recipe for " + rs.getString(1) + " first.");
      }
    }
    // combine all drink lines so shared ingredients are counted together
    try (PreparedStatement needed = conn.prepareStatement(
        "SELECT i.ingredient_id, i.name, SUM(mi.quantity_per_serving * oi.quantity) "
        + "FROM order_items oi JOIN menu_inventory mi USING (menu_item_id) "
        + "JOIN ingredients i USING (ingredient_id) WHERE oi.order_id = ? "
        + "GROUP BY i.ingredient_id, i.name ORDER BY i.ingredient_id");
        PreparedStatement update = conn.prepareStatement(
        "UPDATE ingredients SET quantity_on_hand = quantity_on_hand - ? "
        + "WHERE ingredient_id = ? AND quantity_on_hand >= ?")) {
      needed.setInt(1, orderId);
      try (ResultSet rs = needed.executeQuery()) {
        while (rs.next()) {
          BigDecimal amount = rs.getBigDecimal(3);
          update.setBigDecimal(1, amount);
          update.setInt(2, rs.getInt(1));
          update.setBigDecimal(3, amount);
          if (update.executeUpdate() != 1) {
            throw new SQLException("Not enough " + rs.getString(2) + " in stock.");
          }
        }
      }
    }
    deductCounts(conn, orderId, "toppings", "topping_id",
        "SELECT t.topping_id, t.name, SUM(oi.quantity) FROM order_toppings ot "
        + "JOIN order_items oi USING (order_item_id) JOIN toppings t USING (topping_id) "
        + "WHERE oi.order_id = ? GROUP BY t.topping_id, t.name ORDER BY t.topping_id");
    try (PreparedStatement stmt = conn.prepareStatement(
        "SELECT DISTINCT oi.size FROM order_items oi WHERE oi.order_id = ? "
        + "AND (NOT EXISTS (SELECT 1 FROM supplies WHERE usage_key = oi.size) "
        + "OR NOT EXISTS (SELECT 1 FROM supplies WHERE usage_key = 'seal'))")) {
      stmt.setInt(1, orderId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) throw new SQLException("Set up cup and seal supplies first.");
      }
    }
    deductCounts(conn, orderId, "supplies", "supply_id",
        "SELECT s.supply_id, s.name, SUM(oi.quantity) FROM order_items oi "
        + "JOIN supplies s ON s.usage_key = oi.size OR s.usage_key = 'seal' "
        + "WHERE oi.order_id = ? GROUP BY s.supply_id, s.name ORDER BY s.supply_id");
  }

  /**
   * Deducts whole-number counts from a stock table, such as toppings or supplies.
   *
   * @param conn database connection
   * @param orderId the order to deduct stock for
   * @param table the stock table to update
   * @param id the id column of that table
   * @param sql query that takes the order id and returns the id, name and count needed for each row
   * @throws SQLException if there is not enough stock or the update fails
   */
  private static void deductCounts(Connection conn, int orderId, String table, String id, String sql)
      throws SQLException {
    try (PreparedStatement needed = conn.prepareStatement(sql);
        PreparedStatement update = conn.prepareStatement(
        "UPDATE " + table + " SET quantity_on_hand = quantity_on_hand - ? WHERE " + id
        + " = ? AND quantity_on_hand >= ?")) {
      needed.setInt(1, orderId);
      try (ResultSet rs = needed.executeQuery()) {
        while (rs.next()) {
          update.setLong(1, rs.getLong(3));
          update.setInt(2, rs.getInt(1));
          update.setLong(3, rs.getLong(3));
          if (update.executeUpdate() != 1) throw new SQLException("Not enough " + rs.getString(2) + " in stock.");
        }
      }
    }
  }
}
