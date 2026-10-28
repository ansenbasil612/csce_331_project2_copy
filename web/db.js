const { Pool, types } = require('pg');

// Return DECIMAL/NUMERIC columns as JS numbers instead of strings.
// Fine for prices and stock counts at this scale.
types.setTypeParser(types.builtins.NUMERIC, (value) => parseFloat(value));

// pg reads PGHOST, PGPORT, PGDATABASE, PGUSER and PGPASSWORD from the environment (.env).
const pool = new Pool({
  max: 5,
  // The TAMU server needs an encrypted connection. Set PGSSL=false for a local database.
  ssl: process.env.PGSSL === 'false' ? false : { rejectUnauthorized: false },
});

pool.on('error', (err) => console.error('Unexpected database error:', err.message));

module.exports = { pool };
