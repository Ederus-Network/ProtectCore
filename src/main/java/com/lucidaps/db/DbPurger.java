/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 */
package com.lucidaps.db;

import com.lucidaps.ProtectCore;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.db.PurgeResult;
import com.lucidaps.db.PurgeStatus;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class DbPurger {
    private static final int DEFAULT_PURGE_BATCH = 200;
    private static final int MAX_PURGE_BATCH = 5000;
    private static final int MIN_PURGE_BATCH = 10;
    private static final int MAX_IN_LIST = 1000;
    private static final int STMT_TIMEOUT_SECONDS = 30;
    private static final int JDBC_TIMEOUT_SECONDS = 35;
    private static final long RUN_BUDGET_NS = TimeUnit.MILLISECONDS.toNanos(200L);
    private static final long YIELD_THRESHOLD_MS = 80L;
    private static final double OPTIMIZE_HEADROOM = 1.25;
    private static final long OPTIMIZE_MIN_FREE_REMAIN = 0x80000000L;
    private static final long EMERGENCY_STOP_FREE = 0x20000000L;
    private final ProtectCore plugin;
    private final HikariDataSource dataSource;
    private final DatabaseMariadb db;
    private volatile int configuredBatchSize;
    private volatile boolean optimizeAfterPurge;
    private volatile double optimizeThresholdPercent;
    private volatile long optimizeMinSizeBytes;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicReference<PurgeStatus> statusRef = new AtomicReference<PurgeStatus>(PurgeStatus.idle());
    private static final int RETRY = -1;

    private static int clampChunk(int n) {
        return Math.min(1000, Math.max(10, Math.min(5000, n)));
    }

    public DbPurger(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.db = databaseMariadb;
        this.dataSource = databaseMariadb.getDataSource();
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        this.configuredBatchSize = this.plugin.getConfig().getInt("database.retention.batchSize", 200);
        this.optimizeAfterPurge = this.plugin.getConfig().getBoolean("database.maintenance.optimizeAfterPurge", false);
        this.optimizeThresholdPercent = Math.max(0.0, this.plugin.getConfig().getDouble("database.maintenance.optimizeThresholdPercent", 20.0));
        this.optimizeMinSizeBytes = Math.max(0L, this.plugin.getConfig().getLong("database.maintenance.optimizeMinSizeBytes", 0x20000000L));
    }

    public PurgeStatus getStatus() {
        return this.statusRef.get();
    }

    public boolean requestStop() {
        if (!this.running.get()) {
            return false;
        }
        return this.cancelRequested.compareAndSet(false, true);
    }

    private static String safeGet(ResultSet resultSet, String string) {
        try {
            return resultSet.getString(string);
        }
        catch (SQLException sQLException) {
            return "";
        }
    }

    private static TableSpace queryTableSpace(Connection connection, String string) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("    SELECT COALESCE(data_length,0) + COALESCE(index_length,0) AS total,\n           COALESCE(data_free,0) AS free_inside\n    FROM information_schema.tables\n    WHERE table_schema = DATABASE() AND table_name = ?\n");){
            TableSpace tableSpace;
            block16: {
                ResultSet resultSet;
                block14: {
                    TableSpace tableSpace2;
                    block15: {
                        preparedStatement.setString(1, string);
                        resultSet = preparedStatement.executeQuery();
                        try {
                            if (resultSet.next()) break block14;
                            tableSpace2 = new TableSpace(0L, 0L);
                            if (resultSet == null) break block15;
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
                    return tableSpace2;
                }
                tableSpace = new TableSpace(resultSet.getLong("total"), resultSet.getLong("free_inside"));
                if (resultSet == null) break block16;
                resultSet.close();
            }
            return tableSpace;
        }
    }

    private static String queryVar(Connection connection, String string) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SHOW VARIABLES LIKE ?");){
            String string2;
            block13: {
                preparedStatement.setString(1, string);
                ResultSet resultSet = preparedStatement.executeQuery();
                try {
                    if (!resultSet.next()) {
                        throw new SQLException("Could not read var: " + string);
                    }
                    string2 = resultSet.getString("Value");
                    if (resultSet == null) break block13;
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
            return string2;
        }
    }

    private static long usableBytesAtPath(String string) {
        Path path = Paths.get(string, new String[0]);
        return Files.getFileStore(path).getUsableSpace();
    }

    private static long usableBytesAtTmpdir(String string) {
        long l = Long.MAX_VALUE;
        boolean bl = false;
        for (String string2 : string.split(",")) {
            Path path;
            String string3 = string2.trim();
            if (string3.isEmpty() || !Files.exists(path = Paths.get(string3, new String[0]), new LinkOption[0])) continue;
            long l2 = Files.getFileStore(path).getUsableSpace();
            l = Math.min(l, l2);
            bl = true;
        }
        return bl ? l : Long.MAX_VALUE;
    }

    private static String bigWarn(String string) {
        return "\u00a7c\u00a7l" + string;
    }

    private static String bigInfo(String string) {
        return "\u00a7e\u00a7l" + string;
    }

    private static long estimateOptimizeNeedBytes(long l) {
        long l2 = (long)Math.ceil((double)l * 1.25);
        long l3 = 0x20000000L;
        return l2 + l3;
    }

    private static String human(long l) {
        int n;
        double d = l;
        String[] stringArray = new String[]{"B", "KB", "MB", "GB", "TB", "PB"};
        for (n = 0; d >= 1024.0 && n < stringArray.length - 1; d /= 1024.0, ++n) {
        }
        return String.format(Locale.ROOT, "%.2f%s", d, stringArray[n]);
    }

    private static void runOptimize(Statement statement, String string, Consumer<String> consumer) {
        String string2 = "OPTIMIZE TABLE " + string;
        boolean bl = statement.execute(string2);
        do {
            if (bl) {
                try (ResultSet resultSet = statement.getResultSet();){
                    while (resultSet != null && resultSet.next()) {
                        String string3 = DbPurger.safeGet(resultSet, "Msg_type");
                        String string4 = DbPurger.safeGet(resultSet, "Msg_text");
                        consumer.accept("\u00a77OPTIMIZE: \u00a7f" + string3 + " \u00a77" + string4);
                    }
                }
            }
            int n = statement.getUpdateCount();
            if (n == -1) break;
        } while ((bl = statement.getMoreResults()) || statement.getUpdateCount() != -1);
    }

    public CompletableFuture<PurgeResult> purgeKeepDaysAsync(int n, @Nullable String string2, @Nullable Consumer<String> consumer) {
        if (this.db.getHealthState() != DatabaseMariadb.HealthState.CONNECTED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database is not connected (" + String.valueOf((Object)this.db.getHealthState()) + ")."));
        }
        if (n < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("keepDays must be >= 0"));
        }
        if (!this.running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A purge is already running."));
        }
        this.cancelRequested.set(false);
        boolean bl = true;
        boolean bl2 = false;
        boolean bl3 = true;
        boolean bl4 = false;
        int n2 = this.configuredBatchSize;
        int n3 = DbPurger.clampChunk(n2);
        boolean bl5 = true;
        this.db.setMaintenanceMode(true);
        boolean bl6 = n == 0;
        long l = bl6 ? 0L : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n);
        this.statusRef.set(PurgeStatus.idle().markStart(n, l, n3, true, string2));
        CompletableFuture<PurgeResult> completableFuture = this.purgeOldDataAsync(n, bl, bl2, bl3, bl4, n2, string -> {
            this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withMessage(DbPurger.stripColorIfYouWant(string)));
            if (consumer != null) {
                consumer.accept((String)string);
            }
        });
        return completableFuture.whenComplete((purgeResult, throwable) -> {
            try {
                if (throwable != null) {
                    if (throwable instanceof CancellationException) {
                        this.statusRef.updateAndGet(PurgeStatus::markCancelled);
                    } else {
                        this.statusRef.updateAndGet(purgeStatus -> purgeStatus.markFailed(throwable.getMessage() == null ? throwable.toString() : throwable.getMessage()));
                    }
                } else {
                    this.statusRef.updateAndGet(PurgeStatus::markDone);
                }
            }
            finally {
                this.db.setMaintenanceMode(false);
                this.running.set(false);
                this.cancelRequested.set(false);
            }
        });
    }

    public CompletableFuture<PurgeResult> planKeepDaysAsync(int n, @Nullable Consumer<String> consumer) {
        if (this.db.getHealthState() != DatabaseMariadb.HealthState.CONNECTED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database is not connected (" + String.valueOf((Object)this.db.getHealthState()) + ")."));
        }
        if (n <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Plan days must be greater than zero."));
        }
        return this.purgeOldDataAsync(n, true, false, true, true, this.configuredBatchSize, consumer);
    }

    private static boolean isLockPressure(SQLException sQLException) {
        if (sQLException instanceof SQLTimeoutException) {
            return true;
        }
        String string = sQLException.getSQLState();
        if ("70100".equals(string)) {
            return true;
        }
        int n = sQLException.getErrorCode();
        return n == 1206 || n == 1205 || n == 1213 || n == 1969;
    }

    private int runDeleteAdaptive(AtomicInteger atomicInteger, SqlDeleteOp sqlDeleteOp, Consumer<String> consumer, String string) {
        int n = atomicInteger.get();
        long l = System.currentTimeMillis();
        try {
            int n2 = sqlDeleteOp.run(n);
            long l2 = System.currentTimeMillis() - l;
            if (l2 > 1500L) {
                consumer.accept("\u00a77" + string + " chunk " + n + " took \u00a7f" + l2 + "ms");
            }
            return n2;
        }
        catch (SQLException sQLException) {
            if (DbPurger.isLockPressure(sQLException)) {
                int n3 = atomicInteger.get();
                int n4 = Math.max(10, n3 / 2);
                if ((n4 = Math.min(n4, 1000)) < n3) {
                    atomicInteger.set(n4);
                    consumer.accept("\u00a7eDB lock pressure (" + sQLException.getErrorCode() + "). Reducing chunk \u00a7f" + n3 + "\u00a7e \u2192 \u00a7f" + n4 + "\u00a7e and retrying\u2026");
                } else {
                    consumer.accept("\u00a7eDB lock pressure (" + sQLException.getErrorCode() + "). At min chunk (\u00a7f" + n3 + "\u00a7e). Backing off and retrying\u2026");
                }
                return -1;
            }
            throw sQLException;
        }
    }

    private static boolean isDiskFull(SQLException sQLException) {
        int n = sQLException.getErrorCode();
        String string = String.valueOf(sQLException.getMessage()).toLowerCase(Locale.ROOT);
        if (n == 1021 || n == 1114) {
            return true;
        }
        if (string.contains("errno 28") || string.contains("no space left on device")) {
            return true;
        }
        return string.contains("disk is full") || string.contains("table is full");
    }

    public CompletableFuture<PurgeResult> purgeOldDataAsync(int n, final boolean bl, final boolean bl2, final boolean bl3, boolean bl4, int n2, @Nullable Consumer<String> consumer) {
        boolean bl5;
        final Consumer<String> consumer2 = consumer == null ? string -> {} : consumer;
        final CompletableFuture<PurgeResult> completableFuture = new CompletableFuture<PurgeResult>();
        if (this.dataSource == null || this.dataSource.isClosed()) {
            completableFuture.completeExceptionally(new IllegalStateException("Datasource is not available."));
            return completableFuture;
        }
        int n3 = DbPurger.clampChunk(n2);
        final AtomicInteger atomicInteger = new AtomicInteger(n3);
        final long l = System.currentTimeMillis();
        boolean bl6 = bl5 = n <= 0;
        if (n == 0) {
            this.runNowAsync(() -> {
                try {
                    this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.RESETTING_SCHEMA));
                    this.db.quiesceForSchemaReset(2000L);
                    try (Connection connection = this.dataSource.getConnection();){
                        this.resetAllPluginData(connection, consumer2);
                    }
                    finally {
                        this.db.endHardResetDropMode();
                    }
                    completableFuture.complete(new PurgeResult(false, 0L, 0L, 0L, 0L, System.currentTimeMillis() - l));
                }
                catch (SQLException sQLException) {
                    completableFuture.completeExceptionally(sQLException);
                }
            });
            return completableFuture;
        }
        final long l2 = bl5 ? 0L : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n);
        final Timestamp timestamp = bl5 ? null : new Timestamp(l2);
        consumer2.accept("\u00a7ePurge starting\u2026 " + (String)(bl5 ? "\u00a77(cutoff: ALL TIME)" : "\u00a77(cutoff: " + String.valueOf(new Date(l2)) + ")"));
        consumer2.accept("\u00a77Batch size: \u00a7f" + n3 + " \u00a77| Dry-run: \u00a7f" + bl4);
        if (bl4) {
            this.runNowAsync(() -> {
                try (Connection connection = this.dataSource.getConnection();){
                    consumer2.accept("\u00a7eCounting eligible rows\u2026");
                    long l3 = bl ? this.countActions(connection, timestamp) : 0L;
                    long l4 = bl2 ? this.countSnapshotsByAge(connection, timestamp) : 0L;
                    long l5 = bl3 ? this.countOrphanSnapshots(connection) : 0L;
                    completableFuture.complete(new PurgeResult(true, l2, l3, l4, l5, System.currentTimeMillis() - l));
                }
                catch (SQLException sQLException) {
                    completableFuture.completeExceptionally(sQLException);
                }
            });
            return completableFuture;
        }
        final AtomicLong atomicLong = new AtomicLong();
        final AtomicLong atomicLong2 = new AtomicLong();
        final AtomicLong atomicLong3 = new AtomicLong();
        final class Worker
        implements Runnable {
            int stage = 0;
            long lastProgressMs = 0L;
            int retriesAtMin = 0;
            long backoffMs = 250L;
            static final int MAX_RETRIES_AT_MIN = 20;
            long lastDiskCheckMs = 0L;

            Worker() {
            }

            void onSuccess() {
                this.retriesAtMin = 0;
                this.backoffMs = 250L;
            }

            boolean onRetry() {
                if (atomicInteger.get() <= 10) {
                    ++this.retriesAtMin;
                    return this.retriesAtMin < 20;
                }
                this.backoffMs = Math.min(5000L, (long)((double)this.backoffMs * 1.5));
                return true;
            }

            void scheduleBackoff() {
                long l3 = Math.max(1L, (this.backoffMs + 49L) / 50L);
                DbPurger.this.runLaterAsync(this, l3);
            }

            void progressThrottled(String string) {
                long l3 = System.currentTimeMillis();
                if (l3 - this.lastProgressMs >= 1000L) {
                    this.lastProgressMs = l3;
                    consumer2.accept(string);
                }
            }

            boolean checkCancel() {
                if (!DbPurger.this.cancelRequested.get()) {
                    return false;
                }
                consumer2.accept("\u00a7cPurge cancel requested. Stopping after current operation\u2026");
                completableFuture.completeExceptionally(new CancellationException("Cancelled by admin"));
                return true;
            }

            private boolean emergencyDiskCheck(Connection connection) {
                long l3 = System.currentTimeMillis();
                if (l3 - this.lastDiskCheckMs < 10000L) {
                    return false;
                }
                this.lastDiskCheckMs = l3;
                try {
                    String string = DbPurger.queryVar(connection, "datadir");
                    long l22 = DbPurger.usableBytesAtPath(string);
                    if (l22 < 0x20000000L) {
                        completableFuture.completeExceptionally(new IllegalStateException("Disk critically low in datadir: " + DbPurger.human(l22) + " free. Aborting to prevent corruption."));
                        return true;
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
                return false;
            }

            @Override
            public void run() {
                if (completableFuture.isDone()) {
                    return;
                }
                try {
                    if (DbPurger.isServerStopping() || DbPurger.this.dataSource == null || DbPurger.this.dataSource.isClosed()) {
                        completableFuture.completeExceptionally(new IllegalStateException("Server is stopping / DB closed."));
                        return;
                    }
                    if (this.checkCancel()) {
                        return;
                    }
                    long l13 = System.nanoTime();
                    try (Connection connection = DbPurger.this.dataSource.getConnection();){
                        connection.setAutoCommit(true);
                        connection.setTransactionIsolation(2);
                        try (Statement statement = connection.createStatement();){
                            statement.execute("SET SESSION innodb_lock_wait_timeout=300");
                            statement.execute("SET SESSION lock_wait_timeout=300");
                        }
                        while (System.nanoTime() - l13 < RUN_BUDGET_NS) {
                            long l22;
                            int n2;
                            this.progressThrottled("\u00a77Working\u2026 stage=" + this.stage + " chunk=" + atomicInteger.get());
                            if (this.checkCancel()) {
                                return;
                            }
                            if (this.stage == 0) {
                                DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.DELETING_ACTIONS));
                                if (!bl) {
                                    this.stage = 1;
                                    this.onSuccess();
                                    continue;
                                }
                                if (this.emergencyDiskCheck(connection)) {
                                    return;
                                }
                                int n3 = atomicInteger.get();
                                if (atomicLong.get() == 0L) {
                                    consumer2.accept("\u00a7eDeleting actions\u2026 \u00a77(chunk=" + n3 + ")");
                                }
                                n2 = DbPurger.this.runDeleteAdaptive(atomicInteger, n -> DbPurger.this.deleteActionsChunk(connection, timestamp, n), consumer2, "Actions delete");
                                if (this.emergencyDiskCheck(connection)) {
                                    return;
                                }
                                if (n2 == -1) {
                                    if (!this.onRetry()) {
                                        completableFuture.completeExceptionally(new SQLException("Purge cannot progress: repeated lock/timeout at min chunk=10"));
                                        return;
                                    }
                                    this.scheduleBackoff();
                                    return;
                                }
                                if (n2 > 0) {
                                    this.onSuccess();
                                    l22 = atomicLong.addAndGet(n2);
                                    DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withCounts(l22, atomicLong2.get(), atomicLong3.get()));
                                    this.progressThrottled("\u00a77Actions deleted so far: \u00a7f" + l22 + " \u00a77(chunk=" + atomicInteger.get() + ")");
                                    continue;
                                }
                                this.onSuccess();
                                consumer2.accept("\u00a7aDeleting actions done. \u00a77Total: \u00a7f" + atomicLong.get());
                                this.stage = 1;
                                continue;
                            }
                            if (this.stage == 1) {
                                DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.DELETING_SNAPSHOTS_BY_AGE));
                                if (!bl2) {
                                    this.stage = 2;
                                    this.onSuccess();
                                    continue;
                                }
                                if (this.emergencyDiskCheck(connection)) {
                                    return;
                                }
                                int n4 = atomicInteger.get();
                                if (atomicLong2.get() == 0L) {
                                    consumer2.accept("\u00a7eDeleting old snapshots (by age)\u2026 \u00a77(chunk=" + n4 + ")");
                                }
                                n2 = DbPurger.this.runDeleteAdaptive(atomicInteger, n -> DbPurger.this.deleteSnapshotsByAgeChunk(connection, timestamp, n), consumer2, "Snapshots-by-age delete");
                                if (this.emergencyDiskCheck(connection)) {
                                    return;
                                }
                                if (n2 == -1) {
                                    if (!this.onRetry()) {
                                        completableFuture.completeExceptionally(new SQLException("Purge cannot progress: repeated lock/timeout at min chunk=10"));
                                        return;
                                    }
                                    this.scheduleBackoff();
                                    return;
                                }
                                if (n2 > 0) {
                                    this.onSuccess();
                                    l22 = atomicLong2.addAndGet(n2);
                                    DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withCounts(atomicLong.get(), l22, atomicLong3.get()));
                                    this.progressThrottled("\u00a77Snapshots-by-age deleted so far: \u00a7f" + l22 + " \u00a77(chunk=" + atomicInteger.get() + ")");
                                    continue;
                                }
                                this.onSuccess();
                                consumer2.accept("\u00a7aDeleting snapshots-by-age done. \u00a77Total: \u00a7f" + atomicLong2.get());
                                this.stage = 2;
                                continue;
                            }
                            if (this.stage == 2) {
                                DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.DELETING_ORPHAN_SNAPSHOTS));
                                if (!bl3) {
                                    this.stage = 3;
                                    this.onSuccess();
                                    continue;
                                }
                                if (this.emergencyDiskCheck(connection)) {
                                    return;
                                }
                                int n5 = atomicInteger.get();
                                if (atomicLong3.get() == 0L) {
                                    consumer2.accept("\u00a7eDeleting orphan snapshots\u2026 \u00a77(chunk=" + n5 + ")");
                                }
                                n2 = DbPurger.this.runDeleteAdaptive(atomicInteger, n -> DbPurger.this.deleteOrphanSnapshotsChunk(connection, n), consumer2, "Orphan snapshots delete");
                                if (this.emergencyDiskCheck(connection)) {
                                    return;
                                }
                                if (n2 == -1) {
                                    if (!this.onRetry()) {
                                        completableFuture.completeExceptionally(new SQLException("Purge cannot progress: repeated lock/timeout at min chunk=10"));
                                        return;
                                    }
                                    this.scheduleBackoff();
                                    return;
                                }
                                if (n2 > 0) {
                                    this.onSuccess();
                                    l22 = atomicLong3.addAndGet(n2);
                                    DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withCounts(atomicLong.get(), atomicLong2.get(), l22));
                                    this.progressThrottled("\u00a77Orphan snapshots deleted so far: \u00a7f" + l22 + " \u00a77(chunk=" + atomicInteger.get() + ")");
                                    continue;
                                }
                                this.onSuccess();
                                consumer2.accept("\u00a7aDeleting orphan snapshots done. \u00a77Total: \u00a7f" + atomicLong3.get());
                                this.stage = 3;
                                continue;
                            }
                            if (this.stage == 3) {
                                boolean bl4;
                                long l3 = atomicLong.get();
                                l22 = atomicLong2.get() + atomicLong3.get();
                                if (!DbPurger.this.optimizeAfterPurge) {
                                    consumer2.accept("\u00a77Automatic OPTIMIZE is disabled by configuration.");
                                    this.stage = 4;
                                    continue;
                                }
                                if (l3 + l22 <= 0L) {
                                    this.onSuccess();
                                    consumer2.accept("\u00a77No deletions performed; skipping optimization.");
                                    this.stage = 4;
                                    continue;
                                }
                                TableSpace tableSpace = DbPurger.queryTableSpace(connection, "protectcore_player_actions");
                                TableSpace tableSpace2 = DbPurger.queryTableSpace(connection, "protectcore_container_snapshots");
                                long l4 = DbPurger.estimateOptimizeNeedBytes(tableSpace.usedBytes);
                                long l5 = DbPurger.estimateOptimizeNeedBytes(tableSpace2.usedBytes);
                                long l6 = Math.max(l4, l5);
                                long l7 = l6 + 0x80000000L;
                                double d = tableSpace.totalBytes == 0L ? 0.0 : (double)tableSpace.freeInside * 100.0 / (double)tableSpace.totalBytes;
                                double d2 = tableSpace2.totalBytes == 0L ? 0.0 : (double)tableSpace2.freeInside * 100.0 / (double)tableSpace2.totalBytes;
                                consumer2.accept(DbPurger.bigInfo("ACTIONS TABLE SIZE: ") + "\u00a7f\u00a7l" + DbPurger.human(tableSpace.totalBytes) + " \u00a77(rebuild\u2248" + DbPurger.human(tableSpace.totalBytes) + ", reclaimable\u2248" + DbPurger.human(tableSpace.freeInside) + ", file\u2248" + DbPurger.human(tableSpace.approxFileBytes()) + ", reclaim\u2248" + String.format(Locale.ROOT, "%.2f", d) + "%)");
                                consumer2.accept(DbPurger.bigInfo("SNAPSHOTS TABLE SIZE: ") + "\u00a7f\u00a7l" + DbPurger.human(tableSpace2.totalBytes) + " \u00a77(rebuild\u2248" + DbPurger.human(tableSpace2.totalBytes) + ", reclaimable\u2248" + DbPurger.human(tableSpace2.freeInside) + ", file\u2248" + DbPurger.human(tableSpace2.approxFileBytes()) + ", reclaim\u2248" + String.format(Locale.ROOT, "%.2f", d2) + "%)");
                                consumer2.accept(DbPurger.bigWarn("OPTIMIZE REQUIREMENT: ") + "\u00a7f\u00a7lNeed about " + DbPurger.human(l6) + "\u00a7c\u00a7l TEMP space + keep-free " + DbPurger.human(0x80000000L) + " \u00a77(so target free \u2265 " + DbPurger.human(l7) + ")");
                                String string = null;
                                String string2 = null;
                                Long l8 = null;
                                Long l9 = null;
                                try {
                                    string = DbPurger.queryVar(connection, "datadir");
                                    l8 = DbPurger.usableBytesAtPath(string);
                                }
                                catch (Exception exception) {
                                    // empty catch block
                                }
                                try {
                                    string2 = DbPurger.queryVar(connection, "tmpdir");
                                    l9 = DbPurger.usableBytesAtTmpdir(string2);
                                }
                                catch (Exception exception) {
                                    // empty catch block
                                }
                                if (l8 == null) {
                                    consumer2.accept(DbPurger.bigWarn("DISK CHECK UNAVAILABLE (container/remote DB). OPTIMIZE SKIPPED."));
                                    consumer2.accept("\u00a77To reclaim disk, run OPTIMIZE manually once you confirm free space \u2265 \u00a7f" + DbPurger.human(l7) + "\u00a77.");
                                    this.stage = 4;
                                    continue;
                                }
                                long l10 = l9 == null ? Long.MAX_VALUE : l9;
                                long l11 = Math.min(l8, l10);
                                consumer2.accept("\u00a77DB datadir free: \u00a7f" + DbPurger.human(l8) + " \u00a77(datadir=" + string + ")");
                                if (string2 != null && l9 != null && l9 != Long.MAX_VALUE) {
                                    consumer2.accept("\u00a77DB tmpdir free: \u00a7f" + DbPurger.human(l9) + " \u00a77(tmpdir=" + string2 + ")");
                                }
                                if (l11 < l7) {
                                    consumer2.accept(DbPurger.bigWarn("NOT ENOUGH DISK FOR SAFE OPTIMIZE. OPTIMIZE SKIPPED."));
                                    consumer2.accept("\u00a77Free \u00a7f" + DbPurger.human(l11) + "\u00a77, need \u2265 \u00a7f" + DbPurger.human(l7) + "\u00a77.");
                                    this.stage = 4;
                                    continue;
                                }
                                boolean bl22 = tableSpace.totalBytes >= DbPurger.this.optimizeMinSizeBytes;
                                boolean bl32 = bl4 = tableSpace2.totalBytes >= DbPurger.this.optimizeMinSizeBytes;
                                if (!(bl22 && !(d < DbPurger.this.optimizeThresholdPercent) || bl4 && !(d2 < DbPurger.this.optimizeThresholdPercent))) {
                                    consumer2.accept("\u00a77Skipping OPTIMIZE: tables are below the configured size/fragmentation thresholds.");
                                    this.stage = 4;
                                    continue;
                                }
                                try (Statement statement = connection.createStatement();){
                                    statement.setQueryTimeout(0);
                                    if (l3 > 0L && bl22 && d >= DbPurger.this.optimizeThresholdPercent) {
                                        DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.OPTIMIZING_ACTIONS));
                                        consumer2.accept("\u00a7eRebuilding actions table (OPTIMIZE)\u2026");
                                        DbPurger.runOptimize(statement, "protectcore_player_actions", consumer2);
                                        consumer2.accept("\u00a7aActions table rebuild done.");
                                    } else {
                                        consumer2.accept("\u00a77Skipping actions OPTIMIZE (no deletions or low reclaim%).");
                                    }
                                    if (l22 > 0L && bl4 && d2 >= DbPurger.this.optimizeThresholdPercent) {
                                        DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.OPTIMIZING_SNAPSHOTS));
                                        consumer2.accept("\u00a7eRebuilding snapshots table (OPTIMIZE)\u2026");
                                        DbPurger.runOptimize(statement, "protectcore_container_snapshots", consumer2);
                                        consumer2.accept("\u00a7aSnapshots table rebuild done.");
                                    } else {
                                        consumer2.accept("\u00a77Skipping snapshots OPTIMIZE (no deletions or low reclaim%).");
                                    }
                                }
                                this.onSuccess();
                                this.stage = 4;
                                continue;
                            }
                            if (this.stage != 4) continue;
                            DbPurger.this.statusRef.updateAndGet(purgeStatus -> purgeStatus.withStage(PurgeStatus.Stage.DONE));
                            consumer2.accept("\u00a7aPurge complete.");
                            completableFuture.complete(new PurgeResult(false, l2, atomicLong.get(), atomicLong2.get(), atomicLong3.get(), System.currentTimeMillis() - l));
                            return;
                        }
                    }
                    catch (SQLException sQLException) {
                        if (DbPurger.isDiskFull(sQLException)) {
                            completableFuture.completeExceptionally(new IllegalStateException("Database host is out of disk space (errno 28 / disk full). Free space and re-run. Note: deleting rows may not shrink files; OPTIMIZE requires extra space.", sQLException));
                        } else {
                            completableFuture.completeExceptionally(sQLException);
                        }
                        return;
                    }
                    long l12 = (System.nanoTime() - l13) / 1000000L;
                    DbPurger.this.scheduleNext(l12, this);
                }
                catch (Throwable throwable) {
                    completableFuture.completeExceptionally(throwable);
                }
            }
        }
        this.runNowAsync(new Worker());
        return completableFuture;
    }

    private static boolean queryBoolVar(Connection connection, String string) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SHOW VARIABLES LIKE ?");){
            boolean bl;
            block16: {
                ResultSet resultSet;
                block14: {
                    boolean bl2;
                    block15: {
                        preparedStatement.setString(1, string);
                        resultSet = preparedStatement.executeQuery();
                        try {
                            if (resultSet.next()) break block14;
                            bl2 = false;
                            if (resultSet == null) break block15;
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
                    return bl2;
                }
                String string2 = resultSet.getString("Value");
                boolean bl3 = bl = string2 != null && (string2.equals("1") || string2.equalsIgnoreCase("ON") || string2.equalsIgnoreCase("TRUE"));
                if (resultSet == null) break block16;
                resultSet.close();
            }
            return bl;
        }
    }

    private void createPluginTables(Connection connection, Consumer<String> consumer) {
        consumer.accept("\u00a7eRecreating ProtectCore schema\u2026");
        this.db.ensureSchema(connection);
        consumer.accept("\u00a7aSchema recreated.");
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private void resetAllPluginData(Connection connection, Consumer<String> consumer) {
        consumer.accept(DbPurger.bigWarn("RESETTING PROTECTCORE DATA: ") + "\u00a7f\u00a7lDropping plugin tables to reclaim disk now.");
        try (Statement statement = connection.createStatement();){
            statement.setQueryTimeout(0);
            try {
                this.dropPluginTables(statement, consumer);
                this.createPluginTables(connection, consumer);
                boolean bl = false;
                try {
                    bl = DbPurger.queryBoolVar(connection, "innodb_file_per_table");
                }
                catch (SQLException sQLException) {
                    // empty catch block
                }
                if (!bl) {
                    consumer.accept("\u00a7eNote: \u00a77innodb_file_per_table is OFF. Data is reset, but OS disk may not drop (shared tablespace).");
                    return;
                } else {
                    consumer.accept("\u00a7aReset done. \u00a77Tables were dropped/recreated; this usually frees OS disk immediately.");
                }
                return;
            }
            catch (SQLException sQLException) {
                consumer.accept("\u00a7eDROP/CREATE failed (" + sQLException.getErrorCode() + "): \u00a77" + sQLException.getMessage());
                consumer.accept("\u00a77Falling back to DELETE (may not free OS disk immediately).");
                try {
                    this.db.ensureSchema(connection);
                }
                catch (SQLException sQLException2) {
                    // empty catch block
                }
                statement.execute("DELETE FROM protectcore_player_actions");
                statement.execute("DELETE FROM protectcore_container_snapshots");
                consumer.accept("\u00a7aDELETE reset done (logical delete only).");
                if (statement == null) return;
                statement.close();
                return;
            }
        }
    }

    private void dropPluginTables(Statement statement, Consumer<String> consumer) {
        statement.execute("SET SESSION FOREIGN_KEY_CHECKS=0");
        try {
            consumer.accept("\u00a7eDropping tables\u2026");
            statement.execute("DROP TABLE IF EXISTS protectcore_player_actions");
            statement.execute("DROP TABLE IF EXISTS protectcore_container_snapshots");
        }
        finally {
            statement.execute("SET SESSION FOREIGN_KEY_CHECKS=1");
        }
    }

    private long countActions(Connection connection, @Nullable Timestamp timestamp) {
        if (timestamp == null) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM protectcore_player_actions");){
                long l;
                block25: {
                    ResultSet resultSet = preparedStatement.executeQuery();
                    try {
                        long l2 = l = resultSet.next() ? resultSet.getLong(1) : 0L;
                        if (resultSet == null) break block25;
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
                return l;
            }
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM protectcore_player_actions WHERE sort_ts < ?");){
            long l;
            block26: {
                preparedStatement.setTimestamp(1, timestamp);
                ResultSet resultSet = preparedStatement.executeQuery();
                try {
                    long l3 = l = resultSet.next() ? resultSet.getLong(1) : 0L;
                    if (resultSet == null) break block26;
                }
                catch (Throwable throwable) {
                    if (resultSet != null) {
                        try {
                            resultSet.close();
                        }
                        catch (Throwable throwable3) {
                            throwable.addSuppressed(throwable3);
                        }
                    }
                    throw throwable;
                }
                resultSet.close();
            }
            return l;
        }
    }

    private long countSnapshotsByAge(Connection connection, @Nullable Timestamp timestamp) {
        if (timestamp == null) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM protectcore_container_snapshots");){
                long l;
                block25: {
                    ResultSet resultSet = preparedStatement.executeQuery();
                    try {
                        long l2 = l = resultSet.next() ? resultSet.getLong(1) : 0L;
                        if (resultSet == null) break block25;
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
                return l;
            }
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM protectcore_container_snapshots WHERE `timestamp` < ?");){
            long l;
            block26: {
                preparedStatement.setTimestamp(1, timestamp);
                ResultSet resultSet = preparedStatement.executeQuery();
                try {
                    long l3 = l = resultSet.next() ? resultSet.getLong(1) : 0L;
                    if (resultSet == null) break block26;
                }
                catch (Throwable throwable) {
                    if (resultSet != null) {
                        try {
                            resultSet.close();
                        }
                        catch (Throwable throwable3) {
                            throwable.addSuppressed(throwable3);
                        }
                    }
                    throw throwable;
                }
                resultSet.close();
            }
            return l;
        }
    }

    private long countOrphanSnapshots(Connection connection) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("    SELECT COUNT(*)\n    FROM protectcore_container_snapshots s\n    LEFT JOIN protectcore_player_actions a ON a.snapshot_id = s.id\n    WHERE a.snapshot_id IS NULL\n");){
            long l;
            block12: {
                ResultSet resultSet = preparedStatement.executeQuery();
                try {
                    long l2 = l = resultSet.next() ? resultSet.getLong(1) : 0L;
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
            return l;
        }
    }

    private void scheduleNext(long l, Runnable runnable) {
        if (l >= 80L) {
            this.runLaterAsync(runnable, 1L);
        } else {
            this.runNowAsync(runnable);
        }
    }

    private static String placeholders(int n) {
        StringBuilder stringBuilder = new StringBuilder(n * 2);
        for (int i = 0; i < n; ++i) {
            if (i > 0) {
                stringBuilder.append(',');
            }
            stringBuilder.append('?');
        }
        return stringBuilder.toString();
    }

    private static String withMaxStatementTime(int n, String string) {
        String string2 = string.trim();
        if (string2.endsWith(";")) {
            string2 = string2.substring(0, string2.length() - 1);
        }
        return "SET STATEMENT max_statement_time=" + n + " FOR " + string2;
    }

    private int deleteActionsChunk(Connection connection, @Nullable Timestamp timestamp, int n) {
        PreparedStatement preparedStatement;
        Object object;
        ArrayList<Integer> arrayList;
        block35: {
            ResultSet resultSet;
            int n2 = Math.min(n, 1000);
            arrayList = new ArrayList<Integer>(n2);
            if (timestamp == null) {
                object = "    SELECT id\n    FROM protectcore_player_actions\n    ORDER BY id DESC\n    LIMIT ?\n";
                preparedStatement = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
                try {
                    preparedStatement.setInt(1, n2);
                    preparedStatement.setQueryTimeout(35);
                    resultSet = preparedStatement.executeQuery();
                    try {
                        while (resultSet.next()) {
                            arrayList.add(resultSet.getInt(1));
                        }
                        break block35;
                    }
                    finally {
                        if (resultSet != null) {
                            resultSet.close();
                        }
                    }
                }
                finally {
                    if (preparedStatement != null) {
                        preparedStatement.close();
                    }
                }
            }
            object = "    SELECT id\n    FROM protectcore_player_actions FORCE INDEX (idx_sort_ts_id)\n    WHERE sort_ts < ?\n    ORDER BY sort_ts DESC, id DESC\n    LIMIT ?\n";
            preparedStatement = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
            try {
                preparedStatement.setTimestamp(1, timestamp);
                preparedStatement.setInt(2, n2);
                preparedStatement.setQueryTimeout(35);
                resultSet = preparedStatement.executeQuery();
                try {
                    while (resultSet.next()) {
                        arrayList.add(resultSet.getInt(1));
                    }
                }
                finally {
                    if (resultSet != null) {
                        resultSet.close();
                    }
                }
            }
            finally {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            }
        }
        if (arrayList.isEmpty()) {
            return 0;
        }
        object = "DELETE FROM protectcore_player_actions WHERE id IN (" + DbPurger.placeholders(arrayList.size()) + ")";
        preparedStatement = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
        try {
            int n3;
            for (n3 = 0; n3 < arrayList.size(); ++n3) {
                preparedStatement.setInt(n3 + 1, (Integer)arrayList.get(n3));
            }
            preparedStatement.setQueryTimeout(35);
            n3 = preparedStatement.executeUpdate();
            return n3;
        }
        finally {
            if (preparedStatement != null) {
                preparedStatement.close();
            }
        }
    }

    private int deleteSnapshotsByAgeChunk(Connection connection, @Nullable Timestamp timestamp, int n) {
        PreparedStatement preparedStatement;
        Object object;
        ArrayList<Long> arrayList;
        block35: {
            ResultSet resultSet;
            int n2 = Math.min(n, 1000);
            arrayList = new ArrayList<Long>(n2);
            if (timestamp == null) {
                object = "    SELECT id\n    FROM protectcore_container_snapshots\n    ORDER BY id DESC\n    LIMIT ?\n";
                preparedStatement = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
                try {
                    preparedStatement.setInt(1, n2);
                    preparedStatement.setQueryTimeout(35);
                    resultSet = preparedStatement.executeQuery();
                    try {
                        while (resultSet.next()) {
                            arrayList.add(resultSet.getLong(1));
                        }
                        break block35;
                    }
                    finally {
                        if (resultSet != null) {
                            resultSet.close();
                        }
                    }
                }
                finally {
                    if (preparedStatement != null) {
                        preparedStatement.close();
                    }
                }
            }
            object = "    SELECT id\n    FROM protectcore_container_snapshots FORCE INDEX (idx_snap_ts_id)\n    WHERE `timestamp` < ?\n    ORDER BY `timestamp` DESC, id DESC\n    LIMIT ?\n";
            preparedStatement = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
            try {
                preparedStatement.setTimestamp(1, timestamp);
                preparedStatement.setInt(2, n2);
                preparedStatement.setQueryTimeout(35);
                resultSet = preparedStatement.executeQuery();
                try {
                    while (resultSet.next()) {
                        arrayList.add(resultSet.getLong(1));
                    }
                }
                finally {
                    if (resultSet != null) {
                        resultSet.close();
                    }
                }
            }
            finally {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            }
        }
        if (arrayList.isEmpty()) {
            return 0;
        }
        object = "DELETE FROM protectcore_container_snapshots WHERE id IN (" + DbPurger.placeholders(arrayList.size()) + ")";
        preparedStatement = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
        try {
            int n3;
            for (n3 = 0; n3 < arrayList.size(); ++n3) {
                preparedStatement.setLong(n3 + 1, (Long)arrayList.get(n3));
            }
            preparedStatement.setQueryTimeout(35);
            n3 = preparedStatement.executeUpdate();
            return n3;
        }
        finally {
            if (preparedStatement != null) {
                preparedStatement.close();
            }
        }
    }

    private int deleteOrphanSnapshotsChunk(Connection connection, int n) {
        AutoCloseable autoCloseable;
        int n2 = Math.min(n, 1000);
        ArrayList<Long> arrayList = new ArrayList<Long>(n2);
        String string = "    SELECT s.id\n    FROM protectcore_container_snapshots s\n    LEFT JOIN protectcore_player_actions a ON a.snapshot_id = s.id\n    WHERE a.snapshot_id IS NULL\n    ORDER BY s.id ASC\n    LIMIT ?\n";
        try (Object object = connection.prepareStatement(DbPurger.withMaxStatementTime(30, string));){
            object.setInt(1, n2);
            object.setQueryTimeout(35);
            autoCloseable = object.executeQuery();
            try {
                while (autoCloseable.next()) {
                    arrayList.add(autoCloseable.getLong(1));
                }
            }
            finally {
                if (autoCloseable != null) {
                    autoCloseable.close();
                }
            }
        }
        if (arrayList.isEmpty()) {
            return 0;
        }
        object = "DELETE FROM protectcore_container_snapshots WHERE id IN (" + DbPurger.placeholders(arrayList.size()) + ")";
        autoCloseable = connection.prepareStatement(DbPurger.withMaxStatementTime(30, (String)object));
        try {
            int n3;
            for (n3 = 0; n3 < arrayList.size(); ++n3) {
                autoCloseable.setLong(n3 + 1, (Long)arrayList.get(n3));
            }
            autoCloseable.setQueryTimeout(35);
            n3 = autoCloseable.executeUpdate();
            return n3;
        }
        finally {
            if (autoCloseable != null) {
                autoCloseable.close();
            }
        }
    }

    private void runNowAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, runnable);
    }

    private void runLaterAsync(Runnable runnable, long l) {
        Bukkit.getScheduler().runTaskLaterAsynchronously((Plugin)this.plugin, runnable, l);
    }

    private static boolean isServerStopping() {
        try {
            return (Boolean)Bukkit.class.getMethod("isStopping", new Class[0]).invoke(null, new Object[0]);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    private static String stripColorIfYouWant(String string) {
        return string;
    }

    private static final class TableSpace {
        final long totalBytes;
        final long freeInside;
        final long usedBytes;

        TableSpace(long l, long l2) {
            this.totalBytes = Math.max(0L, l);
            this.freeInside = Math.max(0L, l2);
            this.usedBytes = this.totalBytes;
        }

        long approxFileBytes() {
            return this.totalBytes + this.freeInside;
        }
    }

    @FunctionalInterface
    private static interface SqlDeleteOp {
        public int run(int var1) throws SQLException;
    }
}

