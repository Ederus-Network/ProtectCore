/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PcTpCommand;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.logging.ActionType;
import com.lucidaps.web.EditorAccessTokenStore;
import com.lucidaps.web.EditorPageBuilder;
import com.lucidaps.web.EditorTeleportHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EditorWebServer {
    private final HttpServer server;
    private final DatabaseMariadb databaseMariadb;
    private final EditorAccessTokenStore accessTokens;
    private final TimeZone tz;
    private final String host;
    private final int port;
    private final String dateLocale;
    private final ProtectCore plugin;
    private static final Pattern MC_NAME = Pattern.compile("^[.*_]?[A-Za-z0-9_ -]{1,32}$");
    private final Set<String> disabledActions;
    private final Gson gson;
    private final String pluginVersion;
    private final long dbRowCount;
    private final ThreadPoolExecutor executor;
    private final boolean securePublicOrigin;
    private final int maxPageSize;

    public EditorWebServer(ProtectCore protectCore, DatabaseMariadb databaseMariadb, int n2, String string) {
        Connection connection;
        this.databaseMariadb = databaseMariadb;
        this.port = n2;
        this.tz = TimeZone.getTimeZone(protectCore.getConfig().getString("timezone", "UTC"));
        this.host = protectCore.getConfig().getString("editor.bindAddress", "127.0.0.1");
        this.dateLocale = string;
        this.plugin = protectCore;
        this.pluginVersion = protectCore.getDescription().getVersion();
        long l = Math.max(1L, protectCore.getConfig().getLong("editor.tokenTtlMinutes", 60L));
        this.accessTokens = new EditorAccessTokenStore(l);
        this.securePublicOrigin = protectCore.getConfig().getString("editor.publicBaseUrl", "").startsWith("https://");
        this.maxPageSize = Math.max(1, Math.min(500, protectCore.getConfig().getInt("editor.maxPageSize", 100)));
        long l2 = 0L;
        if (databaseMariadb.getHealthState() == DatabaseMariadb.HealthState.CONNECTED) {
            try {
                connection = databaseMariadb.getDataSource().getConnection();
                try (Statement object = connection.createStatement();
                     ResultSet resultSet = object.executeQuery("SELECT COALESCE(table_rows, 0) FROM information_schema.TABLES WHERE table_schema = DATABASE() AND table_name = 'protectcore_player_actions'");){
                    if (resultSet.next()) {
                        l2 = resultSet.getLong(1);
                    }
                }
                finally {
                    if (connection != null) {
                        connection.close();
                    }
                }
            }
            catch (SQLException sQLException) {
                protectCore.getLogger().log(Level.WARNING, "Could not fetch DB stats", sQLException);
            }
        }
        this.dbRowCount = l2;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Timestamp.class, (com.google.gson.JsonSerializer<Timestamp>) (timestamp, type, ctx) -> timestamp == null ? null : new JsonPrimitive(timestamp.getTime()))
            .registerTypeAdapter(Date.class, (com.google.gson.JsonSerializer<Date>) (date, type, ctx) -> date == null ? null : new JsonPrimitive(date.getTime()))
            .create();
        this.disabledActions = new HashSet<String>();
        this.disabledActions.add("CONTAINER_SNAPSHOT");
        org.bukkit.configuration.ConfigurationSection loggedActionsSec = protectCore.getConfig().getConfigurationSection("loggedActions");
        if (loggedActionsSec != null) {
            for (ActionType actionType : ActionType.values()) {
                if (!actionType.isUserVisible()) continue;
                boolean bl = false;
                if (actionType.belongsTo(ActionType.Category.PLAYER)) {
                    bl |= loggedActionsSec.getBoolean("player." + actionType.name(), true);
                }
                if (actionType.belongsTo(ActionType.Category.WORLD)) {
                    bl |= loggedActionsSec.getBoolean("world." + actionType.name(), true);
                }
                if (bl) continue;
                this.disabledActions.add(actionType.name());
            }
        }
        String string2 = EditorWebServer.bootstrapAssetRoot();
        HttpServer createdServer;
        try {
            createdServer = HttpServer.create(new InetSocketAddress(this.host, n2), 0);
        } catch (IOException e) {
            if (!"0.0.0.0".equals(this.host)) {
                protectCore.getLogger().warning("Could not bind to " + this.host + ":" + n2 + ", falling back to 0.0.0.0:" + n2);
                try {
                    createdServer = HttpServer.create(new InetSocketAddress("0.0.0.0", n2), 0);
                } catch (IOException e2) {
                    throw new RuntimeException("Could not create HttpServer", e2);
                }
            } else {
                throw new RuntimeException("Could not create HttpServer", e);
            }
        }
        this.server = createdServer;
        this.server.createContext("/api/logs", new LogsHandler());
        this.server.createContext("/editor", new EditorHandler());
        EditorTeleportHandler editorTeleportHandler = new EditorTeleportHandler(this.accessTokens::isValid, () -> databaseMariadb.getHealthState() == DatabaseMariadb.HealthState.CONNECTED, n -> databaseMariadb.findEditorTeleportLocation(n, this.disabledActions), this::issueTeleportCommand, exception -> protectCore.getLogger().log(Level.WARNING, "Could not prepare editor teleport command", (Throwable)exception));
        this.server.createContext("/api/teleportCommand", httpExchange -> {
            this.securityHeaders(httpExchange);
            editorTeleportHandler.handle(httpExchange);
        });
        int n3 = Math.max(1, Math.min(64, protectCore.getConfig().getInt("editor.maxRequestThreads", 8)));
        int n4 = Math.max(1, Math.min(10000, protectCore.getConfig().getInt("editor.maxQueuedRequests", 64)));
        this.executor = new ThreadPoolExecutor(n3, n3, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(n4), new ThreadPoolExecutor.AbortPolicy());
        this.server.setExecutor(this.executor);
        HttpHandler httpHandler = SimpleFileServer.createFileHandler(protectCore.getIconFolder().toAbsolutePath());
        this.server.createContext("/icons", httpExchange -> {
            this.securityHeaders(httpExchange);
            httpHandler.handle(httpExchange);
        });
        this.server.createContext("/assets/bootstrap.min.css", httpExchange -> this.serveClasspathAsset(httpExchange, string2 + "css/bootstrap.min.css", "text/css; charset=utf-8"));
        this.server.createContext("/assets/bootstrap.bundle.min.js", httpExchange -> this.serveClasspathAsset(httpExchange, string2 + "js/bootstrap.bundle.min.js", "text/javascript; charset=utf-8"));
        this.server.createContext("/favicon.png", httpExchange -> this.serveClasspathAsset(httpExchange, "/editor/favicon.png", "image/png"));
        this.server.createContext("/favicon.ico", httpExchange -> this.serveClasspathAsset(httpExchange, "/editor/favicon.png", "image/png"));
        this.server.createContext("/assets/ederus-logo.png", httpExchange -> this.serveClasspathAsset(httpExchange, "/editor/ederus-logo.png", "image/png"));
        this.server.createContext("/api/logMeta", httpExchange -> {
            this.securityHeaders(httpExchange);
            if (!"GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
                httpExchange.sendResponseHeaders(405, -1L);
                return;
            }
            Map<String, String> map = EditorWebServer.queryToMap(httpExchange.getRequestURI().getRawQuery());
            if (!this.accessTokens.isValid(map.get("token"))) {
                httpExchange.sendResponseHeaders(403, -1L);
                return;
            }
            if (databaseMariadb.getHealthState() != DatabaseMariadb.HealthState.CONNECTED) {
                httpExchange.getResponseHeaders().set("Retry-After", "10");
                httpExchange.sendResponseHeaders(503, -1L);
                return;
            }
            int n = EditorWebServer.parseInt(map.get("id"), 0, 1, Integer.MAX_VALUE);
            if (n <= 0) {
                httpExchange.sendResponseHeaders(400, -1L);
                return;
            }
            String stringSql = "SELECT metadata FROM protectcore_player_actions WHERE id=?";
            String string2Meta = "{}";
            try (Connection conn = databaseMariadb.openEditorConnection();
                 PreparedStatement ps = conn.prepareStatement(stringSql)) {
                ps.setInt(1, n);
                try (ResultSet resultSet = ps.executeQuery()) {
                    if (resultSet.next()) {
                        string2Meta = EditorWebServer.metadataResponseBody(resultSet.getString(1));
                    }
                }
            }
            catch (SQLException sQLException) {
                protectCore.getLogger().log(Level.WARNING, "Failed to fetch metadata for id=" + n, sQLException);
                this.sendDatabaseFailure(httpExchange, sQLException);
                return;
            }
            byte[] metaBytes = string2Meta.getBytes(StandardCharsets.UTF_8);
            httpExchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            httpExchange.sendResponseHeaders(200, metaBytes.length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(metaBytes);
            }
        });
    }

    public UUID resolvePlayerUUID(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        // 1. Try UUID directly
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {}

        // 2. Try online player exact
        Player onlinePlayer = Bukkit.getPlayerExact(trimmed);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        // 3. Try cached offline player
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(trimmed);
        if (cached != null && (cached.isOnline() || cached.hasPlayedBefore())) {
            return cached.getUniqueId();
        }

        // 4. Handle Bedrock dot prefix variations
        if (trimmed.startsWith(".") && trimmed.length() > 1) {
            OfflinePlayer offNoDot = Bukkit.getOfflinePlayerIfCached(trimmed.substring(1));
            if (offNoDot != null && (offNoDot.isOnline() || offNoDot.hasPlayedBefore())) {
                return offNoDot.getUniqueId();
            }
        } else if (!trimmed.startsWith(".")) {
            OfflinePlayer offWithDot = Bukkit.getOfflinePlayerIfCached("." + trimmed);
            if (offWithDot != null && (offWithDot.isOnline() || offWithDot.hasPlayedBefore())) {
                return offWithDot.getUniqueId();
            }
        }

        // 5. Fallback: Query MariaDB directly from protectcore_player_actions!
        // Guarantees Bedrock players and offline players are found even if Bukkit UserCache doesn't have them
        try (Connection conn = this.databaseMariadb.openEditorConnection()) {
            String sql = "SELECT playerUUID FROM protectcore_player_actions WHERE LOWER(playerName) = LOWER(?) LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trimmed);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String uuidStr = rs.getString("playerUUID");
                        if (uuidStr != null && !uuidStr.isBlank()) {
                            try {
                                return UUID.fromString(uuidStr);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            String altName = trimmed.startsWith(".") ? trimmed.substring(1) : ("." + trimmed);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, altName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String uuidStr = rs.getString("playerUUID");
                        if (uuidStr != null && !uuidStr.isBlank()) {
                            try {
                                return UUID.fromString(uuidStr);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Error querying playerUUID from DB for user " + trimmed + ": " + e.getMessage());
        }

        return null;
    }

    private CompletableFuture<PcTpCommand.IssuedCommand> issueTeleportCommand(DatabaseMariadb.EditorTeleportLocation editorTeleportLocation) {
        CompletableFuture<PcTpCommand.IssuedCommand> completableFuture = new CompletableFuture<PcTpCommand.IssuedCommand>();
        try {
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> {
                if (completableFuture.isDone()) {
                    return;
                }
                try {
                    if (!this.plugin.isEnabled()) {
                        throw new IllegalStateException("Plugin is stopping");
                    }
                    World world = PcTpCommand.resolveWorld(editorTeleportLocation.world());
                    completableFuture.complete(world == null ? null : PcTpCommand.issueCommand(world.getKey().toString(), editorTeleportLocation.x(), editorTeleportLocation.y(), editorTeleportLocation.z()));
                }
                catch (RuntimeException runtimeException) {
                    completableFuture.completeExceptionally(runtimeException);
                }
            });
        }
        catch (RuntimeException runtimeException) {
            completableFuture.completeExceptionally(runtimeException);
        }
        return completableFuture;
    }

    static String metadataResponseBody(String string) {
        return string == null || string.isBlank() ? "{}" : string;
    }

    static String bootstrapAssetRoot() {
        Properties properties = new Properties();
        try (InputStream inputStream = EditorWebServer.class.getResourceAsStream("/META-INF/maven/org.webjars/bootstrap/pom.properties")) {
            if (inputStream == null) {
                throw new IOException("Missing bundled Bootstrap version metadata");
            }
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Could not read Bootstrap version metadata", e);
        }
        String version = properties.getProperty("version");
        if (version == null || version.isBlank()) {
            throw new RuntimeException("Missing bundled Bootstrap version");
        }
        String string = "/META-INF/resources/webjars/bootstrap/" + version.strip() + "/";
        if (EditorWebServer.class.getResource(string + "css/bootstrap.min.css") == null || EditorWebServer.class.getResource(string + "js/bootstrap.bundle.min.js") == null) {
            throw new RuntimeException("Missing bundled Bootstrap assets for " + version);
        }
        return string;
    }

    static int databaseFailureStatus(SQLException sQLException) {
        String string = sQLException.getSQLState();
        return sQLException instanceof SQLTransientConnectionException || sQLException instanceof SQLNonTransientConnectionException || string != null && string.startsWith("08") ? 503 : 500;
    }

    private void sendDatabaseFailure(HttpExchange httpExchange, SQLException sQLException) {
        int n = this.databaseMariadb.getHealthState() != DatabaseMariadb.HealthState.CONNECTED ? 503 : EditorWebServer.databaseFailureStatus(sQLException);
        if (n == 503) {
            httpExchange.getResponseHeaders().set("Retry-After", "10");
        }
        try {
            httpExchange.sendResponseHeaders(n, -1L);
        } catch (IOException ignored) {}
    }

    public void start() {
        this.server.start();
    }

    public void stop() {
        this.server.stop(3);
        this.executor.shutdownNow();
    }

    public String issueAccessToken() {
        return this.accessTokens.issueToken();
    }

    public void purgeExpiredTokens(long l) {
        int n = this.accessTokens.purgeExpired(l);
        if (n > 0) {
            this.plugin.getLogger().info("Purged " + n + " expired editor access token(s).");
        }
    }

    private void securityHeaders(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        httpExchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        httpExchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        httpExchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (this.securePublicOrigin) {
            httpExchange.getResponseHeaders().set("Strict-Transport-Security", "max-age=31536000");
        }
        httpExchange.getResponseHeaders().set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        httpExchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; script-src 'self' 'unsafe-inline'; img-src 'self' data: https://mc-heads.net https://minotar.net; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    }

    private void serveClasspathAsset(HttpExchange httpExchange, String string, String string2) {
        this.securityHeaders(httpExchange);
        try {
            if (!"GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
                httpExchange.sendResponseHeaders(405, -1L);
                return;
            }
            try (InputStream inputStream = EditorWebServer.class.getResourceAsStream(string)) {
                if (inputStream == null) {
                    httpExchange.sendResponseHeaders(404, -1L);
                    return;
                }
                byte[] byArray = inputStream.readAllBytes();
                httpExchange.getResponseHeaders().set("Content-Type", string2);
                httpExchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
                httpExchange.sendResponseHeaders(200, byArray.length);
                try (OutputStream outputStream = httpExchange.getResponseBody()) {
                    outputStream.write(byArray);
                }
            }
        } catch (IOException e) {
            this.plugin.getLogger().warning("Error serving asset " + string + ": " + e.getMessage());
        }
    }

    private static int parseInt(String string, int n, int n2, int n3) {
        if (string == null) {
            return n;
        }
        try {
            int n4 = Integer.parseInt(string);
            return Math.max(n2, Math.min(n3, n4));
        }
        catch (NumberFormatException numberFormatException) {
            return n;
        }
    }

    private static long parseLong(String string, long l, long l2, long l3) {
        if (string == null) {
            return l;
        }
        try {
            long l4 = Long.parseLong(string);
            return Math.max(l2, Math.min(l3, l4));
        }
        catch (NumberFormatException numberFormatException) {
            return l;
        }
    }

    private static String bounded(String string, int n) {
        if (string == null) {
            return "";
        }
        return string.length() <= n ? string : string.substring(0, n);
    }

    static Map<String, String> queryToMap(String string) {
        if (string == null || string.isEmpty()) {
            return Collections.emptyMap();
        }
        HashMap<String, String> hashMap = new HashMap<String, String>();
        for (String string2 : string.split("&")) {
            int n = string2.indexOf(61);
            if (n <= 0) continue;
            try {
                String string3 = URLDecoder.decode(string2.substring(0, n), StandardCharsets.UTF_8.name());
                String string4 = URLDecoder.decode(string2.substring(n + 1), StandardCharsets.UTF_8.name());
                hashMap.put(string3, string4);
            }
            catch (UnsupportedEncodingException unsupportedEncodingException) {}
        }
        return hashMap;
    }

    private class LogsHandler
    implements HttpHandler {
        private LogsHandler() {
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            DatabaseMariadb.SearchResult searchResult;
            EditorWebServer.this.securityHeaders(httpExchange);
            if (!"GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
                httpExchange.sendResponseHeaders(405, -1L);
                return;
            }
            Map<String, String> map = EditorWebServer.queryToMap(httpExchange.getRequestURI().getRawQuery());
            if (!EditorWebServer.this.accessTokens.isValid(map.get("token"))) {
                httpExchange.sendResponseHeaders(403, -1L);
                return;
            }
            if (EditorWebServer.this.databaseMariadb.getHealthState() != DatabaseMariadb.HealthState.CONNECTED) {
                httpExchange.getResponseHeaders().set("Retry-After", "10");
                httpExchange.sendResponseHeaders(503, -1L);
                return;
            }
            int n = EditorWebServer.parseInt(map.get("pageSize"), 30, 1, EditorWebServer.this.maxPageSize);
            long l = EditorWebServer.parseLong(map.get("lastTimestamp"), 0L, 0L, Long.MAX_VALUE);
            int n2 = EditorWebServer.parseInt(map.get("lastId"), 0, 0, Integer.MAX_VALUE);
            String string = EditorWebServer.bounded(map.get("user"), 36);
            boolean bl2 = false;
            UUID uUID = null;
            if (!string.isEmpty()) {
                if (MC_NAME.matcher(string).matches()) {
                    uUID = EditorWebServer.this.resolvePlayerUUID(string);
                    if (uUID == null) {
                        bl2 = true;
                    }
                } else {
                    bl2 = true;
                }
            }
            if (!string.isEmpty() && bl2) {
                HashMap<String, Object> hashMap = new HashMap<String, Object>();
                hashMap.put("logs", Collections.emptyList());
                hashMap.put("playerNotFound", true);
                hashMap.put("lastTimestamp", 0L);
                hashMap.put("lastId", 0);
                byte[] responseBytes = EditorWebServer.this.gson.toJson(hashMap).getBytes(StandardCharsets.UTF_8);
                httpExchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                httpExchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream outputStream = httpExchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
                return;
            }
            String string2 = uUID != null ? uUID.toString() : "";
            String queryText = EditorWebServer.bounded(map.get("q"), 256);
            String string3 = EditorWebServer.bounded(map.get("material"), 128);
            String string4 = EditorWebServer.bounded(map.get("name"), 256);
            String string5 = EditorWebServer.bounded(map.get("lore"), 256);
            String string6 = EditorWebServer.bounded(map.get("enchants"), 512);
            String string7 = EditorWebServer.bounded(map.get("action"), 64);
            if (!string7.isEmpty()) {
                try {
                    ActionType actionType = ActionType.valueOf(string7.toUpperCase(Locale.ROOT));
                    if (!actionType.isUserVisible()) {
                        throw new IllegalArgumentException();
                    }
                    string7 = actionType.name();
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    httpExchange.sendResponseHeaders(400, -1L);
                    return;
                }
            }
            Set<String> actionFilter = EditorWebServer.this.disabledActions;
            if (string7.isEmpty()) {
                actionFilter = new HashSet<>(actionFilter);
                actionFilter.add("PLAYER_MESSAGE");
                actionFilter.add("PLAYER_COMMAND");
            }
            long l2 = EditorWebServer.parseLong(map.get("since"), 0L, 0L, Long.MAX_VALUE);
            String string8 = map.get("sinceDate");
            String string9 = map.get("untilDate");
            Long l3 = null;
            Long l4 = null;
            if (string8 != null && !string8.isEmpty()) {
                try {
                    l3 = LocalDate.parse(string8).atStartOfDay(EditorWebServer.this.tz.toZoneId()).toEpochSecond();
                }
                catch (DateTimeParseException dateTimeParseException) {
                    httpExchange.sendResponseHeaders(400, -1L);
                    return;
                }
            }
            if (string9 != null && !string9.isEmpty()) {
                try {
                    l4 = LocalDate.parse(string9).plusDays(1L).atStartOfDay(EditorWebServer.this.tz.toZoneId()).toEpochSecond() - 1L;
                }
                catch (DateTimeParseException dateTimeParseException) {
                    httpExchange.sendResponseHeaders(400, -1L);
                    return;
                }
            }
            long l5 = l3 != null ? l3 : l2;
            long l6 = l4 != null ? l4 : Long.MAX_VALUE;
            boolean bl3 = "1".equals(map.get("hideRolled")) || "true".equalsIgnoreCase(map.get("hideRolled"));
            boolean bl4 = "1".equals(map.get("hideWE")) || "true".equalsIgnoreCase(map.get("hideWE"));
            searchResult = EditorWebServer.this.databaseMariadb.searchLogsFast(queryText, l5, l6, string2, string3, string4, string5, string6, string7, n, l, n2, actionFilter, bl3, bl4);
            if (searchResult == null) {
                searchResult = new DatabaseMariadb.SearchResult(Collections.emptyList(), 0, 0L, 0);
            }
            HashMap<String, Object> hashMap = new HashMap<String, Object>();
            hashMap.put("logs", searchResult.logs);
            hashMap.put("playerNotFound", bl2);
            hashMap.put("lastTimestamp", searchResult.lastTimestamp);
            hashMap.put("lastId", searchResult.lastId);
            byte[] byArray = EditorWebServer.this.gson.toJson(hashMap).getBytes(StandardCharsets.UTF_8);
            httpExchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            String string10 = httpExchange.getRequestHeaders().getFirst("Accept-Encoding");
            boolean gzip = string10 != null && string10.toLowerCase().contains("gzip");
            if (gzip) {
                httpExchange.getResponseHeaders().set("Content-Encoding", "gzip");
                httpExchange.sendResponseHeaders(200, 0L);
                try (GZIPOutputStream gZIPOutputStream = new GZIPOutputStream(httpExchange.getResponseBody())) {
                    gZIPOutputStream.write(byArray);
                }
            } else {
                httpExchange.sendResponseHeaders(200, byArray.length);
                try (OutputStream outputStream = httpExchange.getResponseBody()) {
                    outputStream.write(byArray);
                }
            }
        }
    }

    private class EditorHandler
    implements HttpHandler {
        private EditorHandler() {
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            long l;
            long l2;
            EditorWebServer.this.securityHeaders(httpExchange);
            if (!"GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
                httpExchange.sendResponseHeaders(405, -1L);
                return;
            }
            Map<String, String> map = EditorWebServer.queryToMap(httpExchange.getRequestURI().getRawQuery());
            String string = map.get("token");
            if (!EditorWebServer.this.accessTokens.isValid(string)) {
                httpExchange.sendResponseHeaders(403, -1L);
                return;
            }
            long l3 = EditorWebServer.parseLong(map.get("since"), 0L, 0L, Long.MAX_VALUE);
            String string2 = EditorWebServer.bounded(map.get("user"), 36);
            try {
                Path path = Paths.get(EditorWebServer.this.plugin.getConfig().getString("diskPath", "/"));
                FileStore fileStore = Files.getFileStore(path);
                l2 = fileStore.getTotalSpace();
                l = fileStore.getUnallocatedSpace();
            }
            catch (IOException iOException) {
                EditorWebServer.this.plugin.getLogger().warning("Could not fetch disk stats from diskPath, falling back to JVM CWD: " + iOException.getMessage());
                File file = new File(".");
                l2 = file.getTotalSpace();
                l = file.getFreeSpace();
            }
            long l4 = l2 - l;
            long l5 = 0L;
            if (EditorWebServer.this.databaseMariadb.getHealthState() == DatabaseMariadb.HealthState.CONNECTED) {
                try (Connection conn = EditorWebServer.this.databaseMariadb.getDataSource().getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT SUM(data_length + index_length) FROM information_schema.TABLES WHERE table_schema = DATABASE()")) {
                    if (rs.next()) {
                        l5 = rs.getLong(1);
                    }
                }
                catch (SQLException sQLException) {
                    EditorWebServer.this.plugin.getLogger().warning("Could not fetch DB size: " + sQLException.getMessage());
                }
            }
            String pageHtml = new EditorPageBuilder(EditorWebServer.this.pluginVersion, l4, l2, l5, EditorWebServer.this.dbRowCount, string2.isEmpty() ? null : string2, Collections.emptyList(), EditorWebServer.this.tz, EditorWebServer.this.dateLocale, EditorWebServer.this.plugin.getEditorTheme()).buildShellHtml(string, l3, string2.isEmpty() ? null : string2, EditorWebServer.this.host, EditorWebServer.this.port, EditorWebServer.this.disabledActions);
            byte[] htmlBytes = pageHtml.getBytes(StandardCharsets.UTF_8);
            String enc = httpExchange.getRequestHeaders().getFirst("Accept-Encoding");
            boolean gzip = enc != null && enc.toLowerCase().contains("gzip");
            httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            httpExchange.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
            httpExchange.getResponseHeaders().add("Pragma", "no-cache");
            httpExchange.getResponseHeaders().add("Expires", "0");
            if (gzip) {
                httpExchange.getResponseHeaders().set("Content-Encoding", "gzip");
                httpExchange.sendResponseHeaders(200, 0L);
                try (GZIPOutputStream gZIPOutputStream = new GZIPOutputStream(httpExchange.getResponseBody())) {
                    gZIPOutputStream.write(htmlBytes);
                }
            } else {
                httpExchange.sendResponseHeaders(200, htmlBytes.length);
                try (OutputStream outputStream = httpExchange.getResponseBody()) {
                    outputStream.write(htmlBytes);
                }
            }
        }
    }
}
