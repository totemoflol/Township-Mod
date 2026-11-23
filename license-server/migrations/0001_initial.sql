CREATE TABLE IF NOT EXISTS licenses (
    id TEXT PRIMARY KEY,
    key_hash TEXT NOT NULL UNIQUE,
    key_hint TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
    expires_at TEXT,
    device_id TEXT,
    created_at TEXT NOT NULL,
    activated_at TEXT,
    last_seen_at TEXT,
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);
CREATE INDEX IF NOT EXISTS idx_licenses_created_at ON licenses(created_at);
CREATE INDEX IF NOT EXISTS idx_licenses_device_id ON licenses(device_id);

CREATE TABLE IF NOT EXISTS activation_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ip_hash TEXT NOT NULL,
    attempted_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_activation_attempts_ip_time
ON activation_attempts(ip_hash, attempted_at);
