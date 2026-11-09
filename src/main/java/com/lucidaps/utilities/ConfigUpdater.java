/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.lucidaps.utilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigUpdater {
    private ConfigUpdater() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static UpdateResult update(JavaPlugin javaPlugin) {
        Object object;
        File file = new File(javaPlugin.getDataFolder(), "config.yml");
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.options().parseComments(true);
        yamlConfiguration.load(file);
        YamlConfiguration yamlConfiguration2 = new YamlConfiguration();
        yamlConfiguration2.options().parseComments(true);
        try (Object object2 = javaPlugin.getResource("config.yml");){
            if (object2 == null) {
                throw new IOException("The plugin JAR does not contain config.yml");
            }
            object = new InputStreamReader((InputStream)object2, StandardCharsets.UTF_8);
            try {
                yamlConfiguration2.load((Reader)object);
            }
            finally {
                ((InputStreamReader)object).close();
            }
        }
        object2 = ConfigUpdater.migrateLegacyEditor(yamlConfiguration);
        object = ConfigUpdater.mergeMissing(yamlConfiguration, yamlConfiguration2);
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>((Collection<String>)object2);
        linkedHashSet.addAll(((UpdateResult)object).addedPaths());
        UpdateResult updateResult = new UpdateResult(new ArrayList<String>(linkedHashSet), ((UpdateResult)object).conflicts(), null);
        if (!updateResult.changed()) {
            return updateResult;
        }
        Path path = file.toPath();
        Path path2 = path.resolveSibling("config.yml.bak");
        Path path3 = Files.createTempFile(javaPlugin.getDataFolder().toPath(), "config.yml.", ".tmp", new FileAttribute[0]);
        try {
            Files.writeString(path3, (CharSequence)yamlConfiguration.saveToString(), StandardCharsets.UTF_8, new OpenOption[0]);
            Files.copy(path, path2, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(path3, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException atomicMoveNotSupportedException) {
                Files.move(path3, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(path3);
        }
        return new UpdateResult(updateResult.addedPaths(), updateResult.conflicts(), path2);
    }

    static List<String> migrateLegacyEditor(YamlConfiguration yamlConfiguration) {
        int n;
        boolean bl;
        ArrayList<String> arrayList = new ArrayList<String>();
        boolean bl2 = yamlConfiguration.contains("editorApiHost") || yamlConfiguration.contains("editorApiPort") || yamlConfiguration.contains("editorTokenTtlMinutes");
        boolean bl3 = bl = yamlConfiguration.contains("editor.linkTtlMinutes") || yamlConfiguration.contains("editor.sessionTtlMinutes");
        if (!bl2 && !bl) {
            return arrayList;
        }
        if (yamlConfiguration.contains("editor") && !yamlConfiguration.isConfigurationSection("editor")) {
            return arrayList;
        }
        if (bl2) {
            n = yamlConfiguration.getInt("editorApiPort", 9123);
            String string = yamlConfiguration.getString("editorApiHost", "localhost");
            ConfigUpdater.setIfMissing(yamlConfiguration, "editor.enabled", true, arrayList);
            ConfigUpdater.setIfMissing(yamlConfiguration, "editor.bindAddress", "0.0.0.0", arrayList);
            ConfigUpdater.setIfMissing(yamlConfiguration, "editor.port", n, arrayList);
            ConfigUpdater.setIfMissing(yamlConfiguration, "editor.publicBaseUrl", "http://" + string + ":" + n, arrayList);
        }
        if (!yamlConfiguration.contains("editor.tokenTtlMinutes")) {
            n = yamlConfiguration.contains("editorTokenTtlMinutes") ? yamlConfiguration.getInt("editorTokenTtlMinutes", 60) : (yamlConfiguration.contains("editor.sessionTtlMinutes") ? yamlConfiguration.getInt("editor.sessionTtlMinutes", 60) : yamlConfiguration.getInt("editor.linkTtlMinutes", 60));
            ConfigUpdater.setIfMissing(yamlConfiguration, "editor.tokenTtlMinutes", Math.max(1, n), arrayList);
        }
        return arrayList;
    }

    private static void setIfMissing(YamlConfiguration yamlConfiguration, String string, Object object, List<String> list) {
        if (!yamlConfiguration.contains(string)) {
            yamlConfiguration.set(string, object);
            list.add(string + " (migrated from legacy editorApi* settings)");
        }
    }

    static UpdateResult mergeMissing(YamlConfiguration yamlConfiguration, YamlConfiguration yamlConfiguration2) {
        ArrayList<String> arrayList = new ArrayList<String>();
        ArrayList<String> arrayList2 = new ArrayList<String>();
        ConfigUpdater.mergeSection((ConfigurationSection)yamlConfiguration, (ConfigurationSection)yamlConfiguration2, "", arrayList, arrayList2);
        return new UpdateResult(arrayList, arrayList2, null);
    }

    private static void mergeSection(ConfigurationSection configurationSection, ConfigurationSection configurationSection2, String string, List<String> list, List<String> list2) {
        for (String string2 : configurationSection2.getKeys(false)) {
            String string3 = string.isEmpty() ? string2 : string + "." + string2;
            Object object = configurationSection2.get(string2);
            Object object2 = configurationSection.get(string2, null);
            if (object instanceof ConfigurationSection) {
                ConfigurationSection configurationSection3;
                ConfigurationSection configurationSection4 = (ConfigurationSection)object;
                if (object2 == null) {
                    configurationSection3 = configurationSection.createSection(string2);
                    ConfigUpdater.copyComments(configurationSection2, configurationSection, string2);
                } else if (object2 instanceof ConfigurationSection) {
                    ConfigurationSection configurationSection5;
                    configurationSection3 = configurationSection5 = (ConfigurationSection)object2;
                } else {
                    list2.add(string3);
                    continue;
                }
                ConfigUpdater.mergeSection(configurationSection3, configurationSection4, string3, list, list2);
                continue;
            }
            if (object2 != null) continue;
            configurationSection.set(string2, object);
            ConfigUpdater.copyComments(configurationSection2, configurationSection, string2);
            list.add(string3);
        }
    }

    private static void copyComments(ConfigurationSection configurationSection, ConfigurationSection configurationSection2, String string) {
        configurationSection2.setComments(string, new ArrayList(configurationSection.getComments(string)));
        configurationSection2.setInlineComments(string, new ArrayList(configurationSection.getInlineComments(string)));
    }

    public record UpdateResult(List<String> addedPaths, List<String> conflicts, Path backupPath) {
        public UpdateResult {
            list = List.copyOf(list);
            list2 = List.copyOf(list2);
        }

        public boolean changed() {
            return !this.addedPaths.isEmpty();
        }
    }
}

