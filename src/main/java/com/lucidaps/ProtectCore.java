/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.World
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.InvalidConfigurationException
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.scheduler.BukkitTask
 */
package com.lucidaps;

import com.lucidaps.UpdateChecker;
import com.lucidaps.commands.PcTpCommand;
import com.lucidaps.commands.PoCommand;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.db.DbPurger;
import com.lucidaps.integrations.worldedit.WorldEditBridge;
import com.lucidaps.listeners.InspectModeListener;
import com.lucidaps.listeners.PlayerActionListener;
import com.lucidaps.listeners.WorldActionListener;
import com.lucidaps.utilities.ConfigUpdater;
import com.lucidaps.utilities.Lang;
import com.lucidaps.utilities.PrettyConsole;
import com.lucidaps.web.EditorTheme;
import com.lucidaps.web.EditorWebServer;
import com.lucidaps.web.IconManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ProtectCore
extends JavaPlugin {
    private DatabaseMariadb databaseMariadb;
    private final Set<UUID> inspectModePlayers = new HashSet<UUID>();
    private EditorWebServer apiServer;
    private String dateLocale;
    private ConfigurationSection loggedActionsConfig;
    private BukkitTask tokenCleanupTask;
    private IconManager iconManager;
    private Lang lang;
    private EditorTheme editorTheme;
    private WorldEditBridge weBridge;
    private PlayerActionListener playerActionListener;
    private DbPurger dbPurger;
    private BukkitTask retentionTask;
    private final Set<String> worldWhitelist = new HashSet<String>();
    private final Set<String> worldBlacklist = new HashSet<String>();
    private String latestVersion = "";

    public void onEnable() {
        ProtectCore.loadConfig0();
        long l = System.nanoTime();
        PrettyConsole.banner(this);
        long l2 = PrettyConsole.begin(this, "Loading config");
        this.saveDefaultConfig();
        this.updateConfigWithMissingDefaults();
        this.reloadConfig();
        this.validateConfiguration();
        this.dateLocale = this.getConfig().getString("dateLocale", "en-GB");
        PrettyConsole.ok(this, l2, "dateLocale=" + this.dateLocale);
        l2 = PrettyConsole.begin(this, "Initializing languages");
        this.lang = new Lang(this);
        PrettyConsole.ok(this, l2);
        l2 = PrettyConsole.begin(this, "Applying world filters");
        this.reloadWorldFilters();
        this.loadLoggedActionsConfig();
        int n = this.getConfig().getStringList("worlds.whitelist").size();
        int n2 = this.getConfig().getStringList("worlds.blacklist").size();
        PrettyConsole.ok(this, l2, "whitelist=" + n + ", blacklist=" + n2);
        l2 = PrettyConsole.begin(this, "Ensuring icons (async)");
        this.iconManager = new IconManager(this);
        this.iconManager.ensureIconsPresent();
        PrettyConsole.ok(this, l2);
        l2 = PrettyConsole.begin(this, "Loading editor theme");
        this.editorTheme = EditorTheme.fromConfig(this.getConfig().getConfigurationSection("editorTheme"));
        PrettyConsole.ok(this, l2);
        l2 = PrettyConsole.begin(this, "Connecting to database");
        try {
            this.databaseMariadb = new DatabaseMariadb(this);
            this.databaseMariadb.connect();
            PrettyConsole.ok(this, l2, this.databaseMariadb.getHealthState().name().toLowerCase(Locale.ROOT));
        }
        catch (Exception exception) {
            PrettyConsole.fail(this, l2, exception);
            this.getLogger().log(Level.SEVERE, "Could not initialize the database pool", exception);
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        this.dbPurger = new DbPurger(this, this.databaseMariadb);
        if (this.isWorldEditEnabled()) {
            l2 = PrettyConsole.begin(this, "Registering WorldEdit bridge");
            this.weBridge = new WorldEditBridge(this, this.databaseMariadb);
            boolean bl = this.weBridge.register();
            PrettyConsole.ok(this, l2, bl ? "enabled" : "not available");
        } else {
            PrettyConsole.kv(this, "WorldEdit", "disabled via config");
        }
        l2 = PrettyConsole.begin(this, "Registering listeners");
        this.playerActionListener = new PlayerActionListener(this, this.databaseMariadb);
        this.getServer().getPluginManager().registerEvents((Listener)this.playerActionListener, (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new WorldActionListener(this, this.databaseMariadb), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new InspectModeListener(this), (Plugin)this);
        PrettyConsole.ok(this, l2);
        l2 = PrettyConsole.begin(this, "Registering commands");
        PoCommand poCommand = new PoCommand(this, this.databaseMariadb, this.dbPurger);
        PluginCommand pluginCommand = this.requireCommand("po");
        pluginCommand.setExecutor((CommandExecutor)poCommand);
        pluginCommand.setTabCompleter((TabCompleter)poCommand);
        PcTpCommand pcTpCommand = new PcTpCommand();
        PluginCommand pluginCommand2 = this.requireCommand("pc_tp");
        pluginCommand2.setExecutor((CommandExecutor)pcTpCommand);
        pluginCommand2.setTabCompleter((TabCompleter)pcTpCommand);
        PrettyConsole.ok(this, l2);
        l2 = PrettyConsole.begin(this, "Starting Editor API");
        try {
            this.initApiServer();
            PrettyConsole.ok(this, l2, (String)(this.getConfig().getBoolean("editor.enabled", true) ? "port " + this.getConfig().getInt("editor.port", 9123) : "disabled"));
            String string2 = this.getConfig().getString("editor.bindAddress", "127.0.0.1");
            if ("0.0.0.0".equals(string2) || "::".equals(string2)) {
                this.getLogger().warning("The editor is listening on all interfaces. Protect it with a firewall or reverse proxy.");
            }
        }
        catch (IOException iOException) {
            PrettyConsole.fail(this, l2, iOException);
            this.getLogger().severe("Failed to start Editor API server: " + iOException.getMessage());
        }
        l2 = PrettyConsole.begin(this, "Scheduling token GC");
        this.startTokenGc();
        PrettyConsole.ok(this, l2, "60s interval");
        this.scheduleRetention();
        final boolean bl = this.getConfig().getBoolean("checkForUpdates", true);
        if (bl) {
            l2 = PrettyConsole.begin(this, "Checking for updates");
            new UpdateChecker(this, 129705).getVersion(string -> {
                this.latestVersion = string;
                if (!this.getDescription().getVersion().equalsIgnoreCase((String)string)) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes((char)'&', (String)("&d[ProtectCore] &eUpdate available: &f" + string + " &8(current " + this.getDescription().getVersion() + ")")));
                } else {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes((char)'&', (String)"&d[ProtectCore] &aPlugin is up to date."));
                }
            });
            PrettyConsole.ok(this, l2);
        } else {
            PrettyConsole.kv(this, "Updates", "disabled");
        }
        this.getServer().getPluginManager().registerEvents(new Listener(){

            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
                if (bl && playerJoinEvent.getPlayer().hasPermission("protectcore.reload") && !ProtectCore.this.getDescription().getVersion().equalsIgnoreCase(ProtectCore.this.latestVersion) && !ProtectCore.this.latestVersion.isEmpty()) {
                    playerJoinEvent.getPlayer().sendMessage(String.valueOf(ChatColor.GOLD) + "[ProtectCore] An update for ProtectCore is available: " + ProtectCore.this.latestVersion);
                }
            }
        }, (Plugin)this);
        PrettyConsole.footer(this, l);
    }

    public void startTokenGc() {
        if (this.tokenCleanupTask != null) {
            this.tokenCleanupTask.cancel();
        }
        this.tokenCleanupTask = Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> {
            long l = System.currentTimeMillis();
            PcTpCommand.purgeExpiredTokens(l);
            if (this.isEnabled() && this.apiServer != null) {
                this.apiServer.purgeExpiredTokens(l);
            }
        }, 1200L, 1200L);
    }

    public void initApiServer() {
        this.apiServer = null;
        if (!this.getConfig().getBoolean("editor.enabled", true)) {
            return;
        }
        int n = this.getConfig().getInt("editor.port", 9123);
        EditorWebServer editorWebServer = new EditorWebServer(this, this.databaseMariadb, n, this.dateLocale);
        editorWebServer.start();
        this.apiServer = editorWebServer;
    }

    public void reloadRuntimeConfiguration() {
        this.validateConfiguration();
        this.dateLocale = this.getConfig().getString("dateLocale", "en-GB");
        this.editorTheme = EditorTheme.fromConfig(this.getConfig().getConfigurationSection("editorTheme"));
        if (this.playerActionListener != null) {
            this.playerActionListener.reloadConfigFilters();
        }
        if (this.dbPurger != null) {
            this.dbPurger.reloadConfiguration();
        }
        this.scheduleRetention();
    }

    public void scheduleRetention() {
        if (this.retentionTask != null) {
            this.retentionTask.cancel();
        }
        if (this.dbPurger == null || !this.getConfig().getBoolean("database.retention.enabled", false)) {
            return;
        }
        int n = Math.max(1, this.getConfig().getInt("database.retention.keepDays", 90));
        long l = Math.max(1L, this.getConfig().getLong("database.retention.intervalHours", 24L));
        long l2 = Math.multiplyExact(l, 72000L);
        this.retentionTask = Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.dbPurger.purgeKeepDaysAsync(n, "automatic-retention", null).exceptionally(throwable -> {
            this.getLogger().log(Level.WARNING, "Automatic retention failed", (Throwable)throwable);
            return null;
        }), l2, l2);
        this.getLogger().info("Automatic retention enabled: keeping " + n + " days, every " + l + " hour(s).");
    }

    public void validateConfiguration() {
        int n = this.getConfig().getInt("editor.port", 9123);
        if (n < 1 || n > 65535) {
            throw new IllegalArgumentException("editor.port must be between 1 and 65535");
        }
        String string = this.getConfig().getString("editor.bindAddress", "");
        if (string == null || string.isBlank()) {
            throw new IllegalArgumentException("editor.bindAddress must not be blank");
        }
        String string2 = this.getConfig().getString("editor.publicBaseUrl", "");
        try {
            if (string2 == null) {
                throw new IllegalArgumentException();
            }
            URI uRI = URI.create(string2);
            if (!"http".equalsIgnoreCase(uRI.getScheme()) && !"https".equalsIgnoreCase(uRI.getScheme()) || uRI.getHost() == null || uRI.getUserInfo() != null || uRI.getQuery() != null || uRI.getFragment() != null || uRI.getPath() != null && !uRI.getPath().isEmpty() && !"/".equals(uRI.getPath())) {
                throw new IllegalArgumentException();
            }
        }
        catch (IllegalArgumentException illegalArgumentException) {
            throw new IllegalArgumentException("editor.publicBaseUrl must be an http(s) origin without credentials, path, query, or fragment");
        }
        this.requirePositive("editor.tokenTtlMinutes");
        this.requirePositive("database.batchSize");
        this.requirePositive("database.maxQueuedActions");
        this.requirePositive("database.retention.keepDays");
        this.requirePositive("database.retention.intervalHours");
        this.requirePositive("database.retention.batchSize");
    }

    private void requirePositive(String string) {
        if (this.getConfig().getLong(string, 0L) <= 0L) {
            throw new IllegalArgumentException(string + " must be greater than zero");
        }
    }

    public boolean isInspectModeEnabled(Player player) {
        return this.inspectModePlayers.contains(player.getUniqueId());
    }

    public void enableInspectMode(Player player) {
        this.inspectModePlayers.add(player.getUniqueId());
    }

    public void disableInspectMode(Player player) {
        this.inspectModePlayers.remove(player.getUniqueId());
    }

    public EditorWebServer getApiServer() {
        return this.apiServer;
    }

    public String getDateLocale() {
        return this.dateLocale;
    }

    public Path getIconFolder() {
        return this.iconManager.getIconsDir();
    }

    public Lang getLang() {
        return this.lang;
    }

    public void setLang(Lang lang) {
        this.lang = lang;
    }

    public EditorTheme getEditorTheme() {
        return this.editorTheme;
    }

    public boolean isWorldEditEnabled() {
        return this.getConfig().getBoolean("worldedit.enabled", true);
    }

    public boolean isWorldEditReportLogsEnabled() {
        return this.getConfig().getBoolean("worldedit.reportLogs.enabled", true);
    }

    public void reloadWorldEditBridge() {
        try {
            if (this.weBridge != null) {
                this.weBridge.unregister();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (this.isWorldEditEnabled()) {
            if (this.weBridge == null) {
                this.weBridge = new WorldEditBridge(this, this.databaseMariadb);
            }
            if (this.weBridge.register()) {
                this.getLogger().info("WorldEdit integration enabled (reload).");
            } else {
                this.getLogger().info("WorldEdit integration not available (reload).");
            }
        } else {
            this.getLogger().info("WorldEdit integration disabled by config (reload).");
        }
    }

    public DatabaseMariadb getDatabaseMariadb() {
        return this.databaseMariadb;
    }

    public DbPurger getDbPurger() {
        return this.dbPurger;
    }

    public void reloadWorldFilters() {
        this.worldWhitelist.clear();
        this.worldBlacklist.clear();
        ConfigurationSection configurationSection = this.getConfig().getConfigurationSection("worlds");
        List list = configurationSection != null ? configurationSection.getStringList("whitelist") : Collections.emptyList();
        List list2 = configurationSection != null ? configurationSection.getStringList("blacklist") : Collections.emptyList();
        for (String string : list) {
            if (string == null) continue;
            this.worldWhitelist.add(string.toLowerCase(Locale.ROOT));
        }
        for (String string : list2) {
            if (string == null) continue;
            this.worldBlacklist.add(string.toLowerCase(Locale.ROOT));
        }
        this.getLogger().info("[ProtectCore] World filters loaded: whitelist=" + String.valueOf(this.worldWhitelist) + " blacklist=" + String.valueOf(this.worldBlacklist));
    }

    public boolean isWorldAllowed(World world) {
        if (world == null) {
            return true;
        }
        String string = world.getName().toLowerCase(Locale.ROOT);
        if (!this.worldWhitelist.isEmpty()) {
            return this.worldWhitelist.contains(string);
        }
        return !this.worldBlacklist.contains(string);
    }

    public boolean isPlayerLoggingEnabled(World world) {
        return this.isPlayerLoggingEnabled() && this.isWorldAllowed(world);
    }

    public boolean isWorldLoggingEnabled(World world) {
        return this.isWorldLoggingEnabled() && this.isWorldAllowed(world);
    }

    public boolean isPlayerLoggingEnabled() {
        return this.getConfig().getBoolean("logPlayerActions", true);
    }

    public boolean isWorldLoggingEnabled() {
        return this.getConfig().getBoolean("logWorldActions", true);
    }

    public boolean isActionLoggingEnabled(String string, String string2, boolean bl) {
        if (this.loggedActionsConfig == null) {
            return bl;
        }
        return this.loggedActionsConfig.getBoolean(string + "." + string2, bl);
    }

    public boolean isPlayerActionLogged(String string, boolean bl) {
        if (!this.isPlayerLoggingEnabled()) {
            return false;
        }
        return this.isActionLoggingEnabled("player", string, bl);
    }

    public boolean isWorldActionLogged(String string, boolean bl) {
        if (!this.isWorldLoggingEnabled()) {
            return false;
        }
        return this.isActionLoggingEnabled("world", string, bl);
    }

    public void loadLoggedActionsConfig() {
        this.loggedActionsConfig = this.getConfig().getConfigurationSection("loggedActions");
        if (this.loggedActionsConfig == null) {
            this.getLogger().warning("'loggedActions' section not found in config.yml. Defaulting all specific actions to not log (or to your chosen default).");
        }
    }

    public void onDisable() {
        this.getLogger().info("ProtectCore disabling\u2026 starting graceful drains");
        if (this.weBridge != null) {
            try {
                this.weBridge.shutdown();
            }
            catch (Throwable throwable) {
                this.getLogger().log(Level.WARNING, "WorldEdit bridge shutdown threw", throwable);
            }
        }
        try {
            if (this.apiServer != null) {
                this.apiServer.stop();
            }
        }
        catch (Exception exception) {
            this.getLogger().warning("API stop: " + String.valueOf(exception));
        }
        if (this.databaseMariadb != null) {
            this.databaseMariadb.shutdown();
        }
        if (this.tokenCleanupTask != null) {
            this.tokenCleanupTask.cancel();
        }
        if (this.retentionTask != null) {
            this.retentionTask.cancel();
        }
        try {
            Object object = Bukkit.class.getMethod("getAsyncScheduler", new Class[0]).invoke(null, new Object[0]);
            object.getClass().getMethod("cancelTasks", Plugin.class).invoke(object, new Object[]{this});
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        Bukkit.getScheduler().cancelTasks((Plugin)this);
        PlayerActionListener.lavaPlacements.clear();
        this.getLogger().info("ProtectCore disabled!");
    }

    private PluginCommand requireCommand(String string) {
        PluginCommand pluginCommand = this.getCommand(string);
        if (pluginCommand == null) {
            throw new IllegalStateException("Command '" + string + "' missing from plugin.yml");
        }
        return pluginCommand;
    }

    public void updateConfigWithMissingDefaults() {
        try {
            ConfigUpdater.UpdateResult updateResult = ConfigUpdater.update(this);
            if (updateResult.changed()) {
                this.getLogger().info("Added " + updateResult.addedPaths().size() + " missing config option(s): " + String.join((CharSequence)", ", updateResult.addedPaths()));
                this.getLogger().info("Previous config backed up to " + String.valueOf(updateResult.backupPath().getFileName()));
            }
            if (!updateResult.conflicts().isEmpty()) {
                this.getLogger().warning("Could not add defaults below config value/section conflict(s): " + String.join((CharSequence)", ", updateResult.conflicts()));
            }
        }
        catch (IOException | InvalidConfigurationException throwable) {
            this.getLogger().log(Level.SEVERE, "Could not add missing options to config.yml; the existing file was left unchanged.", throwable);
        }
    }

    public CompletableFuture<Void> drainWorldEditQueuesAsync() {
        if (this.weBridge == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.weBridge.drainAllAsync();
    }

    private static /* bridge */ /* synthetic */ void loadConfig0() {
        try {
            URLConnection con = new URL("https://api.spigotmc.org/legacy/premium.php?user_id=837024&resource_id=129705&nonce=-153571945").openConnection();
            con.setConnectTimeout(1000);
            con.setReadTimeout(1000);
            ((HttpURLConnection)con).setInstanceFollowRedirects(true);
            String response = new BufferedReader(new InputStreamReader(con.getInputStream())).readLine();
            if ("false".equals(response)) {
                throw new RuntimeException("Access to this plugin has been disabled! Please contact the author!");
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}

