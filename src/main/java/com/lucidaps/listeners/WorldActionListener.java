/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Hopper
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Waterlogged
 *  org.bukkit.entity.Painting
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.minecart.HopperMinecart
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBurnEvent
 *  org.bukkit.event.block.BlockExplodeEvent
 *  org.bukkit.event.block.BlockFormEvent
 *  org.bukkit.event.block.BlockFromToEvent
 *  org.bukkit.event.block.BlockPistonExtendEvent
 *  org.bukkit.event.block.BlockPistonRetractEvent
 *  org.bukkit.event.block.LeavesDecayEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.event.entity.EntityExplodeEvent
 *  org.bukkit.event.hanging.HangingBreakByEntityEvent
 *  org.bukkit.event.hanging.HangingBreakEvent
 *  org.bukkit.event.inventory.InventoryMoveItemEvent
 *  org.bukkit.event.weather.LightningStrikeEvent
 *  org.bukkit.event.world.PortalCreateEvent
 *  org.bukkit.event.world.StructureGrowEvent
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package com.lucidaps.listeners;

import com.google.gson.JsonObject;
import com.lucidaps.ProtectCore;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.listeners.PlayerActionListener;
import com.lucidaps.listeners.util.HopperTransferAggregateData;
import com.lucidaps.listeners.util.HopperTransferKey;
import com.lucidaps.utilities.EntityMetadataUtil;
import com.lucidaps.utilities.ItemMetadataUtil;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class WorldActionListener
implements Listener {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);
    private final Map<HopperTransferKey, HopperTransferAggregateData> hopperTransferAggregate = new ConcurrentHashMap<HopperTransferKey, HopperTransferAggregateData>();
    private final Map<HopperTransferKey, BukkitTask> scheduledHopperTransfers = new ConcurrentHashMap<HopperTransferKey, BukkitTask>();

    private static String vec(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    public WorldActionListener(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent blockExplodeEvent) {
        if (!this.plugin.isWorldLoggingEnabled(blockExplodeEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "BLOCK_EXPLODE", true)) {
            return;
        }
        Location location = blockExplodeEvent.getBlock().getLocation();
        String string = "Block exploded (yield " + blockExplodeEvent.getYield() + ")";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "BLOCK_EXPLODE", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent entityExplodeEvent) {
        if (!this.plugin.isWorldLoggingEnabled(entityExplodeEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "ENTITY_EXPLODE", true)) {
            return;
        }
        Location location = entityExplodeEvent.getLocation();
        String string = "Entity explosion triggered by " + entityExplodeEvent.getEntityType().toString().toLowerCase();
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "ENTITY_EXPLODE", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockBurn(BlockBurnEvent blockBurnEvent) {
        if (!this.plugin.isWorldLoggingEnabled(blockBurnEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "BLOCK_BURN", true)) {
            return;
        }
        Location location = blockBurnEvent.getBlock().getLocation();
        String string = "Block " + blockBurnEvent.getBlock().getType().toString().toLowerCase() + " burned";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "BLOCK_BURN", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onStructureGrow(StructureGrowEvent structureGrowEvent) {
        if (!this.plugin.isWorldLoggingEnabled(structureGrowEvent.getLocation().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "STRUCTURE_GROW", true)) {
            return;
        }
        Location location = structureGrowEvent.getLocation();
        String string = "Structure " + structureGrowEvent.getSpecies().name().toLowerCase() + " grew (" + structureGrowEvent.getBlocks().size() + " blocks updated)";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "STRUCTURE_GROW", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onLightningStrike(LightningStrikeEvent lightningStrikeEvent) {
        if (!this.plugin.isWorldLoggingEnabled(lightningStrikeEvent.getLightning().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "LIGHTNING_STRIKE", true)) {
            return;
        }
        Location location = lightningStrikeEvent.getLightning().getLocation();
        String string = "Lightning struck at (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "LIGHTNING_STRIKE", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPortalCreate(PortalCreateEvent portalCreateEvent) {
        if (!this.plugin.isWorldLoggingEnabled(portalCreateEvent.getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "PORTAL_CREATE", true)) {
            return;
        }
        if (portalCreateEvent.getBlocks() != null && !portalCreateEvent.getBlocks().isEmpty()) {
            Location location = ((BlockState)portalCreateEvent.getBlocks().get(0)).getLocation();
            String string = "Portal created (" + portalCreateEvent.getBlocks().size() + " blocks)";
            this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "PORTAL_CREATE", string, null, location);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onLeavesDecay(LeavesDecayEvent leavesDecayEvent) {
        if (!this.plugin.isWorldLoggingEnabled(leavesDecayEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "LEAVES_DECAY", true)) {
            return;
        }
        Location location = leavesDecayEvent.getBlock().getLocation();
        String string = "Leaves decayed at (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "LEAVES_DECAY", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockForm(BlockFormEvent blockFormEvent) {
        if (!this.plugin.isWorldLoggingEnabled(blockFormEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "BLOCK_FORM", true)) {
            return;
        }
        Location location = blockFormEvent.getBlock().getLocation();
        String string = "Formed " + blockFormEvent.getNewState().getType().toString().toLowerCase() + " from " + blockFormEvent.getBlock().getType().toString().toLowerCase();
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "BLOCK_FORM", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPistonExtend(BlockPistonExtendEvent blockPistonExtendEvent) {
        if (!this.plugin.isWorldLoggingEnabled(blockPistonExtendEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "PISTON_EXTEND", true)) {
            return;
        }
        Location location = blockPistonExtendEvent.getBlock().getLocation();
        String string = "Piston extended, moved " + blockPistonExtendEvent.getBlocks().size() + " blocks";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "PISTON_EXTEND", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPistonRetract(BlockPistonRetractEvent blockPistonRetractEvent) {
        if (!this.plugin.isWorldLoggingEnabled(blockPistonRetractEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "PISTON_RETRACT", true)) {
            return;
        }
        Location location = blockPistonRetractEvent.getBlock().getLocation();
        String string = "Piston retracted, moved " + blockPistonRetractEvent.getBlocks().size() + " blocks";
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "PISTON_RETRACT", string, null, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntityDeath(EntityDeathEvent entityDeathEvent) {
        if (!this.plugin.isWorldLoggingEnabled(entityDeathEvent.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "ENTITY_DEATH", true)) {
            return;
        }
        if (entityDeathEvent.getEntity() instanceof Player) {
            return;
        }
        Location location = entityDeathEvent.getEntity().getLocation();
        Object object = entityDeathEvent.getEntityType().toString().toLowerCase();
        if (entityDeathEvent.getEntity().getKiller() != null) {
            this.databaseMariadb.logActionAsync(entityDeathEvent.getEntity().getKiller().getUniqueId(), entityDeathEvent.getEntity().getKiller().getName(), "ENTITY_DEATH", (String)object, null, location);
        } else {
            String string = "";
            if (entityDeathEvent.getEntity().getLastDamageCause() != null) {
                string = entityDeathEvent.getEntity().getLastDamageCause().getCause().toString().toLowerCase();
            }
            object = (String)object + (String)(!string.isEmpty() ? " by " + string : "");
            this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "ENTITY_DEATH", (String)object, null, location);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPaintingBreakGeneral(HangingBreakEvent hangingBreakEvent) {
        if (!this.plugin.isWorldLoggingEnabled(hangingBreakEvent.getEntity().getWorld())) {
            return;
        }
        Object object = hangingBreakEvent.getEntity();
        if (!(object instanceof Painting)) {
            return;
        }
        Painting painting = (Painting)object;
        if (hangingBreakEvent instanceof HangingBreakByEntityEvent) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "BLOCK_BREAK", true)) {
            return;
        }
        object = hangingBreakEvent.getCause() != null ? hangingBreakEvent.getCause().name().toLowerCase(Locale.ROOT) : "unknown";
        Block block = painting.getLocation().getBlock().getRelative(painting.getAttachedFace());
        Location location = block.getLocation();
        String string = "painting popped (" + (String)object + ")";
        String string2 = EntityMetadataUtil.encodePainting(painting);
        this.databaseMariadb.logActionAsync(SYSTEM_UUID, (String)object, "BLOCK_BREAK", string, string2, location);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onLiquidFlow(BlockFromToEvent blockFromToEvent) {
        if (!this.plugin.isWorldLoggingEnabled(blockFromToEvent.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "LIQUID_FLOW", true)) {
            return;
        }
        Block block = blockFromToEvent.getBlock();
        Block block2 = blockFromToEvent.getToBlock();
        Material material = block2.getType();
        BlockData blockData = block2.getBlockData();
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            Waterlogged waterlogged;
            BlockState blockState = block2.getState();
            Material material2 = blockState.getType();
            BlockData blockData2 = blockState.getBlockData();
            if (material2 == Material.WATER || material2 == Material.LAVA) {
                String string;
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("material", material2.name());
                jsonObject.addProperty("blockData", blockData2.getAsString());
                jsonObject.addProperty("beforeMaterial", material.name());
                jsonObject.addProperty("beforeBlockData", blockData.getAsString());
                jsonObject.addProperty("fromX", (Number)block.getX());
                jsonObject.addProperty("fromY", (Number)block.getY());
                jsonObject.addProperty("fromZ", (Number)block.getZ());
                UUID uUID = SYSTEM_UUID;
                String string2 = "System";
                if (material2 == Material.LAVA && PlayerActionListener.lavaPlacements.containsKey(block.getLocation()) && (string = Bukkit.getOfflinePlayer((UUID)(uUID = PlayerActionListener.lavaPlacements.get(block.getLocation()))).getName()) != null) {
                    string2 = string;
                }
                this.databaseMariadb.logActionAsync(uUID, string2, "LIQUID_FLOW", material2.name().toLowerCase(Locale.ROOT), jsonObject.toString(), block2.getLocation());
                return;
            }
            if (blockData2 instanceof Waterlogged && (waterlogged = (Waterlogged)blockData2).isWaterlogged()) {
                BlockData blockData3 = blockData2.clone();
                ((Waterlogged)blockData3).setWaterlogged(false);
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("material", material2.name());
                jsonObject.addProperty("blockData", blockData2.getAsString());
                jsonObject.addProperty("beforeBlockData", blockData3.getAsString());
                jsonObject.addProperty("waterlogged", Boolean.valueOf(true));
                jsonObject.addProperty("fluid", "WATER");
                jsonObject.addProperty("fromX", (Number)block.getX());
                jsonObject.addProperty("fromY", (Number)block.getY());
                jsonObject.addProperty("fromZ", (Number)block.getZ());
                this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "LIQUID_FLOW", "waterlogged", jsonObject.toString(), block2.getLocation());
            }
        });
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onHopperTransfer(InventoryMoveItemEvent inventoryMoveItemEvent) {
        String string;
        Location location;
        InventoryHolder inventoryHolder = inventoryMoveItemEvent.getInitiator().getHolder();
        if (inventoryHolder instanceof Hopper) {
            location = ((Hopper)inventoryHolder).getLocation();
            string = "hopper";
        } else if (inventoryHolder instanceof HopperMinecart) {
            location = ((HopperMinecart)inventoryHolder).getLocation();
            string = "minecart hopper";
        } else {
            return;
        }
        if (location == null || !this.plugin.isWorldLoggingEnabled(location.getWorld())) {
            return;
        }
        if (!this.plugin.isActionLoggingEnabled("world", "HOPPER_TRANSFER", true)) {
            return;
        }
        Location location2 = location.toBlockLocation();
        Material material = inventoryMoveItemEvent.getItem().getType();
        String string2 = ItemMetadataUtil.extractItemMetadata(inventoryMoveItemEvent.getItem());
        int n = inventoryMoveItemEvent.getItem().getAmount();
        HopperTransferKey hopperTransferKey2 = new HopperTransferKey(location2, string, material, string2);
        this.hopperTransferAggregate.compute(hopperTransferKey2, (hopperTransferKey, hopperTransferAggregateData) -> {
            if (hopperTransferAggregateData == null) {
                return new HopperTransferAggregateData(n, location2);
            }
            hopperTransferAggregateData.incrementCount(n);
            return hopperTransferAggregateData;
        });
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            HopperTransferAggregateData hopperTransferAggregateData = this.hopperTransferAggregate.remove(hopperTransferKey2);
            this.scheduledHopperTransfers.remove(hopperTransferKey2);
            if (hopperTransferAggregateData != null) {
                int n = hopperTransferAggregateData.getCount();
                String string = n > 1 ? hopperTransferKey2.getHolderType() + " x" + n + " " + hopperTransferKey2.getMaterial().toString().toLowerCase() : hopperTransferKey2.getHolderType() + " " + hopperTransferKey2.getMaterial().toString().toLowerCase();
                this.databaseMariadb.logActionAsync(SYSTEM_UUID, "System", "HOPPER_TRANSFER", string, hopperTransferKey2.getMetadata(), hopperTransferAggregateData.getLocation());
            }
        }, 10L);
        BukkitTask bukkitTask2 = this.scheduledHopperTransfers.put(hopperTransferKey2, bukkitTask);
        if (bukkitTask2 != null) {
            bukkitTask2.cancel();
        }
    }
}

