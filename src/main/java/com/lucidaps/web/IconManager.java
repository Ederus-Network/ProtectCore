/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.lucidaps.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class IconManager {
    private static final String ITEMS_VERSION = "1.21.5";
    private static final String ICONS_CACHE_VERSION = "1.21.10-pack-1";
    private static final List<String> ITEM_ZIP_URLS = List.of("https://github.com/TinyTank800/MinecraftAllImages/raw/main/releases/minecraft-items-1.21.5.zip");
    private static final String MOBS_ZIP_URL = "https://drive.google.com/uc?export=download&id=1YNY7ChvqQLZtzQVKtnGhnwJpmkrFixtk";
    private static final String EXTRA_ICONS_ZIP_URL = "https://drive.google.com/uc?export=download&id=16p1vj3m2MmFHh6eiLk9FbjStP11V-b7X";
    private final JavaPlugin plugin;
    private final Path iconsDir;
    private final Path mobsDir;
    private final Path versionFile;

    public IconManager(JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
        this.iconsDir = javaPlugin.getDataFolder().toPath().resolve("icons");
        this.mobsDir = this.iconsDir.resolve("mobs");
        this.versionFile = javaPlugin.getDataFolder().toPath().resolve("icons.version");
        try {
            Files.createDirectories(this.iconsDir, new FileAttribute[0]);
            Files.createDirectories(this.mobsDir, new FileAttribute[0]);
        }
        catch (IOException iOException) {
            javaPlugin.getLogger().severe("[Icons] Could not create icons directory: " + iOException.getMessage());
        }
    }

    public void ensureIconsPresent() {
        this.plugin.getServer().getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            try {
                String string;
                String string2 = string = Files.exists(this.versionFile, new LinkOption[0]) ? Files.readString(this.versionFile).trim() : "";
                if (ICONS_CACHE_VERSION.equals(string)) {
                    this.plugin.getLogger().info("[Icons] Found cached 1.21.10-pack-1");
                    return;
                }
                this.plugin.getLogger().info("[Icons] Clearing old icons\u2026");
                try (AutoCloseable autoCloseable = Files.walk(this.iconsDir, new FileVisitOption[0]);){
                    autoCloseable.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
                Files.createDirectories(this.iconsDir, new FileAttribute[0]);
                Files.createDirectories(this.mobsDir, new FileAttribute[0]);
                autoCloseable = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                this.downloadAndUnzipFirstSuccessful((HttpClient)autoCloseable, ITEM_ZIP_URLS, this.iconsDir);
                this.plugin.getLogger().info("[Icons] Downloading mobs ZIP from Drive\u2026");
                Path path2 = Files.createTempFile("mobs-icons", ".zip", new FileAttribute[0]);
                HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(MOBS_ZIP_URL)).build();
                HttpResponse<Path> httpResponse = ((HttpClient)autoCloseable).send(httpRequest, HttpResponse.BodyHandlers.ofFile(path2));
                if (httpResponse.statusCode() != 200) {
                    throw new IOException("Mobs ZIP returned " + httpResponse.statusCode());
                }
                this.unzipPngs(path2, this.mobsDir);
                Files.deleteIfExists(path2);
                this.plugin.getLogger().info("[Icons] Downloading extra icon pack (1.21.10) from Drive\u2026");
                Path path3 = Files.createTempFile("icons-extra", ".zip", new FileAttribute[0]);
                HttpRequest httpRequest2 = HttpRequest.newBuilder().uri(URI.create(EXTRA_ICONS_ZIP_URL)).build();
                HttpResponse<Path> httpResponse2 = ((HttpClient)autoCloseable).send(httpRequest2, HttpResponse.BodyHandlers.ofFile(path3));
                if (httpResponse2.statusCode() != 200) {
                    throw new IOException("Extra icons ZIP returned " + httpResponse2.statusCode());
                }
                this.unzipPngs(path3, this.iconsDir);
                Files.deleteIfExists(path3);
                Files.writeString(this.versionFile, (CharSequence)ICONS_CACHE_VERSION, new OpenOption[0]);
                this.plugin.getLogger().info("[Icons] All icons ready (1.21.10-pack-1)");
            }
            catch (Exception exception) {
                this.plugin.getLogger().severe("[Icons] " + exception.getMessage());
            }
        });
    }

    private void downloadAndUnzipFirstSuccessful(HttpClient httpClient, List<String> list, Path path) {
        Exception exception = null;
        for (String string : list) {
            this.plugin.getLogger().info("[Icons] Trying download from " + string);
            try {
                Path path2 = Files.createTempFile("icons", ".zip", new FileAttribute[0]);
                HttpResponse<Path> httpResponse = httpClient.send(HttpRequest.newBuilder(URI.create(string)).build(), HttpResponse.BodyHandlers.ofFile(path2));
                if (httpResponse.statusCode() == 200) {
                    this.unzipPngs(path2, path);
                    Files.deleteIfExists(path2);
                    return;
                }
                exception = new IOException("status " + httpResponse.statusCode());
            }
            catch (Exception exception2) {
                exception = exception2;
            }
        }
        throw new IOException("All downloads failed", exception);
    }

    private void unzipPngs(Path path, Path path2) {
        int n = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(path, new OpenOption[0]));){
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory() || !zipEntry.getName().toLowerCase().endsWith(".png")) continue;
                Path path3 = path2.resolve(Paths.get(zipEntry.getName(), new String[0]).getFileName());
                Files.createDirectories(path3.getParent(), new FileAttribute[0]);
                try (OutputStream outputStream = Files.newOutputStream(path3, new OpenOption[0]);){
                    zipInputStream.transferTo(outputStream);
                }
                ++n;
            }
        }
        this.plugin.getLogger().info("[Icons] Extracted " + n + " PNGs to " + String.valueOf(path2));
    }

    public Path getIconsDir() {
        return this.iconsDir;
    }
}

