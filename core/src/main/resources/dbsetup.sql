CREATE TABLE IF NOT EXISTS shop_transaction
(
    id  INTEGER NOT NULL AUTO_INCREMENT,
    t_type CHAR(6) NOT NULL,
    price   DOUBLE NOT NULL,
    amount   SMALLINT NOT NULL,
    item   TEXT NOT NULL,
    barter_item   TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS shop_action
(
    ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    player_uuid     CHAR(36)  NOT NULL,
    owner_uuid     CHAR(36)  NOT NULL,
    shop_uuid     CHAR(36)  NOT NULL,
    shop_world   CHAR(128)  NOT NULL,
    shop_x   INTEGER  NOT NULL,
    shop_y   INTEGER  NOT NULL,
    shop_z   INTEGER  NOT NULL,
    player_action     CHAR(8)  NOT NULL,
    transaction_id INTEGER,
    FOREIGN KEY (transaction_id) REFERENCES shop_transaction(id)
);

-- Successful offline summary coverage, retained across plugin restarts.
CREATE TABLE IF NOT EXISTS shop_offline_summary
(
    owner_uuid CHAR(36) NOT NULL,
    summarized_through TIMESTAMP NOT NULL,
    PRIMARY KEY (owner_uuid)
);

-- shop_action had no indexes at all, so every query against it full-scanned a table that only
-- ever grows. The column order below matches the queries in LogHandler:
--   * owner history  — WHERE owner_uuid=? AND ts > ? ORDER BY ts DESC
--   * filtered log   — WHERE owner_uuid=? AND player_action=? AND ts BETWEEN ? AND ?
--   * customer view  — adds AND player_uuid=?
-- Leading with owner_uuid serves the first two directly and lets the composite satisfy the
-- ORDER BY without a separate sort. transaction_id is indexed for the join to shop_transaction.
--
-- IF NOT EXISTS is verified against H2 2.1.214 (the plugin's default embedded database) as well
-- as SQLite and MariaDB.
--
-- initDb() splits this file on the statement separator, so prose in these comments must never
-- contain one. DbSetupIndexTest executes this file to catch that.
CREATE INDEX IF NOT EXISTS idx_shop_action_owner_ts ON shop_action(owner_uuid, ts);
CREATE INDEX IF NOT EXISTS idx_shop_action_transaction ON shop_action(transaction_id);
CREATE INDEX IF NOT EXISTS idx_shop_action_player_ts ON shop_action(player_uuid, ts);
