package dev.godspear.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.godspear.GodSpearPlugin;
import dev.godspear.model.SpearRecord;
import dev.godspear.model.SpearStage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

public final class Database implements AutoCloseable {
    private final GodSpearPlugin plugin;
    private final ExecutorService executor;
    private HikariDataSource dataSource;
    public Database(GodSpearPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "GodSpear-Database"); t.setDaemon(true); return t; });
    }
    public void open() throws SQLException {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "storage.yml"));
        String type = y.getString("type", "sqlite").toLowerCase();
        HikariConfig c = new HikariConfig();
        if (type.equals("sqlite")) {
            c.setDriverClassName("org.sqlite.JDBC");
            c.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), y.getString("sqlite-file", "godspear.db")).getAbsolutePath());
            c.setMaximumPoolSize(1);
        } else {
            String protocol=type.equals("mariadb")?"mariadb":"mysql";
            c.setJdbcUrl("jdbc:"+protocol+"://" + y.getString("mysql.host") + ":" + y.getInt("mysql.port", 3306) + "/" + y.getString("mysql.database") + "?useSSL=" + y.getBoolean("mysql.use-ssl"));
            c.setUsername(y.getString("mysql.username")); c.setPassword(y.getString("mysql.password")); c.setMaximumPoolSize(y.getInt("mysql.pool-size", 10));
        }
        c.setPoolName("GodSpear-Pool"); c.setConnectionTimeout(10000); dataSource = new HikariDataSource(c);
        try (Connection cn = dataSource.getConnection(); Statement s = cn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS spears (spear_uuid VARCHAR(36) PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL UNIQUE, owner_name VARCHAR(32) NOT NULL, stage VARCHAR(16) NOT NULL, kills INTEGER NOT NULL, created_at BIGINT NOT NULL, plugin_version VARCHAR(32) NOT NULL, destroyed BOOLEAN NOT NULL DEFAULT FALSE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS kills (killer_uuid VARCHAR(36) NOT NULL, victim_uuid VARCHAR(36) NOT NULL, killed_at BIGINT NOT NULL, killer_ip VARCHAR(64), victim_ip VARCHAR(64))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS destroyed_owners (owner_uuid VARCHAR(36) PRIMARY KEY, spear_uuid VARCHAR(36) NOT NULL, destroyed_at BIGINT NOT NULL)");
        }
    }
    public CompletableFuture<Optional<SpearRecord>> findOwner(UUID owner) { return supply(() -> {
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement("SELECT * FROM spears WHERE owner_uuid=?")) { p.setString(1,owner.toString()); try(ResultSet r=p.executeQuery()){ return r.next()?Optional.of(read(r)):Optional.empty(); } }
    }); }
    public CompletableFuture<Void> save(SpearRecord r) { return run(() -> {
        String sql="INSERT INTO spears(spear_uuid,owner_uuid,owner_name,stage,kills,created_at,plugin_version,destroyed) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(owner_uuid) DO UPDATE SET spear_uuid=excluded.spear_uuid,owner_name=excluded.owner_name,stage=excluded.stage,kills=excluded.kills,plugin_version=excluded.plugin_version,destroyed=excluded.destroyed";
        if (!dataSource.getJdbcUrl().startsWith("jdbc:sqlite")) sql="INSERT INTO spears(spear_uuid,owner_uuid,owner_name,stage,kills,created_at,plugin_version,destroyed) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE spear_uuid=VALUES(spear_uuid),owner_name=VALUES(owner_name),stage=VALUES(stage),kills=VALUES(kills),plugin_version=VALUES(plugin_version),destroyed=VALUES(destroyed)";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,r.spearId().toString());p.setString(2,r.ownerId().toString());p.setString(3,r.ownerName());p.setString(4,r.stage().name());p.setInt(5,r.kills());p.setLong(6,r.createdAt().toEpochMilli());p.setString(7,r.pluginVersion());p.setBoolean(8,r.destroyed());p.executeUpdate();try(PreparedStatement clear=c.prepareStatement("DELETE FROM destroyed_owners WHERE owner_uuid=?")){clear.setString(1,r.ownerId().toString());clear.executeUpdate();}}
    }); }
    public CompletableFuture<Void> destroy(SpearRecord r){return run(()->{try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try(PreparedStatement d=c.prepareStatement("DELETE FROM spears WHERE owner_uuid=?");PreparedStatement t=c.prepareStatement(dataSource.getJdbcUrl().startsWith("jdbc:sqlite")?"INSERT OR REPLACE INTO destroyed_owners VALUES(?,?,?)":"INSERT INTO destroyed_owners VALUES(?,?,?) ON DUPLICATE KEY UPDATE spear_uuid=VALUES(spear_uuid),destroyed_at=VALUES(destroyed_at)")){d.setString(1,r.ownerId().toString());d.executeUpdate();t.setString(1,r.ownerId().toString());t.setString(2,r.spearId().toString());t.setLong(3,System.currentTimeMillis());t.executeUpdate();c.commit();}catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}});}
    public CompletableFuture<Boolean> isDestroyedOwner(UUID owner){return supply(()->{try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT 1 FROM destroyed_owners WHERE owner_uuid=?")){p.setString(1,owner.toString());try(ResultSet r=p.executeQuery()){return r.next();}}});}
    public CompletableFuture<Long> victimCount(UUID killer, UUID victim, long since) { return supply(() -> { try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM kills WHERE killer_uuid=? AND victim_uuid=? AND killed_at>=?")){p.setString(1,killer.toString());p.setString(2,victim.toString());p.setLong(3,since);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}} }); }
    public CompletableFuture<Void> recordKill(UUID k, UUID v, String ki, String vi) { return run(() -> {try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO kills VALUES(?,?,?,?,?)")){p.setString(1,k.toString());p.setString(2,v.toString());p.setLong(3,System.currentTimeMillis());p.setString(4,ki);p.setString(5,vi);p.executeUpdate();}}); }
    private SpearRecord read(ResultSet r)throws SQLException{return new SpearRecord(UUID.fromString(r.getString("spear_uuid")),UUID.fromString(r.getString("owner_uuid")),r.getString("owner_name"),SpearStage.valueOf(r.getString("stage")),r.getInt("kills"),Instant.ofEpochMilli(r.getLong("created_at")),r.getString("plugin_version"),r.getBoolean("destroyed"));}
    private CompletableFuture<Void> run(SqlRun r){return CompletableFuture.runAsync(()->{try{r.run();}catch(SQLException e){throw new CompletionException(e);}},executor);}
    private <T> CompletableFuture<T> supply(SqlSupplier<T> s){return CompletableFuture.supplyAsync(()->{try{return s.get();}catch(SQLException e){throw new CompletionException(e);}},executor);}
    @FunctionalInterface interface SqlRun{void run()throws SQLException;} @FunctionalInterface interface SqlSupplier<T>{T get()throws SQLException;}
    public void close(){executor.shutdown();if(dataSource!=null)dataSource.close();}
}
