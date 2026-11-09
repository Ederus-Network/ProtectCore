/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.google.gson.JsonSyntaxException
 *  io.papermc.paper.event.player.AsyncChatEvent
 *  javax.annotation.Nullable
 *  net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Barrel
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Chest
 *  org.bukkit.block.CreatureSpawner
 *  org.bukkit.block.DoubleChest
 *  org.bukkit.block.ShulkerBox
 *  org.bukkit.block.Sign
 *  org.bukkit.block.data.Bisected$Half
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Directional
 *  org.bukkit.block.data.FaceAttachable
 *  org.bukkit.block.data.FaceAttachable$AttachedFace
 *  org.bukkit.block.data.MultipleFacing
 *  org.bukkit.block.data.Waterlogged
 *  org.bukkit.block.data.type.Bed
 *  org.bukkit.block.data.type.Bed$Part
 *  org.bukkit.block.data.type.Bell
 *  org.bukkit.block.data.type.Chest
 *  org.bukkit.block.data.type.Door
 *  org.bukkit.block.data.type.Lantern
 *  org.bukkit.block.sign.Side
 *  org.bukkit.block.sign.SignSide
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.Boat
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Hanging
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.ItemFrame
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Painting
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityPickupItemEvent
 *  org.bukkit.event.entity.EntityPlaceEvent
 *  org.bukkit.event.hanging.HangingBreakByEntityEvent
 *  org.bukkit.event.hanging.HangingPlaceEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.event.inventory.InventoryOpenEvent
 *  org.bukkit.event.player.PlayerArmorStandManipulateEvent
 *  org.bukkit.event.player.PlayerBucketEmptyEvent
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.projectiles.ProjectileSource
 *  org.bukkit.scheduler.BukkitTask
 */
package com.lucidaps.listeners;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.lucidaps.ProtectCore;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.listeners.util.ContainerItemKey;
import com.lucidaps.listeners.util.ContainerKey;
import com.lucidaps.listeners.util.DropAggregateData;
import com.lucidaps.listeners.util.DropKey;
import com.lucidaps.logging.ActionType;
import com.lucidaps.logging.SensitiveCommandRedactor;
import com.lucidaps.utilities.BinaryInventoryUtil;
import com.lucidaps.utilities.EntityMetadataUtil;
import com.lucidaps.utilities.ItemMetadataUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nullable;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

public class PlayerActionListener
implements Listener {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final Map<ContainerKey, ItemStack[]> containerSnapshots = new ConcurrentHashMap<ContainerKey, ItemStack[]>();
    private final Map<DropKey, DropAggregateData> dropAggregate = new ConcurrentHashMap<DropKey, DropAggregateData>();
    private final Map<DropKey, BukkitTask> scheduledDrops = new ConcurrentHashMap<DropKey, BukkitTask>();
    public static final Map<Location, UUID> lavaPlacements = new ConcurrentHashMap<Location, UUID>();
    private final Map<ContainerKey, Boolean> containerTouched = new ConcurrentHashMap<ContainerKey, Boolean>();
    private final Map<ContainerKey, Integer> containerChangeSeq = new ConcurrentHashMap<ContainerKey, Integer>();
    private final Map<ContainerKey, String> containerSession = new ConcurrentHashMap<ContainerKey, String>();
    private final Set<String> commandBlacklist = new HashSet<String>();
    private final Set<String> messageCommandInclude = new HashSet<String>();
    private final Set<String> sensitiveCommands = new HashSet<String>();

    public PlayerActionListener(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
        this.reloadConfigFilters();
    }

    public void reloadConfigFilters() {
        this.commandBlacklist.clear();
        this.messageCommandInclude.clear();
        this.sensitiveCommands.clear();
        try {
            this.plugin.getConfig().getStringList("logging.commands.blacklist").forEach(string -> PlayerActionListener.addNormalized(this.commandBlacklist, string));
            this.plugin.getConfig().getStringList("logging.messages.includeCommands").forEach(string -> PlayerActionListener.addNormalized(this.messageCommandInclude, string));
            this.plugin.getConfig().getStringList("logging.sensitiveCommands").forEach(string -> PlayerActionListener.addNormalized(this.sensitiveCommands, string));
        }
        catch (Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "Could not reload command logging filters", exception);
        }
    }

    private static void addNormalized(Set<String> set, String string) {
        String string2 = SensitiveCommandRedactor.normalize(string);
        if (!string2.isBlank()) {
            set.add(string2);
        }
    }

    public static UUID getLavaPlacer(Location location) {
        return lavaPlacements.get(location);
    }

    public static void removeLavaPlacer(Location location) {
        lavaPlacements.remove(location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent blockBreakEvent) {
        Object object;
        if (!this.plugin.isPlayerLoggingEnabled(blockBreakEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "BLOCK_BREAK", true)) {
            return;
        }
        Player player = blockBreakEvent.getPlayer();
        Block block = blockBreakEvent.getBlock();
        List<Block> list = this.findDependents(block);
        Block block2 = this.findPaired(block);
        if (block2 != null && !block2.getType().isAir()) {
            list.add(block2);
        }
        Location location = block.getLocation();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("material", block.getType().name());
        jsonObject.addProperty("blockData", block.getBlockData().getAsString());
        if (block.getType() == Material.SPAWNER && (object = block.getState(false)) instanceof CreatureSpawner) {
            CreatureSpawner creatureSpawner = (CreatureSpawner)object;
            try {
                jsonObject.addProperty("spawnerType", creatureSpawner.getSpawnedType().name());
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "BLOCK_BREAK", "broken " + block.getType().name().toLowerCase(), jsonObject.toString(), location);
        for (Block block3 : list) {
            try {
                this.logAttachedBreak(player, block3, block3.equals((Object)block2) ? "paired" : "attached");
            }
            catch (Throwable throwable) {
                this.plugin.getLogger().log(Level.FINE, "dep-log fail @ " + String.valueOf(block3.getLocation()), throwable);
            }
        }
    }

    private boolean isSupportedBy(Block block, Block block2) {
        Directional directional;
        BlockFace blockFace;
        Directional directional2;
        Object object;
        if (block.getType().isAir()) {
            return false;
        }
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional && block.getRelative((BlockFace)(object = (directional2 = (Directional)blockData).getFacing().getOppositeFace())).equals((Object)block2)) {
            return true;
        }
        if (blockData instanceof FaceAttachable && ((object = (directional2 = (FaceAttachable)blockData).getAttachedFace()) == FaceAttachable.AttachedFace.WALL && blockData instanceof Directional ? block.getRelative(blockFace = (directional = (Directional)blockData).getFacing().getOppositeFace()).equals((Object)block2) : (object == FaceAttachable.AttachedFace.FLOOR ? block.getRelative(BlockFace.DOWN).equals((Object)block2) : object == FaceAttachable.AttachedFace.CEILING && block.getRelative(BlockFace.UP).equals((Object)block2)))) {
            return true;
        }
        if (blockData instanceof MultipleFacing) {
            directional2 = (MultipleFacing)blockData;
            object = directional2.getAllowedFaces().iterator();
            while (object.hasNext()) {
                directional = (BlockFace)object.next();
                if (!directional2.hasFace((BlockFace)directional) || !block.getRelative((BlockFace)directional).equals((Object)block2)) continue;
                return true;
            }
        }
        if (!((String)(object = (directional2 = block.getType()).name())).contains("WALL") && (((String)object).endsWith("_SIGN") || ((String)object).endsWith("BANNER") || ((String)object).endsWith("HEAD") || ((String)object).endsWith("SKULL") || ((String)object).equals("FLOWER_POT")) && block.getRelative(BlockFace.DOWN).equals((Object)block2)) {
            return true;
        }
        if (((String)object).contains("HANGING_SIGN") && !((String)object).contains("WALL") && block.getRelative(BlockFace.UP).equals((Object)block2)) {
            return true;
        }
        if (blockData instanceof Lantern && ((directional = (Lantern)blockData).isHanging() ? block.getRelative(BlockFace.UP).equals((Object)block2) : block.getRelative(BlockFace.DOWN).equals((Object)block2))) {
            return true;
        }
        if (blockData instanceof Bell) {
            directional = (Bell)blockData;
            switch (directional.getAttachment()) {
                case FLOOR: {
                    if (!block.getRelative(BlockFace.DOWN).equals((Object)block2)) break;
                    return true;
                }
                case CEILING: {
                    if (!block.getRelative(BlockFace.UP).equals((Object)block2)) break;
                    return true;
                }
                case SINGLE_WALL: 
                case DOUBLE_WALL: {
                    if (!(blockData instanceof Directional) || !block.getRelative((blockFace = (Directional)blockData).getFacing().getOppositeFace()).equals((Object)block2)) break;
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private Block findPaired(Block block) {
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Door) {
            Door door = (Door)blockData;
            return block.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
        }
        if (blockData instanceof Bed) {
            Bed bed = (Bed)blockData;
            BlockFace blockFace = bed.getFacing();
            return block.getRelative(bed.getPart() == Bed.Part.FOOT ? blockFace : blockFace.getOppositeFace());
        }
        return null;
    }

    private JsonObject metaForBlock(Block block) {
        JsonObject jsonObject;
        block12: {
            CreatureSpawner creatureSpawner;
            BlockState blockState;
            jsonObject = new JsonObject();
            jsonObject.addProperty("material", block.getType().name());
            try {
                jsonObject.addProperty("blockData", block.getBlockData().getAsString());
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (block.getType() == Material.SPAWNER) {
                try {
                    blockState = block.getState(false);
                    if (blockState instanceof CreatureSpawner) {
                        creatureSpawner = (CreatureSpawner)blockState;
                        jsonObject.addProperty("spawnerType", creatureSpawner.getSpawnedType().name());
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            try {
                blockState = block.getState(false);
                if (!(blockState instanceof Sign)) break block12;
                creatureSpawner = (Sign)blockState;
                JsonObject jsonObject2 = new JsonObject();
                try {
                    SignSide signSide = creatureSpawner.getSide(Side.FRONT);
                    SignSide signSide2 = creatureSpawner.getSide(Side.BACK);
                    jsonObject2.addProperty("glowingFront", Boolean.valueOf(signSide.isGlowingText()));
                    jsonObject2.addProperty("glowingBack", Boolean.valueOf(signSide2.isGlowingText()));
                    jsonObject2.addProperty("colorFront", signSide.getColor().name());
                    jsonObject2.addProperty("colorBack", signSide2.getColor().name());
                    JsonObject jsonObject3 = new JsonObject();
                    for (int i = 0; i < 4; ++i) {
                        jsonObject3.addProperty("front" + (i + 1), signSide.line(i) == null ? "" : PlainTextComponentSerializer.plainText().serialize(signSide.line(i)));
                        jsonObject3.addProperty("back" + (i + 1), signSide2.line(i) == null ? "" : PlainTextComponentSerializer.plainText().serialize(signSide2.line(i)));
                    }
                    jsonObject2.add("lines", (JsonElement)jsonObject3);
                }
                catch (Throwable throwable) {
                    String[] stringArray = creatureSpawner.getLines();
                    JsonObject jsonObject4 = new JsonObject();
                    for (int i = 0; i < Math.min(4, stringArray.length); ++i) {
                        jsonObject4.addProperty("line" + (i + 1), stringArray[i]);
                    }
                    jsonObject2.add("lines", (JsonElement)jsonObject4);
                }
                jsonObject.add("sign", (JsonElement)jsonObject2);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return jsonObject;
    }

    private void logAttachedBreak(Player player, Block block, String string) {
        Location location = block.getLocation();
        Material material = block.getType();
        String string2 = "broken " + material.name().toLowerCase(Locale.ROOT) + " (" + string + ")";
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "BLOCK_BREAK", string2, this.metaForBlock(block).toString(), location);
    }

    private List<Block> findDependents(Block block) {
        ArrayList<Block> arrayList = new ArrayList<Block>(8);
        for (BlockFace blockFace : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block block2 = block.getRelative(blockFace);
            if (block2.getType().isAir() || !this.isSupportedBy(block2, block)) continue;
            arrayList.add(block2);
        }
        return arrayList;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockPlace(BlockPlaceEvent blockPlaceEvent) {
        CreatureSpawner creatureSpawner;
        Directional directional;
        if (!this.plugin.isPlayerLoggingEnabled(blockPlaceEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "BLOCK_PLACE", true)) {
            return;
        }
        Block block = blockPlaceEvent.getBlockPlaced();
        Material material = block.getType();
        BlockData blockData = block.getBlockData();
        Location location = block.getLocation();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("material", material.name());
        jsonObject.addProperty("blockData", blockData.getAsString());
        if (blockData instanceof Directional) {
            directional = (Directional)blockData;
            jsonObject.addProperty("facing", directional.getFacing().name());
        }
        if (blockData instanceof Chest) {
            directional = (Chest)blockData;
            jsonObject.addProperty("chestType", directional.getType().name());
        }
        if (material == Material.SPAWNER && (directional = block.getState(false)) instanceof CreatureSpawner) {
            creatureSpawner = (CreatureSpawner)directional;
            try {
                jsonObject.addProperty("spawnerType", creatureSpawner.getSpawnedType().name());
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
        }
        this.databaseMariadb.logActionAsync(blockPlaceEvent.getPlayer().getUniqueId(), blockPlaceEvent.getPlayer().getName(), "BLOCK_PLACE", "placed " + material.toString().toLowerCase(), jsonObject.toString(), block.getLocation());
        try {
            directional = block.getState(false);
            if (directional instanceof ShulkerBox) {
                creatureSpawner = (ShulkerBox)directional;
                Inventory inventory = creatureSpawner.getInventory();
                boolean bl = false;
                for (ItemStack n2 : inventory.getContents()) {
                    if (n2 == null || n2.getType().isAir()) continue;
                    bl = true;
                    break;
                }
                if (bl) {
                    ItemStack n2;
                    byte[] byArray = BinaryInventoryUtil.serializeCompressed(inventory.getContents(), this.plugin);
                    JsonObject jsonObject2 = new JsonObject();
                    jsonObject2.addProperty("blockMaterial", block.getType().name());
                    jsonObject2.addProperty("blockData", block.getBlockData().getAsString());
                    jsonObject2.addProperty("containerStructure", "SHULKER_BOX");
                    n2 = creatureSpawner.getBlockData();
                    if (n2 instanceof Directional) {
                        Directional hashMap = (Directional)n2;
                        jsonObject2.addProperty("facing", hashMap.getFacing().name());
                    }
                    HashMap<String, Integer> hashMap = new HashMap<String, Integer>();
                    int n3 = 0;
                    int n4 = 0;
                    for (ItemStack itemStack : inventory.getContents()) {
                        if (itemStack == null || itemStack.getType().isAir()) continue;
                        hashMap.merge(itemStack.getType().name(), itemStack.getAmount(), Integer::sum);
                        ++n4;
                        n3 += itemStack.getAmount();
                    }
                    JsonObject jsonObject3 = new JsonObject();
                    hashMap.forEach((arg_0, arg_1) -> ((JsonObject)jsonObject3).addProperty(arg_0, arg_1));
                    jsonObject2.add("hist", (JsonElement)jsonObject3);
                    UUID uUID = blockPlaceEvent.getPlayer().getUniqueId();
                    String string = blockPlaceEvent.getPlayer().getName();
                    ((CompletableFuture)this.databaseMariadb.saveSnapshotAsyncWithPreview(uUID, string, "CONTAINER_SNAPSHOT", byArray, location, jsonObject2.toString(), n4, n3).thenAccept(n -> this.databaseMariadb.logActionAsyncWithSnapshotId(uUID, string, "CONTAINER_SNAPSHOT", "Inventory state on place", jsonObject2.toString(), location, (Integer)n))).exceptionally(throwable -> {
                        this.plugin.getLogger().log(Level.SEVERE, "Snapshot-on-place failed @ " + String.valueOf(location), (Throwable)throwable);
                        return null;
                    });
                }
            }
        }
        catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Error snapshotting shulker on place @ " + String.valueOf(location), exception);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onHangingPlace(HangingPlaceEvent hangingPlaceEvent) {
        Object object;
        if (!this.plugin.isPlayerLoggingEnabled(hangingPlaceEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "ENTITY_PLACE", true)) {
            return;
        }
        Player player = hangingPlaceEvent.getPlayer();
        Hanging hanging = hangingPlaceEvent.getEntity();
        Location location = hanging.getLocation();
        String string = null;
        switch (hanging.getType()) {
            case ITEM_FRAME: 
            case GLOW_ITEM_FRAME: {
                object = (ItemFrame)hanging;
                string = EntityMetadataUtil.encodeItemFrame(object);
                Block block = hanging.getLocation().getBlock().getRelative(hanging.getAttachedFace());
                location = block.getLocation();
                break;
            }
            case PAINTING: {
                object = (Painting)hanging;
                string = EntityMetadataUtil.encodePainting((Painting)object);
                Block block = hanging.getLocation().getBlock().getRelative(object.getAttachedFace());
                location = block.getLocation();
                break;
            }
            case ARMOR_STAND: {
                string = EntityMetadataUtil.encodeArmorStand((ArmorStand)hanging);
                break;
            }
            default: {
                string = null;
            }
        }
        object = "placed " + hanging.getType().name().toLowerCase(Locale.ROOT);
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "ENTITY_PLACE", (String)object, string, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPaintingBreakByEntity(HangingBreakByEntityEvent hangingBreakByEntityEvent) {
        Hanging hanging = hangingBreakByEntityEvent.getEntity();
        if (!(hanging instanceof Painting)) {
            return;
        }
        Painting painting = (Painting)hanging;
        hanging = this.getResponsiblePlayer(hangingBreakByEntityEvent.getRemover());
        if (hanging == null) {
            return;
        }
        if (!this.plugin.isPlayerLoggingEnabled(hangingBreakByEntityEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "BLOCK_BREAK", true)) {
            return;
        }
        Block block = painting.getLocation().getBlock().getRelative(painting.getAttachedFace());
        Location location = block.getLocation();
        String string = "broken painting (" + painting.getArt().name().toLowerCase(Locale.ROOT) + ")";
        String string2 = EntityMetadataUtil.encodePainting(painting);
        this.databaseMariadb.logActionAsync(hanging.getUniqueId(), hanging.getName(), "BLOCK_BREAK", string, string2, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemFrameInteract(PlayerInteractEntityEvent playerInteractEntityEvent) {
        Entity entity = playerInteractEntityEvent.getRightClicked();
        if (!(entity instanceof ItemFrame)) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame)entity;
        if (playerInteractEntityEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!this.plugin.isPlayerLoggingEnabled(playerInteractEntityEvent.getPlayer().getWorld())) {
            return;
        }
        entity = playerInteractEntityEvent.getPlayer();
        ItemStack itemStack = entity.getInventory().getItemInMainHand();
        if (itemStack.getType() != Material.AIR && itemFrame.getItem().getType() == Material.AIR) {
            JsonObject jsonObject;
            if (!this.plugin.isActionLoggingEnabled("player", "CONTAINER_ADD", true)) {
                return;
            }
            Block block = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());
            Location location = block.getLocation();
            String string = ItemMetadataUtil.extractItemMetadata(itemStack);
            ItemMetadataUtil.ItemSearchData itemSearchData = ItemMetadataUtil.extractSearchData(itemStack);
            String string2 = itemStack.getType().toString().toLowerCase(Locale.ROOT);
            String string3 = "added " + string2 + " to " + itemFrame.getType().name().toLowerCase(Locale.ROOT);
            JsonObject jsonObject2 = new JsonObject();
            jsonObject2.addProperty("timestamp", (Number)System.currentTimeMillis());
            if (string != null) {
                jsonObject = JsonParser.parseString((String)string).getAsJsonObject();
            } else {
                jsonObject = new JsonObject();
                jsonObject.addProperty("material", itemStack.getType().name());
                jsonObject.addProperty("amount", (Number)itemStack.getAmount());
            }
            jsonObject2.add("item", (JsonElement)jsonObject);
            if (jsonObject.has("material")) {
                jsonObject2.addProperty("material", jsonObject.get("material").getAsString());
            }
            if (jsonObject.has("displayName")) {
                jsonObject2.add("displayName", jsonObject.get("displayName"));
            }
            if (jsonObject.has("lore")) {
                jsonObject2.add("lore", jsonObject.get("lore"));
            }
            if (jsonObject.has("enchantments")) {
                jsonObject2.add("enchantments", jsonObject.get("enchantments"));
            }
            jsonObject2.addProperty("entity", itemFrame.getType().name());
            jsonObject2.addProperty("facing", itemFrame.getFacing().name());
            jsonObject2.addProperty("rotation", itemFrame.getRotation().name());
            jsonObject2.addProperty("invisible", Boolean.valueOf(itemFrame.isInvisible()));
            jsonObject2.addProperty("fixed", Boolean.valueOf(itemFrame.isFixed()));
            jsonObject2.addProperty("anchorX", (Number)location.getBlockX());
            jsonObject2.addProperty("anchorY", (Number)location.getBlockY());
            jsonObject2.addProperty("anchorZ", (Number)location.getBlockZ());
            this.databaseMariadb.logActionAsync(entity.getUniqueId(), entity.getName(), "CONTAINER_ADD", string3, jsonObject2.toString(), itemSearchData, location);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemFrameBreak(HangingBreakByEntityEvent hangingBreakByEntityEvent) {
        Object object;
        String string;
        Location location;
        ItemStack itemStack;
        Hanging hanging = hangingBreakByEntityEvent.getEntity();
        if (!(hanging instanceof ItemFrame)) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame)hanging;
        hanging = this.getResponsiblePlayer(hangingBreakByEntityEvent.getRemover());
        if (hanging == null) {
            return;
        }
        if (!this.plugin.isPlayerLoggingEnabled(hangingBreakByEntityEvent.getEntity().getWorld())) {
            return;
        }
        if (this.plugin.isActionLoggingEnabled("player", "BLOCK_BREAK", true)) {
            itemStack = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());
            location = itemStack.getLocation();
            string = EntityMetadataUtil.encodeItemFrame(itemFrame);
            object = "broken " + itemFrame.getType().name().toLowerCase(Locale.ROOT);
            this.databaseMariadb.logActionAsync(hanging.getUniqueId(), hanging.getName(), "BLOCK_BREAK", (String)object, string, location);
        }
        if ((itemStack = itemFrame.getItem()) != null && itemStack.getType() != Material.AIR) {
            if (!this.plugin.isActionLoggingEnabled("player", "CONTAINER_TAKE", true)) {
                return;
            }
            location = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());
            string = location.getLocation();
            object = ItemMetadataUtil.extractSearchData(itemStack);
            String string2 = "removed " + (String)(itemStack.getAmount() > 1 ? "x" + itemStack.getAmount() + " " : "") + itemStack.getType().toString().toLowerCase(Locale.ROOT) + " from " + itemFrame.getType().name().toLowerCase(Locale.ROOT) + " (by breaking frame)";
            String string3 = PlayerActionListener.itemMetaOrBasic(itemStack);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("timestamp", (Number)System.currentTimeMillis());
            if (string3 != null) {
                try {
                    jsonObject.add("item", JsonParser.parseString((String)string3));
                }
                catch (JsonSyntaxException jsonSyntaxException) {
                    this.plugin.getLogger().warning("Failed to parse item JSON for item frame TAKE (break) log: " + string3 + " - Error: " + jsonSyntaxException.getMessage());
                    jsonObject.addProperty("item_nbt_parse_error", string3);
                }
            }
            jsonObject.addProperty("entity", itemFrame.getType().name());
            jsonObject.addProperty("facing", itemFrame.getFacing().name());
            jsonObject.addProperty("rotation", itemFrame.getRotation().name());
            jsonObject.addProperty("invisible", Boolean.valueOf(itemFrame.isInvisible()));
            jsonObject.addProperty("fixed", Boolean.valueOf(itemFrame.isFixed()));
            jsonObject.addProperty("anchorX", (Number)string.getBlockX());
            jsonObject.addProperty("anchorY", (Number)string.getBlockY());
            jsonObject.addProperty("anchorZ", (Number)string.getBlockZ());
            this.databaseMariadb.logActionAsync(hanging.getUniqueId(), hanging.getName(), "CONTAINER_TAKE", string2, jsonObject.toString(), (ItemMetadataUtil.ItemSearchData)object, (Location)string);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemFrameHit(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Entity entity = entityDamageByEntityEvent.getEntity();
        if (!(entity instanceof ItemFrame)) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame)entity;
        entity = this.getResponsiblePlayer(entityDamageByEntityEvent.getDamager());
        if (entity == null) {
            return;
        }
        if (!this.plugin.isPlayerLoggingEnabled(entityDamageByEntityEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "CONTAINER_TAKE", true)) {
            return;
        }
        ItemStack itemStack = itemFrame.getItem();
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }
        Block block = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());
        Location location = block.getLocation();
        ItemMetadataUtil.ItemSearchData itemSearchData = ItemMetadataUtil.extractSearchData(itemStack);
        String string = itemStack.getType().toString().toLowerCase(Locale.ROOT);
        String string2 = "removed " + (String)(itemStack.getAmount() > 1 ? "x" + itemStack.getAmount() + " " : "") + string + " from " + itemFrame.getType().name().toLowerCase(Locale.ROOT);
        String string3 = PlayerActionListener.itemMetaOrBasic(itemStack);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("timestamp", (Number)System.currentTimeMillis());
        if (string3 != null) {
            try {
                jsonObject.add("item", JsonParser.parseString((String)string3));
            }
            catch (JsonSyntaxException jsonSyntaxException) {
                this.plugin.getLogger().warning("Failed to parse item JSON for item frame TAKE (hit) log: " + string3 + " - Error: " + jsonSyntaxException.getMessage());
                jsonObject.addProperty("item_nbt_parse_error", string3);
            }
        }
        jsonObject.addProperty("entity", itemFrame.getType().name());
        jsonObject.addProperty("facing", itemFrame.getFacing().name());
        jsonObject.addProperty("rotation", itemFrame.getRotation().name());
        jsonObject.addProperty("invisible", Boolean.valueOf(itemFrame.isInvisible()));
        jsonObject.addProperty("fixed", Boolean.valueOf(itemFrame.isFixed()));
        jsonObject.addProperty("anchorX", (Number)location.getBlockX());
        jsonObject.addProperty("anchorY", (Number)location.getBlockY());
        jsonObject.addProperty("anchorZ", (Number)location.getBlockZ());
        this.databaseMariadb.logActionAsync(entity.getUniqueId(), entity.getName(), "CONTAINER_TAKE", string2, jsonObject.toString(), itemSearchData, location);
    }

    @Nullable
    private Player getResponsiblePlayer(Entity entity) {
        Projectile projectile;
        ProjectileSource projectileSource;
        if (entity instanceof Player) {
            Player player = (Player)entity;
            return player;
        }
        if (entity instanceof Projectile && (projectileSource = (projectile = (Projectile)entity).getShooter()) instanceof Player) {
            Player player = (Player)projectileSource;
            return player;
        }
        return null;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntityPlace(EntityPlaceEvent entityPlaceEvent) {
        JsonObject jsonObject;
        Object object;
        Entity entity = entityPlaceEvent.getEntity();
        if (!(entity instanceof Boat) && !(entity instanceof ArmorStand)) {
            return;
        }
        Player player = entityPlaceEvent.getPlayer();
        if (player == null) {
            return;
        }
        if (!this.plugin.isPlayerLoggingEnabled(entity.getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "ENTITY_PLACE", true)) {
            return;
        }
        Location location = entity.getLocation();
        if (entity instanceof ArmorStand) {
            object = (ArmorStand)entity;
            jsonObject = EntityMetadataUtil.parse(EntityMetadataUtil.encodeArmorStand(object));
        } else {
            jsonObject = new JsonObject();
        }
        JsonObject jsonObject2 = jsonObject;
        jsonObject2.addProperty("entity", entity.getType().name());
        jsonObject2.addProperty("entityUuid", entity.getUniqueId().toString());
        jsonObject2.addProperty("material", entity instanceof ArmorStand ? Material.ARMOR_STAND.name() : entity.getType().name());
        jsonObject2.addProperty("yaw", (Number)Float.valueOf(location.getYaw()));
        jsonObject2.addProperty("timestamp", (Number)System.currentTimeMillis());
        object = "placed " + entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "ENTITY_PLACE", (String)object, jsonObject2.toString(), location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent playerArmorStandManipulateEvent) {
        if (!this.plugin.isPlayerLoggingEnabled(playerArmorStandManipulateEvent.getPlayer().getWorld())) {
            return;
        }
        boolean bl = this.plugin.isActionLoggingEnabled("player", "CONTAINER_ADD", true);
        boolean bl2 = this.plugin.isActionLoggingEnabled("player", "CONTAINER_TAKE", true);
        if (!bl && !bl2) {
            return;
        }
        if (playerArmorStandManipulateEvent.getHand() == EquipmentSlot.OFF_HAND && playerArmorStandManipulateEvent.getPlayer().getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }
        Player player = playerArmorStandManipulateEvent.getPlayer();
        ArmorStand armorStand = playerArmorStandManipulateEvent.getRightClicked();
        Location location = armorStand.getLocation();
        ItemStack itemStack = PlayerActionListener.cloneOrNull(playerArmorStandManipulateEvent.getPlayerItem());
        ItemStack itemStack2 = PlayerActionListener.cloneOrNull(playerArmorStandManipulateEvent.getArmorStandItem());
        boolean bl3 = PlayerActionListener.isAir(itemStack);
        boolean bl4 = PlayerActionListener.isAir(itemStack2);
        if (bl3 && bl4) {
            return;
        }
        if (!bl3 && bl4) {
            if (!bl) {
                return;
            }
            this.logItem(player, location, "CONTAINER_ADD", "added " + itemStack.getType().name().toLowerCase(Locale.ROOT) + " to armor_stand", itemStack);
            return;
        }
        if (bl3 && !bl4) {
            if (!bl2) {
                return;
            }
            this.logItem(player, location, "CONTAINER_TAKE", "removed " + itemStack2.getType().name().toLowerCase(Locale.ROOT) + " from armor_stand", itemStack2);
            return;
        }
        if (bl2) {
            this.logItem(player, location, "CONTAINER_TAKE", "removed " + itemStack2.getType().name().toLowerCase(Locale.ROOT) + " from armor_stand (swap)", itemStack2);
        }
        if (bl) {
            this.logItem(player, location, "CONTAINER_ADD", "added " + itemStack.getType().name().toLowerCase(Locale.ROOT) + " to armor_stand (swap)", itemStack);
        }
    }

    private static boolean isAir(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    private static ItemStack cloneOrNull(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private void logItem(Player player, Location location, String string, String string2, ItemStack itemStack) {
        String string3 = PlayerActionListener.itemMetaOrBasic(itemStack);
        ItemMetadataUtil.ItemSearchData itemSearchData = itemStack == null || itemStack.getType() == Material.AIR ? null : ItemMetadataUtil.extractSearchData(itemStack);
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), string, string2, string3, itemSearchData, location);
    }

    private static String itemMetaOrBasic(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        String string = ItemMetadataUtil.extractItemMetadata(itemStack);
        if (string != null && !string.isBlank()) {
            return string;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("material", itemStack.getType().name());
        jsonObject.addProperty("amount", (Number)itemStack.getAmount());
        return jsonObject.toString();
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onArmorStandHit(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Entity entity = entityDamageByEntityEvent.getEntity();
        if (!(entity instanceof ArmorStand)) {
            return;
        }
        ArmorStand armorStand = (ArmorStand)entity;
        entity = this.getResponsiblePlayer(entityDamageByEntityEvent.getDamager());
        if (entity == null) {
            return;
        }
        if (!this.plugin.isPlayerLoggingEnabled(entityDamageByEntityEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "BLOCK_BREAK", true)) {
            return;
        }
        Location location = armorStand.getLocation();
        String string = "broken armor_stand";
        String string2 = EntityMetadataUtil.encodeArmorStand(armorStand);
        this.databaseMariadb.logActionAsync(entity.getUniqueId(), entity.getName(), "BLOCK_BREAK", string, string2, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemPickup(EntityPickupItemEvent entityPickupItemEvent) {
        if (!this.plugin.isPlayerLoggingEnabled(entityPickupItemEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "ITEM_PICKUP", true)) {
            return;
        }
        LivingEntity livingEntity = entityPickupItemEvent.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player player = (Player)livingEntity;
        livingEntity = player.getLocation();
        ItemStack itemStack = entityPickupItemEvent.getItem().getItemStack();
        int n = itemStack.getAmount();
        Material material = itemStack.getType();
        String string = "Picked up " + (String)(n > 1 ? "x" + n + " " : "") + material.toString().toLowerCase(Locale.ROOT);
        String string2 = ItemMetadataUtil.extractItemMetadata(itemStack);
        ItemMetadataUtil.ItemSearchData itemSearchData = ItemMetadataUtil.extractSearchData(itemStack);
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "ITEM_PICKUP", string, string2, itemSearchData, (Location)livingEntity);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        Player player = playerJoinEvent.getPlayer();
        String string = player.getName();
        UUID uUID = player.getUniqueId();
        this.databaseMariadb.updatePlayerNameInLogs(uUID, string);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onItemDrop(PlayerDropItemEvent playerDropItemEvent) {
        if (!this.plugin.isPlayerLoggingEnabled(playerDropItemEvent.getPlayer().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "ITEM_DROP", true)) {
            return;
        }
        Player player = playerDropItemEvent.getPlayer();
        ItemStack itemStack = playerDropItemEvent.getItemDrop().getItemStack();
        Material material = itemStack.getType();
        String string = ItemMetadataUtil.extractItemMetadata(itemStack);
        ItemMetadataUtil.ItemSearchData itemSearchData = ItemMetadataUtil.extractSearchData(itemStack);
        Location location = player.getLocation();
        DropKey dropKey2 = new DropKey(player.getUniqueId(), material, string);
        this.dropAggregate.compute(dropKey2, (dropKey, dropAggregateData) -> {
            if (dropAggregateData == null) {
                return new DropAggregateData(itemStack.getAmount(), location);
            }
            return dropAggregateData.incrementCount(itemStack.getAmount());
        });
        BukkitTask bukkitTask = this.scheduledDrops.remove(dropKey2);
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
        UUID uUID = player.getUniqueId();
        String string2 = player.getName();
        BukkitTask bukkitTask2 = Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            DropAggregateData dropAggregateData = this.dropAggregate.remove(dropKey2);
            this.scheduledDrops.remove(dropKey2);
            if (dropAggregateData != null) {
                String string3 = "Dropped " + (String)(dropAggregateData.getCount() > 1 ? "x" + dropAggregateData.getCount() + " " : "") + material.toString().toLowerCase(Locale.ROOT);
                this.databaseMariadb.logActionAsync(uUID, string2, "ITEM_DROP", string3, string, itemSearchData, dropAggregateData.getLocation());
            }
        }, 10L);
        this.scheduledDrops.put(dropKey2, bukkitTask2);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBucketEmpty(PlayerBucketEmptyEvent playerBucketEmptyEvent) {
        Location location;
        if (!this.plugin.isPlayerLoggingEnabled(playerBucketEmptyEvent.getBlockClicked().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "BUCKET_EMPTY", true)) {
            return;
        }
        Player player = playerBucketEmptyEvent.getPlayer();
        Location location2 = playerBucketEmptyEvent.getBlockClicked().getRelative(playerBucketEmptyEvent.getBlockFace()).getLocation();
        Material material = playerBucketEmptyEvent.getBucket();
        String string = material == Material.WATER_BUCKET ? "water" : (material == Material.LAVA_BUCKET ? "lava" : "unknown");
        JsonObject jsonObject = new JsonObject();
        if (material == Material.WATER_BUCKET) {
            jsonObject.addProperty("material", "WATER");
            jsonObject.addProperty("blockData", "minecraft:water[level=0]");
        } else if (material == Material.LAVA_BUCKET) {
            jsonObject.addProperty("material", "LAVA");
            jsonObject.addProperty("blockData", "minecraft:lava[level=0]");
        }
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "BUCKET_EMPTY", string, jsonObject.size() == 0 ? null : jsonObject.toString(), location2);
        if (material == Material.LAVA_BUCKET) {
            location = location2.clone();
            lavaPlacements.put(location, player.getUniqueId());
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> lavaPlacements.remove(location), 300L);
        }
        location = playerBucketEmptyEvent.getBlockClicked();
        Location location3 = location.getLocation();
        UUID uUID = player.getUniqueId();
        String string2 = player.getName();
        Material material2 = material;
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            Waterlogged waterlogged;
            Block block = location2.getBlock();
            BlockData blockData = block.getBlockData();
            boolean bl = blockData instanceof Waterlogged && (waterlogged = (Waterlogged)blockData).isWaterlogged();
        });
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.lambda$onBucketEmpty$9((Block)location, material2, uUID, string2, location3), 1L);
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.lambda$onBucketEmpty$10((Block)location, material2, uUID, string2, location3), 3L);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerInteract(PlayerInteractEvent playerInteractEvent) {
        Material material;
        if (!this.plugin.isPlayerLoggingEnabled(playerInteractEvent.getPlayer().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "INTERACT", true)) {
            return;
        }
        if (playerInteractEvent.getAction() == Action.RIGHT_CLICK_BLOCK && playerInteractEvent.getClickedBlock() != null && (material = playerInteractEvent.getClickedBlock().getType()).isInteractable()) {
            Location location = playerInteractEvent.getClickedBlock().getLocation();
            String string = "Interacted with " + material.toString().toLowerCase();
            this.databaseMariadb.logActionAsync(playerInteractEvent.getPlayer().getUniqueId(), playerInteractEvent.getPlayer().getName(), "INTERACT", string, null, location);
        }
    }

    private boolean diffAndLogContainer(Player player, Inventory inventory, ContainerKey containerKey, ItemStack[] itemStackArray, boolean bl, String string) {
        boolean bl2 = false;
        HashMap<ContainerItemKey, Integer> hashMap = new HashMap<ContainerItemKey, Integer>();
        HashMap<ContainerItemKey, ItemMetadataUtil.ItemSearchData> hashMap2 = new HashMap<ContainerItemKey, ItemMetadataUtil.ItemSearchData>();
        for (ItemStack itemStack2 : itemStackArray) {
            if (itemStack2 == null || itemStack2.getType() == Material.AIR) continue;
            String string2 = ItemMetadataUtil.extractItemMetadata(itemStack2);
            ContainerItemKey containerItemKey = new ContainerItemKey(itemStack2.getType(), string2);
            hashMap.merge(containerItemKey, itemStack2.getAmount(), Integer::sum);
            hashMap2.putIfAbsent(containerItemKey, ItemMetadataUtil.extractSearchData(itemStack2));
        }
        HashMap hashMap3 = new HashMap();
        HashMap<ContainerItemKey, ItemMetadataUtil.ItemSearchData> hashMap4 = new HashMap<ContainerItemKey, ItemMetadataUtil.ItemSearchData>();
        for (ItemStack itemStack2 : inventory.getContents()) {
            if (itemStack2 == null || itemStack2.getType() == Material.AIR) continue;
            String string2 = ItemMetadataUtil.extractItemMetadata(itemStack2);
            ContainerItemKey containerItemKey = new ContainerItemKey(itemStack2.getType(), string2);
            hashMap3.merge(containerItemKey, itemStack2.getAmount(), Integer::sum);
            hashMap4.putIfAbsent(containerItemKey, ItemMetadataUtil.extractSearchData(itemStack2));
        }
        HashSet hashSet = new HashSet(hashMap.keySet());
        hashSet.addAll(hashMap3.keySet());
        ItemStack[] itemStackArray2 = hashSet.iterator();
        while (itemStackArray2.hasNext()) {
            String string3;
            int n;
            ContainerItemKey containerItemKey = (ContainerItemKey)itemStackArray2.next();
            int n2 = hashMap.getOrDefault(containerItemKey, 0);
            int n3 = hashMap3.getOrDefault(containerItemKey, 0);
            if (n3 > n2) {
                bl2 = true;
                n = n3 - n2;
                string3 = "added " + (String)(n > 1 ? "x" + n + " " : "") + containerItemKey.getMaterial().toString().toLowerCase(Locale.ROOT);
                this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "CONTAINER_ADD", string3, containerItemKey.getMeta(), (ItemMetadataUtil.ItemSearchData)hashMap4.get(containerItemKey), containerKey.getLocation());
                continue;
            }
            if (n3 >= n2) continue;
            bl2 = true;
            n = n2 - n3;
            string3 = "removed " + (String)(n > 1 ? "x" + n + " " : "") + containerItemKey.getMaterial().toString().toLowerCase(Locale.ROOT);
            this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "CONTAINER_TAKE", string3, containerItemKey.getMeta(), (ItemMetadataUtil.ItemSearchData)hashMap2.get(containerItemKey), containerKey.getLocation());
        }
        if (bl2) {
            this.containerTouched.put(containerKey, true);
        }
        if (bl2 && bl) {
            itemStackArray2 = (ItemStack[])Arrays.stream(inventory.getContents()).map(itemStack -> itemStack == null ? null : itemStack.clone()).toArray(ItemStack[]::new);
            this.containerSnapshots.put(containerKey, itemStackArray2);
        }
        return bl2;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onInventoryClickMonitor(InventoryClickEvent inventoryClickEvent) {
        if (!this.plugin.isPlayerLoggingEnabled() || !this.plugin.isActionLoggingEnabled("player", "CONTAINER_ADD", true) && !this.plugin.isActionLoggingEnabled("player", "CONTAINER_TAKE", true)) {
            return;
        }
        HumanEntity humanEntity = inventoryClickEvent.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        humanEntity = inventoryClickEvent.getView().getTopInventory();
        Location location = inventoryClickEvent.getView().getTopInventory().getLocation();
        if (location == null || !this.plugin.isPlayerLoggingEnabled(location.getWorld())) {
            return;
        }
        Location location2 = location.getBlock().getLocation();
        ContainerKey containerKey = new ContainerKey(player.getUniqueId(), location2);
        ItemStack[] itemStackArray = this.containerSnapshots.get(containerKey);
        if (itemStackArray == null) {
            return;
        }
        String string = this.containerSession.getOrDefault(containerKey, "----");
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            Inventory inventory = inventoryClickEvent.getView().getTopInventory();
            if (inventory.getLocation() != null && inventory.getLocation().getBlock().getLocation().equals((Object)location2)) {
                this.diffAndLogContainer(player, inventory, containerKey, Arrays.copyOf(itemStackArray, itemStackArray.length), true, "CLICK");
            }
        }, 2L);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onInventoryDragMonitor(InventoryDragEvent inventoryDragEvent) {
        if (!this.plugin.isPlayerLoggingEnabled() || !this.plugin.isActionLoggingEnabled("player", "CONTAINER_ADD", true) && !this.plugin.isActionLoggingEnabled("player", "CONTAINER_TAKE", true)) {
            return;
        }
        HumanEntity humanEntity = inventoryDragEvent.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        humanEntity = inventoryDragEvent.getView().getTopInventory();
        Location location = humanEntity.getLocation();
        if (location == null || !this.plugin.isWorldAllowed(location.getWorld())) {
            return;
        }
        Location location2 = location.getBlock().getLocation();
        ContainerKey containerKey = new ContainerKey(player.getUniqueId(), location2);
        ItemStack[] itemStackArray = this.containerSnapshots.get(containerKey);
        if (itemStackArray == null) {
            return;
        }
        String string = this.containerSession.getOrDefault(containerKey, "----");
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            Inventory inventory = inventoryDragEvent.getView().getTopInventory();
            if (inventory.getLocation() != null && inventory.getLocation().getBlock().getLocation().equals((Object)location2)) {
                this.diffAndLogContainer(player, inventory, containerKey, Arrays.copyOf(itemStackArray, itemStackArray.length), true, "DRAG");
            }
        }, 2L);
    }

    private boolean debugContainers() {
        try {
            return this.plugin.getConfig().getBoolean("debug.container", true);
        }
        catch (Exception exception) {
            return true;
        }
    }

    private static String shortMeta(String string) {
        if (string == null) {
            return "";
        }
        String string2 = string.replaceAll("\\s+", "");
        return string2.length() <= 16 ? string2 : string2.substring(0, 8) + "\u2026" + string2.substring(string2.length() - 5);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onInventoryOpen(InventoryOpenEvent inventoryOpenEvent) {
        if (!this.plugin.isPlayerLoggingEnabled(inventoryOpenEvent.getPlayer().getWorld())) {
            return;
        }
        Inventory inventory = inventoryOpenEvent.getInventory();
        Location location = inventoryOpenEvent.getInventory().getLocation();
        if (location == null || !this.plugin.isWorldAllowed(location.getWorld())) {
            return;
        }
        Player player = (Player)inventoryOpenEvent.getPlayer();
        Location location2 = location.getBlock().getLocation();
        ContainerKey containerKey = new ContainerKey(player.getUniqueId(), location2);
        ItemStack[] itemStackArray = (ItemStack[])Arrays.stream(inventory.getContents()).map(itemStack -> itemStack == null ? null : itemStack.clone()).toArray(ItemStack[]::new);
        this.containerSnapshots.put(containerKey, itemStackArray);
        String string = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        this.containerSession.put(containerKey, string);
        this.containerTouched.put(containerKey, false);
        this.containerChangeSeq.put(containerKey, 0);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * WARNING - void declaration
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent inventoryCloseEvent) {
        if (!this.plugin.isPlayerLoggingEnabled(inventoryCloseEvent.getPlayer().getWorld())) {
            return;
        }
        Inventory inventory = inventoryCloseEvent.getInventory();
        Location location = inventory.getLocation();
        if (location == null || !this.plugin.isWorldAllowed(location.getWorld())) {
            return;
        }
        Player player = (Player)inventoryCloseEvent.getPlayer();
        Location location2 = location.getBlock().getLocation();
        ContainerKey containerKey = new ContainerKey(player.getUniqueId(), location2);
        ItemStack[] itemStackArray = this.containerSnapshots.get(containerKey);
        if (itemStackArray == null) {
            return;
        }
        boolean bl = this.diffAndLogContainer(player, inventory, containerKey, itemStackArray, false, "CLOSE");
        boolean bl2 = this.containerTouched.getOrDefault(containerKey, false);
        if (!bl && !bl2) {
            this.containerSnapshots.remove(containerKey);
            this.containerTouched.remove(containerKey);
            this.containerChangeSeq.remove(containerKey);
            this.containerSession.remove(containerKey);
            return;
        }
        try {
            int var20_25 = 0;
            Object object;
            org.bukkit.block.Chest chest;
            DoubleChest doubleChest;
            byte[] byArray = BinaryInventoryUtil.serializeCompressed(inventory.getContents(), this.plugin);
            JsonObject jsonObject = new JsonObject();
            InventoryHolder inventoryHolder = inventory.getHolder();
            if (inventoryHolder instanceof DoubleChest) {
                doubleChest = (DoubleChest)inventoryHolder;
                jsonObject.addProperty("containerStructure", "DOUBLE");
                var16_15 = location2.getBlock();
                if (var16_15.getState(false) instanceof org.bukkit.block.Chest) {
                    var17_17 = (ItemStack[])var16_15.getBlockData();
                    jsonObject.addProperty("facing", var17_17.getFacing().name());
                    jsonObject.addProperty("chestType", var17_17.getType().name());
                }
                var17_17 = null;
                try {
                    InventoryHolder inventoryHolder2 = doubleChest.getLeftSide();
                    if (inventoryHolder2 instanceof org.bukkit.block.Chest) {
                        org.bukkit.block.Chest chest2 = (org.bukkit.block.Chest)inventoryHolder2;
                        InventoryHolder inventoryHolder3 = doubleChest.getRightSide();
                        if (inventoryHolder3 instanceof org.bukkit.block.Chest) {
                            org.bukkit.block.Chest chest3 = (org.bukkit.block.Chest)inventoryHolder3;
                            var17_17 = location2.equals((Object)chest2.getLocation()) ? chest3.getLocation() : chest2.getLocation();
                        }
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
                if (var17_17 != null) {
                    jsonObject.addProperty("partnerX", (Number)var17_17.getBlockX());
                    jsonObject.addProperty("partnerY", (Number)var17_17.getBlockY());
                    jsonObject.addProperty("partnerZ", (Number)var17_17.getBlockZ());
                }
            } else if (inventoryHolder instanceof org.bukkit.block.Chest) {
                chest = (org.bukkit.block.Chest)inventoryHolder;
                jsonObject.addProperty("containerStructure", "SINGLE");
                var16_15 = (Chest)chest.getBlockData();
                jsonObject.addProperty("facing", var16_15.getFacing().name());
                jsonObject.addProperty("chestType", var16_15.getType().name());
            } else if (inventoryHolder instanceof Barrel) {
                object = (Barrel)inventoryHolder;
                jsonObject.addProperty("containerStructure", "BARREL");
                jsonObject.addProperty("facing", ((Directional)object.getBlockData()).getFacing().name());
            } else if (inventoryHolder != null) {
                jsonObject.addProperty("containerStructure", "OTHER");
                jsonObject.addProperty("actualHolderType", inventoryHolder.getClass().getSimpleName().toUpperCase());
                var17_17 = location2.getBlock().getState().getBlockData();
                if (var17_17 instanceof Directional) {
                    var16_15 = (Directional)var17_17;
                    jsonObject.addProperty("facing", var16_15.getFacing().name());
                }
            } else {
                jsonObject.addProperty("containerStructure", "UNKNOWN_NULL_HOLDER");
            }
            doubleChest = new JsonObject();
            jsonObject.entrySet().forEach(arg_0 -> PlayerActionListener.lambda$onInventoryClose$17((JsonObject)doubleChest, arg_0));
            chest = location2.getBlock();
            jsonObject.addProperty("blockMaterial", chest.getType().name());
            jsonObject.addProperty("blockData", chest.getBlockData().getAsString());
            doubleChest.addProperty("blockMaterial", chest.getType().name());
            doubleChest.addProperty("blockData", chest.getBlockData().getAsString());
            object = new HashMap();
            int n2 = 0;
            for (ItemStack itemStack : inventory.getContents()) {
                if (itemStack == null || itemStack.getType().isAir()) continue;
                object.merge(itemStack.getType().name(), itemStack.getAmount(), Integer::sum);
                n2 += itemStack.getAmount();
            }
            int n3 = 0;
            JsonObject jsonObject2 = inventory.getContents();
            int n4 = ((ItemStack[])jsonObject2).length;
            boolean bl3 = false;
            while (var20_25 < n4) {
                ItemStack itemStack = jsonObject2[var20_25];
                if (itemStack != null && !itemStack.getType().isAir()) {
                    ++n3;
                }
                ++var20_25;
            }
            jsonObject2 = new JsonObject();
            object.forEach((arg_0, arg_1) -> ((JsonObject)jsonObject2).addProperty(arg_0, arg_1));
            doubleChest.add("hist", (JsonElement)jsonObject2);
            ((CompletableFuture)this.databaseMariadb.saveSnapshotAsyncWithPreview(player.getUniqueId(), player.getName(), "CONTAINER_SNAPSHOT", byArray, containerKey.getLocation(), doubleChest.toString(), n3, n2).thenAccept(n -> this.databaseMariadb.logActionAsyncWithSnapshotId(player.getUniqueId(), player.getName(), "CONTAINER_SNAPSHOT", "Inventory state after interaction", jsonObject.toString(), containerKey.getLocation(), (Integer)n))).exceptionally(throwable -> {
                this.plugin.getLogger().log(Level.SEVERE, "Snapshot save with preview failed at: " + String.valueOf(containerKey.getLocation()), (Throwable)throwable);
                player.sendMessage(String.valueOf(ChatColor.RED) + "Error saving container snapshot! Please report this.");
                return null;
            });
        }
        catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Snapshot save or metadata generation failed at: " + String.valueOf(containerKey.getLocation()) + " for player " + player.getName(), exception);
            player.sendMessage(String.valueOf(ChatColor.RED) + "Error saving container snapshot! Please report this.");
        }
        finally {
            this.containerSnapshots.remove(containerKey);
            this.containerTouched.remove(containerKey);
            this.containerChangeSeq.remove(containerKey);
            this.containerSession.remove(containerKey);
        }
    }

    private static BlockFace getFacingForInventory(Inventory inventory) {
        BlockFace blockFace;
        InventoryHolder inventoryHolder = inventory.getHolder();
        if (inventoryHolder instanceof DoubleChest) {
            org.bukkit.block.Chest chest;
            BlockData blockData;
            DoubleChest doubleChest = (DoubleChest)inventoryHolder;
            InventoryHolder inventoryHolder2 = doubleChest.getLeftSide();
            if (inventoryHolder2 instanceof org.bukkit.block.Chest && (blockData = (chest = (org.bukkit.block.Chest)inventoryHolder2).getBlockData()) instanceof Directional) {
                Directional directional = (Directional)blockData;
                return directional.getFacing();
            }
            return null;
        }
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Block block = location.getBlock();
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional) {
            Directional directional = (Directional)blockData;
            blockFace = directional.getFacing();
        } else {
            blockFace = null;
        }
        return blockFace;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerChat(AsyncChatEvent asyncChatEvent) {
        Player player = asyncChatEvent.getPlayer();
        if (!this.plugin.isPlayerLoggingEnabled(player.getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "PLAYER_MESSAGE", true)) {
            return;
        }
        String string = PlainTextComponentSerializer.plainText().serialize(asyncChatEvent.message());
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("source", "CHAT");
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            Location location = player.getLocation();
            this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "PLAYER_MESSAGE", string, jsonObject.toString(), location);
        });
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (!this.plugin.isPlayerLoggingEnabled(player.getWorld())) {
            return;
        }
        String string = playerCommandPreprocessEvent.getMessage();
        if (string == null || string.isEmpty() || string.charAt(0) != '/') {
            return;
        }
        String string2 = string.substring(1).trim();
        String string3 = SensitiveCommandRedactor.normalize(string2);
        String[] stringArray = string2.split("\\s+");
        String string4 = stringArray.length > 0 ? stringArray[0] : "";
        String string5 = stringArray.length > 1 ? stringArray[1] : "";
        String string6 = SensitiveCommandRedactor.normalize(string4);
        String string7 = SensitiveCommandRedactor.normalize(string4 + " " + string5);
        Optional<SensitiveCommandRedactor.Redaction> optional = SensitiveCommandRedactor.redact(string, this.sensitiveCommands);
        if (optional.isPresent()) {
            if (!this.plugin.isActionLoggingEnabled("player", ActionType.PLAYER_COMMAND.name(), true)) {
                return;
            }
            String string8 = optional.get().command();
            String string9 = optional.get().storedText();
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("command", string8);
            jsonObject.addProperty("sensitive", Boolean.valueOf(true));
            jsonObject.addProperty("raw", string9);
            this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), ActionType.PLAYER_COMMAND.name(), string9, jsonObject.toString(), player.getLocation());
            return;
        }
        if (SensitiveCommandRedactor.longestPrefixMatch(this.commandBlacklist, string3).isPresent()) {
            return;
        }
        if (this.messageCommandInclude.contains(string7) || this.messageCommandInclude.contains(string6)) {
            if (!this.plugin.isActionLoggingEnabled("player", "PLAYER_MESSAGE", true)) {
                return;
            }
            String string10 = this.messageCommandInclude.contains(string7) ? string7 : string6;
            String string11 = "";
            if (stringArray.length >= 2) {
                int n = string10.equals(string7) ? 2 : 1;
                string11 = String.join((CharSequence)" ", Arrays.copyOfRange(stringArray, n, stringArray.length));
            }
            Object object = switch (string10) {
                case "me", "cmi me" -> "* " + player.getName() + " " + string11;
                case "msg", "tell", "w", "r", "cmi msg", "cmi tell", "cmi w", "cmi r" -> {
                    String var17_23 = "";
                    String var18_24 = string11;
                    if (!string10.endsWith(" r")) {
                        String[] var19_25 = string11.split("\\s+", 2);
                        var17_23 = var19_25.length > 0 ? var19_25[0] : "";
                        var18_24 = var19_25.length > 1 ? var19_25[1] : "";
                    }
                    yield "(to " + var17_23 + ") " + var18_24;
                }
                default -> string11;
            };
            String string12 = new JsonObject();
            string12.addProperty("source", "COMMAND");
            string12.addProperty("command", string10);
            string12.addProperty("raw", string);
            string12.addProperty("args", string11);
            string12.addProperty("rendered", (String)object);
            this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "PLAYER_MESSAGE", (String)object, string12.toString(), player.getLocation());
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("player", "PLAYER_COMMAND", true)) {
            return;
        }
        String string13 = string2.substring(string4.length()).trim();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("command", string4.toLowerCase(Locale.ROOT));
        jsonObject.addProperty("args", string13);
        jsonObject.addProperty("raw", string);
        this.databaseMariadb.logActionAsync(player.getUniqueId(), player.getName(), "PLAYER_COMMAND", string, jsonObject.toString(), player.getLocation());
    }

    private static /* synthetic */ void lambda$onInventoryClose$17(JsonObject jsonObject, Map.Entry entry) {
        jsonObject.add((String)entry.getKey(), (JsonElement)entry.getValue());
    }

    private /* synthetic */ void lambda$onBucketEmpty$10(Block block, Material material, UUID uUID, String string, Location location) {
        Waterlogged waterlogged;
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Waterlogged && (waterlogged = (Waterlogged)blockData).isWaterlogged()) {
            BlockData blockData2 = blockData.clone();
            ((Waterlogged)blockData2).setWaterlogged(false);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("material", block.getType().name());
            jsonObject.addProperty("blockData", blockData.getAsString());
            jsonObject.addProperty("beforeBlockData", blockData2.getAsString());
            jsonObject.addProperty("waterlogged", Boolean.valueOf(true));
            jsonObject.addProperty("fluid", material == Material.LAVA_BUCKET ? "LAVA" : "WATER");
            jsonObject.addProperty("origin", "bucket+late");
            this.databaseMariadb.logActionAsync(uUID, string, "LIQUID_FLOW", "waterlogged", jsonObject.toString(), location);
        }
    }

    private /* synthetic */ void lambda$onBucketEmpty$9(Block block, Material material, UUID uUID, String string, Location location) {
        Waterlogged waterlogged;
        boolean bl;
        BlockData blockData = block.getBlockData();
        boolean bl2 = bl = blockData instanceof Waterlogged && (waterlogged = (Waterlogged)blockData).isWaterlogged();
        if (bl) {
            waterlogged = blockData.clone();
            waterlogged.setWaterlogged(false);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("material", block.getType().name());
            jsonObject.addProperty("blockData", blockData.getAsString());
            jsonObject.addProperty("beforeBlockData", waterlogged.getAsString());
            jsonObject.addProperty("waterlogged", Boolean.valueOf(true));
            jsonObject.addProperty("fluid", material == Material.LAVA_BUCKET ? "LAVA" : "WATER");
            jsonObject.addProperty("origin", "bucket");
            this.databaseMariadb.logActionAsync(uUID, string, "LIQUID_FLOW", "waterlogged", jsonObject.toString(), location);
        }
    }
}

