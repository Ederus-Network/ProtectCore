/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  javax.annotation.Nullable
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package com.lucidaps.utilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemMetadataUtil {
    @Nullable
    public static ItemSearchData extractSearchData(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        String string2 = itemStack.getType().name().toLowerCase(Locale.ROOT);
        String string3 = null;
        String string4 = null;
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            string3 = itemMeta.getDisplayName();
            string4 = ItemMetadataUtil.normalizeForSearch(string3);
        }
        String string5 = null;
        String string6 = null;
        if (itemMeta != null && itemMeta.hasLore() && itemMeta.getLore() != null && !itemMeta.getLore().isEmpty()) {
            string5 = String.join((CharSequence)"\n", itemMeta.getLore());
            string6 = itemMeta.getLore().stream().filter(Objects::nonNull).map(ItemMetadataUtil::normalizeForSearch).filter(string -> !string.isBlank()).collect(Collectors.joining(" "));
        }
        String string7 = null;
        if (!itemStack.getEnchantments().isEmpty()) {
            string7 = itemStack.getEnchantments().keySet().stream().map(enchantment -> enchantment.getKey().getKey().toLowerCase(Locale.ROOT)).sorted().collect(Collectors.joining(","));
        }
        StringBuilder stringBuilder = new StringBuilder(string2);
        ItemMetadataUtil.appendSearchPart(stringBuilder, string4);
        ItemMetadataUtil.appendSearchPart(stringBuilder, string6);
        ItemMetadataUtil.appendSearchPart(stringBuilder, string7 == null ? null : string7.replace(',', ' '));
        String string8 = stringBuilder.toString().trim();
        if (string8.isEmpty()) {
            string8 = null;
        }
        return new ItemSearchData(string2, string3, ItemMetadataUtil.blankToNull(string4), ItemMetadataUtil.blankToNull(string5), ItemMetadataUtil.blankToNull(string6), ItemMetadataUtil.blankToNull(string7), ItemMetadataUtil.blankToNull(string8));
    }

    private static void appendSearchPart(StringBuilder stringBuilder, String string) {
        if (string == null || string.isBlank()) {
            return;
        }
        stringBuilder.append(' ').append(string);
    }

    private static String blankToNull(String string) {
        return string == null || string.isBlank() ? null : string;
    }

    private static String normalizeForSearch(String string) {
        if (string == null) {
            return "";
        }
        string = string.replaceAll("(?i)(\u00a7x(\u00a7[0-9a-f]){6}|&x(&[0-9a-f]){6})", "");
        string = string.replaceAll("(?i)[\u00a7&][0-9a-fk-or]", "");
        return string.toLowerCase(Locale.ROOT).trim();
    }

    public static String extractItemMetadata(ItemStack itemStack) {
        Map map;
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        boolean bl = false;
        if (itemMeta != null) {
            if (itemMeta.hasCustomModelData()) {
                bl = true;
            }
            if (itemMeta.hasDisplayName()) {
                bl = true;
            }
            if (itemMeta.hasLore() && itemMeta.getLore() != null && !itemMeta.getLore().isEmpty()) {
                bl = true;
            }
        }
        if (!itemStack.getEnchantments().isEmpty()) {
            bl = true;
        }
        if (!bl) {
            return null;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("material", itemStack.getType().name());
        jsonObject.addProperty("amount", (Number)itemStack.getAmount());
        if (itemMeta.hasCustomModelData()) {
            jsonObject.addProperty("cmd", (Number)itemMeta.getCustomModelData());
        }
        if (itemMeta.hasDisplayName()) {
            jsonObject.addProperty("displayName", itemMeta.getDisplayName());
        }
        if (itemMeta.hasLore()) {
            map = new JsonArray();
            for (String string : itemMeta.getLore()) {
                if (string == null) continue;
                map.add(string);
            }
            if (map.size() > 0) {
                jsonObject.add("lore", (JsonElement)map);
            }
        }
        if (!(map = itemStack.getEnchantments()).isEmpty()) {
            JsonObject jsonObject2 = new JsonObject();
            map.forEach((enchantment, n) -> jsonObject2.addProperty(enchantment.getKey().getKey(), (Number)n));
            if (jsonObject2.size() > 0) {
                jsonObject.add("enchantments", (JsonElement)jsonObject2);
            }
        }
        return jsonObject.toString();
    }

    @Nullable
    public static ItemSearchData extractSearchDataFromLoggedMetadata(String string) {
        if (string == null || string.isBlank()) {
            return null;
        }
        try {
            Serializable serializable;
            Object object;
            JsonObject jsonObject;
            JsonObject jsonObject2 = jsonObject = JsonParser.parseString((String)string).getAsJsonObject();
            if (jsonObject.has("item") && jsonObject.get("item").isJsonObject()) {
                jsonObject2 = jsonObject.getAsJsonObject("item");
            }
            String string2 = null;
            if (jsonObject2.has("material") && jsonObject2.get("material").isJsonPrimitive()) {
                string2 = jsonObject2.get("material").getAsString().toLowerCase(Locale.ROOT);
            }
            if (string2 == null || string2.isBlank()) {
                return null;
            }
            String string3 = null;
            String string4 = null;
            if (jsonObject2.has("displayName") && jsonObject2.get("displayName").isJsonPrimitive()) {
                string3 = jsonObject2.get("displayName").getAsString();
                string4 = ItemMetadataUtil.blankToNull(ItemMetadataUtil.normalizeForSearch(string3));
            }
            String string5 = null;
            String string6 = null;
            if (jsonObject2.has("lore") && jsonObject2.get("lore").isJsonArray()) {
                object = new ArrayList();
                serializable = new ArrayList();
                for (Object object2 : jsonObject2.getAsJsonArray("lore")) {
                    if (object2 == null || object2.isJsonNull()) continue;
                    String string7 = object2.getAsString();
                    object.add(string7);
                    String string8 = ItemMetadataUtil.normalizeForSearch(string7);
                    if (string8.isBlank()) continue;
                    serializable.add(string8);
                }
                if (!object.isEmpty()) {
                    string5 = String.join((CharSequence)"\n", object);
                }
                if (!serializable.isEmpty()) {
                    string6 = String.join((CharSequence)" ", (Iterable<? extends CharSequence>)((Object)serializable));
                }
            }
            object = null;
            if (jsonObject2.has("enchantments") && jsonObject2.get("enchantments").isJsonObject()) {
                serializable = new ArrayList();
                for (Object object2 : jsonObject2.getAsJsonObject("enchantments").entrySet()) {
                    if (object2.getKey() == null || ((String)object2.getKey()).isBlank()) continue;
                    serializable.add(((String)object2.getKey()).toLowerCase(Locale.ROOT));
                }
                if (!serializable.isEmpty()) {
                    Collections.sort(serializable);
                    object = String.join((CharSequence)",", (Iterable<? extends CharSequence>)((Object)serializable));
                }
            }
            serializable = new StringBuilder(string2);
            ItemMetadataUtil.appendSearchPart((StringBuilder)serializable, string4);
            ItemMetadataUtil.appendSearchPart((StringBuilder)serializable, string6);
            ItemMetadataUtil.appendSearchPart((StringBuilder)serializable, object == null ? null : ((String)object).replace(',', ' '));
            String string9 = ItemMetadataUtil.blankToNull(((StringBuilder)serializable).toString().trim());
            return new ItemSearchData(string2, ItemMetadataUtil.blankToNull(string3), ItemMetadataUtil.blankToNull(string4), ItemMetadataUtil.blankToNull(string5), ItemMetadataUtil.blankToNull(string6), ItemMetadataUtil.blankToNull((String)object), string9);
        }
        catch (Exception exception) {
            return null;
        }
    }

    public static ItemStack jsonToItem(String string) {
        JsonObject jsonObject;
        if (string == null || string.isBlank()) {
            return null;
        }
        JsonObject jsonObject2 = JsonParser.parseString((String)string).getAsJsonObject();
        if (!jsonObject2.has("material")) {
            return null;
        }
        Material material = Material.matchMaterial((String)jsonObject2.get("material").getAsString());
        if (material == null) {
            return null;
        }
        int n = jsonObject2.has("amount") ? jsonObject2.get("amount").getAsInt() : 1;
        ItemStack itemStack = new ItemStack(material, Math.max(1, Math.min(n, material.getMaxStackSize())));
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (jsonObject2.has("displayName")) {
            itemMeta.setDisplayName(jsonObject2.get("displayName").getAsString());
        }
        if (jsonObject2.has("lore") && jsonObject2.get("lore").isJsonArray()) {
            jsonObject = new ArrayList();
            jsonObject2.get("lore").getAsJsonArray().forEach(arg_0 -> ItemMetadataUtil.lambda$jsonToItem$3((List)jsonObject, arg_0));
            itemMeta.setLore((List)jsonObject);
        }
        if (jsonObject2.has("enchantments") && jsonObject2.get("enchantments").isJsonObject()) {
            jsonObject = jsonObject2.get("enchantments").getAsJsonObject();
            for (Map.Entry entry : jsonObject.entrySet()) {
                Enchantment enchantment = Enchantment.getByKey((NamespacedKey)NamespacedKey.minecraft((String)((String)entry.getKey())));
                if (enchantment == null) continue;
                itemMeta.addEnchant(enchantment, ((JsonElement)entry.getValue()).getAsInt(), true);
            }
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static /* synthetic */ void lambda$jsonToItem$3(List list, JsonElement jsonElement) {
        list.add(jsonElement.getAsString());
    }

    public record ItemSearchData(String material, String nameRaw, String nameNorm, String loreRaw, String loreNorm, String enchantsCsv, String searchText) {
    }
}

