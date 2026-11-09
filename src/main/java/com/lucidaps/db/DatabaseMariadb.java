/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  javax.annotation.Nullable
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package com.lucidaps.db;

import com.google.gson.JsonObject;
import com.lucidaps.ProtectCore;
import com.lucidaps.commands.subcommands.rollback.RollbackEngine;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.EditorItemSummary;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.lucidaps.utilities.Debug;
import com.lucidaps.utilities.ItemMetadataUtil;
import java.lang.invoke.CallSite;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.mariadb.jdbc.MariaDbDataSource;

public class DatabaseMariadb {
    private HikariDataSource dataSource;
    private final ProtectCore plugin;
    private final int maxBatchSize;
    private final int maxQueuedActions;
    private final long actionRetryBaseDelayTicks;
    private final long actionRetryMaxDelayTicks;
    private static final int UPDATE_BATCH_CAP = 500;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_TICKS = 20L;
    private static final long RETRY_JITTER_TICKS = 10L;
    private final AtomicBoolean hardMaintenanceDropAll = new AtomicBoolean(false);
    private volatile HealthState healthState = HealthState.STARTING;
    private volatile String lastConnectionError = "";
    private volatile long lastConnectedAtMillis;
    private volatile long lastConnectionWarningMillis;
    private final ConcurrentLinkedDeque<LogData> actionLogDataQueue = new ConcurrentLinkedDeque();
    private final AtomicBoolean isProcessingActionLogBatch = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Runnable> genericTaskQueue = new ConcurrentLinkedQueue();
    private final AtomicBoolean isProcessingGenericTaskQueue = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicLong actionQueueDepth = new AtomicLong();
    private final AtomicInteger consecutiveActionBatchFailures = new AtomicInteger();
    private final AtomicLong nextActionBatchAttemptMillis = new AtomicLong();
    private static final String SYSTEM_UUID_STR = "00000000-0000-0000-0000-000000000000";
    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);
    private final AtomicLong droppedDuringMaintenance = new AtomicLong();
    private volatile long lastDropWarnMs = 0L;
    private volatile boolean hasItemMaterial = false;
    private volatile boolean hasItemNameNorm = false;
    private volatile boolean hasItemLoreNorm = false;
    private volatile boolean hasItemSearchText = false;
    private volatile boolean hasItemEnchantsCsv = false;
    private volatile boolean hasFtItemSearch = false;
    private final AtomicBoolean heavyMigrationsScheduled = new AtomicBoolean(false);
    private final AtomicBoolean heavyMigrationsRunning = new AtomicBoolean(false);
    private static final int MAX_GENERIC_QUEUE = 50000;
    private final AtomicLong genericDroppedDuringMaintenance = new AtomicLong();
    private volatile long lastGenericDropWarnMs = 0L;
    private final LongAdder genericQueueDepth = new LongAdder();
    private static final long TICKS_PER_SECOND = 20L;
    private TaskHandle batchTask;
    private TaskHandle genericTask;
    private TaskHandle reconnectTask;
    private volatile boolean hasCx = false;
    private volatile boolean hasCz = false;
    private volatile boolean hasFtDetailsMetadata = false;
    private volatile boolean hasMsgCmdSearchText = false;
    private volatile boolean hasFtMsgCmdSearch = false;
    private volatile boolean hasCommandRootVc = false;
    private static final int DEFAULT_WINDOW_PAGES = 10;
    private static final Pattern MC_HEX = Pattern.compile("(?i)(\u00a7x(\u00a7[0-9a-f]){6}|&x(&[0-9a-f]){6})");
    private static final Pattern MC_LEGACY = Pattern.compile("(?i)[\u00a7&][0-9a-fk-or]");

    private boolean isStoppingNow() {
        return this.shuttingDown.get() || DatabaseMariadb.isServerStopping();
    }

    private static Integer tryGetInt(ResultSet resultSet, String string) {
        try {
            Object object = resultSet.getObject(string);
            return object == null ? null : Integer.valueOf(((Number)object).intValue());
        }
        catch (SQLException sQLException) {
            return null;
        }
    }

    private static byte[] tryGetBytes(ResultSet resultSet, String string) {
        try {
            return resultSet.getBytes(string);
        }
        catch (SQLException sQLException) {
            return null;
        }
    }

    public boolean submitGenericTask(Runnable runnable) {
        if (runnable == null) {
            return false;
        }
        if (this.isStoppingNow()) {
            return false;
        }
        if (this.dataSource == null || this.dataSource.isClosed()) {
            return false;
        }
        if (this.maintenanceMode.get()) {
            if (this.hardMaintenanceDropAll.get()) {
                this.genericDroppedDuringMaintenance.incrementAndGet();
                return false;
            }
            long l = this.genericQueueDepth.sum();
            if (l >= 50000L) {
                long l2 = this.genericDroppedDuringMaintenance.incrementAndGet();
                long l3 = System.currentTimeMillis();
                if (l3 - this.lastGenericDropWarnMs > 5000L) {
                    this.lastGenericDropWarnMs = l3;
                    this.plugin.getLogger().warning("[ProtectCore] DB maintenance active; dropping generic tasks to avoid OOM. genericQueue~=" + l + " dropped=" + l2);
                }
                return false;
            }
        }
        Runnable runnable2 = () -> {
            try {
                runnable.run();
            }
            catch (Throwable throwable) {
                this.plugin.getLogger().log(Level.SEVERE, "Generic task crashed", throwable);
            }
        };
        this.genericTaskQueue.offer(runnable2);
        this.genericQueueDepth.increment();
        return true;
    }

    private boolean enqueueAction(LogData logData) {
        long l;
        long l2;
        if (logData == null) {
            return false;
        }
        if (this.isStoppingNow()) {
            return false;
        }
        if (this.dataSource != null && this.dataSource.isClosed()) {
            return false;
        }
        if (this.maintenanceMode.get() && this.hardMaintenanceDropAll.get()) {
            this.droppedDuringMaintenance.incrementAndGet();
            return false;
        }
        do {
            if ((l2 = this.actionQueueDepth.get()) < (long)this.maxQueuedActions) continue;
            long l3 = this.droppedDuringMaintenance.incrementAndGet();
            long l4 = System.currentTimeMillis();
            if (l4 - this.lastDropWarnMs > 5000L) {
                this.lastDropWarnMs = l4;
                this.plugin.getLogger().warning("[ProtectCore] Action-log queue is full; dropping new logs to avoid OOM. queue=" + l2 + "/" + this.maxQueuedActions + " dropped=" + l3);
            }
            return false;
        } while (!this.actionQueueDepth.compareAndSet(l2, l = l2 + 1L));
        if (!this.actionLogDataQueue.offer(logData)) {
            this.actionQueueDepth.decrementAndGet();
            return false;
        }
        return true;
    }

    private void requeueBatchIfAllowed(List<LogData> list, String string, SQLException sQLException) {
        if (list == null || list.isEmpty()) {
            return;
        }
        this.markDegraded(sQLException);
        if (this.isStoppingNow()) {
            this.plugin.getLogger().warning("[ProtectCore] " + string + " failed during shutdown; dropping " + list.size() + " queued log items: " + sQLException.getMessage());
            return;
        }
        if (this.dataSource != null && this.dataSource.isClosed()) {
            this.plugin.getLogger().warning("[ProtectCore] " + string + " failed after datasource closed; dropping " + list.size() + " queued log items: " + sQLException.getMessage());
            return;
        }
        int n = 0;
        for (int i = list.size() - 1; i >= 0; --i) {
            this.actionLogDataQueue.offerFirst(list.get(i));
            this.actionQueueDepth.incrementAndGet();
            ++n;
        }
        Debug.warn(this.plugin, "db.batch", string + " transient error; re-queued " + n + "/" + list.size() + " :: " + sQLException.getMessage(), new Object[0]);
    }

    public void quiesceForSchemaReset(long l) {
        this.maintenanceMode.set(true);
        this.hardMaintenanceDropAll.set(true);
        this.awaitWorkersIdle(l);
        this.actionLogDataQueue.clear();
        this.genericTaskQueue.clear();
        this.actionQueueDepth.set(0L);
        this.genericQueueDepth.sumThenReset();
    }

    public void endHardResetDropMode() {
        this.hardMaintenanceDropAll.set(false);
    }

    @Nullable
    private LogData dequeueAction() {
        LogData logData = this.actionLogDataQueue.poll();
        if (logData != null) {
            this.actionQueueDepth.decrementAndGet();
        }
        return logData;
    }

    private void markDegraded(Throwable throwable) {
        if (this.maintenanceMode.get() || this.shuttingDown.get()) {
            return;
        }
        this.healthState = HealthState.DEGRADED;
        String string = throwable.getMessage();
        this.lastConnectionError = string == null || string.isBlank() ? throwable.getClass().getSimpleName() : string;
    }

    private long actionQueueSizeFast() {
        return this.actionQueueDepth.get();
    }

    public DatabaseMariadb(ProtectCore protectCore) {
        this.plugin = protectCore;
        this.maxBatchSize = DatabaseMariadb.clamp(protectCore.getConfig().getInt("database.batchSize", 5000), 1, 10000);
        this.maxQueuedActions = DatabaseMariadb.clamp(protectCore.getConfig().getInt("database.maxQueuedActions", 200000), this.maxBatchSize, 1000000);
        this.actionRetryBaseDelayTicks = DatabaseMariadb.clamp(protectCore.getConfig().getLong("database.retryBaseDelayTicks", 20L), 1L, 1200L);
        this.actionRetryMaxDelayTicks = DatabaseMariadb.clamp(protectCore.getConfig().getLong("database.retryMaxDelayTicks", 600L), this.actionRetryBaseDelayTicks, 12000L);
        this.scheduleTasks();
    }

    private static int clamp(int n, int n2, int n3) {
        return Math.max(n2, Math.min(n3, n));
    }

    private static long clamp(long l, long l2, long l3) {
        return Math.max(l2, Math.min(l3, l));
    }

    @Nullable
    static String normalizeMetadataForStorage(@Nullable String string) {
        if (string == null) {
            return null;
        }
        String string2 = string.strip();
        if (string2.isEmpty() || "{}".equals(string2)) {
            return null;
        }
        return string;
    }

    static void bindNullableMetadata(PreparedStatement preparedStatement, int n, @Nullable String string) {
        if (string == null) {
            preparedStatement.setNull(n, -1);
        } else {
            preparedStatement.setString(n, string);
        }
    }

    private static boolean hasPaperAsync() {
        try {
            Bukkit.class.getMethod("getAsyncScheduler", new Class[0]);
            return true;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    private static boolean isServerStopping() {
        try {
            return (Boolean)Bukkit.class.getMethod("isStopping", new Class[0]).invoke(null, new Object[0]);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public void setMaintenanceMode(boolean bl) {
        this.maintenanceMode.set(bl);
        this.healthState = bl ? HealthState.MAINTENANCE : (this.lastConnectedAtMillis > 0L ? HealthState.CONNECTED : HealthState.DEGRADED);
    }

    public boolean isMaintenanceMode() {
        return this.maintenanceMode.get();
    }

    private TaskHandle runFixedRateAsync(Runnable runnable, long l, long l2) {
        if (DatabaseMariadb.hasPaperAsync()) {
            try {
                Object object2 = Bukkit.class.getMethod("getAsyncScheduler", new Class[0]).invoke(null, new Object[0]);
                Object object3 = object2.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, Long.TYPE, Long.TYPE, TimeUnit.class).invoke(object2, new Object[]{this.plugin, object -> runnable.run(), l, l2, TimeUnit.SECONDS});
                return new TaskHandle(null, object3);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously((Plugin)this.plugin, runnable, l * 20L, l2 * 20L);
        return new TaskHandle(bukkitTask, null);
    }

    private void runNowAsync(Runnable runnable) {
        if (DatabaseMariadb.hasPaperAsync()) {
            try {
                Object object2 = Bukkit.class.getMethod("getAsyncScheduler", new Class[0]).invoke(null, new Object[0]);
                object2.getClass().getMethod("runNow", Plugin.class, Consumer.class).invoke(object2, new Object[]{this.plugin, object -> runnable.run()});
                return;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, runnable);
    }

    private void runLaterAsync(Runnable runnable, long l) {
        if (DatabaseMariadb.hasPaperAsync()) {
            try {
                Object object2 = Bukkit.class.getMethod("getAsyncScheduler", new Class[0]).invoke(null, new Object[0]);
                long l2 = Math.max(1L, l * 50L);
                object2.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Long.TYPE, TimeUnit.class).invoke(object2, new Object[]{this.plugin, object -> runnable.run(), l2, TimeUnit.MILLISECONDS});
                return;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously((Plugin)this.plugin, runnable, l);
    }

    private void scheduleTasks() {
        this.batchTask = this.runFixedRateAsync(this::processActionLogBatchQueue, 1L, 1L);
        this.genericTask = this.runFixedRateAsync(this::processGenericTaskQueue, 1L, 1L);
        this.reconnectTask = this.runFixedRateAsync(this::attemptReconnect, 10L, 10L);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void connect() {
        ConfigurationSection configurationSection = this.plugin.getConfig().getConfigurationSection("database");
        if (configurationSection == null || !"mariadb".equalsIgnoreCase(configurationSection.getString("type"))) {
            this.plugin.getLogger().severe("MariaDB configuration section 'database' is missing or 'type' is not 'mariadb'.");
            throw new IllegalStateException("MariaDB configuration is required");
        }
        String string = configurationSection.getString("host", "localhost");
        int n = configurationSection.getInt("port", 3306);
        String string2 = configurationSection.getString("name", "protectcore");
        String string3 = configurationSection.getString("user", "protectcore");
        String string4 = configurationSection.getString("password", "");
        boolean bl = configurationSection.getBoolean("useSSL", false);
        String string5 = "jdbc:mariadb://" + string + ":" + n + "/" + string2 + "?useSsl=" + bl + "&tcpKeepAlive=true";
        if (configurationSection.getBoolean("permitMysqlScheme", false)) {
            string5 = string5 + "&permitMysqlScheme=true";
        }
        MariaDbDataSource mariaDbDataSource = new MariaDbDataSource();
        try {
            mariaDbDataSource.setUrl(string5);
        }
        catch (SQLException sQLException) {
            throw new RuntimeException("Invalid MariaDB JDBC URL: " + string5, sQLException);
        }
        mariaDbDataSource.setUser(string3);
        mariaDbDataSource.setPassword(string4);
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("ProtectCore-MariaDB");
        hikariConfig.setConnectionInitSql("SET SESSION time_zone = '+00:00', sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION'");
        hikariConfig.setMaxLifetime(configurationSection.getLong("pool.maxLifetime", 600000L));
        hikariConfig.setIdleTimeout(configurationSection.getLong("pool.idleTimeout", 300000L));
        hikariConfig.setConnectionTimeout(configurationSection.getLong("pool.connectionTimeout", 30000L));
        ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection("pool");
        hikariConfig.setMaximumPoolSize(configurationSection2 != null ? configurationSection2.getInt("maximumPoolSize", 10) : 10);
        hikariConfig.setMinimumIdle(configurationSection2 != null ? configurationSection2.getInt("minimumIdle", 2) : 2);
        hikariConfig.setInitializationFailTimeout(-1L);
        hikariConfig.setValidationTimeout(3000L);
        hikariConfig.setDataSource(mariaDbDataSource);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            this.dataSource = new HikariDataSource(hikariConfig);
        }
        finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        this.healthState = HealthState.DEGRADED;
        this.runNowAsync(this::attemptReconnect);
    }

    private synchronized void attemptReconnect() {
        if (this.shuttingDown.get() || this.maintenanceMode.get() || this.dataSource == null || this.dataSource.isClosed() || this.healthState == HealthState.CONNECTED) {
            return;
        }
        try (Connection connection = this.dataSource.getConnection();){
            this.initializeDatabaseLight(connection);
            this.lastConnectedAtMillis = System.currentTimeMillis();
            this.lastConnectionError = "";
            this.healthState = HealthState.CONNECTED;
            this.scheduleHeavyMigrationsAsync();
            this.plugin.getLogger().info("Successfully connected to MariaDB and initialized tables.");
        }
        catch (RuntimeException | SQLException exception) {
            this.healthState = HealthState.DEGRADED;
            String string = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            long l = System.currentTimeMillis();
            if (!string.equals(this.lastConnectionError) || l - this.lastConnectionWarningMillis >= 60000L) {
                this.lastConnectionWarningMillis = l;
                this.plugin.getLogger().warning("MariaDB unavailable; logging is buffered (" + this.actionQueueSizeFast() + "/" + this.maxQueuedActions + "): " + string);
            }
            this.lastConnectionError = string;
        }
    }

    public void prepareForShutdown() {
        this.shuttingDown.set(true);
        this.healthState = HealthState.SHUTTING_DOWN;
        if (this.batchTask != null) {
            this.batchTask.cancel();
        }
        if (this.genericTask != null) {
            this.genericTask.cancel();
        }
        if (this.reconnectTask != null) {
            this.reconnectTask.cancel();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void shutdown() {
        this.prepareForShutdown();
        if (this.dataSource == null || this.dataSource.isClosed()) {
            this.actionLogDataQueue.clear();
            this.genericTaskQueue.clear();
            this.actionQueueDepth.set(0L);
            this.genericQueueDepth.sumThenReset();
            this.plugin.getLogger().info("Graceful DB shutdown complete (datasource unavailable).");
            return;
        }
        this.awaitWorkersIdle(2000L);
        this.drainGenericQueueDirect(3000L);
        this.plugin.getLogger().info("Borrowing last connection \u2026");
        try (Connection connection = this.dataSource.getConnection();){
            this.flushActionLogQueue(connection);
        }
        catch (SQLException sQLException) {
            this.plugin.getLogger().log(Level.SEVERE, "Graceful DB flush failed", sQLException);
        }
        finally {
            this.plugin.getLogger().info("Closing HikariDataSource \u2026");
            this.dataSource.close();
        }
        this.plugin.getLogger().info("Graceful DB shutdown complete.");
    }

    private void addColumnIfMissing(Connection connection, String string, String string2, String string3) {
        block9: {
            try {
                if (!this.columnExists(connection, string, string2)) {
                    try (Statement statement = connection.createStatement();){
                        this.execDDL(statement, "add column " + string2, string3);
                        break block9;
                    }
                }
                this.plugin.getLogger().fine("Column " + string2 + " already exists in " + string + ".");
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "Failed ensuring column " + string2 + " on " + string, sQLException);
            }
        }
    }

    private boolean columnExists(Connection connection, String string, String string2) {
        String string3 = "SELECT 1\nFROM information_schema.columns\nWHERE table_schema = DATABASE()\n  AND table_name = ?\n  AND column_name = ?\nLIMIT 1\n";
        try (PreparedStatement preparedStatement = connection.prepareStatement(string3);){
            boolean bl;
            block12: {
                preparedStatement.setString(1, string);
                preparedStatement.setString(2, string2);
                ResultSet resultSet = preparedStatement.executeQuery();
                try {
                    bl = resultSet.next();
                    if (resultSet == null) break block12;
                }
                catch (Throwable throwable) {
                    if (resultSet != null) {
                        try {
                            resultSet.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                resultSet.close();
            }
            return bl;
        }
    }

    public void ensureSchema(Connection connection) {
        this.initializeDatabaseLight(connection);
    }

    private void refreshSearchCapabilities(Connection connection) {
        try {
            this.hasFtDetailsMetadata = this.indexExists(connection, "protectcore_player_actions", "ft_details_metadata");
            this.hasCx = this.columnExists(connection, "protectcore_player_actions", "cx");
            this.hasCz = this.columnExists(connection, "protectcore_player_actions", "cz");
            this.hasItemMaterial = this.columnExists(connection, "protectcore_player_actions", "item_material");
            this.hasItemNameNorm = this.columnExists(connection, "protectcore_player_actions", "item_name_norm");
            this.hasItemLoreNorm = this.columnExists(connection, "protectcore_player_actions", "item_lore_norm");
            this.hasItemSearchText = this.columnExists(connection, "protectcore_player_actions", "item_search_text");
            this.hasItemEnchantsCsv = this.columnExists(connection, "protectcore_player_actions", "item_enchants_csv");
            this.hasFtItemSearch = this.indexExists(connection, "protectcore_player_actions", "ft_item_search");
            this.hasMsgCmdSearchText = this.columnExists(connection, "protectcore_player_actions", "msgcmd_search_text");
            this.hasFtMsgCmdSearch = this.indexExists(connection, "protectcore_player_actions", "ft_msgcmd_search");
            this.hasCommandRootVc = this.columnExists(connection, "protectcore_player_actions", "command_root_vc");
            this.plugin.getLogger().info("[ProtectCore] Search caps: ft_details_metadata=" + this.hasFtDetailsMetadata + " ft_item_search=" + this.hasFtItemSearch + " cx=" + this.hasCx + " cz=" + this.hasCz);
        }
        catch (SQLException sQLException) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to refresh search capabilities", sQLException);
        }
    }

    private void applyOptionalGeneralFulltextMigration(Connection connection) {
        this.createIndexIfMissing(connection, "protectcore_player_actions", "ft_details_metadata", "ALTER TABLE protectcore_player_actions ADD FULLTEXT INDEX ft_details_metadata (details, metadata)");
    }

    private void initializeDatabaseLight(Connection connection) {
        try (Statement statement = connection.createStatement();){
            this.execDDL(statement, "create snapshots table (if missing)", "    CREATE TABLE IF NOT EXISTS protectcore_container_snapshots (\n        id INT AUTO_INCREMENT PRIMARY KEY,\n        playerUUID VARCHAR(36) NOT NULL,\n        playerName VARCHAR(32) NOT NULL,\n        details TEXT,\n        snapshot MEDIUMBLOB NOT NULL,\n        world VARCHAR(64) NOT NULL,\n        x INT NOT NULL,\n        y INT NOT NULL,\n        z INT NOT NULL,\n        `timestamp` TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),\n        preview_json JSON NULL,\n        slot_nonempty INT NULL,\n        item_count INT NULL\n    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci\n");
            this.execDDL(statement, "create actions table (if missing)", "    CREATE TABLE IF NOT EXISTS protectcore_player_actions (\n        id INT AUTO_INCREMENT PRIMARY KEY,\n\n        playerUUID VARCHAR(36) NOT NULL,\n        playerName VARCHAR(32) NOT NULL,\n        action VARCHAR(40) NOT NULL,\n\n        details MEDIUMTEXT NULL,\n        metadata MEDIUMTEXT NULL,\n\n        source ENUM('player','rollback') NOT NULL DEFAULT 'player',\n        rb_id BIGINT NULL,\n        origin_id BIGINT NULL,\n        from_block VARCHAR(64) NULL,\n        from_data TEXT NULL,\n        to_block VARCHAR(64) NULL,\n        to_data TEXT NULL,\n\n        world VARCHAR(64) NOT NULL,\n        x INT NOT NULL,\n        y INT NOT NULL,\n        z INT NOT NULL,\n\n        `timestamp` TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),\n        event_ts   TIMESTAMP(3) NULL,\n\n        snapshot_id INT NULL,\n\n        rolled_back    TINYINT(1) NOT NULL DEFAULT 0,\n        rolled_back_ts TIMESTAMP(3) NULL,\n        rolled_back_by VARCHAR(36) NULL,\n\n        origin_vc VARCHAR(32)\n          AS (CASE WHEN JSON_VALID(metadata)\n                   THEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.origin')))\n                   ELSE NULL END) STORED,\n\n        sort_ts TIMESTAMP(3)\n          AS (COALESCE(event_ts, `timestamp`)) STORED,\n\n        entity_vc VARCHAR(32)\n          AS (CASE WHEN JSON_VALID(metadata)\n                   THEN UPPER(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.entity')))\n                   ELSE NULL END) STORED,\n\n        material_vc VARCHAR(64)\n          AS (CASE WHEN JSON_VALID(metadata)\n                   THEN LOWER(COALESCE(\n                       JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.material')),\n                       JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.item.material')),\n                       JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.block.material'))\n                   ))\n                   ELSE NULL END) STORED,\n\n        CONSTRAINT fk_actions_snapshot\n          FOREIGN KEY (snapshot_id) REFERENCES protectcore_container_snapshots(id)\n          ON DELETE SET NULL\n    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci\n");
        }
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_player_uuid", "CREATE INDEX idx_player_uuid ON protectcore_player_actions(playerUUID)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_sort_ts_id", "CREATE INDEX idx_sort_ts_id ON protectcore_player_actions(sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_world_xyz_sort_id", "CREATE INDEX idx_world_xyz_sort_id ON protectcore_player_actions(world, x, y, z, sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_source_evt_id", "CREATE INDEX idx_source_evt_id ON protectcore_player_actions(source, event_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_world_rbts_id", "CREATE INDEX idx_world_rbts_id ON protectcore_player_actions(world, rolled_back_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_material_vc", "CREATE INDEX idx_material_vc ON protectcore_player_actions(material_vc)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_entity_vc", "CREATE INDEX idx_entity_vc ON protectcore_player_actions(entity_vc)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_player_sort", "CREATE INDEX idx_player_sort ON protectcore_player_actions(playerUUID, sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_container_snapshots", "idx_snap_ts_id", "CREATE INDEX idx_snap_ts_id ON protectcore_container_snapshots(`timestamp` DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_snapshot_id", "CREATE INDEX idx_snapshot_id ON protectcore_player_actions(snapshot_id)");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "item_material", "ALTER TABLE protectcore_player_actions ADD COLUMN item_material VARCHAR(64) NULL");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "item_name_norm", "ALTER TABLE protectcore_player_actions ADD COLUMN item_name_norm VARCHAR(512) NULL");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "item_lore_norm", "ALTER TABLE protectcore_player_actions ADD COLUMN item_lore_norm TEXT NULL");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "item_search_text", "ALTER TABLE protectcore_player_actions ADD COLUMN item_search_text TEXT NULL");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "item_enchants_csv", "ALTER TABLE protectcore_player_actions ADD COLUMN item_enchants_csv VARCHAR(512) NULL");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_item_material", "CREATE INDEX idx_item_material ON protectcore_player_actions(item_material)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "ft_item_search", "ALTER TABLE protectcore_player_actions ADD FULLTEXT INDEX ft_item_search (item_search_text)");
        this.refreshSearchCapabilities(connection);
    }

    private void applyHeavyCoreMigrations(Connection connection) {
        this.ensureSortTsSTORED(connection);
        this.addColumnIfMissing(connection, "protectcore_player_actions", "cx", "ALTER TABLE protectcore_player_actions ADD COLUMN cx INT AS (FLOOR(x/16)) STORED");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "cz", "ALTER TABLE protectcore_player_actions ADD COLUMN cz INT AS (FLOOR(z/16)) STORED");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_world_cx_cz_sort_id", "CREATE INDEX idx_world_cx_cz_sort_id ON protectcore_player_actions(world, cx, cz, sort_ts DESC, id DESC)");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "command_root_vc", "ALTER TABLE protectcore_player_actions ADD COLUMN command_root_vc VARCHAR(64) AS (  CASE     WHEN action = 'PLAYER_COMMAND' AND JSON_VALID(metadata)     THEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.command')))     ELSE NULL   END) STORED");
        this.addColumnIfMissing(connection, "protectcore_player_actions", "msgcmd_search_text", "ALTER TABLE protectcore_player_actions ADD COLUMN msgcmd_search_text TEXT AS (  CASE     WHEN action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN       LOWER(CONCAT_WS(' ',         COALESCE(details, ''),         CASE WHEN JSON_VALID(metadata) THEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.command')), '') ELSE '' END,         CASE WHEN JSON_VALID(metadata) THEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.args')), '') ELSE '' END,         CASE WHEN JSON_VALID(metadata) THEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.raw')), '') ELSE '' END       ))     ELSE NULL   END) STORED");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "ft_msgcmd_search", "ALTER TABLE protectcore_player_actions ADD FULLTEXT INDEX ft_msgcmd_search (msgcmd_search_text)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_action_sort_id", "CREATE INDEX idx_action_sort_id ON protectcore_player_actions(action, sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_player_action_sort_id", "CREATE INDEX idx_player_action_sort_id ON protectcore_player_actions(playerUUID, action, sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_command_root_sort_id", "CREATE INDEX idx_command_root_sort_id ON protectcore_player_actions(command_root_vc, sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_world_action_sort_id", "CREATE INDEX idx_world_action_sort_id ON protectcore_player_actions(world, action, sort_ts DESC, id DESC)");
        this.createIndexIfMissing(connection, "protectcore_player_actions", "idx_world_cx_cz_action_sort_id", "CREATE INDEX idx_world_cx_cz_action_sort_id ON protectcore_player_actions(world, cx, cz, action, sort_ts DESC, id DESC)");
    }

    private String areaFromClause(@Nullable String string) {
        if (this.hasCx && this.hasCz) {
            if (DatabaseMariadb.isMsgCmdAction(string)) {
                return "FROM protectcore_player_actions pa FORCE INDEX (idx_world_cx_cz_action_sort_id) ";
            }
            return "FROM protectcore_player_actions pa FORCE INDEX (idx_world_cx_cz_sort_id) ";
        }
        if (DatabaseMariadb.isMsgCmdAction(string)) {
            return "FROM protectcore_player_actions pa FORCE INDEX (idx_world_action_sort_id) ";
        }
        return "FROM protectcore_player_actions pa FORCE INDEX (idx_world_xyz_sort_id) ";
    }

    private void rescheduleHeavyMigrationsLater(long l) {
        this.heavyMigrationsScheduled.set(false);
        if (this.shuttingDown.get() || DatabaseMariadb.isServerStopping()) {
            return;
        }
        this.runLaterAsync(this::scheduleHeavyMigrationsAsync, l);
    }

    private void scheduleHeavyMigrationsAsync() {
        boolean bl = this.plugin.getConfig().getBoolean("database.migrations.runHeavyAsync", true);
        if (!bl) {
            this.plugin.getLogger().warning("[ProtectCore] Heavy DB migrations disabled by config. Queries requiring optional schema additions will use fallbacks where available.");
            return;
        }
        if (this.shuttingDown.get() || DatabaseMariadb.isServerStopping()) {
            return;
        }
        if (!this.heavyMigrationsScheduled.compareAndSet(false, true)) {
            return;
        }
        this.runLaterAsync(this::runHeavyMigrationsWorker, 20L);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void runHeavyMigrationsWorker() {
        if (this.shuttingDown.get() || DatabaseMariadb.isServerStopping()) {
            this.heavyMigrationsScheduled.set(false);
            return;
        }
        if (this.maintenanceMode.get()) {
            this.rescheduleHeavyMigrationsLater(100L);
            return;
        }
        if (this.dataSource == null || this.dataSource.isClosed()) {
            this.heavyMigrationsScheduled.set(false);
            return;
        }
        if (!this.heavyMigrationsRunning.compareAndSet(false, true)) {
            return;
        }
        this.plugin.getLogger().warning("[ProtectCore] Starting heavy DB migrations in background...");
        long l = System.currentTimeMillis();
        try (Connection connection = this.dataSource.getConnection();){
            this.applyHeavyCoreMigrations(connection);
            this.refreshSearchCapabilities(connection);
            boolean bl = this.plugin.getConfig().getBoolean("database.search.useFulltext", false);
            if (bl && !this.hasFtDetailsMetadata) {
                this.applyOptionalGeneralFulltextMigration(connection);
                this.refreshSearchCapabilities(connection);
            } else {
                this.plugin.getLogger().info("[ProtectCore] Optional general fulltext migration not needed.");
            }
            this.plugin.getLogger().warning("[ProtectCore] Heavy DB migrations finished in " + (System.currentTimeMillis() - l) + " ms");
        }
        catch (SQLException sQLException) {
            this.plugin.getLogger().log(Level.SEVERE, "Heavy DB migrations failed", sQLException);
            if (!this.shuttingDown.get() && !DatabaseMariadb.isServerStopping()) {
                this.rescheduleHeavyMigrationsLater(200L);
                return;
            }
        }
        finally {
            this.heavyMigrationsRunning.set(false);
            this.heavyMigrationsScheduled.set(false);
        }
    }

    private void execDDL(Statement statement, String string, String string2) {
        this.plugin.getLogger().info("[DB MIGRATION] Begin: " + string);
        long l = System.currentTimeMillis();
        statement.execute(string2);
        this.plugin.getLogger().info("[DB MIGRATION] Done: " + string + " (" + (System.currentTimeMillis() - l) + " ms)");
    }

    public void logSystemAsync(UUID uUID, String string, String string2, String string3, String string4, Location location, @Nullable Long l, @Nullable Integer n, @Nullable String string5, @Nullable String string6, @Nullable String string7, @Nullable String string8) {
        if (this.shuttingDown.get() || DatabaseMariadb.isServerStopping() || location == null) {
            return;
        }
        this.enqueueAction(new LogData(uUID, string, string2, string3, string4, "rollback", l, n, string5, string6, string7, string8, null, null, null, null, null, location, System.currentTimeMillis(), null));
    }

    private void createIndexIfMissing(Connection connection, String string, String string2, String string3) {
        block10: {
            try {
                if (!this.indexExists(connection, string, string2)) {
                    try (Statement statement = connection.createStatement();){
                        statement.execute(string3);
                    }
                    this.plugin.getLogger().info("Created index " + string2 + ".");
                    break block10;
                }
                this.plugin.getLogger().fine("Index " + string2 + " already exists.");
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "Failed ensuring index " + string2, sQLException);
            }
        }
    }

    private boolean indexExists(Connection connection, String string, String string2) {
        String string3 = "    SELECT 1\n    FROM information_schema.statistics\n    WHERE table_schema = DATABASE()\n      AND table_name = ?\n      AND index_name = ?\n    LIMIT 1\n";
        try (PreparedStatement preparedStatement = connection.prepareStatement(string3);){
            boolean bl;
            block12: {
                preparedStatement.setString(1, string);
                preparedStatement.setString(2, string2);
                ResultSet resultSet = preparedStatement.executeQuery();
                try {
                    bl = resultSet.next();
                    if (resultSet == null) break block12;
                }
                catch (Throwable throwable) {
                    if (resultSet != null) {
                        try {
                            resultSet.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                resultSet.close();
            }
            return bl;
        }
    }

    private void awaitWorkersIdle(long l) {
        long l2 = System.currentTimeMillis() + Math.max(0L, l);
        while (System.currentTimeMillis() < l2) {
            if (!this.isProcessingActionLogBatch.get() && !this.isProcessingGenericTaskQueue.get()) {
                return;
            }
            try {
                Thread.sleep(5L);
            }
            catch (InterruptedException interruptedException) {}
        }
        this.plugin.getLogger().warning("DB shutdown: timed out waiting for workers to go idle; continuing.");
    }

    private void drainGenericQueueDirect(long l) {
        Runnable runnable;
        long l2 = System.currentTimeMillis() + Math.max(0L, l);
        while (System.currentTimeMillis() < l2 && (runnable = this.genericTaskQueue.poll()) != null) {
            this.genericQueueDepth.add(-1L);
            try {
                runnable.run();
            }
            catch (Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "Error draining generic task", exception);
            }
        }
        if (!this.genericTaskQueue.isEmpty()) {
            this.plugin.getLogger().warning("DB shutdown: generic task queue not fully drained (" + this.genericTaskQueue.size() + " left).");
        }
    }

    private void processGenericTaskQueue() {
        block11: {
            if (this.maintenanceMode.get()) {
                return;
            }
            if (this.dataSource == null || this.dataSource.isClosed()) {
                return;
            }
            if (!this.isProcessingGenericTaskQueue.compareAndSet(false, true)) {
                return;
            }
            try {
                while (!this.isStoppingNow()) {
                    Runnable runnable = this.genericTaskQueue.poll();
                    if (runnable == null) {
                        break;
                    }
                    this.genericQueueDepth.add(-1L);
                    try {
                        runnable.run();
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().log(Level.SEVERE, "Error processing generic task", throwable);
                    }
                }
            }
            finally {
                this.isProcessingGenericTaskQueue.set(false);
            }
        }
    }

    public HikariDataSource getDataSource() {
        return this.dataSource;
    }

    public HealthState getHealthState() {
        return this.healthState;
    }

    public String getLastConnectionError() {
        return this.lastConnectionError;
    }

    public long getLastConnectedAtMillis() {
        return this.lastConnectedAtMillis;
    }

    public long getQueuedActionCount() {
        return this.actionQueueSizeFast();
    }

    public int getMaxQueuedActions() {
        return this.maxQueuedActions;
    }

    public long getQueuedGenericTaskCount() {
        return this.genericQueueDepth.sum();
    }

    public void flushThroughAsync(long l, final Runnable runnable) {
        this.runNowAsync(() -> {
            final long l2 = System.currentTimeMillis() + Math.max(0L, l);
            Runnable runnable2 = new Runnable(){

                @Override
                public void run() {
                    if (System.currentTimeMillis() >= l2 || DatabaseMariadb.this.shuttingDown.get() || DatabaseMariadb.this.actionLogDataQueue.isEmpty()) {
                        Bukkit.getScheduler().runTask((Plugin)DatabaseMariadb.this.plugin, runnable);
                        return;
                    }
                    DatabaseMariadb.this.processActionLogBatchQueue();
                    if (!DatabaseMariadb.this.actionLogDataQueue.isEmpty()) {
                        DatabaseMariadb.this.runLaterAsync(this, 1L);
                    } else {
                        Bukkit.getScheduler().runTask((Plugin)DatabaseMariadb.this.plugin, runnable);
                    }
                }
            };
            runnable2.run();
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void flushActionLogQueue(Connection connection) {
        String string = "INSERT INTO protectcore_player_actions (playerUUID,playerName,action,details,metadata,source,rb_id,origin_id,from_block,from_data,to_block,to_data,item_material,item_name_norm,item_lore_norm,item_search_text,item_enchants_csv,world,x,y,z,`timestamp`,event_ts,snapshot_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        ArrayList<LogData> arrayList = new ArrayList<LogData>(this.maxBatchSize);
        while (true) {
            Object object;
            LogData logData;
            arrayList.clear();
            while (arrayList.size() < this.maxBatchSize && (logData = this.dequeueAction()) != null) {
                arrayList.add(logData);
            }
            if (arrayList.isEmpty()) break;
            boolean bl = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO protectcore_player_actions (playerUUID,playerName,action,details,metadata,source,rb_id,origin_id,from_block,from_data,to_block,to_data,item_material,item_name_norm,item_lore_norm,item_search_text,item_enchants_csv,world,x,y,z,`timestamp`,event_ts,snapshot_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                try {
                    object = new Timestamp(System.currentTimeMillis());
                    for (LogData logData2 : arrayList) {
                        preparedStatement.setString(1, logData2.playerUUID.toString());
                        preparedStatement.setString(2, logData2.playerName);
                        preparedStatement.setString(3, logData2.action);
                        preparedStatement.setString(4, logData2.details);
                        DatabaseMariadb.bindNullableMetadata(preparedStatement, 5, logData2.metadata);
                        preparedStatement.setString(6, logData2.source);
                        if (logData2.rbId == null) {
                            preparedStatement.setNull(7, -5);
                        } else {
                            preparedStatement.setLong(7, logData2.rbId);
                        }
                        if (logData2.originId == null) {
                            preparedStatement.setNull(8, -5);
                        } else {
                            preparedStatement.setLong(8, logData2.originId.longValue());
                        }
                        if (logData2.fromBlock == null) {
                            preparedStatement.setNull(9, 12);
                        } else {
                            preparedStatement.setString(9, logData2.fromBlock);
                        }
                        if (logData2.fromData == null) {
                            preparedStatement.setNull(10, -1);
                        } else {
                            preparedStatement.setString(10, logData2.fromData);
                        }
                        if (logData2.toBlock == null) {
                            preparedStatement.setNull(11, 12);
                        } else {
                            preparedStatement.setString(11, logData2.toBlock);
                        }
                        if (logData2.toData == null) {
                            preparedStatement.setNull(12, -1);
                        } else {
                            preparedStatement.setString(12, logData2.toData);
                        }
                        if (logData2.itemMaterial == null) {
                            preparedStatement.setNull(13, 12);
                        } else {
                            preparedStatement.setString(13, logData2.itemMaterial);
                        }
                        if (logData2.itemNameNorm == null) {
                            preparedStatement.setNull(14, 12);
                        } else {
                            preparedStatement.setString(14, logData2.itemNameNorm);
                        }
                        if (logData2.itemLoreNorm == null) {
                            preparedStatement.setNull(15, -1);
                        } else {
                            preparedStatement.setString(15, logData2.itemLoreNorm);
                        }
                        if (logData2.itemSearchText == null) {
                            preparedStatement.setNull(16, -1);
                        } else {
                            preparedStatement.setString(16, logData2.itemSearchText);
                        }
                        if (logData2.itemEnchantsCsv == null) {
                            preparedStatement.setNull(17, 12);
                        } else {
                            preparedStatement.setString(17, logData2.itemEnchantsCsv);
                        }
                        preparedStatement.setString(18, logData2.worldName);
                        preparedStatement.setInt(19, logData2.x);
                        preparedStatement.setInt(20, logData2.y);
                        preparedStatement.setInt(21, logData2.z);
                        preparedStatement.setTimestamp(22, (Timestamp)object);
                        preparedStatement.setTimestamp(23, new Timestamp(logData2.eventTimeMillis));
                        if (logData2.snapshotId == null) {
                            preparedStatement.setNull(24, 4);
                        } else {
                            preparedStatement.setInt(24, logData2.snapshotId);
                        }
                        preparedStatement.addBatch();
                    }
                    preparedStatement.executeBatch();
                    connection.commit();
                    continue;
                }
                finally {
                    if (preparedStatement == null) continue;
                    preparedStatement.close();
                    continue;
                }
            }
            catch (SQLException sQLException) {
                boolean bl2;
                connection.rollback();
                object = sQLException.getSQLState();
                boolean bl3 = bl2 = object != null && ((String)object).startsWith("08");
                if (bl2) {
                    this.requeueBatchIfAllowed(arrayList, "flushActionLogQueue", sQLException);
                    break;
                }
                this.plugin.getLogger().log(Level.SEVERE, "Flush batch failed (non-retryable). Dropping " + arrayList.size(), sQLException);
            }
            finally {
                try {
                    connection.setAutoCommit(bl);
                }
                catch (SQLException sQLException) {}
                continue;
            }
            break;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void processActionLogBatchQueue() {
        block66: {
            if (this.maintenanceMode.get()) {
                return;
            }
            if (this.dataSource == null || this.dataSource.isClosed() || this.actionLogDataQueue.isEmpty()) {
                return;
            }
            if (System.currentTimeMillis() < this.nextActionBatchAttemptMillis.get()) {
                return;
            }
            if (!this.isProcessingActionLogBatch.compareAndSet(false, true)) {
                return;
            }
            long l = this.actionQueueSizeFast();
            long l2 = Debug.tic();
            ArrayList<LogData> arrayList = new ArrayList<LogData>(this.maxBatchSize);
            long l3 = 0L;
            try {
                LogData logData;
                while (arrayList.size() < this.maxBatchSize && (logData = this.dequeueAction()) != null) {
                    arrayList.add(logData);
                }
                if (arrayList.isEmpty()) {
                    return;
                }
                String string = "INSERT INTO protectcore_player_actions (playerUUID,playerName,action,details,metadata,source,rb_id,origin_id,from_block,from_data,to_block,to_data,item_material,item_name_norm,item_lore_norm,item_search_text,item_enchants_csv,world,x,y,z,`timestamp`,event_ts,snapshot_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                try (Connection connection = this.dataSource.getConnection();){
                    boolean bl = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                    try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO protectcore_player_actions (playerUUID,playerName,action,details,metadata,source,rb_id,origin_id,from_block,from_data,to_block,to_data,item_material,item_name_norm,item_lore_norm,item_search_text,item_enchants_csv,world,x,y,z,`timestamp`,event_ts,snapshot_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");){
                        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                        for (LogData logData2 : arrayList) {
                            preparedStatement.setString(1, logData2.playerUUID.toString());
                            preparedStatement.setString(2, logData2.playerName);
                            preparedStatement.setString(3, logData2.action);
                            preparedStatement.setString(4, logData2.details);
                            DatabaseMariadb.bindNullableMetadata(preparedStatement, 5, logData2.metadata);
                            preparedStatement.setString(6, logData2.source);
                            if (logData2.rbId == null) {
                                preparedStatement.setNull(7, -5);
                            } else {
                                preparedStatement.setLong(7, logData2.rbId);
                            }
                            if (logData2.originId == null) {
                                preparedStatement.setNull(8, -5);
                            } else {
                                preparedStatement.setLong(8, logData2.originId.longValue());
                            }
                            if (logData2.fromBlock == null) {
                                preparedStatement.setNull(9, 12);
                            } else {
                                preparedStatement.setString(9, logData2.fromBlock);
                            }
                            if (logData2.fromData == null) {
                                preparedStatement.setNull(10, -1);
                            } else {
                                preparedStatement.setString(10, logData2.fromData);
                            }
                            if (logData2.toBlock == null) {
                                preparedStatement.setNull(11, 12);
                            } else {
                                preparedStatement.setString(11, logData2.toBlock);
                            }
                            if (logData2.toData == null) {
                                preparedStatement.setNull(12, -1);
                            } else {
                                preparedStatement.setString(12, logData2.toData);
                            }
                            if (logData2.itemMaterial == null) {
                                preparedStatement.setNull(13, 12);
                            } else {
                                preparedStatement.setString(13, logData2.itemMaterial);
                            }
                            if (logData2.itemNameNorm == null) {
                                preparedStatement.setNull(14, 12);
                            } else {
                                preparedStatement.setString(14, logData2.itemNameNorm);
                            }
                            if (logData2.itemLoreNorm == null) {
                                preparedStatement.setNull(15, -1);
                            } else {
                                preparedStatement.setString(15, logData2.itemLoreNorm);
                            }
                            if (logData2.itemSearchText == null) {
                                preparedStatement.setNull(16, -1);
                            } else {
                                preparedStatement.setString(16, logData2.itemSearchText);
                            }
                            if (logData2.itemEnchantsCsv == null) {
                                preparedStatement.setNull(17, 12);
                            } else {
                                preparedStatement.setString(17, logData2.itemEnchantsCsv);
                            }
                            preparedStatement.setString(18, logData2.worldName);
                            preparedStatement.setInt(19, logData2.x);
                            preparedStatement.setInt(20, logData2.y);
                            preparedStatement.setInt(21, logData2.z);
                            preparedStatement.setTimestamp(22, timestamp);
                            preparedStatement.setTimestamp(23, new Timestamp(logData2.eventTimeMillis));
                            if (logData2.snapshotId == null) {
                                preparedStatement.setNull(24, 4);
                            } else {
                                preparedStatement.setInt(24, logData2.snapshotId);
                            }
                            preparedStatement.addBatch();
                        }
                        preparedStatement.executeBatch();
                        connection.commit();
                        this.clearActionBatchBackoff();
                        Debug.log(this.plugin, "db.batch", "inserted " + arrayList.size() + " (queueBefore=" + l + ", queueAfter=" + this.actionQueueSizeFast() + ", took=" + Debug.toc(l2) + ")", new Object[0]);
                    }
                    catch (SQLException sQLException) {
                        boolean bl2;
                        connection.rollback();
                        String string2 = sQLException.getSQLState();
                        boolean bl3 = bl2 = string2 != null && string2.startsWith("08");
                        if (bl2) {
                            this.requeueBatchIfAllowed(arrayList, "processActionLogBatchQueue", sQLException);
                            l3 = this.registerActionBatchBackoff();
                        } else {
                            this.clearActionBatchBackoff();
                            Debug.warn(this.plugin, "db.batch", "batch insert FAILED size=" + arrayList.size() + " took=" + Debug.toc(l2) + " :: " + sQLException.getMessage(), new Object[0]);
                            this.plugin.getLogger().log(Level.SEVERE, "Batch insert failed (non-retryable). Dropping " + arrayList.size() + " log items.", sQLException);
                        }
                    }
                    finally {
                        try {
                            connection.setAutoCommit(bl);
                        }
                        catch (SQLException sQLException) {}
                    }
                }
                catch (SQLException sQLException) {
                    boolean bl;
                    String string3 = sQLException.getSQLState();
                    boolean bl4 = bl = string3 != null && string3.startsWith("08");
                    if (bl) {
                        this.requeueBatchIfAllowed(arrayList, "processActionLogBatchQueue(getConnection)", sQLException);
                        l3 = this.registerActionBatchBackoff();
                        break block66;
                    }
                    this.clearActionBatchBackoff();
                    Debug.warn(this.plugin, "db.batch", "getConnection/setAutoCommit failed after polling " + arrayList.size() + " :: " + sQLException.getMessage(), new Object[0]);
                    this.plugin.getLogger().log(Level.SEVERE, "Database connection error during batch processing. " + arrayList.size() + " items lost.", sQLException);
                }
            }
            finally {
                this.isProcessingActionLogBatch.set(false);
                if (!this.isStoppingNow() && !this.actionLogDataQueue.isEmpty()) {
                    if (l3 > 0L) {
                        this.runLaterAsync(this::processActionLogBatchQueue, l3);
                    } else {
                        this.runNowAsync(this::processActionLogBatchQueue);
                    }
                }
            }
        }
    }

    private long registerActionBatchBackoff() {
        int n2 = this.consecutiveActionBatchFailures.updateAndGet(n -> Math.min(n + 1, 30));
        int n3 = Math.min(n2 - 1, 20);
        long l = this.actionRetryBaseDelayTicks > Long.MAX_VALUE >> n3 ? this.actionRetryMaxDelayTicks : Math.min(this.actionRetryMaxDelayTicks, this.actionRetryBaseDelayTicks << n3);
        this.nextActionBatchAttemptMillis.set(System.currentTimeMillis() + l * 50L);
        Debug.warn(this.plugin, "db.batch", "action-log writes paused for " + l + " ticks after " + n2 + " consecutive transient failure(s)", new Object[0]);
        return l;
    }

    private void clearActionBatchBackoff() {
        this.consecutiveActionBatchFailures.set(0);
        this.nextActionBatchAttemptMillis.set(0L);
    }

    private static String safeWorldName(@Nullable Location location) {
        World world = location != null ? location.getWorld() : null;
        return world != null ? world.getName() : "unknown_world";
    }

    private void ensureSortTsSTORED(Connection connection) {
        block20: {
            String string = "  SELECT EXTRA\n  FROM information_schema.COLUMNS\n  WHERE TABLE_SCHEMA = DATABASE()\n    AND TABLE_NAME = 'protectcore_player_actions'\n    AND COLUMN_NAME = 'sort_ts'\n";
            try (PreparedStatement preparedStatement = connection.prepareStatement(string);
                 ResultSet resultSet = preparedStatement.executeQuery();){
                boolean bl;
                if (!resultSet.next()) break block20;
                String string2 = resultSet.getString(1);
                boolean bl2 = bl = string2 != null && string2.toUpperCase(Locale.ROOT).contains("VIRTUAL");
                if (!bl) break block20;
                try (Statement statement = connection.createStatement();){
                    this.execDDL(statement, "alter sort_ts -> STORED", "ALTER TABLE protectcore_player_actions MODIFY COLUMN sort_ts TIMESTAMP(3) AS (COALESCE(event_ts, `timestamp`)) STORED");
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.FINE, "Skipping sort_ts migration (maybe already STORED / MariaDB too old): " + sQLException.getMessage());
            }
        }
    }

    public void logRollbackBlockChange(long l, @Nullable Integer n, Location location, @Nullable Material material, @Nullable BlockData blockData, @Nullable Material material2, @Nullable BlockData blockData2) {
        String string = material == null ? null : material.name().toLowerCase(Locale.ROOT);
        String string2 = material2 == null ? null : material2.name().toLowerCase(Locale.ROOT);
        String string3 = blockData == null ? null : RollbackEngine.sanitizeBlockDataString(blockData.getAsString());
        String string4 = blockData2 == null ? null : RollbackEngine.sanitizeBlockDataString(blockData2.getAsString());
        String string5 = material2 == null || material2.isAir() ? "BLOCK_BREAK" : "BLOCK_PLACE";
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("origin", "rollback");
        jsonObject.addProperty("rb_id", (Number)l);
        if (n != null) {
            jsonObject.addProperty("origin_id", (Number)n);
        }
        if (string != null) {
            jsonObject.addProperty("from_block", string);
        }
        if (string3 != null) {
            jsonObject.addProperty("from_data", string3);
        }
        if (string2 != null) {
            jsonObject.addProperty("to_block", string2);
        }
        if (string4 != null) {
            jsonObject.addProperty("to_data", string4);
        }
        this.logSystemAsync(UUID.fromString(SYSTEM_UUID_STR), "SYSTEM", string5, "rollback apply", jsonObject.toString(), location, l, n, string, string3, string2, string4);
    }

    public void fetchRecentBySource(long l, Consumer<Map<String, Integer>> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            HashMap<String, Integer> hashMap = new HashMap<String, Integer>();
            String string = "SELECT source, COUNT(*) c FROM protectcore_player_actions WHERE event_ts >= ? GROUP BY source";
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(string);){
                preparedStatement.setTimestamp(1, new Timestamp(l * 1000L));
                try (ResultSet resultSet = preparedStatement.executeQuery();){
                    while (resultSet.next()) {
                        hashMap.put(resultSet.getString(1), resultSet.getInt(2));
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "fetchRecentBySource failed", sQLException);
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(hashMap));
        });
    }

    private List<ActionLog> fetchPageByUserKeyset(Connection connection, String string, List<Object> list, @Nullable Anchor anchor, int n) {
        String string2 = string + (anchor == null ? "" : " (pa.sort_ts < ? OR (pa.sort_ts = ? AND pa.id < ?)) AND ") + " 1=1  ORDER BY pa.sort_ts DESC, pa.id DESC LIMIT ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(string2);){
            ArrayList<ActionLog> arrayList;
            int n2 = 1;
            for (Object arrayList22 : list) {
                preparedStatement.setObject(n2++, arrayList22);
            }
            if (anchor != null) {
                arrayList = new Timestamp(anchor.ts);
                preparedStatement.setTimestamp(n2++, (Timestamp)((Object)arrayList));
                preparedStatement.setTimestamp(n2++, (Timestamp)((Object)arrayList));
                preparedStatement.setInt(n2++, anchor.id);
            }
            preparedStatement.setInt(n2, n);
            arrayList = new ArrayList<ActionLog>(n);
            try (ResultSet resultSet = preparedStatement.executeQuery();){
                while (resultSet.next()) {
                    String string3 = resultSet.getString("metadata_json");
                    ActionLog actionLog = new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), string3, resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), null, null, resultSet.getBoolean("rolled_back"));
                    actionLog.setHasMeta(resultSet.getInt("has_meta"));
                    arrayList.add(actionLog);
                }
            }
            ArrayList<ActionLog> arrayList2 = arrayList;
            return arrayList2;
        }
    }

    @Nullable
    private Anchor advanceAnchorByPages(Connection connection, String string, List<Object> list, @Nullable Anchor anchor, int n, int n2) {
        Anchor anchor2 = anchor;
        for (int i = 0; i < n; ++i) {
            List<ActionLog> list2 = this.fetchPageByUserKeyset(connection, string, list, anchor2, n2);
            if (list2.isEmpty()) {
                return null;
            }
            ActionLog actionLog = list2.get(list2.size() - 1);
            Timestamp timestamp = actionLog.getEventTimestamp() != null ? actionLog.getEventTimestamp() : actionLog.getTimestamp();
            anchor2 = new Anchor(timestamp.getTime(), actionLog.getId());
        }
        return anchor2;
    }

    public void searchLogsWindowedByUserAsync(String string, long l, long l2, String string2, String string3, String string4, String string5, String string6, String string7, Set<String> set, boolean bl, boolean bl2, int n, int n2, int n3, @Nullable Set<String> set2, @Nullable Set<String> set3, Consumer<WindowedResult> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            int n4;
            int n5 = Math.max(1, Math.min(200, n));
            int n6 = Math.max(1, n2);
            int n7 = n4 = n3 <= 0 ? 10 : n3;
            if (this.dataSource == null || this.dataSource.isClosed()) {
                WindowedResult windowedResult = new WindowedResult(List.of(), n6, 1, n4, false, false);
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(windowedResult));
                return;
            }
            FilterResult filterResult = this.buildFilter(string2 == null ? "" : string2, l, l2, string, string3, string4, string5, string6, string7, bl, bl2, set2, set3);
            Object object = filterResult.clause;
            ArrayList<Object> arrayList = new ArrayList<Object>(filterResult.params);
            if (set != null && !set.isEmpty()) {
                object = (String)object + " AND pa.action NOT IN (" + String.join((CharSequence)",", Collections.nCopies(set.size(), "?")) + ") ";
                arrayList.addAll(set);
            }
            int n8 = (n6 - 1) / n4;
            int n9 = n8 * n4 + 1;
            int n10 = n9 + n4 - 1;
            String string8 = this.playerFromClause(string7);
            String string9 = "SELECT\n  pa.id, pa.playerUUID, pa.playerName, pa.action, pa.details,\n  pa.world, pa.x, pa.y, pa.z, pa.`timestamp`, pa.event_ts, pa.rolled_back,\n  CASE\n    WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN 0\n    WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN 0\n    WHEN JSON_VALID(pa.metadata) = 0 THEN 0\n    WHEN (\n      JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n      OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n    ) THEN 1 ELSE 0\n  END AS has_meta,\n  CASE\n    WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN NULL\n    WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN NULL\n    WHEN JSON_VALID(pa.metadata) = 0 THEN NULL\n    WHEN (\n      JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n      OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n    ) THEN pa.metadata\n    ELSE NULL\n  END AS metadata_json\n" + string8 + (String)object + " AND ";
            try (Connection connection = this.dataSource.getConnection();){
                Object object2;
                List<ActionLog> list;
                int n11;
                int n12 = n9;
                int n13 = n10;
                Anchor anchor = null;
                if (n9 > 1 && (anchor = this.advanceAnchorByPages(connection, string9, arrayList, null, n9 - 1, n5)) == null) {
                    n6 = 1;
                    n12 = 1;
                    n13 = n4;
                    anchor = null;
                }
                Anchor anchor2 = anchor;
                List<ActionLog> list2 = List.of();
                int n14 = n12;
                boolean bl3 = false;
                for (n11 = n12; n11 <= Math.min(n6, n13) && !(list = this.fetchPageByUserKeyset(connection, string9, arrayList, anchor2, n5)).isEmpty(); ++n11) {
                    list2 = list;
                    n14 = n11;
                    bl3 = true;
                    if (n11 == n6) break;
                    object2 = list.get(list.size() - 1);
                    Timestamp timestamp = ((ActionLog)object2).getEventTimestamp() != null ? ((ActionLog)object2).getEventTimestamp() : ((ActionLog)object2).getTimestamp();
                    anchor2 = new Anchor(timestamp.getTime(), ((ActionLog)object2).getId());
                }
                if (!bl3) {
                    list2 = List.of();
                    n14 = n12;
                }
                n11 = n12 > 1 ? 1 : 0;
                boolean bl4 = false;
                object2 = anchor;
                for (int i = n12; i <= n13; ++i) {
                    List<ActionLog> list3 = this.fetchPageByUserKeyset(connection, string9, arrayList, (Anchor)object2, n5);
                    if (list3.isEmpty()) {
                        bl4 = false;
                        break;
                    }
                    ActionLog actionLog = list3.get(list3.size() - 1);
                    Timestamp timestamp = actionLog.getEventTimestamp() != null ? actionLog.getEventTimestamp() : actionLog.getTimestamp();
                    object2 = new Anchor(timestamp.getTime(), actionLog.getId());
                    if (i != n13) continue;
                    bl4 = !this.fetchPageByUserKeyset(connection, string9, arrayList, (Anchor)object2, 1).isEmpty();
                }
                WindowedResult windowedResult = new WindowedResult(list2, n14, n12, n13, n11 != 0, bl4);
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(windowedResult));
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.SEVERE, "searchLogsWindowedByUserAsync failed", sQLException);
                WindowedResult windowedResult = new WindowedResult(List.of(), n6, n9, n10, false, false);
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(windowedResult));
            }
        });
    }

    public void logActionAsyncWithEventTime(UUID uUID, String string, String string2, String string3, String string4, Location location, long l) {
        if (this.isStoppingNow()) {
            return;
        }
        if (location == null) {
            this.plugin.getLogger().warning("Attempted to log action with null location for player " + string + ", action: " + string2);
            return;
        }
        if (Debug.on(this.plugin, "db.queue")) {
            Debug.log(this.plugin, "db.queue", "enqueue action=" + string2 + " world=" + DatabaseMariadb.safeWorldName(location) + " pos=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + " qDepth=" + this.actionQueueSizeFast(), new Object[0]);
        }
        this.enqueueAction(new LogData(uUID, string, string2, string3, string4, location, l, null));
    }

    public CompletableFuture<Integer> saveSnapshotAsyncWithPreview(UUID uUID, String string, String string2, byte[] byArray, Location location, String string3, int n, int n2) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<Integer>();
        if (this.isStoppingNow()) {
            completableFuture.completeExceptionally(new IllegalStateException("Shutting down"));
            return completableFuture;
        }
        boolean bl = this.submitGenericTask(() -> {
            String string4 = "INSERT INTO protectcore_container_snapshots (playerUUID,playerName,details,snapshot,world,x,y,z,`timestamp`,preview_json, slot_nonempty, item_count) VALUES (?,?,?,?,?,?,?,?,?, ?,?,?)";
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(string4, 1);){
                preparedStatement.setString(1, uUID.toString());
                preparedStatement.setString(2, string);
                preparedStatement.setString(3, string2);
                preparedStatement.setBytes(4, byArray);
                String string5 = location.getWorld() != null ? location.getWorld().getName() : "unknown_world";
                preparedStatement.setString(5, string5);
                preparedStatement.setInt(6, location.getBlockX());
                preparedStatement.setInt(7, location.getBlockY());
                preparedStatement.setInt(8, location.getBlockZ());
                preparedStatement.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
                preparedStatement.setString(10, string3);
                preparedStatement.setInt(11, n);
                preparedStatement.setInt(12, n2);
                preparedStatement.executeUpdate();
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys();){
                    if (resultSet.next()) {
                        completableFuture.complete(resultSet.getInt(1));
                    } else {
                        completableFuture.completeExceptionally(new SQLException("No generated key"));
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to save container snapshot with preview", sQLException);
                completableFuture.completeExceptionally(sQLException);
            }
        });
        if (!bl) {
            completableFuture.completeExceptionally(new IllegalStateException("DB task rejected (shutdown / maintenance / queue cap / datasource closed)"));
        }
        return completableFuture;
    }

    public void logActionAsyncWithSnapshotId(UUID uUID, String string, String string2, String string3, String string4, Location location, @Nullable Integer n) {
        if (this.isStoppingNow()) {
            return;
        }
        if (location == null) {
            this.plugin.getLogger().warning("Attempted to log action with null location: player=" + string + ", action=" + string2);
            return;
        }
        if (Debug.on(this.plugin, "db.queue")) {
            Debug.log(this.plugin, "db.queue", "enqueue action=" + string2 + " world=" + DatabaseMariadb.safeWorldName(location) + " pos=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + " qDepth=" + this.actionQueueSizeFast(), new Object[0]);
        }
        this.enqueueAction(new LogData(uUID, string, string2, string3, string4, location, System.currentTimeMillis(), n));
    }

    public void logActionAsync(UUID uUID, String string, String string2, String string3, String string4, Location location) {
        this.logActionAsync(uUID, string, string2, string3, string4, null, location);
    }

    public void logActionAsync(UUID uUID, String string, String string2, String string3, String string4, @Nullable ItemMetadataUtil.ItemSearchData itemSearchData, Location location) {
        if (this.isStoppingNow()) {
            return;
        }
        if (location == null) {
            this.plugin.getLogger().warning("Attempted to log action with null location: player=" + string + ", action=" + string2);
            return;
        }
        if (Debug.on(this.plugin, "db.queue")) {
            Debug.log(this.plugin, "db.queue", "enqueue action=" + string2 + " world=" + DatabaseMariadb.safeWorldName(location) + " pos=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + " qDepth=" + this.actionQueueSizeFast(), new Object[0]);
        }
        this.enqueueAction(new LogData(uUID, string, string2, string3, string4, itemSearchData == null ? null : itemSearchData.material(), itemSearchData == null ? null : itemSearchData.nameNorm(), itemSearchData == null ? null : itemSearchData.loreNorm(), itemSearchData == null ? null : itemSearchData.searchText(), itemSearchData == null ? null : itemSearchData.enchantsCsv(), location, System.currentTimeMillis(), null));
    }

    @Nullable
    private static UUID tryGetUuid(ResultSet resultSet, String string) {
        try {
            String string2 = resultSet.getString(string);
            return string2 == null || string2.isBlank() ? null : UUID.fromString(string2);
        }
        catch (Exception exception) {
            return null;
        }
    }

    public void queryActions(String string, int n, int n2, int n3, int n4, boolean bl, Consumer<List<ActionLog>> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            if (this.dataSource == null || this.dataSource.isClosed()) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(Collections.emptyList()));
                return;
            }
            ArrayList<ActionLog> arrayList = new ArrayList<ActionLog>();
            int n5 = n - n4;
            int n6 = n + n4;
            int n7 = n2 - n4;
            int n8 = n2 + n4;
            int n9 = n3 - n4;
            int n10 = n3 + n4;
            String string2 = bl ? "    SELECT pa.*, s.id AS snap_id, s.snapshot AS snap_blob\n    FROM protectcore_player_actions pa\n    LEFT JOIN protectcore_container_snapshots s ON s.id = pa.snapshot_id\n    WHERE pa.world = ?\n      AND pa.x BETWEEN ? AND ? AND pa.y BETWEEN ? AND ? AND pa.z BETWEEN ? AND ?\n    ORDER BY pa.sort_ts DESC, pa.id DESC\n" : "    SELECT pa.*\n    FROM protectcore_player_actions pa\n    WHERE pa.world = ?\n      AND pa.x BETWEEN ? AND ? AND pa.y BETWEEN ? AND ? AND pa.z BETWEEN ? AND ?\n      AND pa.action <> 'CONTAINER_SNAPSHOT'\n    ORDER BY pa.sort_ts DESC, pa.id DESC\n";
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(string2);){
                preparedStatement.setString(1, string);
                preparedStatement.setInt(2, n5);
                preparedStatement.setInt(3, n6);
                preparedStatement.setInt(4, n7);
                preparedStatement.setInt(5, n8);
                preparedStatement.setInt(6, n9);
                preparedStatement.setInt(7, n10);
                try (ResultSet resultSet = preparedStatement.executeQuery();){
                    while (resultSet.next()) {
                        Integer n11 = DatabaseMariadb.tryGetInt(resultSet, "snap_id");
                        byte[] byArray = DatabaseMariadb.tryGetBytes(resultSet, "snap_blob");
                        Timestamp timestamp = resultSet.getTimestamp("rolled_back_ts");
                        UUID uUID = DatabaseMariadb.tryGetUuid(resultSet, "rolled_back_by");
                        arrayList.add(new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), resultSet.getString("metadata"), resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), n11, byArray, resultSet.getBoolean("rolled_back"), timestamp, uUID));
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to query actions by location", sQLException);
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(arrayList));
        });
    }

    private static Set<String> expandMaterialKeys(@Nullable Set<String> set) {
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        for (String string : set) {
            if (string == null || string.isBlank()) continue;
            String string2 = string.trim().toLowerCase(Locale.ROOT);
            linkedHashSet.add(string2);
            linkedHashSet.add(string2.contains(":") ? string2.substring(string2.indexOf(58) + 1) : string2);
        }
        return linkedHashSet;
    }

    private static Set<String> expandEntityKeys(@Nullable Set<String> set) {
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        for (String string : set) {
            if (string == null || string.isBlank()) continue;
            String string2 = string.trim().toLowerCase(Locale.ROOT);
            String string3 = string2.contains(":") ? string2.substring(string2.indexOf(58) + 1) : string2;
            String string4 = string3.toUpperCase(Locale.ROOT);
            if (string4.equals("ITEM_FRAME")) {
                linkedHashSet.add("ITEM_FRAME");
                linkedHashSet.add("GLOW_ITEM_FRAME");
                continue;
            }
            if (string4.equals("GLOW_ITEM_FRAME")) {
                linkedHashSet.add("ITEM_FRAME");
                linkedHashSet.add("GLOW_ITEM_FRAME");
                continue;
            }
            linkedHashSet.add(string4);
        }
        return linkedHashSet;
    }

    private void appendIgnoredUsernamesFilter(StringBuilder stringBuilder, List<Object> list, String string) {
        List list2 = this.plugin.getConfig().getStringList("ignore.usernames");
        if (list2 == null || list2.isEmpty()) {
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>(list2.size());
        for (String string2 : list2) {
            if (string2 == null || string2.isBlank()) continue;
            arrayList.add(string2.trim().toLowerCase(Locale.ROOT));
        }
        if (arrayList.isEmpty()) {
            return;
        }
        stringBuilder.append(" AND NOT (").append(string).append(".action IN ('PLAYER_COMMAND','PLAYER_MESSAGE') ").append("AND LOWER(").append(string).append(".playerName) IN (").append(String.join((CharSequence)",", Collections.nCopies(arrayList.size(), "?"))).append(")) ");
        list.addAll(arrayList);
    }

    public void findRollbackChunks(String string, int n, int n2, int n3, @Nullable UUID uUID, long l, @Nullable String string2, @Nullable String string3, @Nullable Set<String> set, @Nullable Set<String> set2, Consumer<List<ChunkHit>> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            int n4;
            String[] stringArray;
            Connection connection;
            String string4;
            boolean bl;
            ArrayList<ChunkHit> arrayList = new ArrayList<ChunkHit>();
            Timestamp timestamp = new Timestamp(l * 1000L);
            StringBuilder stringBuilder = new StringBuilder(" WHERE pa.world = ? AND COALESCE(pa.origin_vc,'') <> 'rollback' ");
            ArrayList<Object> arrayList2 = new ArrayList<Object>();
            Set<String> set3 = DatabaseMariadb.expandMaterialKeys(set);
            Set<String> set4 = DatabaseMariadb.expandMaterialKeys(set2);
            Set<String> set5 = DatabaseMariadb.expandEntityKeys(set);
            Set<String> set6 = DatabaseMariadb.expandEntityKeys(set2);
            arrayList2.add(string);
            boolean bl2 = bl = n3 >= 0;
            if (bl) {
                stringBuilder.append(" AND pa.x BETWEEN ? AND ? AND pa.z BETWEEN ? AND ? ");
                arrayList2.add(n - n3);
                arrayList2.add(n + n3);
                arrayList2.add(n2 - n3);
                arrayList2.add(n2 + n3);
            }
            if (uUID != null) {
                stringBuilder.append(" AND pa.playerUUID = ? ");
                arrayList2.add(uUID.toString());
            }
            if (string2 != null) {
                stringBuilder.append(" AND pa.action = ? ");
                arrayList2.add(string2);
            }
            if (string3 != null) {
                stringBuilder.append(" AND (LOWER(pa.details) LIKE ? OR pa.material_vc = ?) ");
                string4 = string3.toLowerCase(Locale.ROOT);
                arrayList2.add("%" + string4 + "%");
                arrayList2.add(string4);
            }
            if (!set3.isEmpty() || !set5.isEmpty()) {
                stringBuilder.append(" AND (");
                boolean bl3 = false;
                if (!set3.isEmpty()) {
                    stringBuilder.append(" pa.material_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set3.size(), "?"))).append(") ");
                    arrayList2.addAll(set3);
                    bl3 = true;
                }
                if (!set5.isEmpty()) {
                    if (bl3) {
                        stringBuilder.append(" OR ");
                    }
                    stringBuilder.append(" pa.entity_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set5.size(), "?"))).append(") ");
                    arrayList2.addAll(set5);
                }
                stringBuilder.append(") ");
            }
            if (!set4.isEmpty() || !set6.isEmpty()) {
                stringBuilder.append(" AND (");
                if (!set4.isEmpty()) {
                    stringBuilder.append(" (pa.material_vc IS NULL OR pa.material_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set4.size(), "?"))).append(")) ");
                    arrayList2.addAll(set4);
                } else {
                    stringBuilder.append(" 1=1 ");
                }
                stringBuilder.append(" AND ");
                if (!set6.isEmpty()) {
                    stringBuilder.append(" (pa.entity_vc IS NULL OR pa.entity_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set6.size(), "?"))).append(")) ");
                    arrayList2.addAll(set6);
                } else {
                    stringBuilder.append(" 1=1 ");
                }
                stringBuilder.append(") ");
            }
            this.appendIgnoredUsernamesFilter(stringBuilder, arrayList2, "pa");
            string4 = this.hasCx && this.hasCz ? "SELECT pa.cx AS cx, pa.cz AS cz, COUNT(*) AS hits FROM protectcore_player_actions pa " : "SELECT CAST(FLOOR(pa.x/16.0) AS SIGNED) AS cx, CAST(FLOOR(pa.z/16.0) AS SIGNED) AS cz, COUNT(*) AS hits FROM protectcore_player_actions pa ";
            String string5 = " GROUP BY cx, cz";
            ArrayList<CallSite> arrayList3 = new ArrayList<CallSite>();
            ArrayList<Object> arrayList4 = new ArrayList<Object>();
            arrayList3.add((CallSite)((Object)(string4 + String.valueOf(stringBuilder) + " AND pa.sort_ts >= ? AND pa.action <> 'CONTAINER_SNAPSHOT' " + string5)));
            arrayList4.addAll(arrayList2);
            arrayList4.add(timestamp);
            arrayList3.add((CallSite)((Object)(string4 + String.valueOf(stringBuilder) + " AND pa.action = 'CONTAINER_SNAPSHOT' " + string5)));
            arrayList4.addAll(arrayList2);
            arrayList3.add((CallSite)((Object)(string4 + String.valueOf(stringBuilder) + " AND pa.sort_ts <= ? AND (pa.action LIKE 'BLOCK\\_%' OR pa.action IN ('BUCKET_EMPTY','LIQUID_FLOW')) " + string5)));
            arrayList4.addAll(arrayList2);
            arrayList4.add(timestamp);
            arrayList3.add((CallSite)((Object)(string4 + String.valueOf(stringBuilder) + " AND pa.sort_ts <= ? AND (pa.entity_vc IN ('ITEM_FRAME','GLOW_ITEM_FRAME','PAINTING','ARMOR_STAND') OR (pa.action IN ('CONTAINER_ADD','CONTAINER_TAKE') AND pa.entity_vc IN ('ITEM_FRAME','GLOW_ITEM_FRAME'))) " + string5)));
            arrayList4.addAll(arrayList2);
            arrayList4.add(timestamp);
            arrayList3.add((CallSite)((Object)(string4 + String.valueOf(stringBuilder) + " AND pa.rolled_back_ts >= ? " + string5)));
            arrayList4.addAll(arrayList2);
            arrayList4.add(timestamp);
            String string6 = String.join((CharSequence)" UNION ALL ", arrayList3);
            String string7 = "SELECT cx, cz, SUM(hits) AS hits FROM (" + string6 + ") AS subquery GROUP BY cx, cz ORDER BY hits DESC";
            if (Debug.on(this.plugin, "rollback.manifest")) {
                try {
                    connection = this.dataSource.getConnection();
                    try {
                        stringArray = new String[]{"recent", "snapshots", "old_blocks", "old_entities", "rolled"};
                        for (n4 = 0; n4 < arrayList3.size(); ++n4) {
                            String string8 = "SELECT cx, cz, hits FROM (" + (String)arrayList3.get(n4) + ") AS sub ORDER BY hits DESC LIMIT 3";
                            try (PreparedStatement preparedStatement = connection.prepareStatement(string8);){
                                int n5 = 1;
                                ArrayList<Object> arrayList5 = new ArrayList<Object>(arrayList2);
                                if (n4 == 0 || n4 >= 2) {
                                    arrayList5.add(timestamp);
                                }
                                for (Object e : arrayList5) {
                                    preparedStatement.setObject(n5++, e);
                                }
                                try (ResultSet resultSet = preparedStatement.executeQuery();){
                                    while (resultSet.next()) {
                                        Debug.log(this.plugin, "rollback.manifest", "[sub=%s] e.g. (%d,%d) hits=%d", stringArray[n4], resultSet.getInt("cx"), resultSet.getInt("cz"), resultSet.getInt("hits"));
                                    }
                                    continue;
                                }
                            }
                        }
                    }
                    finally {
                        if (connection != null) {
                            connection.close();
                        }
                    }
                }
                catch (SQLException sQLException) {
                    Debug.warn(this.plugin, "rollback.manifest", "subquery debug logging failed: " + sQLException.getMessage(), new Object[0]);
                }
            }
            try {
                connection = this.dataSource.getConnection();
                try {
                    stringArray = connection.prepareStatement(string7);
                    try {
                        for (n4 = 0; n4 < arrayList4.size(); ++n4) {
                            stringArray.setObject(n4 + 1, arrayList4.get(n4));
                        }
                        try (ResultSet resultSet = stringArray.executeQuery();){
                            while (resultSet.next()) {
                                arrayList.add(new ChunkHit(resultSet.getInt("cx"), resultSet.getInt("cz"), resultSet.getInt("hits")));
                            }
                        }
                    }
                    finally {
                        if (stringArray != null) {
                            stringArray.close();
                        }
                    }
                }
                finally {
                    if (connection != null) {
                        connection.close();
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "findRollbackChunks failed", sQLException);
            }
            Debug.log(this.plugin, "rollback.manifest", "manifest world=" + string + " bounded=" + (n3 >= 0) + " since=" + l + " rows=" + arrayList.size(), new Object[0]);
            if (Debug.on(this.plugin, "db.queries")) {
                arrayList.stream().limit(5L).forEach(chunkHit -> Debug.log(this.plugin, "db.queries", "  chunk (" + chunkHit.cx() + "," + chunkHit.cz() + ") hits=" + chunkHit.hits(), new Object[0]));
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(arrayList));
        });
    }

    public void queryActionsForRollbackChunk(String string, int n, int n2, @Nullable UUID uUID, long l, @Nullable String string2, @Nullable String string3, Consumer<List<ActionLog>> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            Object object;
            ArrayList<ActionLog> arrayList = new ArrayList<ActionLog>();
            Timestamp timestamp = new Timestamp(l * 1000L);
            int n3 = n << 4;
            int n4 = (n << 4) + 15;
            int n5 = n2 << 4;
            int n6 = (n2 << 4) + 15;
            StringBuilder stringBuilder = new StringBuilder("    WHERE pa.world = ?\n      AND pa.x BETWEEN ? AND ? AND pa.z BETWEEN ? AND ?\n      AND (\n             pa.sort_ts >= ?\n          OR pa.rolled_back_ts >= ?\n          OR pa.action = 'CONTAINER_SNAPSHOT'\n          OR (pa.sort_ts <= ? AND (pa.action LIKE 'BLOCK\\_%' OR pa.action IN ('BUCKET_EMPTY','LIQUID_FLOW')))\n          OR (pa.sort_ts <= ? AND (\n                pa.entity_vc IN ('ITEM_FRAME','GLOW_ITEM_FRAME','PAINTING','ARMOR_STAND')\n             OR (pa.action IN ('CONTAINER_ADD','CONTAINER_TAKE') AND pa.entity_vc IN ('ITEM_FRAME','GLOW_ITEM_FRAME'))\n          ))\n      )\n");
            ArrayList<Object> arrayList2 = new ArrayList<Object>();
            arrayList2.add(string);
            arrayList2.add(n3);
            arrayList2.add(n4);
            arrayList2.add(n5);
            arrayList2.add(n6);
            arrayList2.add(timestamp);
            arrayList2.add(timestamp);
            arrayList2.add(timestamp);
            arrayList2.add(timestamp);
            if (uUID != null) {
                stringBuilder.append(" AND pa.playerUUID = ? ");
                arrayList2.add(uUID.toString());
            }
            if (string2 != null) {
                stringBuilder.append(" AND pa.action = ? ");
                arrayList2.add(string2);
            }
            if (string3 != null) {
                stringBuilder.append(" AND (LOWER(pa.details) LIKE ? OR pa.material_vc = ?) ");
                object = string3.toLowerCase(Locale.ROOT);
                arrayList2.add("%" + (String)object + "%");
                arrayList2.add(object);
            }
            this.appendIgnoredUsernamesFilter(stringBuilder, arrayList2, "pa");
            object = "    SELECT pa.*, s.id AS snap_id, s.snapshot AS snap_blob\n    FROM protectcore_player_actions pa\n    LEFT JOIN protectcore_container_snapshots s ON s.id = pa.snapshot_id\n" + String.valueOf(stringBuilder) + " ORDER BY pa.sort_ts DESC, pa.id DESC";
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(((String)object).toString());){
                int n7 = 1;
                for (Object object2 : arrayList2) {
                    preparedStatement.setObject(n7++, object2);
                }
                try (ResultSet resultSet = preparedStatement.executeQuery();){
                    while (resultSet.next()) {
                        Integer n8 = DatabaseMariadb.tryGetInt(resultSet, "snap_id");
                        byte[] byArray = DatabaseMariadb.tryGetBytes(resultSet, "snap_blob");
                        Timestamp timestamp2 = resultSet.getTimestamp("rolled_back_ts");
                        UUID uUID2 = DatabaseMariadb.tryGetUuid(resultSet, "rolled_back_by");
                        arrayList.add(new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), resultSet.getString("metadata"), resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), n8, byArray, resultSet.getBoolean("rolled_back"), timestamp2, uUID2));
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "queryActionsForRollbackChunk failed", sQLException);
            }
            Debug.log(this.plugin, "rollback.perChunk", "chunk (" + n + "," + n2 + ") returned " + arrayList.size() + " logs (user=" + (uUID != null) + ", since=" + l + ")", new Object[0]);
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(arrayList));
        });
    }

    public void markRolledBackBatchAsync(Collection<Integer> collection, @Nullable UUID uUID) {
        if (this.shuttingDown.get() || Bukkit.isStopping() || collection == null || collection.isEmpty()) {
            return;
        }
        String string = "UPDATE protectcore_player_actions SET rolled_back = 1, rolled_back_ts = CURRENT_TIMESTAMP(3), rolled_back_by = ? WHERE id = ? AND rolled_back = 0";
        SqlSetter sqlSetter = (preparedStatement, n) -> {
            if (uUID == null) {
                preparedStatement.setNull(1, 12);
            } else {
                preparedStatement.setString(1, uUID.toString());
            }
            preparedStatement.setInt(2, n);
        };
        ArrayList<Integer> arrayList = new ArrayList<Integer>(collection);
        for (int i = 0; i < arrayList.size(); i += 500) {
            this.submitUpdateSlice(new ArrayList<Integer>(arrayList.subList(i, Math.min(i + 500, arrayList.size()))), "UPDATE protectcore_player_actions SET rolled_back = 1, rolled_back_ts = CURRENT_TIMESTAMP(3), rolled_back_by = ? WHERE id = ? AND rolled_back = 0", sqlSetter, 0);
        }
    }

    public void unmarkRolledBackBatchAsync(Collection<Integer> collection) {
        if (this.shuttingDown.get() || Bukkit.isStopping() || collection == null || collection.isEmpty()) {
            return;
        }
        String string = "UPDATE protectcore_player_actions SET rolled_back = 0,     rolled_back_ts = CURRENT_TIMESTAMP(3),     rolled_back_by = NULL WHERE id = ? AND rolled_back <> 0";
        SqlSetter sqlSetter = (preparedStatement, n) -> preparedStatement.setInt(1, n);
        ArrayList<Integer> arrayList = new ArrayList<Integer>(collection);
        for (int i = 0; i < arrayList.size(); i += 500) {
            this.submitUpdateSlice(new ArrayList<Integer>(arrayList.subList(i, Math.min(i + 500, arrayList.size()))), "UPDATE protectcore_player_actions SET rolled_back = 0,     rolled_back_ts = CURRENT_TIMESTAMP(3),     rolled_back_by = NULL WHERE id = ? AND rolled_back <> 0", sqlSetter, 0);
        }
    }

    private void submitUpdateSlice(List<Integer> list, String string, SqlSetter sqlSetter, int n) {
        if (list == null || list.isEmpty()) {
            return;
        }
        boolean bl = this.submitGenericTask(() -> {
            if (this.dataSource == null || this.dataSource.isClosed()) {
                return;
            }
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(string);){
                Iterator iterator = list.iterator();
                while (iterator.hasNext()) {
                    int n2 = (Integer)iterator.next();
                    sqlSetter.bind(preparedStatement, n2);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            }
            catch (SQLException sQLException) {
                boolean bl;
                String string2 = sQLException.getSQLState();
                boolean bl2 = bl = string2 != null && string2.startsWith("08");
                if (bl && n < 3 && !this.isStoppingNow()) {
                    long l = 20L * (1L << n);
                    long l2 = ThreadLocalRandom.current().nextLong(10L);
                    this.runLaterAsync(() -> {
                        if (!this.isStoppingNow()) {
                            this.submitUpdateSlice(list, string, sqlSetter, n + 1);
                        }
                    }, l + l2);
                }
                this.plugin.getLogger().log(Level.WARNING, "update slice failed after " + n + " retries (size=" + list.size() + ")", sQLException);
            }
        });
        if (!bl) {
            this.plugin.getLogger().warning("[ProtectCore] Dropped update slice because DB task queue rejected it (shutdown / maintenance / datasource closed). size=" + list.size());
        }
    }

    public void fetchRolledBackFlags(Collection<Integer> collection, Consumer<Set<Integer>> consumer) {
        if (collection == null || collection.isEmpty()) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(Collections.emptySet()));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            HashSet<Integer> hashSet = new HashSet<Integer>();
            String string = String.join((CharSequence)",", Collections.nCopies(collection.size(), "?"));
            String string2 = "SELECT id FROM protectcore_player_actions WHERE rolled_back=1 AND id IN (" + string + ")";
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(string2);){
                int n = 1;
                Object object = collection.iterator();
                while (object.hasNext()) {
                    int n2 = (Integer)object.next();
                    preparedStatement.setInt(n++, n2);
                }
                object = preparedStatement.executeQuery();
                try {
                    while (object.next()) {
                        hashSet.add(object.getInt(1));
                    }
                }
                finally {
                    if (object != null) {
                        object.close();
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.WARNING, "fetchRolledBackFlags failed", sQLException);
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(hashSet));
        });
    }

    public void updatePlayerNameInLogs(UUID uUID, String string) {
        if (this.shuttingDown.get() || this.dataSource == null || this.dataSource.isClosed()) {
            return;
        }
        int n = Math.max(500, this.plugin.getConfig().getInt("database.renameSlice", 5000));
        boolean bl = this.submitGenericTask(() -> {
            int n2 = 0;
            int n3 = n;
            try {
                Connection connection = this.dataSource.getConnection();
                try {
                    connection.setAutoCommit(true);
                    while (!this.shuttingDown.get()) {
                        int n4;
                        Object object;
                        ArrayList<Integer> arrayList = new ArrayList<Integer>(n3);
                        String string2 = "SELECT id FROM protectcore_player_actions WHERE playerUUID = ? AND (playerName IS NULL OR playerName = '' OR playerName <> ?) ORDER BY id ASC LIMIT ?";
                        try (Object object2 = connection.prepareStatement(string2);){
                            object2.setString(1, uUID.toString());
                            object2.setString(2, string);
                            object2.setInt(3, n3);
                            object = object2.executeQuery();
                            try {
                                while (object.next()) {
                                    arrayList.add(object.getInt(1));
                                }
                            }
                            finally {
                                if (object != null) {
                                    object.close();
                                }
                            }
                        }
                        if (arrayList.isEmpty()) break;
                        object2 = String.join((CharSequence)",", Collections.nCopies(arrayList.size(), "?"));
                        object = "UPDATE protectcore_player_actions SET playerName=? WHERE id IN (" + (String)object2 + ")";
                        try (PreparedStatement preparedStatement = connection.prepareStatement((String)object);){
                            n4 = 1;
                            preparedStatement.setString(n4++, string);
                            Iterator iterator = arrayList.iterator();
                            while (iterator.hasNext()) {
                                int n5 = (Integer)iterator.next();
                                preparedStatement.setInt(n4++, n5);
                            }
                            int n6 = preparedStatement.executeUpdate();
                            n2 += n6;
                        }
                        catch (SQLException sQLException) {
                            if (sQLException.getErrorCode() != 1206 && sQLException.getErrorCode() != 1205) {
                                if (sQLException.getErrorCode() != 1213) throw sQLException;
                            }
                            n4 = Math.max(250, n3 / 2);
                            this.plugin.getLogger().warning("[ProtectCore] player rename slice hit lock pressure; reducing slice from " + n3 + " to " + n4 + " for " + String.valueOf(uUID) + ": " + sQLException.getMessage());
                            this.runLaterAsync(() -> {
                                if (!this.isStoppingNow()) {
                                    this.updatePlayerNameInLogs(uUID, string);
                                }
                            }, 100L + (long)(Math.random() * 100.0));
                            if (connection == null) return;
                            connection.close();
                            return;
                        }
                        try {
                            Thread.sleep(10L);
                        }
                        catch (InterruptedException interruptedException) {}
                    }
                    this.plugin.getLogger().info("Renamed " + n2 + " rows for player " + string + " (UUID: " + String.valueOf(uUID) + ")");
                    return;
                }
                finally {
                    if (connection != null) {
                        try {
                            connection.close();
                        }
                        catch (Throwable throwable) {
                            Throwable throwable2;
                            throwable2.addSuppressed(throwable);
                        }
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to chunked-update playerName for " + String.valueOf(uUID), sQLException);
            }
        });
        if (!bl) {
            this.plugin.getLogger().warning("[ProtectCore] Skipped player rename task for " + String.valueOf(uUID) + " because DB queue rejected it.");
        }
    }

    private FilterResult buildFilter(String string, long l, long l2, String string2, String string3, String string4, String string5, String string6, String string7, boolean bl, boolean bl2) {
        return this.buildFilter(string, l, l2, string2, string3, string4, string5, string6, string7, bl, bl2, null, null);
    }

    private static String stripMcFormatting(String string) {
        if (string == null) {
            return "";
        }
        string = MC_HEX.matcher(string).replaceAll("");
        string = MC_LEGACY.matcher(string).replaceAll("");
        return string;
    }

    private static String sqlStripMcCodesLowerExpr(String string) {
        String string2 = "LOWER(" + string + ")";
        String string3 = "0123456789abcdefklmnorx";
        for (int i = 0; i < "0123456789abcdefklmnorx".length(); ++i) {
            char c = "0123456789abcdefklmnorx".charAt(i);
            string2 = "REPLACE(" + string2 + ", '\u00a7" + c + "', '')";
            string2 = "REPLACE(" + string2 + ", '&" + c + "', '')";
        }
        return string2;
    }

    private static String normalizeSearch(String string) {
        return DatabaseMariadb.stripMcFormatting(string).toLowerCase(Locale.ROOT).trim();
    }

    private static String toBooleanFtQuery(String string) {
        String string2 = DatabaseMariadb.normalizeSearch(string);
        if (string2.isEmpty()) {
            return "";
        }
        ArrayList<CallSite> arrayList = new ArrayList<CallSite>();
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}_]+").matcher(string2);
        while (matcher.find()) {
            String string3 = matcher.group();
            if (string3.length() < 3) continue;
            arrayList.add((CallSite)((Object)("+" + string3 + "*")));
        }
        return String.join((CharSequence)" ", arrayList);
    }

    private void appendNameFilter(StringBuilder stringBuilder, List<Object> list, @Nullable String string) {
        if (string == null || string.isBlank()) {
            return;
        }
        String string2 = DatabaseMariadb.normalizeSearch(string);
        if (string2.isEmpty()) {
            return;
        }
        String string3 = DatabaseMariadb.toBooleanFtQuery(string2);
        if (!string3.isEmpty() && this.hasItemSearchText && this.hasFtItemSearch) {
            stringBuilder.append(" AND MATCH(pa.item_search_text) AGAINST (? IN BOOLEAN MODE) ");
            list.add(string3);
        } else if (this.hasItemNameNorm) {
            stringBuilder.append(" AND pa.item_name_norm LIKE ? ");
            list.add("%" + string2 + "%");
        } else {
            stringBuilder.append(" AND LOWER(pa.metadata) LIKE ? ");
            list.add("%" + string2 + "%");
        }
    }

    private void appendLoreFilter(StringBuilder stringBuilder, List<Object> list, @Nullable String string) {
        if (string == null || string.isBlank()) {
            return;
        }
        String string2 = DatabaseMariadb.normalizeSearch(string);
        if (string2.isEmpty()) {
            return;
        }
        String string3 = DatabaseMariadb.toBooleanFtQuery(string2);
        if (!string3.isEmpty() && this.hasItemSearchText && this.hasFtItemSearch) {
            stringBuilder.append(" AND MATCH(pa.item_search_text) AGAINST (? IN BOOLEAN MODE) ");
            list.add(string3);
        } else if (this.hasItemLoreNorm) {
            stringBuilder.append(" AND pa.item_lore_norm LIKE ? ");
            list.add("%" + string2 + "%");
        } else {
            stringBuilder.append(" AND LOWER(pa.metadata) LIKE ? ");
            list.add("%" + string2 + "%");
        }
    }

    private FilterResult buildFilter(String string2, long l, long l2, String string3, String string4, String string5, String string6, String string7, String string8, boolean bl, boolean bl2, @Nullable Set<String> set, @Nullable Set<String> set2) {
        String[] stringArray;
        ArrayList<Object> arrayList = new ArrayList<Object>();
        StringBuilder stringBuilder = new StringBuilder(" WHERE 1=1 ");
        Set<String> set3 = DatabaseMariadb.expandMaterialKeys(set);
        Set<String> set4 = DatabaseMariadb.expandMaterialKeys(set2);
        Set<String> set5 = DatabaseMariadb.expandEntityKeys(set);
        Set<String> set6 = DatabaseMariadb.expandEntityKeys(set2);
        if (l > 0L) {
            stringBuilder.append(" AND pa.sort_ts >= ? ");
            arrayList.add(new Timestamp(l * 1000L));
        }
        if (l2 != Long.MAX_VALUE) {
            stringBuilder.append(" AND pa.sort_ts <= ? ");
            arrayList.add(new Timestamp(l2 * 1000L));
        }
        if (string3 == null || string3.isEmpty()) {
            stringBuilder.append(" AND pa.playerUUID <> ? ");
            arrayList.add(SYSTEM_UUID_STR);
        } else {
            stringBuilder.append(" AND pa.playerUUID = ? ");
            arrayList.add(string3);
        }
        if (string8 != null && !string8.isEmpty()) {
            stringBuilder.append(" AND pa.action = ? ");
            arrayList.add(string8);
        }
        this.appendTextQueryFilter(stringBuilder, arrayList, string2, string8);
        if (string4 != null && !string4.isEmpty()) {
            stringArray = string4.toLowerCase(Locale.ROOT);
            String[] stringArray2 = stringArray.startsWith("minecraft:") ? stringArray.substring(10) : stringArray;
            stringBuilder.append(" AND (");
            int n = 0;
            if (this.hasItemMaterial) {
                stringBuilder.append(" pa.item_material IN (?, ?) ");
                arrayList.add(stringArray);
                arrayList.add(stringArray2);
                n = 1;
            }
            if (n != 0) {
                stringBuilder.append(" OR ");
            }
            stringBuilder.append(" pa.material_vc IN (?, ?) ");
            arrayList.add(stringArray);
            arrayList.add(stringArray2);
            n = 1;
            stringBuilder.append(" OR LOWER(pa.details) LIKE ? ) ");
            arrayList.add("%" + (String)stringArray2 + "%");
        }
        this.appendNameFilter(stringBuilder, arrayList, string5);
        this.appendLoreFilter(stringBuilder, arrayList, string6);
        if (string7 != null && !string7.isEmpty() && (stringArray = (String[])Arrays.stream(string7.split(",")).map(String::trim).filter(string -> !string.isEmpty()).toArray(String[]::new)).length > 0) {
            for (String string9 : stringArray) {
                stringBuilder.append(" AND pa.metadata LIKE ? ");
                arrayList.add("%" + string9 + "%");
            }
        }
        if (bl) {
            stringBuilder.append(" AND pa.rolled_back = 0 ");
        }
        if (bl2) {
            stringBuilder.append(" AND COALESCE(pa.origin_vc,'') <> 'worldedit' ");
        }
        if (!set3.isEmpty() || !set5.isEmpty()) {
            stringBuilder.append(" AND (");
            boolean bl3 = false;
            if (!set3.isEmpty()) {
                stringBuilder.append(" pa.material_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set3.size(), "?"))).append(") ");
                arrayList.addAll(set3);
                bl3 = true;
                stringBuilder.append(" OR (");
                boolean bl4 = true;
                for (String string10 : set3) {
                    String string9;
                    if (!bl4) {
                        stringBuilder.append(" OR ");
                    }
                    bl4 = false;
                    string9 = string10.contains(":") ? string10.substring(string10.indexOf(58) + 1) : string10;
                    stringBuilder.append(" LOWER(pa.details) LIKE ? ");
                    arrayList.add("%" + string9 + "%");
                }
                stringBuilder.append(")");
            }
            if (!set5.isEmpty()) {
                if (bl3) {
                    stringBuilder.append(" OR ");
                }
                stringBuilder.append(" pa.entity_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set5.size(), "?"))).append(") ");
                arrayList.addAll(set5);
            }
            stringBuilder.append(") ");
        }
        if (!set4.isEmpty() || !set6.isEmpty()) {
            stringBuilder.append(" AND (");
            if (!set4.isEmpty()) {
                stringBuilder.append(" (pa.material_vc IS NULL OR pa.material_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set4.size(), "?"))).append(")) ");
                arrayList.addAll(set4);
                stringBuilder.append(" AND ");
                boolean bl5 = true;
                for (String string11 : set4) {
                    if (!bl5) {
                        stringBuilder.append(" AND ");
                    }
                    bl5 = false;
                    String string12 = string11.contains(":") ? string11.substring(string11.indexOf(58) + 1) : string11;
                    stringBuilder.append(" LOWER(pa.details) NOT LIKE ? ");
                    arrayList.add("%" + string12 + "%");
                }
            } else {
                stringBuilder.append(" 1=1 ");
            }
            stringBuilder.append(" AND ");
            if (!set6.isEmpty()) {
                stringBuilder.append(" (pa.entity_vc IS NULL OR pa.entity_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set6.size(), "?"))).append(")) ");
                arrayList.addAll(set6);
            } else {
                stringBuilder.append(" 1=1 ");
            }
            stringBuilder.append(") ");
        }
        this.appendIgnoredUsernamesFilter(stringBuilder, arrayList, "pa");
        return new FilterResult(stringBuilder.toString(), arrayList);
    }

    private static boolean isMsgCmdAction(@Nullable String string) {
        return "PLAYER_MESSAGE".equals(string) || "PLAYER_COMMAND".equals(string);
    }

    private static String normalizeCommandKey(String string) {
        if (string == null) {
            return "";
        }
        if ((string = string.trim()).startsWith("/")) {
            string = string.substring(1);
        }
        string = string.toLowerCase(Locale.ROOT);
        string = string.replace(':', ' ');
        string = string.replaceAll("\\s+", " ").trim();
        return string;
    }

    private void appendTextQueryFilter(StringBuilder stringBuilder, List<Object> list, @Nullable String string, @Nullable String string2) {
        String string3;
        if (string == null || string.isBlank()) {
            return;
        }
        String string4 = DatabaseMariadb.normalizeSearch(string);
        String string5 = DatabaseMariadb.toBooleanFtQuery(string4);
        if ("PLAYER_COMMAND".equals(string2) && this.hasCommandRootVc && !(string3 = DatabaseMariadb.normalizeCommandKey(string)).isBlank() && !string3.contains(" ")) {
            stringBuilder.append(" AND (pa.command_root_vc = ? ");
            list.add(string3);
            if (this.hasMsgCmdSearchText && this.hasFtMsgCmdSearch && !string5.isEmpty()) {
                stringBuilder.append(" OR MATCH(pa.msgcmd_search_text) AGAINST (? IN BOOLEAN MODE) ");
                list.add(string5);
            } else {
                stringBuilder.append(" OR LOWER(pa.details) LIKE ? ");
                list.add("%" + string4 + "%");
            }
            stringBuilder.append(") ");
            return;
        }
        if (DatabaseMariadb.isMsgCmdAction(string2) && this.hasMsgCmdSearchText && this.hasFtMsgCmdSearch && !string5.isEmpty()) {
            stringBuilder.append(" AND MATCH(pa.msgcmd_search_text) AGAINST (? IN BOOLEAN MODE) ");
            list.add(string5);
            return;
        }
        if (this.plugin.getConfig().getBoolean("database.search.useFulltext", false) && this.hasFtDetailsMetadata && !string5.isEmpty()) {
            stringBuilder.append(" AND MATCH(pa.details, pa.metadata) AGAINST (? IN BOOLEAN MODE) ");
            list.add(string5);
            return;
        }
        if (DatabaseMariadb.isMsgCmdAction(string2)) {
            stringBuilder.append(" AND LOWER(pa.details) LIKE ? ");
            list.add("%" + string4 + "%");
        } else {
            stringBuilder.append(" AND (LOWER(pa.details) LIKE ? OR LOWER(pa.metadata) LIKE ?) ");
            list.add("%" + string4 + "%");
            list.add("%" + string4 + "%");
        }
    }

    public void searchLogsByAreaKeysetAsync(String string, int n, int n2, int n3, int n4, long l, long l2, @Nullable String string2, String string3, String string4, String string5, String string6, String string7, String string8, Set<String> set, boolean bl, boolean bl2, int n5, @Nullable Long l3, @Nullable Integer n6, @Nullable Set<String> set2, @Nullable Set<String> set3, Consumer<CursorResult> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            boolean bl3;
            Object object;
            Object object22;
            Object object3;
            if (this.dataSource == null || this.dataSource.isClosed()) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(new CursorResult(List.of(), false, 0L, 0)));
                return;
            }
            int n7 = (int)Math.floor((double)(n - n4) / 16.0);
            int n8 = (int)Math.floor((double)(n + n4) / 16.0);
            int n9 = (int)Math.floor((double)(n3 - n4) / 16.0);
            int n10 = (int)Math.floor((double)(n3 + n4) / 16.0);
            int n11 = n - n4;
            int n12 = n + n4;
            int n13 = n2 - n4;
            int n14 = n2 + n4;
            int n15 = n3 - n4;
            int n16 = n3 + n4;
            ArrayList<Object> arrayList = new ArrayList<Object>();
            StringBuilder stringBuilder = new StringBuilder(" WHERE pa.world = ? ");
            arrayList.add(string);
            if (this.hasCx && this.hasCz) {
                stringBuilder.append(" AND pa.cx BETWEEN ? AND ? AND pa.cz BETWEEN ? AND ? ");
                arrayList.add(n7);
                arrayList.add(n8);
                arrayList.add(n9);
                arrayList.add(n10);
            }
            stringBuilder.append(" AND pa.x BETWEEN ? AND ? AND pa.y BETWEEN ? AND ? AND pa.z BETWEEN ? AND ? ");
            arrayList.add(n11);
            arrayList.add(n12);
            arrayList.add(n13);
            arrayList.add(n14);
            arrayList.add(n15);
            arrayList.add(n16);
            Set<String> set4 = DatabaseMariadb.expandMaterialKeys(set2);
            Set<String> set5 = DatabaseMariadb.expandMaterialKeys(set3);
            Set<String> set6 = DatabaseMariadb.expandEntityKeys(set2);
            Set<String> set7 = DatabaseMariadb.expandEntityKeys(set3);
            if (string2 == null || string2.isBlank()) {
                stringBuilder.append(" AND pa.playerUUID <> ? ");
                arrayList.add(SYSTEM_UUID_STR);
            } else {
                stringBuilder.append(" AND pa.playerUUID = ? ");
                arrayList.add(string2);
            }
            if (l > 0L) {
                stringBuilder.append(" AND pa.sort_ts >= ? ");
                arrayList.add(new Timestamp(l * 1000L));
            }
            if (l2 != Long.MAX_VALUE) {
                stringBuilder.append(" AND pa.sort_ts <= ? ");
                arrayList.add(new Timestamp(l2 * 1000L));
            }
            if (string8 != null && !string8.isBlank()) {
                stringBuilder.append(" AND pa.action = ? ");
                arrayList.add(string8);
            }
            if (set != null && !set.isEmpty()) {
                stringBuilder.append(" AND pa.action NOT IN (" + String.join((CharSequence)",", Collections.nCopies(set.size(), "?")) + ") ");
                arrayList.addAll(set);
            }
            if (bl) {
                stringBuilder.append(" AND pa.rolled_back = 0 ");
            }
            if (bl2) {
                stringBuilder.append(" AND COALESCE(pa.origin_vc,'') <> 'worldedit' ");
            }
            this.appendTextQueryFilter(stringBuilder, arrayList, string3, string8);
            if (string4 != null && !string4.isBlank()) {
                String[] stringArray = string4.toLowerCase(Locale.ROOT);
                stringBuilder.append(" AND (pa.material_vc = ? OR LOWER(pa.details) LIKE ? OR LOWER(pa.details) LIKE ?) ");
                arrayList.add(stringArray);
                String[] object52 = stringArray.startsWith("minecraft:") ? stringArray.substring(10) : stringArray;
                arrayList.add("%" + (String)stringArray + "%");
                arrayList.add("%" + (String)object52 + "%");
            }
            this.appendNameFilter(stringBuilder, arrayList, string5);
            this.appendLoreFilter(stringBuilder, arrayList, string6);
            if (string7 != null && !string7.isBlank()) {
                for (String string9 : string7.split(",")) {
                    object3 = string9.trim();
                    if (((String)object3).isEmpty()) continue;
                    stringBuilder.append(" AND pa.metadata LIKE ? ");
                    arrayList.add("%" + (String)object3 + "%");
                }
            }
            if (!set4.isEmpty() || !set6.isEmpty()) {
                stringBuilder.append(" AND (");
                boolean bl4 = false;
                if (!set4.isEmpty()) {
                    stringBuilder.append(" (pa.material_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set4.size(), "?"))).append(") ");
                    arrayList.addAll(set4);
                    stringBuilder.append(" OR (pa.material_vc IS NULL AND (");
                    boolean bl5 = true;
                    for (String string10 : set4) {
                        boolean bl6;
                        if (!bl6) {
                            stringBuilder.append(" OR ");
                        }
                        bl6 = false;
                        object3 = string10.contains(":") ? string10.substring(string10.indexOf(58) + 1) : string10;
                        stringBuilder.append(" LOWER(pa.details) LIKE ? ");
                        arrayList.add("%" + (String)object3 + "%");
                    }
                    stringBuilder.append("))) ");
                    bl4 = true;
                }
                if (!set6.isEmpty()) {
                    if (bl4) {
                        stringBuilder.append(" OR ");
                    }
                    stringBuilder.append(" pa.entity_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set6.size(), "?"))).append(") ");
                    arrayList.addAll(set6);
                }
                stringBuilder.append(") ");
            }
            if (!set5.isEmpty() || !set7.isEmpty()) {
                stringBuilder.append(" AND (");
                if (!set5.isEmpty()) {
                    stringBuilder.append(" ( (pa.material_vc IS NOT NULL AND pa.material_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set5.size(), "?"))).append(")) ");
                    arrayList.addAll(set5);
                    stringBuilder.append(" OR (pa.material_vc IS NULL");
                    for (String string11 : set5) {
                        String string12 = string11.contains(":") ? string11.substring(string11.indexOf(58) + 1) : string11;
                        stringBuilder.append(" AND LOWER(pa.details) NOT LIKE ? ");
                        arrayList.add("%" + string12 + "%");
                    }
                    stringBuilder.append(") ) ");
                } else {
                    stringBuilder.append(" 1=1 ");
                }
                stringBuilder.append(" AND ");
                if (!set7.isEmpty()) {
                    stringBuilder.append(" (pa.entity_vc IS NULL OR pa.entity_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set7.size(), "?"))).append(")) ");
                    arrayList.addAll(set7);
                } else {
                    stringBuilder.append(" 1=1 ");
                }
                stringBuilder.append(") ");
            }
            if (l3 != null && n6 != null) {
                stringBuilder.append(" AND (pa.sort_ts < ? OR (pa.sort_ts = ? AND pa.id < ?)) ");
                Timestamp timestamp = new Timestamp(l3);
                arrayList.add(timestamp);
                arrayList.add(timestamp);
                arrayList.add(n6);
            }
            this.appendIgnoredUsernamesFilter(stringBuilder, arrayList, "pa");
            int n17 = n5 + 1;
            String string13 = this.areaFromClause(string8);
            String string14 = "SELECT\n  pa.id, pa.playerUUID, pa.playerName, pa.action, pa.details,\n  pa.world, pa.x, pa.y, pa.z, pa.`timestamp`, pa.event_ts, pa.rolled_back, pa.sort_ts,\n  CASE\n    WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN 0\n    WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN 0\n    WHEN JSON_VALID(pa.metadata) = 0 THEN 0\n    WHEN (\n      JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n      OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n    ) THEN 1 ELSE 0\n  END AS has_meta,\n  CASE\n    WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN NULL\n    WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN NULL\n    WHEN JSON_VALID(pa.metadata) = 0 THEN NULL\n    WHEN (\n      JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n      OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n      OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n    ) THEN pa.metadata\n    ELSE NULL\n  END AS metadata_json\n" + string13 + String.valueOf(stringBuilder) + " ORDER BY pa.sort_ts DESC, pa.id DESC LIMIT ?";
            ArrayList<ActionLog> arrayList2 = new ArrayList<ActionLog>();
            try {
                object3 = this.dataSource.getConnection();
                try (PreparedStatement preparedStatement = object3.prepareStatement(string14);){
                    int n18 = 1;
                    for (Object object22 : arrayList) {
                        preparedStatement.setObject(n18++, object22);
                    }
                    preparedStatement.setInt(n18, n17);
                    try (ResultSet resultSet = preparedStatement.executeQuery();){
                        while (resultSet.next()) {
                            object22 = resultSet.getString("metadata_json");
                            object = new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), (String)object22, resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), null, null, resultSet.getBoolean("rolled_back"));
                            ((ActionLog)object).setHasMeta(resultSet.getInt("has_meta"));
                            arrayList2.add((ActionLog)object);
                        }
                    }
                }
                finally {
                    if (object3 != null) {
                        object3.close();
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.SEVERE, "searchLogsByAreaKeyset failed", sQLException);
            }
            boolean bl7 = bl3 = arrayList2.size() > n5;
            if (bl3) {
                arrayList2.remove(n5);
            }
            long l4 = 0L;
            int n19 = 0;
            if (!arrayList2.isEmpty()) {
                object22 = (ActionLog)arrayList2.get(arrayList2.size() - 1);
                object = ((ActionLog)object22).getEventTimestamp() != null ? ((ActionLog)object22).getEventTimestamp() : ((ActionLog)object22).getTimestamp();
                l4 = ((Timestamp)object).getTime();
                n19 = ((ActionLog)object22).getId();
            }
            object22 = new CursorResult(arrayList2, bl3, l4, n19);
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> DatabaseMariadb.lambda$searchLogsByAreaKeysetAsync$33(consumer, (CursorResult)object22));
        });
    }

    private String playerFromClause(@Nullable String string) {
        if (DatabaseMariadb.isMsgCmdAction(string)) {
            return "FROM protectcore_player_actions pa FORCE INDEX (idx_player_action_sort_id) ";
        }
        return "FROM protectcore_player_actions pa FORCE INDEX (idx_player_sort) ";
    }

    public void searchLogsPagedByUserAsync(String string, long l, long l2, String string2, String string3, String string4, String string5, String string6, String string7, Set<String> set, boolean bl, boolean bl2, int n, int n2, @Nullable Set<String> set2, @Nullable Set<String> set3, Consumer<SearchResult> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            Object object;
            if (this.dataSource == null || this.dataSource.isClosed()) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(new SearchResult(List.of(), 0, 0L, 0)));
                return;
            }
            FilterResult filterResult = this.buildFilter(string2 == null ? "" : string2, l, l2, string, string3, string4, string5, string6, string7, bl, bl2, set2, set3);
            Object object2 = filterResult.clause;
            ArrayList<Object> arrayList = new ArrayList<Object>(filterResult.params);
            if (set != null && !set.isEmpty()) {
                object2 = (String)object2 + " AND pa.action NOT IN (" + String.join((CharSequence)",", Collections.nCopies(set.size(), "?")) + ") ";
                arrayList.addAll(set);
            }
            int n3 = Math.max(1, Math.min(200, n));
            int n4 = Math.max(1, n2);
            int n5 = (n4 - 1) * n3;
            long l3 = 0L;
            String string8 = this.playerFromClause(string7);
            String string9 = "SELECT COUNT(*) " + string8 + (String)object2;
            String string10 = "    SELECT\n      pa.id, pa.playerUUID, pa.playerName, pa.action, pa.details,\n      pa.world, pa.x, pa.y, pa.z, pa.`timestamp`, pa.event_ts, pa.rolled_back,\n      CASE\n        WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN 0\n        WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN 0\n        WHEN JSON_VALID(pa.metadata) = 0 THEN 0\n        WHEN (\n          JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n          OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n        ) THEN 1 ELSE 0\n      END AS has_meta,\n      CASE\n        WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN NULL\n        WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN NULL\n        WHEN JSON_VALID(pa.metadata) = 0 THEN NULL\n        WHEN (\n          JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n          OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n          OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n        ) THEN pa.metadata\n        ELSE NULL\n      END AS metadata_json\n" + string8 + (String)object2 + " ORDER BY pa.sort_ts DESC, pa.id DESC LIMIT ? OFFSET ?";
            ArrayList<ActionLog> arrayList2 = new ArrayList<ActionLog>();
            try (Connection connection = this.dataSource.getConnection();){
                try (PreparedStatement preparedStatement = connection.prepareStatement(string9);){
                    for (int i = 0; i < arrayList.size(); ++i) {
                        preparedStatement.setObject(i + 1, arrayList.get(i));
                    }
                    try (ResultSet resultSet = preparedStatement.executeQuery();){
                        if (resultSet.next()) {
                            l3 = resultSet.getLong(1);
                        }
                    }
                }
                preparedStatement = connection.prepareStatement(string10);
                try {
                    int n6;
                    for (n6 = 1; n6 <= arrayList.size(); ++n6) {
                        preparedStatement.setObject(n6, arrayList.get(n6 - 1));
                    }
                    preparedStatement.setInt(n6++, n3);
                    preparedStatement.setInt(n6, n5);
                    try (ResultSet resultSet = preparedStatement.executeQuery();){
                        while (resultSet.next()) {
                            object = resultSet.getString("metadata_json");
                            ActionLog actionLog = new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), (String)object, resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), null, null, resultSet.getBoolean("rolled_back"));
                            actionLog.setHasMeta(resultSet.getInt("has_meta"));
                            arrayList2.add(actionLog);
                        }
                    }
                }
                finally {
                    if (preparedStatement != null) {
                        preparedStatement.close();
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.SEVERE, "searchLogsPagedByUser failed", sQLException);
            }
            int n7 = (int)Math.ceil((double)l3 / (double)n3);
            long l4 = arrayList2.isEmpty() ? 0L : (((ActionLog)arrayList2.get(arrayList2.size() - 1)).getEventTimestamp() != null ? ((ActionLog)arrayList2.get(arrayList2.size() - 1)).getEventTimestamp().getTime() : ((ActionLog)arrayList2.get(arrayList2.size() - 1)).getTimestamp().getTime());
            int n8 = arrayList2.isEmpty() ? 0 : ((ActionLog)arrayList2.get(arrayList2.size() - 1)).getId();
            object = new SearchResult(arrayList2, n7, l4, n8);
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> DatabaseMariadb.lambda$searchLogsPagedByUserAsync$36(consumer, (SearchResult)object));
        });
    }

    public void searchLogsPagedByAreaAsync(String string, int n, int n2, int n3, int n4, long l, long l2, @Nullable String string2, String string3, String string4, String string5, String string6, String string7, String string8, Set<String> set, boolean bl, boolean bl2, int n5, int n6, @Nullable Set<String> set2, @Nullable Set<String> set3, Consumer<SearchResult> consumer) {
        if (this.isStoppingNow()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            Object object;
            Object object2;
            String string92;
            if (this.dataSource == null || this.dataSource.isClosed()) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(new SearchResult(List.of(), 0, 0L, 0)));
                return;
            }
            int n7 = n - n4;
            int n8 = n + n4;
            int n9 = n2 - n4;
            int n10 = n2 + n4;
            int n11 = n3 - n4;
            int n12 = n3 + n4;
            int n13 = (int)Math.floor((double)(n - n4) / 16.0);
            int n14 = (int)Math.floor((double)(n + n4) / 16.0);
            int n15 = (int)Math.floor((double)(n3 - n4) / 16.0);
            int n16 = (int)Math.floor((double)(n3 + n4) / 16.0);
            ArrayList<Object> arrayList = new ArrayList<Object>();
            StringBuilder stringBuilder = new StringBuilder(" WHERE pa.world = ? ");
            arrayList.add(string);
            if (this.hasCx && this.hasCz) {
                stringBuilder.append(" AND pa.cx BETWEEN ? AND ? AND pa.cz BETWEEN ? AND ? ");
                arrayList.add(n13);
                arrayList.add(n14);
                arrayList.add(n15);
                arrayList.add(n16);
            }
            stringBuilder.append(" AND pa.x BETWEEN ? AND ? AND pa.y BETWEEN ? AND ? AND pa.z BETWEEN ? AND ? ");
            arrayList.add(n7);
            arrayList.add(n8);
            arrayList.add(n9);
            arrayList.add(n10);
            arrayList.add(n11);
            arrayList.add(n12);
            Set<String> set4 = DatabaseMariadb.expandMaterialKeys(set2);
            Set<String> set5 = DatabaseMariadb.expandMaterialKeys(set3);
            Set<String> set6 = DatabaseMariadb.expandEntityKeys(set2);
            Set<String> set7 = DatabaseMariadb.expandEntityKeys(set3);
            if (string2 == null || string2.isBlank()) {
                stringBuilder.append(" AND pa.playerUUID <> ? ");
                arrayList.add(SYSTEM_UUID_STR);
            } else {
                stringBuilder.append(" AND pa.playerUUID = ? ");
                arrayList.add(string2);
            }
            if (l > 0L) {
                stringBuilder.append(" AND pa.sort_ts >= ? ");
                arrayList.add(new Timestamp(l * 1000L));
            }
            if (l2 != Long.MAX_VALUE) {
                stringBuilder.append(" AND pa.sort_ts <= ? ");
                arrayList.add(new Timestamp(l2 * 1000L));
            }
            this.appendTextQueryFilter(stringBuilder, arrayList, string3, string8);
            if (string4 != null && !string4.isBlank()) {
                String[] stringArray = string4.toLowerCase(Locale.ROOT);
                stringBuilder.append(" AND (pa.material_vc = ? OR LOWER(pa.details) LIKE ? OR LOWER(pa.details) LIKE ?) ");
                arrayList.add(stringArray);
                String[] object3 = stringArray.startsWith("minecraft:") ? stringArray.substring(10) : stringArray;
                arrayList.add("%" + (String)stringArray + "%");
                arrayList.add("%" + (String)object3 + "%");
            }
            this.appendNameFilter(stringBuilder, arrayList, string5);
            this.appendLoreFilter(stringBuilder, arrayList, string6);
            if (string7 != null && !string7.isBlank()) {
                for (String string92 : string7.split(",")) {
                    object2 = string92.trim();
                    if (((String)object2).isEmpty()) continue;
                    stringBuilder.append(" AND pa.metadata LIKE ? ");
                    arrayList.add("%" + (String)object2 + "%");
                }
            }
            if (string8 != null && !string8.isBlank()) {
                stringBuilder.append(" AND pa.action = ? ");
                arrayList.add(string8);
            }
            if (set != null && !set.isEmpty()) {
                stringBuilder.append(" AND pa.action NOT IN (" + String.join((CharSequence)",", Collections.nCopies(set.size(), "?")) + ") ");
                arrayList.addAll(set);
            }
            if (bl) {
                stringBuilder.append(" AND pa.rolled_back = 0 ");
            }
            if (bl2) {
                stringBuilder.append(" AND COALESCE(pa.origin_vc,'') <> 'worldedit' ");
            }
            if (!set4.isEmpty() || !set6.isEmpty()) {
                stringBuilder.append(" AND (");
                boolean bl3 = false;
                if (!set4.isEmpty()) {
                    stringBuilder.append(" (pa.material_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set4.size(), "?"))).append(") ");
                    arrayList.addAll(set4);
                    stringBuilder.append(" OR (pa.material_vc IS NULL AND (");
                    boolean bl4 = true;
                    for (String string92 : set4) {
                        boolean n18;
                        if (!n18) {
                            stringBuilder.append(" OR ");
                        }
                        n18 = false;
                        object2 = string92.contains(":") ? string92.substring(string92.indexOf(58) + 1) : string92;
                        stringBuilder.append(" LOWER(pa.details) LIKE ? ");
                        arrayList.add("%" + (String)object2 + "%");
                    }
                    stringBuilder.append("))) ");
                    bl3 = true;
                }
                if (!set6.isEmpty()) {
                    if (bl3) {
                        stringBuilder.append(" OR ");
                    }
                    stringBuilder.append(" pa.entity_vc IN (").append(String.join((CharSequence)",", Collections.nCopies(set6.size(), "?"))).append(") ");
                    arrayList.addAll(set6);
                }
                stringBuilder.append(") ");
            }
            if (!set5.isEmpty() || !set7.isEmpty()) {
                stringBuilder.append(" AND (");
                if (!set5.isEmpty()) {
                    stringBuilder.append(" ( (pa.material_vc IS NOT NULL AND pa.material_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set5.size(), "?"))).append(")) ");
                    arrayList.addAll(set5);
                    stringBuilder.append(" OR (pa.material_vc IS NULL");
                    for (String string10 : set5) {
                        String string11 = string10.contains(":") ? string10.substring(string10.indexOf(58) + 1) : string10;
                        stringBuilder.append(" AND LOWER(pa.details) NOT LIKE ? ");
                        arrayList.add("%" + string11 + "%");
                    }
                    stringBuilder.append(") ) ");
                } else {
                    stringBuilder.append(" 1=1 ");
                }
                stringBuilder.append(" AND ");
                if (!set7.isEmpty()) {
                    stringBuilder.append(" (pa.entity_vc IS NULL OR pa.entity_vc NOT IN (").append(String.join((CharSequence)",", Collections.nCopies(set7.size(), "?"))).append(")) ");
                    arrayList.addAll(set7);
                } else {
                    stringBuilder.append(" 1=1 ");
                }
                stringBuilder.append(") ");
            }
            this.appendIgnoredUsernamesFilter(stringBuilder, arrayList, "pa");
            int n17 = Math.max(1, Math.min(200, n5));
            int n18 = Math.max(1, n6);
            int n19 = (n18 - 1) * n17;
            string92 = this.areaFromClause(string8);
            object2 = "SELECT COUNT(*) " + string92 + String.valueOf(stringBuilder);
            String string12 = "  SELECT\n    pa.id, pa.playerUUID, pa.playerName, pa.action, pa.details,\n    pa.world, pa.x, pa.y, pa.z, pa.`timestamp`, pa.event_ts, pa.rolled_back,\n    CASE\n      WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN 0\n      WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN 0\n      WHEN JSON_VALID(pa.metadata) = 0 THEN 0\n      WHEN (\n        JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n        OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n      ) THEN 1 ELSE 0\n    END AS has_meta,\n    CASE\n      WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN NULL\n      WHEN pa.metadata IS NULL OR TRIM(pa.metadata) IN ('', '{}', '[]') THEN NULL\n      WHEN JSON_VALID(pa.metadata) = 0 THEN NULL\n      WHEN (\n        JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.displayName')) IS NOT NULL\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.lore')) = 'ARRAY'\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.enchantments')) IN ('OBJECT','ARRAY')\n        OR JSON_UNQUOTE(JSON_EXTRACT(pa.metadata,'$.item.displayName')) IS NOT NULL\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.lore')) = 'ARRAY'\n        OR JSON_TYPE(JSON_EXTRACT(pa.metadata,'$.item.enchantments')) IN ('OBJECT','ARRAY')\n      ) THEN pa.metadata\n      ELSE NULL\n    END AS metadata_json\n" + string92 + String.valueOf(stringBuilder) + " ORDER BY pa.sort_ts DESC, pa.id DESC LIMIT ? OFFSET ?";
            long l3 = 0L;
            ArrayList<ActionLog> arrayList2 = new ArrayList<ActionLog>();
            try (Connection connection = this.dataSource.getConnection();){
                try (PreparedStatement preparedStatement = connection.prepareStatement((String)object2);){
                    for (int n20 = 0; n20 < arrayList.size(); ++n20) {
                        preparedStatement.setObject(n20 + 1, arrayList.get(n20));
                    }
                    try (ResultSet resultSet = preparedStatement.executeQuery();){
                        if (resultSet.next()) {
                            l3 = resultSet.getLong(1);
                        }
                    }
                }
                preparedStatement = connection.prepareStatement(string12);
                try {
                    int n20;
                    for (n20 = 1; n20 <= arrayList.size(); ++n20) {
                        preparedStatement.setObject(n20, arrayList.get(n20 - 1));
                    }
                    preparedStatement.setInt(n20++, n17);
                    preparedStatement.setInt(n20, n19);
                    try (ResultSet resultSet = preparedStatement.executeQuery();){
                        while (resultSet.next()) {
                            object = resultSet.getString("metadata_json");
                            ActionLog actionLog = new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), (String)object, resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), null, null, resultSet.getBoolean("rolled_back"));
                            actionLog.setHasMeta(resultSet.getInt("has_meta"));
                            arrayList2.add(actionLog);
                        }
                    }
                }
                finally {
                    if (preparedStatement != null) {
                        preparedStatement.close();
                    }
                }
            }
            catch (SQLException sQLException) {
                this.plugin.getLogger().log(Level.SEVERE, "searchLogsPagedByArea failed", sQLException);
            }
            int n21 = (int)Math.ceil((double)l3 / (double)n17);
            long l4 = arrayList2.isEmpty() ? 0L : (((ActionLog)arrayList2.get(arrayList2.size() - 1)).getEventTimestamp() != null ? ((ActionLog)arrayList2.get(arrayList2.size() - 1)).getEventTimestamp().getTime() : ((ActionLog)arrayList2.get(arrayList2.size() - 1)).getTimestamp().getTime());
            int n22 = arrayList2.isEmpty() ? 0 : ((ActionLog)arrayList2.get(arrayList2.size() - 1)).getId();
            object = new SearchResult(arrayList2, n21, l4, n22);
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> DatabaseMariadb.lambda$searchLogsPagedByAreaAsync$39(consumer, (SearchResult)object));
        });
    }

    public SearchResult searchLogsFast(String string, long l, long l2, String string2, String string3, String string4, String string5, String string6, String string7, int n, long l3, int n2, Set<String> set, boolean bl, boolean bl2) {
        CharSequence charSequence;
        n = Math.max(1, Math.min(1000, n));
        FilterResult filterResult = this.buildFilter(string, l, l2, string2, string3, string4, string5, string6, string7, bl, bl2);
        Object object = filterResult.clause;
        ArrayList<Object> arrayList = new ArrayList<Object>(filterResult.params);
        if (set != null && !set.isEmpty()) {
            charSequence = String.join((CharSequence)",", Collections.nCopies(set.size(), "?"));
            object = (String)object + " AND pa.action NOT IN (" + (String)charSequence + ") ";
            arrayList.addAll(new ArrayList<String>(set));
        }
        charSequence = new StringBuilder("SELECT\n  pa.id, pa.playerUUID, pa.playerName, pa.action, pa.details,\n  pa.world, pa.x, pa.y, pa.z, pa.`timestamp`, pa.event_ts, pa.rolled_back,\n  %s AS material_hint,\n  CASE WHEN pa.action IN ('PLAYER_MESSAGE','PLAYER_COMMAND') THEN NULL\n       ELSE pa.metadata END AS metadata_json\nFROM protectcore_player_actions pa\n".formatted(this.hasItemMaterial ? "COALESCE(pa.item_material, pa.material_vc)" : "pa.material_vc")).append((String)object);
        ArrayList<Object> arrayList2 = new ArrayList<Object>(arrayList);
        if (l3 > 0L) {
            ((StringBuilder)charSequence).append(" AND (pa.sort_ts < ? OR (pa.sort_ts = ? AND pa.id < ?)) ");
            Timestamp timestamp = new Timestamp(l3);
            arrayList2.add(timestamp);
            arrayList2.add(timestamp);
            arrayList2.add(n2);
        }
        ((StringBuilder)charSequence).append(" ORDER BY pa.sort_ts DESC, pa.id DESC");
        ((StringBuilder)charSequence).append(" LIMIT ?");
        arrayList2.add(n);
        return DatabaseMariadb.fetchEditorLogs(this.dataSource, ((StringBuilder)charSequence).toString(), arrayList2);
    }

    static SearchResult fetchEditorLogs(HikariDataSource hikariDataSource, String string, List<Object> list) {
        ArrayList<ActionLog> arrayList = new ArrayList<ActionLog>();
        long l = 0L;
        int n = 0;
        try (Object object = DatabaseMariadb.openEditorConnection(hikariDataSource);
             PreparedStatement preparedStatement = object.prepareStatement(string);){
            for (int i = 0; i < list.size(); ++i) {
                preparedStatement.setObject(i + 1, list.get(i));
            }
            try (ResultSet resultSet = preparedStatement.executeQuery();){
                while (resultSet.next()) {
                    boolean bl = resultSet.getBoolean("rolled_back");
                    String string2 = resultSet.getString("metadata_json");
                    EditorItemSummary editorItemSummary = EditorItemSummary.from(resultSet.getString("action"), string2, resultSet.getString("material_hint"));
                    String string3 = editorItemSummary.hasExtra() ? string2 : null;
                    ActionLog actionLog = new ActionLog(resultSet.getInt("id"), UUID.fromString(resultSet.getString("playerUUID")), resultSet.getString("playerName"), resultSet.getString("action"), resultSet.getString("details"), string3, resultSet.getString("world"), resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"), resultSet.getTimestamp("timestamp"), resultSet.getTimestamp("event_ts"), null, null, bl);
                    actionLog.setItemSummary(editorItemSummary);
                    arrayList.add(actionLog);
                }
            }
        }
        if (!arrayList.isEmpty()) {
            object = (ActionLog)arrayList.get(arrayList.size() - 1);
            l = (((ActionLog)object).getEventTimestamp() != null ? ((ActionLog)object).getEventTimestamp() : ((ActionLog)object).getTimestamp()).getTime();
            n = ((ActionLog)object).getId();
        }
        return new SearchResult(arrayList, 0, l, n);
    }

    public EditorTeleportLocation findEditorTeleportLocation(int n, Set<String> set) {
        return DatabaseMariadb.fetchEditorTeleportLocation(this.dataSource, n, set);
    }

    /*
     * Exception decompiling
     */
    static EditorTeleportLocation fetchEditorTeleportLocation(HikariDataSource var0, int var1_1, Set<String> var2_2) {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Started 2 blocks at once
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:531)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
         *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         */
        throw new IllegalStateException("Decompilation failed");
    }

    public Connection openEditorConnection() {
        return DatabaseMariadb.openEditorConnection(this.dataSource);
    }

    static Connection openEditorConnection(HikariDataSource hikariDataSource) {
        if (hikariDataSource == null || hikariDataSource.isClosed()) {
            throw new SQLTransientConnectionException("The editor database is unavailable");
        }
        try {
            return hikariDataSource.getConnection();
        }
        catch (IllegalStateException illegalStateException) {
            throw new SQLTransientConnectionException("The editor database is unavailable", illegalStateException);
        }
    }

    private static /* synthetic */ void lambda$searchLogsPagedByAreaAsync$39(Consumer consumer, SearchResult searchResult) {
        consumer.accept(searchResult);
    }

    private static /* synthetic */ void lambda$searchLogsPagedByUserAsync$36(Consumer consumer, SearchResult searchResult) {
        consumer.accept(searchResult);
    }

    private static /* synthetic */ void lambda$searchLogsByAreaKeysetAsync$33(Consumer consumer, CursorResult cursorResult) {
        consumer.accept(cursorResult);
    }

    private static class LogData {
        final UUID playerUUID;
        final String playerName;
        final String action;
        final String details;
        final String metadata;
        final String source;
        final Long rbId;
        final Integer originId;
        final String fromBlock;
        final String fromData;
        final String toBlock;
        final String toData;
        final String itemMaterial;
        final String itemNameNorm;
        final String itemLoreNorm;
        final String itemSearchText;
        final String itemEnchantsCsv;
        final String worldName;
        final int x;
        final int y;
        final int z;
        final long eventTimeMillis;
        final Integer snapshotId;

        LogData(UUID uUID, String string, String string2, String string3, String string4, Location location, long l, @Nullable Integer n) {
            this(uUID, string, string2, string3, string4, "player", null, null, null, null, null, null, null, null, null, null, null, location, l, n);
        }

        LogData(UUID uUID, String string, String string2, String string3, String string4, @Nullable String string5, @Nullable String string6, @Nullable String string7, @Nullable String string8, @Nullable String string9, Location location, long l, @Nullable Integer n) {
            this(uUID, string, string2, string3, string4, "player", null, null, null, null, null, null, string5, string6, string7, string8, string9, location, l, n);
        }

        LogData(UUID uUID, String string, String string2, String string3, String string4, String string5, @Nullable Long l, @Nullable Integer n, @Nullable String string6, @Nullable String string7, @Nullable String string8, @Nullable String string9, @Nullable String string10, @Nullable String string11, @Nullable String string12, @Nullable String string13, @Nullable String string14, Location location, long l2, @Nullable Integer n2) {
            this.playerUUID = uUID;
            this.playerName = string;
            this.action = string2;
            this.details = string3;
            this.metadata = DatabaseMariadb.normalizeMetadataForStorage(string4);
            this.source = string5 == null ? "player" : string5;
            this.rbId = l;
            this.originId = n;
            this.fromBlock = string6;
            this.fromData = string7;
            this.toBlock = string8;
            this.toData = string9;
            this.itemMaterial = string10;
            this.itemNameNorm = string11;
            this.itemLoreNorm = string12;
            this.itemSearchText = string13;
            this.itemEnchantsCsv = string14;
            World world = location.getWorld();
            this.worldName = world != null ? world.getName() : "unknown_world";
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
            this.eventTimeMillis = l2;
            this.snapshotId = n2;
        }
    }

    public static enum HealthState {
        STARTING,
        CONNECTED,
        DEGRADED,
        MAINTENANCE,
        SHUTTING_DOWN;

    }

    private static class TaskHandle {
        final BukkitTask bukkitTask;
        final Object paperTask;

        TaskHandle(BukkitTask bukkitTask, Object object) {
            this.bukkitTask = bukkitTask;
            this.paperTask = object;
        }

        void cancel() {
            if (this.bukkitTask != null) {
                this.bukkitTask.cancel();
                return;
            }
            if (this.paperTask != null) {
                try {
                    this.paperTask.getClass().getMethod("cancel", new Class[0]).invoke(this.paperTask, new Object[0]);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
    }

    private static final class Anchor {
        final long ts;
        final int id;

        Anchor(long l, int n) {
            this.ts = l;
            this.id = n;
        }
    }

    @FunctionalInterface
    private static interface SqlSetter {
        public void bind(PreparedStatement var1, int var2) throws SQLException;
    }

    private static class FilterResult {
        final String clause;
        final List<Object> params;

        FilterResult(String string, List<Object> list) {
            this.clause = string;
            this.params = list;
        }
    }

    public static class SearchResult {
        public final List<ActionLog> logs;
        public final int totalPages;
        public final long lastTimestamp;
        public final int lastId;

        public SearchResult(List<ActionLog> list, int n, long l, int n2) {
            this.logs = list;
            this.totalPages = n;
            this.lastTimestamp = l;
            this.lastId = n2;
        }
    }

    public record EditorTeleportLocation(String world, int x, int y, int z) {
    }

    public static class CursorResult {
        public final List<ActionLog> logs;
        public final boolean hasNext;
        public final long nextTs;
        public final int nextId;

        public CursorResult(List<ActionLog> list, boolean bl, long l, int n) {
            this.logs = list;
            this.hasNext = bl;
            this.nextTs = l;
            this.nextId = n;
        }
    }

    public record ChunkHit(int cx, int cz, int hits) {
    }

    public static final class WindowedResult {
        public final List<ActionLog> logs;
        public final int page;
        public final int windowStart;
        public final int windowEnd;
        public final boolean hasPrevWindow;
        public final boolean hasNextWindow;

        public WindowedResult(List<ActionLog> list, int n, int n2, int n3, boolean bl, boolean bl2) {
            this.logs = list;
            this.page = n;
            this.windowStart = n2;
            this.windowEnd = n3;
            this.hasPrevWindow = bl;
            this.hasNextWindow = bl2;
        }
    }
}

