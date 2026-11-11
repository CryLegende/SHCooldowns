package org.Cry_Legende.sHCooldowns.network;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldowns en MariaDB, partagés par tous les serveurs. Chargée seulement avec SHNetwork
 * (HikariCP et le pilote MariaDB viennent de {@code libraries:} dans plugin.yml).
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal.</p>
 */
public final class CooldownDatabase {

    private static final String TABLE = "shcooldowns";

    private final HikariDataSource pool;

    public CooldownDatabase(String url, String user, String password, int timeoutMs) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SHCooldowns");
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(timeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(timeoutMs));
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(timeoutMs);
        // le serveur démarre même si la base est absente : le plugin reste alors en mode local
        hikari.setInitializationFailTimeout(-1);
        hikari.setKeepaliveTime(60_000);
        this.pool = new HikariDataSource(hikari);
    }

    /** Crée la table si elle manque. Une table déjà là ne demande aucun droit CREATE à l'utilisateur. */
    public void createTable() throws SQLException {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + TABLE + " LIMIT 1")) {
                return;
            } catch (SQLException missing) {
                // la table n'existe pas encore : création ci-dessous
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "uuid CHAR(36) NOT NULL, "
                + "id VARCHAR(100) NOT NULL, "
                + "expiry BIGINT NOT NULL, "
                + "PRIMARY KEY (uuid, id), "
                + "KEY idx_expiry (expiry)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
    }

    /** Écrit l'échéance d'un cooldown. Une échéance plus ancienne n'écrase jamais une plus récente. */
    public void upsert(UUID uuid, String id, long expiry) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE + " (uuid, id, expiry) VALUES (?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE expiry = GREATEST(expiry, VALUES(expiry))")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, id);
            ps.setLong(3, expiry);
            ps.executeUpdate();
        }
    }

    /** Cooldowns du joueur qui ne sont pas encore échus : id -> échéance. */
    public Map<String, Long> load(UUID uuid, long now) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, expiry FROM " + TABLE + " WHERE uuid = ? AND expiry > ?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return result;
    }

    /** Supprime les cooldowns d'un joueur : celui d'un groupe, ou tous si id est nul. */
    public int delete(UUID uuid, String id) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE uuid = ?" + (id == null ? "" : " AND id = ?");
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (id != null) {
                ps.setString(2, id);
            }
            return ps.executeUpdate();
        }
    }

    /** Supprime les cooldowns échus. */
    public int purge(long now) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE expiry <= ?")) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        }
    }

    public void close() {
        pool.close();
    }
}
