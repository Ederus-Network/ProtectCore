package com.lucidaps.utilities;

import com.lucidaps.db.DatabaseMariadb;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Resuelve nombres de jugador (Java o Bedrock con prefijo ".") a UUID sin bloquear el hilo principal.
 *
 * <p>Antes, /po lookup llamaba a {@code Bukkit.getOfflinePlayer(String)} (petición web a Mojang) y a una
 * consulta {@code LOWER(playerName) = LOWER(?)} sin índice sobre toda la tabla de acciones, ambas en el hilo
 * principal. Con un nombre desconocido eso recorría decenas de GB y el watchdog de Paper mataba el servidor.
 *
 * <ul>
 *   <li>{@link #resolveCached}: solo memoria (UUID literal, conectados, caché de Paper y caché propia).</li>
 *   <li>{@link #resolveFromDatabase}: consulta por índice {@code idx_player_name}; llamar fuera del hilo principal.</li>
 *   <li>{@link #resolveAsync}: memoria al momento; si no, base de datos en segundo plano y respuesta en el hilo principal.</li>
 * </ul>
 * La columna playerName usa utf8mb4_unicode_ci, así que {@code playerName = ?} ya ignora mayúsculas.
 */
public final class PlayerResolver {
    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int QUERY_TIMEOUT_S = 3;
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final String SQL = "SELECT playerUUID FROM protectcore_player_actions WHERE playerName = ? ORDER BY id DESC LIMIT 1";

    private record Entry(UUID uuid, long at) {}

    private PlayerResolver() {}

    /** Resolución solo en memoria. Seguro en el hilo principal. */
    @Nullable
    public static UUID resolveCached(@Nullable String input) {
        if (input == null || input.isBlank()) return null;
        String name = input.trim();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {}

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        for (String variant : variants(name)) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(variant);
            if (cached != null) return cached.getUniqueId();
            Entry entry = CACHE.get(variant.toLowerCase(Locale.ROOT));
            if (entry != null) {
                if (System.currentTimeMillis() - entry.at() < TTL_MS) return entry.uuid();
                CACHE.remove(variant.toLowerCase(Locale.ROOT));
            }
        }
        return null;
    }

    /** Busca el nombre (y su variante con/sin ".") en el historial. Bloquea: llamar fuera del hilo principal. */
    @Nullable
    public static UUID resolveFromDatabase(DatabaseMariadb db, @Nullable String input) {
        if (input == null || input.isBlank()) return null;
        String name = input.trim();
        if (name.length() > 32) return null;
        try (Connection connection = db.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_S);
            for (String variant : variants(name)) {
                statement.setString(1, variant);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String raw = rs.getString(1);
                        if (raw != null && !raw.isBlank()) {
                            UUID uuid = UUID.fromString(raw.trim());
                            remember(variant, uuid);
                            return uuid;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Base de datos caída o consulta cortada por tiempo: se trata como jugador desconocido.
        }
        return null;
    }

    /** Memoria al momento; si no, base de datos en segundo plano. El callback siempre corre en el hilo principal. */
    public static void resolveAsync(Plugin plugin, DatabaseMariadb db, @Nullable String input, Consumer<UUID> callback) {
        UUID cached = resolveCached(input);
        if (cached != null || input == null || input.isBlank()) {
            callback.accept(cached);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = resolveFromDatabase(db, input);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(uuid));
        });
    }

    public static void remember(String name, UUID uuid) {
        if (name != null && uuid != null) CACHE.put(name.toLowerCase(Locale.ROOT), new Entry(uuid, System.currentTimeMillis()));
    }

    private static String[] variants(String name) {
        if (name.startsWith(".")) {
            return name.length() > 1 ? new String[]{name, name.substring(1)} : new String[]{name};
        }
        return new String[]{name, "." + name};
    }
}
