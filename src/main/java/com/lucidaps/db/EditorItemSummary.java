/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 */
package com.lucidaps.db;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

record EditorItemSummary(String material, Integer amount, boolean hasExtra) {
    static EditorItemSummary from(String string, String string2, String string3) {
        if ("PLAYER_MESSAGE".equals(string) || "PLAYER_COMMAND".equals(string)) {
            return new EditorItemSummary(null, null, false);
        }
        String string4 = EditorItemSummary.clean(string3);
        try {
            Integer n;
            JsonElement jsonElement = JsonParser.parseString((String)(string2 == null ? "{}" : string2));
            if (!jsonElement.isJsonObject()) {
                return new EditorItemSummary(string4, null, false);
            }
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            boolean bl = jsonObject.has("item") && jsonObject.get("item").isJsonObject();
            JsonObject jsonObject2 = bl ? jsonObject.getAsJsonObject("item") : jsonObject;
            String string5 = EditorItemSummary.string(jsonObject2.get("material"));
            if (string5 == null) {
                string5 = EditorItemSummary.string(jsonObject.get("blockMaterial"));
            }
            if (string5 == null && jsonObject.has("block") && jsonObject.get("block").isJsonObject()) {
                string5 = EditorItemSummary.string(jsonObject.getAsJsonObject("block").get("material"));
            }
            if (string5 != null) {
                string4 = string5;
            }
            if ((n = EditorItemSummary.positiveInteger(jsonObject2.get("amount"))) == null) {
                n = EditorItemSummary.positiveInteger(jsonObject2.get("count"));
            }
            boolean bl2 = bl || !jsonObject.has("item") && !jsonObject.has("entity") && !jsonObject.has("blockData") && !jsonObject.has("blockMaterial") && !jsonObject.has("block") && !jsonObject.has("sign") && !jsonObject.has("containerStructure");
            boolean bl3 = bl2 && (EditorItemSummary.text(jsonObject2.get("displayName")) || EditorItemSummary.text(jsonObject2.get("customName")) || EditorItemSummary.hasLore(jsonObject2.get("lore")) || EditorItemSummary.hasEnchantments(jsonObject2.get("enchantments")));
            return new EditorItemSummary(string4, n, bl3);
        }
        catch (RuntimeException runtimeException) {
            return new EditorItemSummary(string4, null, false);
        }
    }

    private static String string(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString() ? EditorItemSummary.clean(jsonElement.getAsString()) : null;
    }

    private static String clean(String string) {
        return string == null || string.isBlank() || "null".equals(string) ? null : string.strip();
    }

    private static Integer positiveInteger(JsonElement jsonElement) {
        try {
            if (jsonElement == null || !jsonElement.isJsonPrimitive()) {
                return null;
            }
            int n = jsonElement.getAsBigDecimal().intValueExact();
            return n > 0 ? Integer.valueOf(n) : null;
        }
        catch (RuntimeException runtimeException) {
            return null;
        }
    }

    private static boolean text(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString() && !jsonElement.getAsString().replaceAll("(?i)\u00a7[0-9a-fk-orx]", "").isBlank();
    }

    private static boolean hasLore(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonArray()) {
            return false;
        }
        for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
            if (!EditorItemSummary.text(jsonElement2)) continue;
            return true;
        }
        return false;
    }

    private static boolean hasEnchantments(JsonElement jsonElement) {
        if (jsonElement == null) {
            return false;
        }
        if (jsonElement.isJsonObject()) {
            return jsonElement.getAsJsonObject().entrySet().stream().anyMatch(entry -> !((String)entry.getKey()).isBlank() && EditorItemSummary.positiveInteger((JsonElement)entry.getValue()) != null);
        }
        if (!jsonElement.isJsonArray()) {
            return false;
        }
        for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
            JsonElement jsonElement3;
            if (EditorItemSummary.text(jsonElement2)) {
                return true;
            }
            if (!jsonElement2.isJsonObject()) continue;
            JsonObject jsonObject = jsonElement2.getAsJsonObject();
            boolean bl = EditorItemSummary.text(jsonObject.get("id")) || EditorItemSummary.text(jsonObject.get("name")) || EditorItemSummary.text(jsonObject.get("key"));
            JsonElement jsonElement4 = jsonElement3 = jsonObject.has("level") ? jsonObject.get("level") : jsonObject.get("lvl");
            if (!bl || jsonElement3 != null && EditorItemSummary.positiveInteger(jsonElement3) == null) continue;
            return true;
        }
        return false;
    }
}

