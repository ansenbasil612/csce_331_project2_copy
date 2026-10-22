import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Manager tab that lists stock for toppings or supplies and lets a manager
 * update the quantity on hand and restock minimum.
 */
public class StockPanel extends JPanel {
  /**
   * Creates the panel and loads the current stock.
   *
   * @param conn database connection
   * @param tableName the stock table to show, such as "toppings" or "supplies"
   * @param idColumn the id column of that table
   */
  public StockPanel(Connection conn, String tableName, String idColumn) {
    super(new BorderLayout(10, 10));
    DefaultTableModel model = new DefaultTableModel(new String[] {"ID", "Name", "Quantity on hand", "Restock minimum"}, 0) {
      public boolean isCellEditable(int row, int column) { return false; }
    };
    JTable table = new JTable(model);
    JButton refresh = new JButton("Refresh");
    JButton update = new JButton("Update stock");
    JLabel status = new JLabel(tableName.equals("toppings") ? "Quantities are topping servings" : "Quantities are individual pieces");
    JPanel controls = new JPanel();
    controls.add(refresh);
    controls.add(update);
    add(controls, BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
    add(status, BorderLayout.SOUTH);
    refresh.addActionListener(e -> {
      try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
          "SELECT " + idColumn + ", name, quantity_on_hand, min_quantity FROM " + tableName + " ORDER BY " + idColumn)) {
        model.setRowCount(0);
        while (rs.next()) model.addRow(new Object[] {rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4)});
      } catch (SQLException ex) { status.setText("Could not load stock: " + ex.getMessage()); }
    });
    update.addActionListener(e -> {
      int row = table.getSelectedRow();
      if (row < 0) { status.setText("Select an item first"); return; }
      JTextField quantityField = new JTextField(String.valueOf(model.getValueAt(row, 2)));
      JTextField minimumField = new JTextField(String.valueOf(model.getValueAt(row, 3)));
      JPanel fields = new JPanel(new GridLayout(0, 2, 5, 5));
      fields.add(new JLabel("Quantity on hand"));
      fields.add(quantityField);
      fields.add(new JLabel("Restock minimum"));
      fields.add(minimumField);
      if (JOptionPane.showConfirmDialog(this, fields, "Update " + model.getValueAt(row, 1),
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
      try {
        int quantity = Integer.parseInt(quantityField.getText().trim());
        int minimum = Integer.parseInt(minimumField.getText().trim());
        if (quantity < 0 || minimum < 0) throw new NumberFormatException();
        try (PreparedStatement stmt = conn.prepareStatement(
            "UPDATE " + tableName + " SET quantity_on_hand = ?, min_quantity = ? WHERE " + idColumn + " = ?")) {
          stmt.setInt(1, quantity);
          stmt.setInt(2, minimum);
          stmt.setInt(3, (Integer) model.getValueAt(row, 0));
          stmt.executeUpdate();
        }
        refresh.doClick();
        status.setText("Stock updated");
      } catch (NumberFormatException ex) { status.setText("Enter whole numbers of zero or more"); }
      catch (SQLException ex) { status.setText("Could not update stock: " + ex.getMessage()); }
    });
    refresh.doClick();
  }
}
