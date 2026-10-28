require('dotenv').config();
const path = require('path');
const express = require('express');
const { pool } = require('./db');
const api = require('./routes/api');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));
app.use('/api', api);

// Express 5 forwards errors thrown in async routes here.
app.use((err, req, res, next) => {
  const status = err.status || 500;
  if (status >= 500) console.error(err);
  res.status(status).json({ error: err.message || 'Server error' });
});

const port = process.env.PORT || 3000;
const server = app.listen(port, async () => {
  console.log(`Boba POS running at http://localhost:${port}`);
  try {
    await pool.query('SELECT 1');
    console.log(`Connected to ${process.env.PGDATABASE} on ${process.env.PGHOST}`);
  } catch (err) {
    console.error(`Could not reach the database: ${err.message}`);
    console.error('Check web/.env, and use TAMU Wi-Fi or the VPN for the TAMU database.');
  }
});

process.on('SIGINT', async () => {
  server.close();
  await pool.end();
  process.exit(0);
});
