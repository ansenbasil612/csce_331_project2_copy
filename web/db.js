const { Pool, types } = require('pg');

// Return DECIMAL/NUMERIC columns as JS numbers instead of strings.
// Fine for prices and stock counts at this scale.
types.setTypeParser(types.builtins.NUMERIC, (value) => parseFloat(value));

// pg reads PGHOST, PGPORT, PGDATABASE, PGUSER and PGPASSWORD from the environment (.env).
const pool = new Pool({
  max: 5,
  ssl: process.env.PGSSL === 'true' ? { rejectUnauthorized: false } : false,
});

pool.on('error', (err) => console.error('Unexpected database error:', err.message));

module.exports = { pool };
