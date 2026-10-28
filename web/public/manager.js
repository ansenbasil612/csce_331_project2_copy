// Manager view: read-only tables for now. Editing comes next.
const VIEWS = [
  { key: 'today', label: "Today's sales" },
  { key: 'orders', label: 'Recent orders', note: 'Last 50 orders. Click one to see its drinks.' },
  { key: 'menu', label: 'Menu items' },
  { key: 'ingredients', label: 'Ingredients', note: 'Rows in red are below their restock minimum.' },
  { key: 'toppings', label: 'Toppings', note: 'Rows in red are below their restock minimum.' },
  { key: 'supplies', label: 'Supplies', note: 'Rows in red are below their restock minimum.' },
  { key: 'employees', label: 'Employees' },
];
const MONEY_COLUMNS = new Set(['Price', 'Cost', 'Total', 'Sales', 'Hourly rate', 'item_total']);
const content = document.getElementById('content');

function dataTable(columns, rows, { onRowClick } = {}) {
  const head = el('tr', {}, ...columns.map((c) => el('th', { textContent: c })));
  const body = rows.map((row) => {
    const tr = el('tr', {}, ...columns.map((c) => {
      const value = row[c];
      const isNum = typeof value === 'number';
      return el('td', {
        className: isNum ? 'num' : '',
        textContent: value == null ? '' : MONEY_COLUMNS.has(c) ? money(value) : value,
      });
    }));
    if ('Minimum' in row && row['On hand'] < row.Minimum) tr.classList.add('low');
    if (onRowClick) {
      tr.classList.add('clickable');
      tr.onclick = () => onRowClick(row);
    }
    return tr;
  });
  return el('table', { className: 'data' }, el('thead', {}, head), el('tbody', {}, ...body));
}

async function showToday() {
  const { summary, hourly, top } = await api('/api/reports/today');
  const stat = (label, value) => el('div', { className: 'stat' },
    el('div', { className: 'label', textContent: label }),
    el('div', { className: 'value', textContent: value }));
  content.replaceChildren(
    el('h2', { textContent: "Today's sales" }),
    el('div', { className: 'stats' },
      stat('Orders', summary.orders),
      stat('Revenue', money(summary.revenue)),
      stat('Average order', money(summary.average))),
    el('div', { className: 'report-grid' },
      el('section', {}, el('h3', { textContent: 'By hour' }),
        hourly.length ? dataTable(['Hour', 'Orders', 'Sales'], hourly) : el('p', { className: 'note', textContent: 'No orders yet today.' })),
      el('section', {}, el('h3', { textContent: 'Top drinks' }),
        top.length ? dataTable(['Drink', 'Sold'], top) : el('p', { className: 'note', textContent: 'No drinks sold yet today.' }))));
}

async function showOrderItems(orderId, holder) {
  const items = await api(`/api/orders/${orderId}/items`);
  const rows = items.map((i) => ({
    Drink: i.name, Qty: i.quantity, Size: i.size, Sugar: `${i.sugar_percent}%`,
    Ice: i.ice_level, Toppings: i.toppings, Total: i.item_total,
  }));
  holder.replaceChildren(el('h3', { textContent: `Order #${orderId}` }),
    dataTable(['Drink', 'Qty', 'Size', 'Sugar', 'Ice', 'Toppings', 'Total'], rows));
}

async function show(view) {
  for (const b of document.querySelectorAll('.side-nav button')) {
    b.classList.toggle('active', b.dataset.key === view.key);
  }
  content.replaceChildren(el('p', { className: 'note', textContent: 'Loading…' }));
  try {
    if (view.key === 'today') return await showToday();
    const { columns, rows } = await api(`/api/manager/${view.key}`);
    const detail = el('div', { className: 'order-detail' });
    const onRowClick = view.key === 'orders' ? (row) => showOrderItems(row.Order, detail) : undefined;
    content.replaceChildren(
      el('h2', { textContent: view.label }),
      view.note ? el('p', { className: 'note', textContent: view.note }) : null,
      dataTable(columns, rows, { onRowClick }),
      detail);
  } catch (err) {
    content.replaceChildren(el('p', { className: 'status error', textContent: err.message }));
  }
}

document.getElementById('nav').replaceChildren(...VIEWS.map((v) => {
  const button = el('button', { textContent: v.label, onclick: () => show(v) });
  button.dataset.key = v.key;
  return button;
}));
show(VIEWS[0]);
