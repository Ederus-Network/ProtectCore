/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonNull
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.google.gson.JsonSyntaxException
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.ItemFrame
 *  org.bukkit.entity.Painting
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.util.EulerAngle
 */
package com.lucidaps.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.lucidaps.utilities.ItemMetadataUtil;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

public final class EntityMetadataUtil {
    private static final Gson GSON = new GsonBuilder().create();

    public static String encodeItemFrame(ItemFrame itemFrame) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("entity", itemFrame.getType().name().toLowerCase());
        jsonObject.addProperty("facing", itemFrame.getFacing().name());
        jsonObject.addProperty("rotation", itemFrame.getRotation().name());
        jsonObject.addProperty("attachedFace", itemFrame.getAttachedFace().name());
        jsonObject.addProperty("yaw", (Number)Float.valueOf(itemFrame.getLocation().getYaw()));
        ItemStack itemStack = itemFrame.getItem();
        if (itemStack != null && !itemStack.getType().isAir()) {
            JsonObject jsonObject2;
            String string = ItemMetadataUtil.extractItemMetadata(itemStack);
            if (string != null && !string.isBlank()) {
                jsonObject2 = JsonParser.parseString((String)string).getAsJsonObject();
            } else {
                jsonObject2 = new JsonObject();
                jsonObject2.addProperty("material", itemStack.getType().name());
                jsonObject2.addProperty("amount", (Number)itemStack.getAmount());
            }
            jsonObject.add("item", (JsonElement)jsonObject2);
        }
        jsonObject.addProperty("timestamp", (Number)System.currentTimeMillis());
        return GSON.toJson((JsonElement)jsonObject);
    }

    public static String encodePainting(Painting painting) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("entity", "painting");
        jsonObject.addProperty("facing", painting.getFacing().name());
        jsonObject.addProperty("art", painting.getArt().name());
        jsonObject.addProperty("attachedFace", painting.getAttachedFace().name());
        jsonObject.addProperty("yaw", (Number)Float.valueOf(painting.getLocation().getYaw()));
        Location location = painting.getLocation();
        jsonObject.addProperty("cx", (Number)location.getX());
        jsonObject.addProperty("cy", (Number)location.getY());
        jsonObject.addProperty("cz", (Number)location.getZ());
        Block block = location.getBlock().getRelative(painting.getAttachedFace());
        if (block.getType().isSolid()) {
            jsonObject.addProperty("support", block.getType().name());
        }
        Location location2 = painting.getLocation();
        Block block2 = painting.getLocation().getBlock().getRelative(painting.getAttachedFace()).getRelative(BlockFace.DOWN, painting.getArt().getBlockHeight() - 1);
        jsonObject.addProperty("tlx", (Number)location2.getBlockX());
        jsonObject.addProperty("tly", (Number)location2.getBlockY());
        jsonObject.addProperty("tlz", (Number)location2.getBlockZ());
        jsonObject.addProperty("blx", (Number)block2.getX());
        jsonObject.addProperty("bly", (Number)block2.getY());
        jsonObject.addProperty("blz", (Number)block2.getZ());
        jsonObject.addProperty("timestamp", (Number)System.currentTimeMillis());
        return jsonObject.toString();
    }

    private static JsonObject poseToJson(ArmorStand armorStand) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("head", (JsonElement)EntityMetadataUtil.angleToJasonArray(armorStand.getHeadPose()));
        jsonObject.add("body", (JsonElement)EntityMetadataUtil.angleToJasonArray(armorStand.getBodyPose()));
        jsonObject.add("larm", (JsonElement)EntityMetadataUtil.angleToJasonArray(armorStand.getLeftArmPose()));
        jsonObject.add("rarm", (JsonElement)EntityMetadataUtil.angleToJasonArray(armorStand.getRightArmPose()));
        jsonObject.add("lleg", (JsonElement)EntityMetadataUtil.angleToJasonArray(armorStand.getLeftLegPose()));
        jsonObject.add("rleg", (JsonElement)EntityMetadataUtil.angleToJasonArray(armorStand.getRightLegPose()));
        return jsonObject;
    }

    private static JsonArray angleToJasonArray(EulerAngle eulerAngle) {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add((Number)eulerAngle.getX());
        jsonArray.add((Number)eulerAngle.getY());
        jsonArray.add((Number)eulerAngle.getZ());
        return jsonArray;
    }

    public static EulerAngle angle(JsonArray jsonArray, EulerAngle eulerAngle) {
        if (jsonArray == null || jsonArray.size() != 3) {
            return eulerAngle;
        }
        return new EulerAngle(jsonArray.get(0).getAsDouble(), jsonArray.get(1).getAsDouble(), jsonArray.get(2).getAsDouble());
    }

    public static String encodeArmorStand(ArmorStand armorStand) {
        JsonArray jsonArray;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("entity", "armor_stand");
        jsonObject.addProperty("yaw", (Number)Float.valueOf(armorStand.getLocation().getYaw()));
        JsonArray jsonArray2 = new JsonArray();
        for (ItemStack itemStack : jsonArray = new JsonArray[]{armorStand.getEquipment().getItemInMainHand(), armorStand.getEquipment().getItemInOffHand(), armorStand.getEquipment().getBoots(), armorStand.getEquipment().getLeggings(), armorStand.getEquipment().getChestplate(), armorStand.getEquipment().getHelmet()}) {
            if (itemStack == null || itemStack.getType().isAir()) {
                jsonArray2.add((JsonElement)JsonNull.INSTANCE);
                continue;
            }
            jsonArray2.add((JsonElement)EntityMetadataUtil.encodeItemLossless(itemStack));
        }
        jsonObject.add("equipment", (JsonElement)jsonArray2);
        jsonObject.add("pose", (JsonElement)EntityMetadataUtil.poseToJson(armorStand));
        jsonObject.addProperty("small", Boolean.valueOf(armorStand.isSmall()));
        jsonObject.addProperty("marker", Boolean.valueOf(armorStand.isMarker()));
        jsonObject.addProperty("invisible", Boolean.valueOf(armorStand.isInvisible()));
        jsonObject.addProperty("arms", Boolean.valueOf(armorStand.hasArms()));
        jsonObject.addProperty("basePlate", Boolean.valueOf(armorStand.hasBasePlate()));
        jsonObject.addProperty("gravity", Boolean.valueOf(armorStand.hasGravity()));
        jsonObject.addProperty("invulnerable", Boolean.valueOf(armorStand.isInvulnerable()));
        jsonObject.addProperty("glowing", Boolean.valueOf(armorStand.isGlowing()));
        jsonObject.addProperty("silent", Boolean.valueOf(armorStand.isSilent()));
        jsonObject.addProperty("collidable", Boolean.valueOf(armorStand.isCollidable()));
        jsonObject.addProperty("canMove", Boolean.valueOf(armorStand.canMove()));
        jsonObject.addProperty("canTick", Boolean.valueOf(armorStand.canTick()));
        jsonObject.addProperty("customNameVisible", Boolean.valueOf(armorStand.isCustomNameVisible()));
        if (armorStand.getCustomName() != null) {
            jsonObject.addProperty("customName", armorStand.getCustomName());
        }
        JsonArray jsonArray3 = new JsonArray();
        armorStand.getDisabledSlots().forEach(equipmentSlot -> jsonArray3.add(equipmentSlot.name()));
        jsonObject.add("disabledSlots", (JsonElement)jsonArray3);
        jsonObject.addProperty("timestamp", (Number)System.currentTimeMillis());
        return GSON.toJson((JsonElement)jsonObject);
    }

    public static JsonObject parse(String string) {
        if (string == null || string.isBlank() || !string.trim().startsWith("{")) {
            return new JsonObject();
        }
        try {
            JsonElement jsonElement = JsonParser.parseString((String)string);
            return jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : new JsonObject();
        }
        catch (Exception exception) {
            System.err.println("[EntityMetadataUtil] Bad JSON: " + string + " \u2013 " + exception.getMessage());
            return new JsonObject();
        }
    }

    public static void applyFullMeta(ArmorStand armorStand, JsonObject jsonObject) {
        JsonObject jsonObject2;
        if (jsonObject == null || jsonObject.size() == 0) {
            return;
        }
        if (jsonObject.has("small")) {
            armorStand.setSmall(jsonObject.get("small").getAsBoolean());
        }
        if (jsonObject.has("marker")) {
            armorStand.setMarker(jsonObject.get("marker").getAsBoolean());
        }
        if (jsonObject.has("invisible")) {
            armorStand.setInvisible(jsonObject.get("invisible").getAsBoolean());
        }
        if (jsonObject.has("arms")) {
            armorStand.setArms(jsonObject.get("arms").getAsBoolean());
        }
        if (jsonObject.has("basePlate")) {
            armorStand.setBasePlate(jsonObject.get("basePlate").getAsBoolean());
        }
        if (jsonObject.has("gravity")) {
            armorStand.setGravity(jsonObject.get("gravity").getAsBoolean());
        }
        if (jsonObject.has("invulnerable")) {
            armorStand.setInvulnerable(jsonObject.get("invulnerable").getAsBoolean());
        }
        if (jsonObject.has("glowing")) {
            armorStand.setGlowing(jsonObject.get("glowing").getAsBoolean());
        }
        if (jsonObject.has("silent")) {
            armorStand.setSilent(jsonObject.get("silent").getAsBoolean());
        }
        if (jsonObject.has("collidable")) {
            armorStand.setCollidable(jsonObject.get("collidable").getAsBoolean());
        }
        if (jsonObject.has("canMove")) {
            armorStand.setCanMove(jsonObject.get("canMove").getAsBoolean());
        }
        if (jsonObject.has("canTick")) {
            armorStand.setCanTick(jsonObject.get("canTick").getAsBoolean());
        }
        if (jsonObject.has("customName")) {
            armorStand.setCustomName(jsonObject.get("customName").getAsString());
        }
        if (jsonObject.has("customNameVisible")) {
            armorStand.setCustomNameVisible(jsonObject.get("customNameVisible").getAsBoolean());
        }
        if (jsonObject.has("disabledSlots") && jsonObject.get("disabledSlots").isJsonArray()) {
            jsonObject2 = new ArrayList();
            for (ItemStack[] itemStackArray : jsonObject.getAsJsonArray("disabledSlots")) {
                try {
                    jsonObject2.add(EquipmentSlot.valueOf((String)itemStackArray.getAsString()));
                }
                catch (RuntimeException runtimeException) {}
            }
            armorStand.setDisabledSlots((EquipmentSlot[])jsonObject2.toArray(EquipmentSlot[]::new));
        }
        if (jsonObject.has("pose") && jsonObject.get("pose").isJsonObject()) {
            jsonObject2 = jsonObject.getAsJsonObject("pose");
            armorStand.setHeadPose(EntityMetadataUtil.angle(jsonObject2.getAsJsonArray("head"), armorStand.getHeadPose()));
            armorStand.setBodyPose(EntityMetadataUtil.angle(jsonObject2.getAsJsonArray("body"), armorStand.getBodyPose()));
            armorStand.setLeftArmPose(EntityMetadataUtil.angle(jsonObject2.getAsJsonArray("larm"), armorStand.getLeftArmPose()));
            armorStand.setRightArmPose(EntityMetadataUtil.angle(jsonObject2.getAsJsonArray("rarm"), armorStand.getRightArmPose()));
            armorStand.setLeftLegPose(EntityMetadataUtil.angle(jsonObject2.getAsJsonArray("lleg"), armorStand.getLeftLegPose()));
            armorStand.setRightLegPose(EntityMetadataUtil.angle(jsonObject2.getAsJsonArray("rleg"), armorStand.getRightLegPose()));
        }
        if (jsonObject.has("equipment") && jsonObject.get("equipment").isJsonArray()) {
            ItemStack[] itemStackArray;
            jsonObject2 = jsonObject.getAsJsonArray("equipment");
            Iterator iterator = armorStand.getEquipment();
            itemStackArray = new ItemStack[6];
            for (int i = 0; i < Math.min(jsonObject2.size(), itemStackArray.length); ++i) {
                itemStackArray[i] = EntityMetadataUtil.decodeItem(jsonObject2.get(i));
            }
            iterator.setItemInMainHand(itemStackArray[0]);
            iterator.setItemInOffHand(itemStackArray[1]);
            iterator.setBoots(itemStackArray[2]);
            iterator.setLeggings(itemStackArray[3]);
            iterator.setChestplate(itemStackArray[4]);
            iterator.setHelmet(itemStackArray[5]);
        }
    }

    public static ItemStack decodeItem(JsonElement jsonElement) {
        JsonObject jsonObject;
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        }
        if (jsonElement.isJsonObject() && (jsonObject = jsonElement.getAsJsonObject()).has("serialized")) {
            try {
                return ItemStack.deserializeBytes((byte[])Base64.getDecoder().decode(jsonObject.get("serialized").getAsString()));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return ItemMetadataUtil.jsonToItem(jsonElement.toString());
    }

    private static JsonObject encodeItemLossless(ItemStack itemStack) {
        JsonObject jsonObject = new JsonObject();
        String string = ItemMetadataUtil.extractItemMetadata(itemStack);
        if (string != null && !string.isBlank()) {
            try {
                jsonObject = JsonParser.parseString((String)string).getAsJsonObject();
            }
            catch (JsonSyntaxException jsonSyntaxException) {
                // empty catch block
            }
        }
        jsonObject.addProperty("material", itemStack.getType().name());
        jsonObject.addProperty("amount", (Number)itemStack.getAmount());
        try {
            jsonObject.addProperty("serialized", Base64.getEncoder().encodeToString(itemStack.serializeAsBytes()));
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
        return jsonObject;
    }

    public static BlockFace face(JsonObject jsonObject, String string, BlockFace blockFace) {
        if (jsonObject.has(string) && jsonObject.get(string).isJsonPrimitive() && jsonObject.get(string).getAsJsonPrimitive().isString()) {
            try {
                return BlockFace.valueOf((String)jsonObject.get(string).getAsString().toUpperCase());
            }
            catch (IllegalArgumentException illegalArgumentException) {
                System.err.println("Invalid BlockFace value: " + jsonObject.get(string).getAsString());
                return blockFace;
            }
        }
        return blockFace;
    }

    public static float yawFromFacing(BlockFace blockFace) {
        return switch (blockFace) {
            case BlockFace.NORTH -> 180.0f;
            case BlockFace.SOUTH -> 0.0f;
            case BlockFace.WEST -> 90.0f;
            case BlockFace.EAST -> -90.0f;
            default -> 0.0f;
        };
    }
}

