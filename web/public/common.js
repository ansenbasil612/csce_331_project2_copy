// Small helpers shared by every page.

/** fetch() that returns JSON and throws the server's error message on failure. */
async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

const money = (n) => `$${Number(n).toFixed(2)}`;

/** Creates an element: el('td', { className: 'num' }, 'text', childNode) */
function el(tag, props = {}, ...children) {
  const node = Object.assign(document.createElement(tag), props);
  for (const child of children) {
    if (child != null) node.append(child);
  }
  return node;
}
