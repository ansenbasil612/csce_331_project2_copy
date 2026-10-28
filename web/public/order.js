// Ordering screen shared by the cashier and kiosk pages.
// <body data-mode="cashier"> or <body data-mode="kiosk"> picks the differences.
const MODE = document.body.dataset.mode;
const PAYMENTS = MODE === 'kiosk' ? ['Card', 'Gift Card'] : ['Cash', 'Card', 'Gift Card'];
const SUGAR = [100, 75, 50, 25, 0];
const ICE = ['normal', 'less', 'no ice'];

const state = { items: [], toppings: [], sizes: [], category: null, cart: [] };
const $ = (id) => document.getElementById(id);

function setStatus(text, kind = '') {
  $('status').textContent = text;
  $('status').className = `status ${kind}`;
}

function surcharge(size) {
  return state.sizes.find((s) => s.size === size)?.surcharge ?? 0;
}

/** Price of one line, same rule as the server: (base + size + toppings) x quantity. */
function linePrice(line) {
  const item = state.items.find((i) => i.id === line.menuItemId);
  const tops = line.toppings.reduce((sum, id) => sum + state.toppings.find((t) => t.id === id).price, 0);
  return Math.round((item.price + surcharge(line.size) + tops) * 100) * line.quantity / 100;
}

// ---------- menu ----------

function renderTabs() {
  const categories = [...new Set(state.items.map((i) => i.category || 'Other'))];
  state.category ??= categories[0];
  $('tabs').replaceChildren(...categories.map((c) => el('button', {
    className: `btn ${c === state.category ? 'selected' : ''}`,
    textContent: c,
    onclick: () => { state.category = c; renderTabs(); renderDrinks(); },
  })));
}

function renderDrinks() {
  const drinks = state.items.filter((i) => (i.category || 'Other') === state.category);
  $('drinks').replaceChildren(...drinks.map((item) => el('button', {
    className: 'drink',
    onclick: () => openCustomize(item),
  }, el('span', { className: 'name', textContent: item.name }),
     el('span', { className: 'price', textContent: money(item.price) }))));
}

// ---------- customize dialog ----------

function choiceGroup(title, name, options, checked, type = 'radio') {
  return el('fieldset', {},
    el('legend', { textContent: title }),
    el('div', { className: 'choices' }, ...options.map((opt) => el('label', {},
      el('input', { type, name, value: opt.value, checked: checked(opt.value) }),
      opt.label))));
}

function openCustomize(item) {
  const dialog = $('customize');
  const priceLabel = el('span', { className: 'line-price' });
  const qty = el('input', { type: 'number', name: 'quantity', min: 1, max: 50, value: 1 });

  const form = el('form', { method: 'dialog' },
    el('div', {}, el('h3', { textContent: item.name }),
      el('div', { className: 'desc', textContent: item.description || '' })),
    choiceGroup('Size', 'size',
      state.sizes.map((s) => ({ value: s.size, label: s.surcharge ? `${cap(s.size)} +${money(s.surcharge)}` : cap(s.size) })),
      (v) => v === 'medium'),
    choiceGroup('Sugar', 'sugar', SUGAR.map((v) => ({ value: v, label: `${v}%` })), (v) => v === 100),
    choiceGroup('Ice', 'ice', ICE.map((v) => ({ value: v, label: cap(v) })), (v) => v === 'normal'),
    choiceGroup('Toppings', 'topping',
      state.toppings.map((t) => ({ value: t.id, label: `${t.name} +${money(t.price)}` })),
      () => false, 'checkbox'),
    el('div', { className: 'qty' }, el('strong', { textContent: 'Quantity' }), qty),
    el('div', { className: 'dialog-actions' },
      priceLabel,
      el('div', {},
        el('button', { type: 'button', className: 'btn ghost', textContent: 'Cancel', onclick: () => dialog.close() }),
        ' ',
        el('button', { type: 'submit', className: 'btn primary', textContent: 'Add to order' }))));

  const readLine = () => {
    const data = new FormData(form);
    return {
      menuItemId: item.id,
      size: data.get('size'),
      sugar: Number(data.get('sugar')),
      ice: data.get('ice'),
      toppings: data.getAll('topping').map(Number),
      quantity: Math.min(50, Math.max(1, Number(data.get('quantity')) || 1)),
    };
  };
  const updatePrice = () => { priceLabel.textContent = money(linePrice(readLine())); };
  form.addEventListener('input', updatePrice);
  form.addEventListener('submit', () => {
    state.cart.push(readLine());
    renderCart();
    setStatus('');
  });

  dialog.replaceChildren(form);
  updatePrice();
  dialog.showModal();
}

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

// ---------- cart + checkout ----------

function renderCart() {
  const cart = $('cart');
  if (state.cart.length === 0) {
    cart.replaceChildren(el('li', { className: 'cart-empty', textContent: 'No drinks yet' }));
  } else {
    cart.replaceChildren(...state.cart.map((line, index) => {
      const item = state.items.find((i) => i.id === line.menuItemId);
      const tops = line.toppings.map((id) => state.toppings.find((t) => t.id === id).name);
      const details = [cap(line.size), `${line.sugar}% sugar`, `${line.ice} ice`, ...tops].join(' · ');
      return el('li', {},
        el('strong', { textContent: `${line.quantity} × ${item.name}` }),
        el('span', { textContent: money(linePrice(line)) }),
        el('span', { className: 'details', textContent: details }),
        el('button', {
          className: 'btn ghost remove', textContent: 'Remove',
          onclick: () => { state.cart.splice(index, 1); renderCart(); },
        }));
    }));
  }
  const total = state.cart.reduce((sum, line) => sum + linePrice(line), 0);
  $('total').textContent = money(total);
  for (const button of $('pay').children) button.disabled = state.cart.length === 0;
}

async function checkout(paymentMethod) {
  const body = { paymentMethod, items: state.cart };
  let path = '/api/kiosk/orders';
  if (MODE === 'cashier') {
    const employeeId = Number($('employee').value);
    if (!employeeId) return setStatus('Choose an employee first.', 'error');
    body.employeeId = employeeId;
    path = '/api/orders';
  }
  for (const button of $('pay').children) button.disabled = true;
  setStatus('Saving order…');
  try {
    const order = await api(path, { method: 'POST', body });
    state.cart = [];
    renderCart();
    setStatus(`Order #${order.orderId} complete (${money(order.total)}, ${paymentMethod})`, 'ok');
  } catch (err) {
    // the cart is kept so the order can be fixed and retried
    renderCart();
    setStatus(err.message, 'error');
  }
}

// ---------- start ----------

async function init() {
  $('pay').replaceChildren(...PAYMENTS.map((p) => el('button', {
    className: 'btn primary', textContent: p, onclick: () => checkout(p),
  })));
  renderCart();
  try {
    Object.assign(state, await api('/api/menu'));
    renderTabs();
    renderDrinks();
    if (MODE === 'cashier') {
      const employees = await api('/api/employees');
      $('employee').replaceChildren(el('option', { value: '', textContent: 'Choose…' }),
        ...employees.map((e) => el('option', { value: e.id, textContent: `${e.name} (${e.position})` })));
    }
  } catch (err) {
    setStatus(`Could not load the menu: ${err.message}`, 'error');
  }
}

init();
