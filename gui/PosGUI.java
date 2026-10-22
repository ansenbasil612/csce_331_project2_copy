import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.*;
import java.time.LocalDate;
import java.util.Vector;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Boba shop point-of-sale GUI with a Cashier view for taking orders
 * and a Manager view for reports, inventory, employees and menu items.
 *
 * @author Aaron DallaRosa
 * @author Joyce You
 * @author Ansen Basil
 * @author Matthew Shi
 */

public class PosGUI {
  /** This class only uses static methods. */
  private PosGUI() { }

  /** Database connection shared by every view, opened in {@link #main}. */
  private static Connection conn;

  /**
   * Runs a SQL query and loads every result row into a read-only table model.
   *
   * @param sql the SELECT statement to execute
   * @param columns the column headers for the returned table
   * @return a table model holding the query results
   * @throws SQLException if the query fails
   */
  private static DefaultTableModel query(String sql, String[] columns) throws SQLException {
    DefaultTableModel model = (DefaultTableModel) emptyTable(columns).getModel();
    try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
      int cols = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        Vector<Object> row = new Vector<>();
        for (int i = 1; i <= cols; i++) {
          row.add(rs.getObject(i));
        }
        model.addRow(row);
      }
    }
    return model;
  }

  /**
   * Builds the Cashier view, where a cashier picks drinks by category,
   * customizes them, and checks out the order.
   *
   * @param employeeId the id of the logged-in cashier, saved with each order
   * @param employeeName the cashier's name, shown in the header
   * @return the cashier panel
   */
  private static JPanel cashierView(int employeeId, String employeeName) {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
    DefaultTableModel menu;
    DefaultTableModel toppingData;
    try {//helps with display
      menu = query("SELECT menu_item_id, name, base_price, category, (SELECT surcharge FROM drink_sizes WHERE size = 'large') FROM menu_items ORDER BY name",
          new String[] {"id", "name", "price", "category", "large surcharge"});
      toppingData = query("SELECT topping_id, name, cost FROM toppings ORDER BY name",
          new String[] {"id", "name", "cost"});
    } catch (SQLException e) {
      panel.add(new JLabel("Could not load cashier data: " + e.getMessage()));//exception message for data
      return panel;
    }
    JPanel header = new JPanel();
    header.add(new JLabel("Cashier: " + employeeName));
    JButton submit = new JButton("Checkout — $0.00");
    submit.setEnabled(false);
    JLabel status = new JLabel("Choose a category to start");
    JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
    JButton edit = new JButton("Edit drink");
    JButton remove = new JButton("Remove drink");
    edit.setEnabled(false);
    remove.setEnabled(false);
    buttons.add(edit);
    buttons.add(remove);
    buttons.add(submit);
    JTable cartTable = emptyTable(new String[] {
        "Drink", "Qty", "Size", "Sweetness", "Ice", "Toppings", "Total"
    });//options for drinks
    cartTable.setRowHeight(32);
    cartTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    DefaultTableModel cart = (DefaultTableModel) cartTable.getModel();
    java.util.List<int[]> cartIds = new java.util.ArrayList<>();

    cart.addTableModelListener(e -> {
      double total = cartTotal(cart);
      submit.setText(String.format("Checkout — $%.2f", total));
      submit.setEnabled(cart.getRowCount() > 0);
    });
    cartTable.getSelectionModel().addListSelectionListener(e -> {
      edit.setEnabled(cartTable.getSelectedRow() >= 0);
      remove.setEnabled(cartTable.getSelectedRow() >= 0);
    });
    edit.addActionListener(e -> {
      int selected = cartTable.getSelectedRow();
      if (selected < 0) return;
      int id = cartIds.get(selected)[0];
      for (int i = 0; i < menu.getRowCount(); i++) {
        if (((Number) menu.getValueAt(i, 0)).intValue() == id) {
          customizeDrink(panel, menu, i, toppingData, cart, cartIds, selected);
          break;
        }
      }
    });
    remove.addActionListener(e -> {
      int selected = cartTable.getSelectedRow();
      if (selected < 0) return;
      cartIds.remove(selected);
      cart.removeRow(selected);
      status.setText("Drink removed");
    });

    JPanel categories = new JPanel(new GridLayout(0, 1, 10, 10));
    for (String category : new String[] {"Tea", "Coffee"}) {
      JButton categoryButton = new JButton(category);
      categories.add(categoryButton);
      categoryButton.addActionListener(e -> {
        JDialog drinks = new JDialog(SwingUtilities.getWindowAncestor(panel), category,
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel choices = new JPanel(new GridLayout(0, 1, 5, 5));
        for (int i = 0; i < menu.getRowCount(); i++) {
          if (!category.equalsIgnoreCase(String.valueOf(menu.getValueAt(i, 3)))) continue;
          int row = i;
          JButton drink = new JButton(menu.getValueAt(i, 1) + " — $" + menu.getValueAt(i, 2));
          choices.add(drink);
          drink.addActionListener(event -> {
            if (customizeDrink(drinks, menu, row, toppingData, cart, cartIds, -1)) {
              status.setText("Drink added");
              drinks.dispose();
            }
          });//menu panel meant for ordering on customer side
        }
        if (choices.getComponentCount() == 0) choices.add(new JLabel("No drinks in this category"));
        JButton back = new JButton("Back");
        back.addActionListener(event -> drinks.dispose());
        drinks.add(new JScrollPane(choices), BorderLayout.CENTER);
        drinks.add(back, BorderLayout.SOUTH);
        cashierStyle(drinks.getContentPane());
        drinks.setSize(420, 500);
        drinks.setLocationRelativeTo(panel);
        drinks.setVisible(true);
      });//setting sizes and labels
    }

    submit.addActionListener(e -> {
      if (cart.getRowCount() == 0) {
        status.setText("Add a drink first");
        return;
      }
      String summary = "Cashier: " + employeeName + "\n";//cashier side of GUI
      for (int r = 0; r < cart.getRowCount(); r++) {
        summary += cart.getValueAt(r, 1) + " x " + cart.getValueAt(r, 0)
            + " — $" + String.format("%.2f", cart.getValueAt(r, 6)) + "\n";
      }
      summary += String.format("\nTotal $%.2f", cartTotal(cart));
      int confirm = cashierDialog(panel, summary, "Review order",
          new String[] {"Confirm order", "Back"});
      if (confirm != 0) return;
      String payment = paymentDialog(panel, cartTotal(cart));
      if (payment == null) return;
      try {
        conn.setAutoCommit(false);
        // let the demo stream finish before choosing the next ids
        try (Statement stmt = conn.createStatement()) {
          stmt.execute("LOCK TABLE orders, order_items, order_toppings IN SHARE ROW EXCLUSIVE MODE");
        }
        int orderId = nextId("orders", "order_id");
        int itemId = nextId("order_items", "order_item_id");
        int toppingId = nextId("order_toppings", "order_topping_id");
        double total = cartTotal(cart);
        try (PreparedStatement order = conn.prepareStatement(
            "INSERT INTO orders (order_id, order_date, order_total, employee_id, payment_method) "
            + "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)")) {
          order.setInt(1, orderId);
          order.setDouble(2, Math.round(total * 100) / 100.0);
          order.setInt(3, employeeId);
          order.setString(4, payment);
          order.executeUpdate();
        }
        try (PreparedStatement item = conn.prepareStatement(
            "INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, size, "
            + "sugar_percent, ice_level, item_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            PreparedStatement top = conn.prepareStatement(
            "INSERT INTO order_toppings (order_topping_id, order_item_id, topping_id) VALUES (?, ?, ?)")) {
          for (int r = 0; r < cart.getRowCount(); r++) {
            int[] ids = cartIds.get(r);
            item.setInt(1, itemId);
            item.setInt(2, orderId);
            item.setInt(3, ids[0]);
            item.setInt(4, (Integer) cart.getValueAt(r, 1));
            item.setString(5, (String) cart.getValueAt(r, 2));
            item.setInt(6, Integer.parseInt(((String) cart.getValueAt(r, 3)).replace("%", "")));
            item.setString(7, (String) cart.getValueAt(r, 4));
            item.setDouble(8, (Double) cart.getValueAt(r, 6));
            item.executeUpdate();
            for (int t = 1; t < ids.length; t++) {
              top.setInt(1, toppingId++);
              top.setInt(2, itemId);
              top.setInt(3, ids[t]);
              top.executeUpdate();
            }
            itemId++;
          }
        }
        OrderInventory.deduct(conn, orderId);//completeting order
        conn.commit();
        cartIds.clear();
        cart.setRowCount(0);
        status.setText(String.format("Order %d complete ($%.2f)", orderId, total));
      } catch (SQLException ex) {
        try { conn.rollback(); } catch (SQLException ignored) { }
        status.setText("Order failed: " + ex.getMessage());
      } finally {
        try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
      }
    });

    JPanel order = new JPanel(new BorderLayout(10, 10));
    order.add(new JLabel("Current order"), BorderLayout.NORTH);
    order.add(new JScrollPane(cartTable), BorderLayout.CENTER);
    JPanel orderFooter = new JPanel(new BorderLayout());
    orderFooter.add(buttons, BorderLayout.NORTH);
    orderFooter.add(status, BorderLayout.SOUTH);
    order.add(orderFooter, BorderLayout.SOUTH);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categories, order);
    split.setDividerLocation(220);
    split.setResizeWeight(0.2);
    panel.add(header, BorderLayout.NORTH);
    panel.add(split, BorderLayout.CENTER);
    cashierStyle(panel);
    return panel;
  }//setging size and layout

  /**
   * Shows a dialog asking how the customer is paying.
   *
   * @param owner the component the dialog is centered over
   * @param total the order total shown in the dialog
   * @return "Cash", "Card" or "Gift Card", or null if the cashier went back to the order
   */
  private static String paymentDialog(java.awt.Component owner, double total) {
    JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Payment",
        java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    JPanel body = new JPanel(new BorderLayout(10, 10));
    body.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
    body.add(new JLabel(String.format("Choose payment method — $%.2f", total)), BorderLayout.NORTH);
    JPanel methods = new JPanel(new GridLayout(0, 1, 0, 10));
    String[] selected = {null};
    for (String method : new String[] {"Cash", "Card", "Gift Card"}) {
      JButton button = new JButton(method);
      button.addActionListener(e -> { selected[0] = method; dialog.dispose(); });
      methods.add(button);
    }
    body.add(methods, BorderLayout.CENTER);
    JButton back = new JButton("Back to order");
    back.addActionListener(e -> dialog.dispose());
    body.add(back, BorderLayout.SOUTH);
    cashierStyle(body);
    dialog.setContentPane(body);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setSize(420, 310);
    dialog.setMinimumSize(dialog.getSize());
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
    return selected[0];
  }

  /**
   * Shows a dialog in the cashier style with a button for each option.
   *
   * @param owner the component the dialog is centered over
   * @param content a component to show, or any other object to show as text
   * @param title the dialog title
   * @param options the button labels, left to right
   * @return the index of the button clicked, or -1 if the dialog was closed
   */
  private static int cashierDialog(java.awt.Component owner, Object content, String title, String[] options) {
    JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), title,
        java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    JPanel body = new JPanel(new BorderLayout(10, 10));
    body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    if (content instanceof java.awt.Component) {
      body.add((java.awt.Component) content, BorderLayout.CENTER);
    } else {
      JTextArea summary = new JTextArea(String.valueOf(content), 12, 45);
      summary.setEditable(false);
      summary.setLineWrap(true);
      summary.setWrapStyleWord(true);
      body.add(new JScrollPane(summary), BorderLayout.CENTER);
    }
    int[] selected = {-1};
    JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
    for (int i = 0; i < options.length; i++) {
      final int index = i;
      JButton button = new JButton(options[i]);
      button.addActionListener(e -> { selected[0] = index; dialog.dispose(); });
      buttons.add(button);
    }
    body.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(body);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    cashierStyle(body);
    dialog.pack();
    dialog.setMinimumSize(dialog.getSize());
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
    return selected[0];
  }

  // keep these styles local so the manager screens stay the same
  /**
   * Applies the black-and-white cashier style to a component and all of its
   * children. Selected buttons turn black, and tables get black selections.
   *
   * @param component the component to style
   */
  private static void cashierStyle(java.awt.Component component) {
    component.setBackground(java.awt.Color.WHITE);
    component.setForeground(java.awt.Color.BLACK);
    // leave dropdown arrows and spinner controls out of the large button styling
    if (component instanceof JComboBox) {
      JComboBox<?> combo = (JComboBox<?>) component;
      combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
      java.awt.Dimension size = combo.getPreferredSize();
      combo.setPreferredSize(new java.awt.Dimension(size.width + 24, Math.max(34, size.height)));
      return;
    }
    if (component instanceof JSpinner) return;
    if (component instanceof AbstractButton) {
      AbstractButton button = (AbstractButton) component;
      button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
      button.setOpaque(true);
      button.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(java.awt.Color.BLACK),
          BorderFactory.createEmptyBorder(10, 12, 10, 12)));
      button.setBackground(button.isSelected() ? java.awt.Color.BLACK : java.awt.Color.WHITE);
      button.setForeground(button.isSelected() ? java.awt.Color.WHITE : java.awt.Color.BLACK);
      button.addItemListener(e -> {
        button.setBackground(button.isSelected() ? java.awt.Color.BLACK : java.awt.Color.WHITE);
        button.setForeground(button.isSelected() ? java.awt.Color.WHITE : java.awt.Color.BLACK);
      });
    }
    if (component instanceof JTabbedPane) {
      ((JTabbedPane) component).setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
        protected void paintTabBackground(java.awt.Graphics g, int placement, int index,
            int x, int y, int w, int h, boolean selected) {
          g.setColor(selected ? java.awt.Color.LIGHT_GRAY : java.awt.Color.WHITE);
          g.fillRect(x, y, w, h);
        }
      });
    }
    if (component instanceof JTable) {
      JTable table = (JTable) component;
      table.setSelectionBackground(java.awt.Color.BLACK);
      table.setSelectionForeground(java.awt.Color.WHITE);
      table.setGridColor(java.awt.Color.LIGHT_GRAY);
    }
    if (component instanceof java.awt.Container) {
      for (java.awt.Component child : ((java.awt.Container) component).getComponents()) cashierStyle(child);
    }
  }

  /**
   * Adds up the line totals in the cart.
   *
   * @param cart the cart table model
   * @return the order total, rounded to cents
   */
  private static double cartTotal(DefaultTableModel cart) {
    double total = 0;
    for (int r = 0; r < cart.getRowCount(); r++) total += ((Number) cart.getValueAt(r, 6)).doubleValue();
    return Math.round(total * 100) / 100.0;
  }

  /**
   * Shows the drink options dialog for quantity, sweetness, toppings, ice and
   * size, then adds the drink to the cart or updates an existing cart line.
   *
   * @param owner the component the dialog is centered over
   * @param menu the menu table model
   * @param row the menu row of the drink
   * @param toppings the toppings table model
   * @param cart the cart table model
   * @param cartIds for each cart line, the menu item id followed by its topping ids
   * @param edit the cart line to edit, or -1 to add a new drink
   * @return true if the cart changed, false if the cashier cancelled
   */
  private static boolean customizeDrink(java.awt.Component owner, DefaultTableModel menu, int row,
      DefaultTableModel toppings, DefaultTableModel cart, java.util.List<int[]> cartIds, int edit) {
    JPanel fields = new JPanel(new BorderLayout(10, 10));
    JSpinner qty = new JSpinner(new SpinnerNumberModel(edit < 0 ? 1 : (Integer) cart.getValueAt(edit, 1), 1, 50, 1));
    JPanel quantity = new JPanel();
    quantity.add(new JLabel("Quantity"));
    quantity.add(qty);//increase the number of drinks wanted
    fields.add(quantity, BorderLayout.NORTH);
    JTabbedPane tabs = new JTabbedPane();
    ButtonGroup sugar = new ButtonGroup();
    ButtonGroup ice = new ButtonGroup();
    ButtonGroup size = new ButtonGroup();
    tabs.addTab("Sweetness", choiceButtons(sugar, new String[] {"100%", "75%", "50%", "25%", "0%"},
        edit < 0 ? "100%" : (String) cart.getValueAt(edit, 3)));
    JPanel toppingPanel = new JPanel(new GridLayout(0, 2, 5, 5));
    JToggleButton[] toppingButtons = new JToggleButton[toppings.getRowCount()];
    for (int i = 0; i < toppings.getRowCount(); i++) {
      toppingButtons[i] = new JToggleButton(toppings.getValueAt(i, 1) + " +$" + toppings.getValueAt(i, 2));
      if (edit >= 0) {
        int id = ((Number) toppings.getValueAt(i, 0)).intValue();
        for (int t = 1; t < cartIds.get(edit).length; t++) {
          if (cartIds.get(edit)[t] == id) toppingButtons[i].setSelected(true);
        }
      }
      toppingPanel.add(toppingButtons[i]);
    }
    JPanel toppingPage = new JPanel(new BorderLayout(5, 5));
    toppingPage.add(new JScrollPane(toppingPanel), BorderLayout.CENTER);
    JButton next = new JButton("Next: ice");
    next.addActionListener(e -> tabs.setSelectedIndex(2));
    JPanel toppingNavigation = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
    toppingNavigation.add(next);
    toppingPage.add(toppingNavigation, BorderLayout.SOUTH);
    tabs.addTab("Toppings", toppingPage);
    tabs.addTab("Ice", choiceButtons(ice, new String[] {"normal", "less", "no ice"},
        edit < 0 ? "normal" : (String) cart.getValueAt(edit, 4)));//settig the sizes for all topppings buttons
    tabs.addTab("Size", choiceButtons(size, new String[] {"medium", "large"},
        edit < 0 ? "medium" : (String) cart.getValueAt(edit, 2)));
    tabs.setPreferredSize(new java.awt.Dimension(620, 290));
    java.util.Enumeration<AbstractButton> sweetness = sugar.getElements();
    while (sweetness.hasMoreElements()) sweetness.nextElement().addActionListener(e -> tabs.setSelectedIndex(1));
    java.util.Enumeration<AbstractButton> iceOptions = ice.getElements();
    while (iceOptions.hasMoreElements()) iceOptions.nextElement().addActionListener(e -> tabs.setSelectedIndex(3));
    fields.add(tabs, BorderLayout.CENTER);
    JLabel price = new JLabel();
    Runnable updatePrice = () -> {
      double each = ((Number) menu.getValueAt(row, 2)).doubleValue();
      if (size.getSelection().getActionCommand().equals("large")) each += ((Number) menu.getValueAt(row, 4)).doubleValue();
      for (int i = 0; i < toppingButtons.length; i++) {
        if (toppingButtons[i].isSelected()) each += ((Number) toppings.getValueAt(i, 2)).doubleValue();
      }
      price.setText(String.format("Total $%.2f", each * (Integer) qty.getValue()));
    };
    for (JToggleButton button : toppingButtons) button.addActionListener(e -> updatePrice.run());
    java.util.Enumeration<AbstractButton> sizes = size.getElements();
    while (sizes.hasMoreElements()) {
      AbstractButton button = sizes.nextElement();
      if (button.getActionCommand().equals("large")) button.setText("large +$" + menu.getValueAt(row, 4));
      button.addActionListener(e -> updatePrice.run());
    }
    qty.addChangeListener(e -> updatePrice.run());
    updatePrice.run();
    fields.add(price, BorderLayout.SOUTH);
    // cancel leaves the cart unchanged
    while (cashierDialog(owner, fields, String.valueOf(menu.getValueAt(row, 1)),
        new String[] {edit < 0 ? "Add to order" : "Save changes", "Cancel"}) == 0) {
      try { qty.commitEdit(); } catch (java.text.ParseException ex) {
        JOptionPane.showMessageDialog(owner, "Enter a quantity from 1 to 50");
        continue;
      }
      int count = (Integer) qty.getValue();
      double each = ((Number) menu.getValueAt(row, 2)).doubleValue();
      if (size.getSelection().getActionCommand().equals("large")) each += ((Number) menu.getValueAt(row, 4)).doubleValue();
      java.util.List<Integer> ids = new java.util.ArrayList<>();
      java.util.List<String> names = new java.util.ArrayList<>();
      ids.add(((Number) menu.getValueAt(row, 0)).intValue());
      for (int i = 0; i < toppingButtons.length; i++) {
        if (!toppingButtons[i].isSelected()) continue;
        ids.add(((Number) toppings.getValueAt(i, 0)).intValue());
        names.add((String) toppings.getValueAt(i, 1));
        each += ((Number) toppings.getValueAt(i, 2)).doubleValue();
      }
      Object[] values = {menu.getValueAt(row, 1), count, size.getSelection().getActionCommand(),
          sugar.getSelection().getActionCommand(), ice.getSelection().getActionCommand(),
          String.join(", ", names), Math.round(each * count * 100) / 100.0};
      int[] itemIds = ids.stream().mapToInt(Integer::intValue).toArray();
      if (edit < 0) {
        cartIds.add(itemIds);//adds items to cart and selects their value
        cart.addRow(values);
      } else {
        cartIds.set(edit, itemIds);
        for (int c = 0; c < values.length; c++) cart.setValueAt(values[c], edit, c);
      }
      return true;
    }
    return false;
  }

  /**
   * Creates a grid of toggle buttons where only one can be selected.
   *
   * @param group the button group the buttons are added to
   * @param choices the button labels, also used as their action commands
   * @param selected the choice that starts selected
   * @return the panel holding the buttons
   */
  private static JPanel choiceButtons(ButtonGroup group, String[] choices, String selected) {
    JPanel panel = new JPanel(new GridLayout(0, 3, 5, 5));
    for (String choice : choices) {
      JToggleButton button = new JToggleButton(choice);
      button.setActionCommand(choice);
      button.setSelected(choice.equals(selected));
      group.add(button);
      panel.add(button);
    }
    return panel;
  }

  /**
   * Finds the next unused id in a table. The seed data inserted ids directly,
   * so the database sequences are behind and can't be used.
   *
   * @param table the table to look in
   * @param column the id column of that table
   * @return one more than the largest id in the table, or 1 if it is empty
   * @throws SQLException if the query fails
   */
  private static int nextId(String table, String column) throws SQLException {
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM " + table)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  /**
   * Builds the Manager view, with tabs for menu items, inventory
   * (ingredients, toppings and supplies), employees and reports.
   *
   * @return the manager panel
   */
  private static JPanel managerView() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Menu Items", menuItemsPanel());
    JTabbedPane inventory = new JTabbedPane();
    inventory.addTab("Ingredients", inventoryPanel());
    inventory.addTab("Toppings", new StockPanel(conn, "toppings", "topping_id"));
    inventory.addTab("Supplies", new StockPanel(conn, "supplies", "supply_id"));
    cashierStyle(inventory);
    tabs.addTab("Inventory", inventory);
    tabs.addTab("Employees", employeePanel());
    JPanel orders = new OrdersPanel(conn);
    cashierStyle(orders);
    tabs.addTab("Orders", orders);
    JPanel reports = reportsPanel();
    cashierStyle(reports);
    tabs.addTab("Reports", reports);
    panel.add(tabs, BorderLayout.CENTER);
    return panel;
  }

  /** Names of the reports shown in the Reports tab drop-down. */
  private static final String[] REPORT_NAMES = {
      "X-Report (Today by Hour)", "Sales Summary", "Sales by Day of Week", "Sales by Hour", "Revenue by Menu Item",
      "Most Popular Drinks", "Most Popular Toppings", "Orders per Employee", "Ingredient Usage",
      "Restock Report"
  };

  /**
   * Gets the column headers for a report. The columns and SQL for each report
   * are kept in matching switch statements so a report name is the single key
   * needed to find both.
   *
   * @param report a report name from {@link #REPORT_NAMES}
   * @return the column headers, or an empty array if the report is unknown
   * @see #reportSql(String)
   */
  private static String[] reportColumns(String report) {
    switch (report) {
      case "X-Report (Today by Hour)":
        return new String[] {"Hour", "Orders", "Drinks", "Sales", "Avg Order"};
      case "Sales Summary": return new String[] {"Orders", "Total Sales", "Avg Order"};
      case "Sales by Day of Week": return new String[] {"Day", "Orders", "Total Sales"};
      case "Sales by Hour": return new String[] {"Hour", "Orders", "Total Sales"};
      case "Revenue by Menu Item":return new String[] {"Menu Item", "Quantity Sold", "Total Sales"};
      case "Most Popular Drinks": return new String[] {"Drink", "Times Ordered"};
      case "Most Popular Toppings": return new String[] {"Topping", "Times Added"};
      case "Orders per Employee": return new String[] {"Employee", "Orders Handled"};
      case "Ingredient Usage": return new String[] {"Ingredient", "Total Used", "Unit"};
      case "Restock Report":
        return new String[] {"Type", "Item", "On Hand", "Minimum", "Short By", "Unit"};
      default: return new String[0];
    }
  }

  /**
   * Gets the SQL query for a report. Each query takes two timestamp
   * parameters: the start of the range (inclusive) and the end (exclusive),
   * except the Restock Report, which shows current stock and takes none.
   *
   * @param report a report name from {@link #REPORT_NAMES}
   * @return the SQL query, or null if the report is unknown
   * @see #reportColumns(String)
   */
  private static String reportSql(String report) {
    switch (report) {
      case "X-Report (Today by Hour)":
        // every hour from the first to the last sale, so slow hours show as 0, then a total row
        return "WITH day_orders AS (SELECT o.order_date, o.order_total, "
            + "(SELECT COALESCE(SUM(i.quantity), 0) FROM order_items i WHERE i.order_id = o.order_id) AS drinks "
            + "FROM orders o WHERE o.order_date >= ? AND o.order_date < ?), "
            + "hours AS (SELECT generate_series(date_trunc('hour', MIN(order_date)), "
            + "date_trunc('hour', MAX(order_date)), interval '1 hour') AS hr FROM day_orders) "
            + "SELECT to_char(h.hr, 'HH24:00') || ' - ' || to_char(h.hr + interval '1 hour', 'HH24:00'), "
            + "COUNT(d.order_date), COALESCE(SUM(d.drinks), 0), COALESCE(SUM(d.order_total), 0), "
            + "ROUND(COALESCE(AVG(d.order_total), 0), 2) "
            + "FROM hours h LEFT JOIN day_orders d ON date_trunc('hour', d.order_date) = h.hr GROUP BY h.hr "
            + "UNION ALL SELECT 'Total', COUNT(*), COALESCE(SUM(drinks), 0), COALESCE(SUM(order_total), 0), "
            + "ROUND(COALESCE(AVG(order_total), 0), 2) FROM day_orders ORDER BY 1";
      case "Sales Summary":
        return "SELECT COUNT(*), COALESCE(SUM(order_total), 0), COALESCE(AVG(order_total), 0) "
            + "FROM orders WHERE order_date >= ? AND order_date < ?";
      case "Sales by Day of Week":
        return "SELECT to_char(order_date, 'Day'), COUNT(*), SUM(order_total) FROM orders "
            + "WHERE order_date >= ? AND order_date < ? "
            + "GROUP BY to_char(order_date, 'Day'), EXTRACT(ISODOW FROM order_date) "
            + "ORDER BY EXTRACT(ISODOW FROM order_date)";
      case "Sales by Hour":
        return "SELECT EXTRACT(HOUR FROM order_date), COUNT(*), SUM(order_total) FROM orders "
            + "WHERE order_date >= ? AND order_date < ? "
            + "GROUP BY EXTRACT(HOUR FROM order_date) ORDER BY 1";
      case "Revenue by Menu Item":
    return "SELECT m.name, SUM(oi.quantity), SUM(oi.item_total) "
        + "FROM order_items oi "
        + "JOIN menu_items m ON m.menu_item_id = oi.menu_item_id "
        + "JOIN orders o ON o.order_id = oi.order_id "
        + "WHERE o.order_date >= ? AND o.order_date < ? "
        + "GROUP BY m.menu_item_id, m.name "
        + "ORDER BY 3 DESC";
      case "Most Popular Drinks":
        return "SELECT m.name, SUM(oi.quantity) FROM order_items oi "
            + "JOIN menu_items m ON m.menu_item_id = oi.menu_item_id "
            + "JOIN orders o ON o.order_id = oi.order_id "
            + "WHERE o.order_date >= ? AND o.order_date < ? "
            + "GROUP BY m.name ORDER BY 2 DESC";
      case "Most Popular Toppings":
        return "SELECT t.name, SUM(oi.quantity) FROM order_toppings ot "
            + "JOIN order_items oi ON oi.order_item_id = ot.order_item_id "
            + "JOIN toppings t ON t.topping_id = ot.topping_id "
            + "JOIN orders o ON o.order_id = oi.order_id "
            + "WHERE o.order_date >= ? AND o.order_date < ? "
            + "GROUP BY t.name ORDER BY 2 DESC";
      case "Orders per Employee":
        return "SELECT e.name, COUNT(*) FROM orders o "
            + "JOIN employees e ON e.employee_id = o.employee_id "
            + "WHERE o.order_date >= ? AND o.order_date < ? "
            + "GROUP BY e.name ORDER BY 2 DESC";
      case "Ingredient Usage":
        return "SELECT i.name, SUM(mi.quantity_per_serving * oi.quantity), i.unit_of_measure "
            + "FROM order_items oi "
            + "JOIN menu_inventory mi ON mi.menu_item_id = oi.menu_item_id "
            + "JOIN ingredients i ON i.ingredient_id = mi.ingredient_id "
            + "JOIN orders o ON o.order_id = oi.order_id "
            + "WHERE o.order_date >= ? AND o.order_date < ? "
            + "GROUP BY i.name, i.unit_of_measure ORDER BY 2 DESC";
      case "Restock Report":
        return "SELECT 'Ingredient', name, quantity_on_hand, min_quantity, "
            + "min_quantity - quantity_on_hand, unit_of_measure FROM ingredients "
            + "WHERE quantity_on_hand < min_quantity "
            + "UNION ALL SELECT 'Topping', name, quantity_on_hand, min_quantity, "
            + "min_quantity - quantity_on_hand, 'servings' FROM toppings "
            + "WHERE quantity_on_hand < min_quantity "
            + "UNION ALL SELECT 'Supply', name, quantity_on_hand, min_quantity, "
            + "min_quantity - quantity_on_hand, 'pieces' FROM supplies "
            + "WHERE quantity_on_hand < min_quantity "
            + "ORDER BY 1, 2";
      default: return null;
    }
  }

  /**
   * Runs a parameterized SQL query and loads every result row into a
   * read-only table model.
   *
   * @param sql the SELECT statement to execute, with {@code ?} placeholders
   * @param columns the column headers for the returned table
   * @param params the values for the placeholders, in order
   * @return a table model holding one row per result row
   * @throws SQLException if the query fails
   */
  private static DefaultTableModel queryParams(String sql, String[] columns, Object... params)
      throws SQLException {
    DefaultTableModel model = (DefaultTableModel) emptyTable(columns).getModel();
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
      try (ResultSet rs = stmt.executeQuery()) {
        int cols = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Vector<Object> row = new Vector<>();
          for (int i = 1; i <= cols; i++) row.add(rs.getObject(i));
          model.addRow(row);
        }
      }
    }
    return model;
  }

  /**
   * Builds the Reports tab, where a manager picks a report and date range,
   * runs it, and can export the results to a CSV file.
   *
   * @return the reports panel
   */
  private static JPanel reportsPanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

    JComboBox<String> reportType = new JComboBox<>(REPORT_NAMES);
    JTextField from = new JTextField(LocalDate.now().toString(), 10);
    JTextField to = new JTextField(LocalDate.now().toString(), 10);
    JButton run = new JButton("Run report");
    JButton export = new JButton("Export to CSV");
    JButton chart = new JButton("Show Chart");
    export.setEnabled(false);
    chart.setEnabled(false);

    JPanel controls = new JPanel();
    controls.add(new JLabel("Report"));
    controls.add(reportType);
    JPanel dates = new JPanel();
    dates.add(new JLabel("From"));
    dates.add(from);
    dates.add(new JLabel("To"));
    dates.add(to);
    dates.add(run);
    dates.add(chart);
    dates.add(export);
    JPanel reportControls = new JPanel(new GridLayout(0, 1));
    reportControls.add(controls);
    reportControls.add(dates);

    JTable table = emptyTable(new String[0]);
    JLabel status = new JLabel("Choose a report and date range, then run it");
    reportType.addActionListener(e -> {
        chart.setEnabled(false);
        export.setEnabled(false);
    });
      run.addActionListener(e -> {
      export.setEnabled(false);
      chart.setEnabled(false);
      table.setModel(emptyTable(new String[0]).getModel());
      String report = (String) reportType.getSelectedItem();
      if (report.startsWith("X-Report")) {
        // the x-report always uses today
        from.setText(LocalDate.now().toString());
        to.setText(from.getText());
      }
      LocalDate fromDate;
      LocalDate toDate;
      try {
        fromDate = LocalDate.parse(from.getText().trim());
        toDate = LocalDate.parse(to.getText().trim());
        if (toDate.isBefore(fromDate)) {
          status.setText("End date must be on or after start date");
          return;
        }
        toDate.plusDays(1);
      } catch (java.time.DateTimeException ex) {
        status.setText("Dates must use yyyy-MM-dd within the supported range");
        return;
      }
      try {
        // the restock report shows current stock, so it ignores the dates
        Object[] params = "Restock Report".equals(report) ? new Object[0]
            : new Object[] {Timestamp.valueOf(fromDate.atStartOfDay()),
                Timestamp.valueOf(toDate.plusDays(1).atStartOfDay())};
        DefaultTableModel model = queryParams(reportSql(report), reportColumns(report), params);
          table.setModel(model);
          status.setText(model.getRowCount() + " rows");
          export.setEnabled(model.getRowCount() > 0);
          chart.setEnabled(
            "Ingredient Usage".equals(report)
            && model.getRowCount() > 0
            );
      } catch (SQLException ex) {
        status.setText("Report failed: " + ex.getMessage());
        export.setEnabled(false);
        chart.setEnabled(false);
      }
    });
  
    chart.addActionListener(e -> {
      DefaultTableModel model = (DefaultTableModel) table.getModel();
      if (model.getRowCount() == 0) {
        return;
      }
      JDialog dialog = new JDialog(
        SwingUtilities.getWindowAncestor(panel),
        "Product Usage Chart",
        java.awt.Dialog.ModalityType.MODELESS
    );

    dialog.add(new JScrollPane(createUsageChart(model)));

    dialog.setSize(900, 600);
    dialog.setLocationRelativeTo(panel);
    dialog.setVisible(true);
});

    export.addActionListener(e -> {
      JFileChooser chooser = new JFileChooser();
      chooser.setSelectedFile(new java.io.File(
          ((String) reportType.getSelectedItem()).replace(" ", "_") + ".csv"));
      if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;
      DefaultTableModel model = (DefaultTableModel) table.getModel();
      try (java.io.PrintWriter writer = new java.io.PrintWriter(chooser.getSelectedFile())) {
        for (int c = 0; c < model.getColumnCount(); c++) {
          writer.print(c > 0 ? "," + model.getColumnName(c) : model.getColumnName(c));
        }
        writer.println();
        for (int r = 0; r < model.getRowCount(); r++) {
          for (int c = 0; c < model.getColumnCount(); c++) {
            writer.print(c > 0 ? "," + model.getValueAt(r, c) : model.getValueAt(r, c));
          }
          writer.println();
        }
        status.setText("Saved to " + chooser.getSelectedFile().getName());
      } catch (java.io.IOException ex) {
        status.setText("Could not save report: " + ex.getMessage());
      }
    });

    panel.add(reportControls, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    return panel;
  }


  /**
   * Draws a horizontal bar chart of ingredient usage, scaled to the largest value.
   *
   * @param model the report rows, with the name, amount used and unit in the first three columns
   * @return the chart panel
   */
  private static JPanel createUsageChart(DefaultTableModel model) {

    int rows = model.getRowCount();
    int barHeight = 30;
    int spacing = 15;

    JPanel chart = new JPanel() {

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);

            java.awt.Graphics2D g2 =
                (java.awt.Graphics2D) g.create();

            g2.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON
            );

            int left = 180;
            int top = 60;
            int right = 130;
            int availableWidth = Math.max(
                1, getWidth() - left - right
            );

            double max = 0;

            for (int i = 0; i < rows; i++) {
                double value = ((Number)
                    model.getValueAt(i, 1)).doubleValue();

                max = Math.max(max, value);
            }

            g2.setColor(java.awt.Color.BLACK);
            g2.setFont(new java.awt.Font(
                "Arial", java.awt.Font.BOLD, 18
            ));

            g2.drawString("Product Usage Chart", 20, 30);

            g2.setFont(new java.awt.Font(
                "Arial", java.awt.Font.PLAIN, 12
            ));

            for (int i = 0; i < rows; i++) {

                String name = model.getValueAt(i, 0).toString();

                double value = ((Number)
                    model.getValueAt(i, 1)).doubleValue();

                String unit = model.getValueAt(i, 2).toString();

                int y = top + i * (barHeight + spacing);

                int width = max > 0
                    ? (int) (value / max * availableWidth)
                    : 0;

                // Ingredient name
                g2.setColor(java.awt.Color.BLACK);
                g2.drawString(name, 10, y + 20);

                // Draw bar
                g2.setColor(java.awt.Color.BLACK);
                g2.fillRect(left, y, width, barHeight);

                // Display quantity used
                g2.setColor(java.awt.Color.BLACK);
                g2.drawString(
                    String.format("%,.2f %s", value, unit),
                    left + width + 8,
                    y + 20
                );
            }

            g2.dispose();
        }
    };

    chart.setBackground(java.awt.Color.WHITE);

    chart.setPreferredSize(new java.awt.Dimension(
        1100,
        Math.max(500, 80 + rows * (barHeight + spacing))
    ));

    return chart;
}

  /**
   * Builds the Ingredients tab, which lists ingredients and lets a manager
   * add, update or remove them.
   *
   * @return the inventory panel
   */
  private static JPanel inventoryPanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JPanel controls = new JPanel();
    JButton refresh = new JButton("Refresh");
    JButton add = new JButton("Add");
    JButton update = new JButton("Modify");
    controls.add(refresh);
    controls.add(add);
    controls.add(update);

    String[] columns = {"ID", "Name", "Cost", "Unit", "Quantity on hand", "Restock minimum"};
    JTable table = emptyTable(columns);
    JLabel status = new JLabel();
    String sql = "SELECT ingredient_id, name, cost, unit_of_measure, quantity_on_hand, min_quantity "
        + "FROM ingredients ORDER BY 1";
    
    refresh.addActionListener(e -> {
      try {
        table.setModel(query(sql, columns));
        status.setText(table.getRowCount() + " rows");
      } catch (SQLException ex) {
        status.setText("Error: " + ex.getMessage());
      }
    });
    
    add.addActionListener(e -> {
      JTextField name = new JTextField();
      JTextField cost = new JTextField();
      JTextField unit = new JTextField();
      JTextField quantity = new JTextField("0");
      JTextField minimum = new JTextField("0");
      JPanel fields = inventoryFields(name, cost, unit, quantity, minimum);
      int result = JOptionPane.showConfirmDialog(panel, fields, "Add inventory item",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) {
        return;
      }
      try {
        java.math.BigDecimal parsedCost = inventoryNumber(cost);
        java.math.BigDecimal parsedQuantity = inventoryNumber(quantity);
        java.math.BigDecimal parsedMinimum = inventoryNumber(minimum);
        if (name.getText().trim().isEmpty() || unit.getText().trim().isEmpty()) {
          status.setText("Enter a name and unit");
          return;
        }
        int id = nextId("ingredients", "ingredient_id");
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO ingredients (ingredient_id, name, cost, unit_of_measure, quantity_on_hand, "
                + "min_quantity) VALUES (?, ?, ?, ?, ?, ?)")) {
          stmt.setInt(1, id);
          stmt.setString(2, name.getText().trim());
          stmt.setBigDecimal(3, parsedCost);
          stmt.setString(4, unit.getText().trim());
          stmt.setBigDecimal(5, parsedQuantity);
          stmt.setBigDecimal(6, parsedMinimum);
          stmt.executeUpdate();
        }
        status.setText("Inventory item added");
        refresh.doClick();
      } catch (NumberFormatException ex) {
        status.setText("Cost, quantity and minimum must be nonnegative numbers with at most two decimal places");
      } catch (SQLException ex) {
        status.setText("Could not add inventory item: " + ex.getMessage());
      }
    });
    
    update.addActionListener(e -> {
      int selected = table.getSelectedRow();
      if (selected < 0) {
        status.setText("Select an inventory item first");
        return;
      }
      int modelRow = table.convertRowIndexToModel(selected);
      int id = ((Number) table.getModel().getValueAt(modelRow, 0)).intValue();
      JTextField name = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 1)));
      JTextField cost = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 2)));
      JTextField unit = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 3)));
      JTextField quantity = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 4)));
      JTextField minimum = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 5)));
      JPanel fields = inventoryFields(name, cost, unit, quantity, minimum);
      Object[] options = {"Update", "Remove", "Cancel"};
      int result = JOptionPane.showOptionDialog(panel, fields, "Update inventory item",
      JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
      if (result == 1) {
        int confirm = JOptionPane.showConfirmDialog(panel,
        "Are you sure you want to remove this item?",
        "Confirm removal", JOptionPane.YES_NO_OPTION);
      if (confirm != JOptionPane.YES_OPTION) {
        return;
      }
      try (PreparedStatement stmt = conn.prepareStatement(
          "DELETE FROM ingredients WHERE ingredient_id = ?")) {
        stmt.setInt(1, id);
        stmt.executeUpdate();
        status.setText("Inventory item removed");
        refresh.doClick();
      }
      catch (SQLException ex) {
        status.setText("Could not remove inventory item: " + ex.getMessage());
      }
      return;
      }
      if (result != 0) {
        return;
      }
      try {
        java.math.BigDecimal parsedCost = inventoryNumber(cost);
        java.math.BigDecimal parsedQuantity = inventoryNumber(quantity);
        java.math.BigDecimal parsedMinimum = inventoryNumber(minimum);
        if (name.getText().trim().isEmpty() || unit.getText().trim().isEmpty()) {
          status.setText("Enter a name and unit");
          return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
            "UPDATE ingredients SET name = ?, cost = ?, unit_of_measure = ?, quantity_on_hand = ?, "
                + "min_quantity = ? WHERE ingredient_id = ?")) {
          stmt.setString(1, name.getText().trim());
          stmt.setBigDecimal(2, parsedCost);
          stmt.setString(3, unit.getText().trim());
          stmt.setBigDecimal(4, parsedQuantity);
          stmt.setBigDecimal(5, parsedMinimum);
          stmt.setInt(6, id);
          stmt.executeUpdate();
        }
        status.setText("Inventory item updated");
        refresh.doClick();
      } catch (NumberFormatException ex) {
        status.setText("Cost, quantity and minimum must be nonnegative numbers with at most two decimal places");
      } catch (SQLException ex) {
        status.setText("Could not update inventory item: " + ex.getMessage());
      }
    });
    
    refresh.doClick();
    panel.add(controls, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    return panel;
  }

  /**
   * Builds the Employees tab, which lists employees and lets a manager
   * add or modify them.
   *
   * @return the employee panel
   */
  private static JPanel employeePanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JPanel controls = new JPanel();
    JButton refresh = new JButton("Refresh");
    JButton add = new JButton("Add");
    JButton update = new JButton("Modify");
    controls.add(refresh);
    controls.add(add);
    controls.add(update);

    String[] columns = {"ID", "Name", "Position", "Hourly Rate", "Hire Date"};
    JTable table = emptyTable(columns);
    JLabel status = new JLabel();
    String sql = "SELECT employee_id, name, position, hourly_rate, hire_date "
        + "FROM employees";
    
    refresh.addActionListener(e -> {
      try {
        table.setModel(query(sql, columns));
        status.setText(table.getRowCount() + " rows");
      } catch (SQLException ex) {
        status.setText("Error: " + ex.getMessage());
      }
    });
    
    add.addActionListener(e -> {
      JTextField name = new JTextField();
      JTextField position = new JTextField();
      JTextField hourlyRate = new JTextField();
      JTextField hireDate = new JTextField();
      JPanel fields = employeeFields(name, position, hourlyRate, hireDate);
      int result = JOptionPane.showConfirmDialog(panel, fields, "Add Employee",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) {
        return;
      }
      try {
        double parsedHourlyRate = Double.parseDouble(hourlyRate.getText().trim());
        int id = nextId("employees", "employee_id");
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO employees "
                + "( employee_id, name, position, hourly_rate, hire_date) "
                + "VALUES (?, ?, ?, ?, ?)")) {
          stmt.setInt(1, id);
          stmt.setString(2, name.getText().trim());
          stmt.setString(3, position.getText().trim());
          stmt.setDouble(4, parsedHourlyRate);
          if (hireDate.getText().trim().isEmpty()) {
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
          } else {
            stmt.setTimestamp(5, Timestamp.valueOf(hireDate.getText().trim()));
          }
          stmt.executeUpdate();
        }
        status.setText("Employee added");
        refresh.doClick();
      } catch (NumberFormatException ex) {
        status.setText("Hourly rate must be a number");
      } catch (IllegalArgumentException ex) {
        status.setText("Hire date must use yyyy-[m]m-[d]d hh:mm:ss");
      } catch (SQLException ex) {
        status.setText("Could not add employee: " + ex.getMessage());
      }
    });
    
    update.addActionListener(e -> {
      int selected = table.getSelectedRow();
      if (selected < 0) {
        status.setText("Select an employee first");
        return;
      }
      int modelRow = table.convertRowIndexToModel(selected);
      int id = ((Number) table.getModel().getValueAt(modelRow, 0)).intValue();
      JTextField name = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 1)));
      JTextField position = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 2)));
      JTextField hourlyRate = new JTextField(
          String.valueOf(table.getModel().getValueAt(modelRow, 3)));
      JTextField hireDate = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 4)));
      JPanel fields = employeeFields(name, position, hourlyRate, hireDate);
      Object[] options = {"Update", "Remove", "Cancel"};
      int result = JOptionPane.showOptionDialog(panel, fields, "Update employee",
      JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
      if (result == 1) {
        int confirm = JOptionPane.showConfirmDialog(panel,
            "Are you sure you want to remove this employee?",
            "Confirm removal", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
          return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
            "DELETE FROM employees WHERE employee_id = ?")) {
          stmt.setInt(1, id);
          stmt.executeUpdate();
          status.setText("Employee removed");
          refresh.doClick();
        }
        catch (SQLException ex) {
          status.setText("Could not remove employee: " + ex.getMessage());
        }
        return;
      }
      if (result != 0) {
        return;
      }
      try {
        double parsedHourlyRate = Double.parseDouble(hourlyRate.getText().trim());
        try (PreparedStatement stmt = conn.prepareStatement(
            "UPDATE employees SET name = ?, position = ?, hourly_rate = ?, hire_date = ? "
                + "WHERE employee_id = ?")) {
          stmt.setString(1, name.getText().trim());
          stmt.setString(2, position.getText().trim());
          stmt.setDouble(3, parsedHourlyRate);
          stmt.setTimestamp(4, Timestamp.valueOf(hireDate.getText().trim()));
          stmt.setInt(5, id);
          stmt.executeUpdate();
        }
        status.setText("Employee updated");
        refresh.doClick();
      } catch (NumberFormatException ex) {
        status.setText("Hourly rate must be a number");
      } catch (IllegalArgumentException ex) {
        status.setText("Hire date must use yyyy-[m]m-[d]d hh:mm:ss");
      } catch (SQLException ex) {
        status.setText("Could not update employee: " + ex.getMessage());
      }
    });
    
    refresh.doClick();
    panel.add(controls, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    return panel;
  }

  /**
   * Builds the Menu Items tab, which lists menu items and lets a manager
   * add or modify them.
   *
   * @return the menu items panel
   */
  private static JPanel menuItemsPanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    JPanel controls = new JPanel();
    JButton refresh = new JButton("Refresh");
    JButton add = new JButton("Add");
    JButton update = new JButton("Modify");
    controls.add(refresh);
    controls.add(add);
    controls.add(update);

    String[] columns = {"ID", "Name", "Price", "Category", "Description"};
    JTable table = emptyTable(columns);
    JLabel status = new JLabel();
    String sql = "SELECT menu_item_id, name, base_price, category, description "
        + "FROM menu_items ORDER BY 1";
    
    refresh.addActionListener(e -> {
      try {
        table.setModel(query(sql, columns));
        status.setText(table.getRowCount() + " rows");
      } catch (SQLException ex) {
        status.setText("Error: " + ex.getMessage());
      }
    });

    add.addActionListener(e -> {
      JTextField name = new JTextField();
      JTextField price = new JTextField();
      JTextField category = new JTextField();
      JTextField description = new JTextField();
      JPanel fields = menuItemFields(name, price, category, description);
      int result = JOptionPane.showConfirmDialog(panel, fields, "Add menu item",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) {
        return;
      }
      try {
        double parsedPrice = Double.parseDouble(price.getText().trim());
        int id = nextId("menu_items", "menu_item_id");
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO menu_items "
                + "(menu_item_id, name, base_price, category, description) "
                + "VALUES (?, ?, ?, ?, ?)")) {
          stmt.setInt(1, id);
          stmt.setString(2, name.getText().trim());
          stmt.setDouble(3, parsedPrice);
          stmt.setString(4, category.getText().trim());
          stmt.setString(5, description.getText().trim());
          stmt.executeUpdate();
        }
        status.setText("Menu item added");
        refresh.doClick();
      } catch (NumberFormatException ex) {
        status.setText("Price must be a number");
      } catch (SQLException ex) {
        status.setText("Could not add menu item: " + ex.getMessage());
      }
    });
    
    update.addActionListener(e -> {
      int selected = table.getSelectedRow();
      if (selected < 0) {
        status.setText("Select an menu item first");
        return;
      }
      int modelRow = table.convertRowIndexToModel(selected);
      int id = ((Number) table.getModel().getValueAt(modelRow, 0)).intValue();
      JTextField name = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 1)));
      JTextField price = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 2)));
      JTextField category = new JTextField(String.valueOf(table.getModel().getValueAt(modelRow, 3)));
      JTextField description = new JTextField(
          String.valueOf(table.getModel().getValueAt(modelRow, 4)));
      JPanel fields = menuItemFields(name, price, category, description);
      Object[] options = {"Update", "Remove", "Cancel"};
      int result = JOptionPane.showOptionDialog(panel, fields, "Update menu item",
          JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
      if (result == 1) {
        int confirm = JOptionPane.showConfirmDialog(panel,
        "Are you sure you want to remove this item?",
        "Confirm removal", JOptionPane.YES_NO_OPTION);
      if (confirm != JOptionPane.YES_OPTION) {
        return;
      }
      try (PreparedStatement stmt = conn.prepareStatement(
          "DELETE FROM menu_items WHERE menu_item_id = ?")) {
        stmt.setInt(1, id);
        stmt.executeUpdate();
        status.setText("Menu item removed");
        refresh.doClick();
      }
      catch (SQLException ex) {
        status.setText("Could not remove menu item: " + ex.getMessage());
      }
      return;
      }
      if (result != JOptionPane.OK_OPTION) {
        return;
      }
      try {
        double parsedPrice = Double.parseDouble(price.getText().trim());
        try (PreparedStatement stmt = conn.prepareStatement(
            "UPDATE menu_items SET name = ?, base_price = ?, category = ?, description = ? "
                + "WHERE menu_item_id = ?")) {
          stmt.setString(1, name.getText().trim());
          stmt.setDouble(2, parsedPrice);
          stmt.setString(3, category.getText().trim());
          stmt.setString(4, description.getText().trim());
          stmt.setInt(5, id);
          stmt.executeUpdate();
        }
        status.setText("Menu item updated");
        refresh.doClick();
      } catch (NumberFormatException ex) {
        status.setText("Price must be a number");
      } catch (SQLException ex) {
        status.setText("Could not update menu item: " + ex.getMessage());
      }
    });

    refresh.doClick();
    panel.add(controls, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    return panel;
  }

 
  /**
  * Creates a panel containing the input fields for a menu item.
  * The fields are arranged as labeled name, price, category, and description
  * @param name the text field for the menu item's name
  * @param price the text field for the menu item's base price
  * @param category the text field for the menu item's category
  * @param description the text field for the menu item's description
  * @return a panel containing the labeled menu-item input fields
  */
  private static JPanel menuItemFields(JTextField name, JTextField price,
    JTextField category, JTextField description) {
    JPanel fields = new JPanel(new GridLayout(0, 2, 5, 5));
    fields.add(new JLabel("Name"));
    fields.add(name);
    fields.add(new JLabel("Price"));
    fields.add(price);
    fields.add(new JLabel("Category"));
    fields.add(category);
    fields.add(new JLabel("Description"));
    fields.add(description);
    return fields;
  }


  /**
  * Creates a panel containing the input fields for an employee.
  * The fields are arranged as labeled name, position, hourly rate, and hire date
  *
  * @param name the text field for the employee's name
  * @param position the text field for the employee's position
  * @param hourlyRate the text field for the employee's hourly rate
  * @param hireDate the text field for the employee's hire date
  * @return a panel containing the labeled employee input fields
  */
  private static JPanel employeeFields(JTextField name, JTextField position,
   JTextField hourlyRate, JTextField hireDate) {
    JPanel fields = new JPanel(new GridLayout(0, 2, 5, 5));
    fields.add(new JLabel("Name"));
    fields.add(name);
    fields.add(new JLabel("Position"));
    fields.add(position);
    fields.add(new JLabel("Hourly Rate"));
    fields.add(hourlyRate);
    fields.add(new JLabel("Hire Date"));
    fields.add(hireDate);
    return fields;
    }

  /**
  * Creates a panel containing the input fields for an inventory item.
  * The fields are arranged as labeled name, cost, unit, quantity, and restock minimum
  *
  * @param name the text field for the inventory item's name
  * @param cost the text field for the inventory item's cost
  * @param unit the text field for the inventory item's unit of measure
  * @param quantity the text field for the quantity of the inventory item
  * @param minimum the text field for the stock level below which the item needs restocking
  * @return a panel containing the labeled inventory input fields
  */
  private static JPanel inventoryFields(JTextField name, JTextField cost,
    JTextField unit, JTextField quantity, JTextField minimum) {
    JPanel fields = new JPanel(new GridLayout(0, 2, 5, 5));
    fields.add(new JLabel("Name"));
    fields.add(name);
    fields.add(new JLabel("Cost"));
    fields.add(cost);
    fields.add(new JLabel("Unit of Measure"));
    fields.add(unit);
    fields.add(new JLabel("Quantity on hand"));
    fields.add(quantity);
    fields.add(new JLabel("Restock minimum"));
    fields.add(minimum);
    return fields;
    }

  /**
  *  Function that parses a text field containing an inventory number.
  *
  * @param field the text field containing the inventory number to be parsed
  * @return a BigDecimal representing the inventory number
  */
  private static java.math.BigDecimal inventoryNumber(JTextField field) {
    java.math.BigDecimal value = new java.math.BigDecimal(field.getText().trim());
    if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
      throw new NumberFormatException();
    }
    return value;
  }

  /**
  *  Function that creates an empty table with the specified columns.
  *
  * @param columns the column names for the table
  * @return a JTable with the specified columns
  */
  private static JTable emptyTable(String[] columns) {
    return new JTable(new DefaultTableModel(columns, 0) {
      public boolean isCellEditable(int row, int column) { return false; }
    });
  }

  /**
   * Shows the login screen. After a correct password, opens the Manager view
   * for managers and the Cashier view for everyone else.
   *
   * @param frame the main window
   */
  private static void showLogin(JFrame frame) {
    JPanel fields = new JPanel(new GridLayout(0, 1, 8, 8));
    fields.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
    JComboBox<String> people = new JComboBox<>();
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    JPasswordField password = new JPasswordField(20);
    JButton login = new JButton("Log in");
    JLabel status = new JLabel(" ");
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT employee_id, name, position FROM employees ORDER BY name")) {
      while (rs.next()) {
        ids.add(rs.getInt(1));
        people.addItem(rs.getString(2) + " (" + rs.getString(3) + ")");
      }
      if (ids.isEmpty()) status.setText("No employees available");
    } catch (SQLException ex) {
      status.setText("Could not load employees");
    }
    login.setEnabled(!ids.isEmpty());
    fields.add(new JLabel("Boba POS login"));
    fields.add(people);
    fields.add(new JLabel("Password"));
    fields.add(password);
    fields.add(login);
    fields.add(status);
    login.addActionListener(e -> {
      if (people.getSelectedIndex() < 0) return;
      char[] entered = password.getPassword();
      password.setText("");
      // check the current role instead of trusting the dropdown label
      try (PreparedStatement stmt = conn.prepareStatement(
          "SELECT name, position FROM employees WHERE employee_id = ?")) {
        int id = ids.get(people.getSelectedIndex());
        stmt.setInt(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
          if (!rs.next()) { status.setText("Employee no longer available. Reopen the app."); return; }
          String name = rs.getString(1);
          boolean manager = "manager".equalsIgnoreCase(rs.getString(2).trim());
          char[] expected = {(manager ? '2' : '1')};
          if (!java.util.Arrays.equals(entered, expected)) {
            status.setText("Incorrect password");
            password.requestFocusInWindow();
            return;
          }
          JPanel view = new JPanel(new BorderLayout(10, 10));
          JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
          header.add(new JLabel(name + " (" + rs.getString(2) + ")"));
          JButton logout = new JButton("Log out");
          logout.addActionListener(event -> {
            // logging out starts a new cart next time
            if (!manager && JOptionPane.showConfirmDialog(frame,
                "Log out and clear the current cart?", "Log out", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
            showLogin(frame);
          });
          header.add(logout);
          cashierStyle(header);
          view.add(header, BorderLayout.NORTH);
          view.add(manager ? managerView() : cashierView(id, name), BorderLayout.CENTER);
          frame.getRootPane().setDefaultButton(null);
          frame.setContentPane(view);
          frame.setTitle("Boba POS — " + (manager ? "Manager" : "Cashier"));
          frame.setSize(1100, 650);
          frame.setLocationRelativeTo(null);
          frame.revalidate();
          frame.repaint();
        }
      } catch (SQLException ex) { status.setText("Login failed. Check the database connection."); }
      finally { java.util.Arrays.fill(entered, '\0'); }
    });
    cashierStyle(fields);
    frame.setContentPane(fields);
    frame.setTitle("Boba POS — Login");
    frame.getRootPane().setDefaultButton(login);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.revalidate();
    frame.repaint();
  }

  /**
   * Connects to the database from DB_URL, DB_USER and DB_PASSWORD and opens
   * the login window.
   *
   * @param args not used
   */
  public static void main(String[] args) {
    try {
      conn = DriverManager.getConnection(System.getenv("DB_URL"),
          System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
    } catch (SQLException e) {
      JOptionPane.showMessageDialog(null, "Could not connect to database: " + e.getMessage()
          + "\nSet DB_URL, DB_USER, and DB_PASSWORD first.");
      return;
    }

    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("Boba POS");
      showLogin(frame);
      frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      frame.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosed(java.awt.event.WindowEvent e) {
          try { conn.close(); } catch (SQLException ex) { }
        }
      });
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });
  }
}
