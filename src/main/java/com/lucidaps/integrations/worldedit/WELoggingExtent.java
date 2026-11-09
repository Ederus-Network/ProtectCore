/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  com.sk89q.worldedit.extent.AbstractDelegateExtent
 *  com.sk89q.worldedit.extent.Extent
 *  com.sk89q.worldedit.math.BlockVector3
 *  com.sk89q.worldedit.world.block.BlockState
 *  com.sk89q.worldedit.world.block.BlockStateHolder
 *  com.sk89q.worldedit.world.block.BlockType
 *  com.sk89q.worldedit.world.block.BlockTypes
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package com.lucidaps.integrations.worldedit;

import com.google.gson.JsonObject;
import com.lucidaps.ProtectCore;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.utilities.Debug;
import com.lucidaps.utilities.ProtectIds;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class WELoggingExtent
extends AbstractDelegateExtent {
    private final ProtectCore plugin;
    private final DatabaseMariadb db;
    private final String worldName;
    private final UUID actorUuid;
    private final String actorName;
    private static final int FLUSH_THRESHOLD = 4096;
    private final AtomicInteger queuedCount = new AtomicInteger(0);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Queued> queue = new ConcurrentLinkedQueue();
    private volatile CompletableFuture<Void> drainFuture = CompletableFuture.completedFuture(null);
    private final String extentId = UUID.randomUUID().toString().substring(0, 8);
    private static final Set<WELoggingExtent> LIVE = Collections.newSetFromMap(new ConcurrentHashMap());

    public WELoggingExtent(ProtectCore protectCore, DatabaseMariadb databaseMariadb, String string, UUID uUID, String string2, Extent extent) {
        super(extent);
        this.plugin = protectCore;
        this.db = databaseMariadb;
        this.worldName = string;
        this.actorUuid = uUID;
        this.actorName = string2;
        LIVE.add(this);
    }

    public static void flushAllQueuesDirect() {
        for (WELoggingExtent wELoggingExtent : LIVE) {
            try {
                wELoggingExtent.flushDirect();
            }
            catch (Throwable throwable) {
                wELoggingExtent.plugin.getLogger().warning("[WE-Bridge] Direct shutdown drain failed for extent " + wELoggingExtent.extentId + ": " + throwable.getMessage());
            }
        }
    }

    private void flushDirect() {
        Queued queued;
        if (this.queue.isEmpty()) {
            this.draining.set(false);
            LIVE.remove((Object)this);
            return;
        }
        World world = Bukkit.getWorld((String)this.worldName);
        if (world == null) {
            int n = 0;
            while (this.queue.poll() != null) {
                ++n;
            }
            this.queuedCount.set(0);
            this.draining.set(false);
            LIVE.remove((Object)this);
            this.plugin.getLogger().warning("[WE-Bridge] Unknown world '" + this.worldName + "' during shutdown direct drain. Dropped " + n + " queued logs.");
            return;
        }
        if (Debug.on(this.plugin, "we.drain")) {
            Debug.log(this.plugin, "we.drain", "begin direct drain id=" + this.extentId + " world=" + this.worldName + " qDepth=" + this.queuedCount.get(), new Object[0]);
        }
        int n = 0;
        while ((queued = this.queue.poll()) != null) {
            this.queuedCount.decrementAndGet();
            Location location = new Location(world, (double)queued.x(), (double)queued.y(), (double)queued.z());
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("origin", "worldedit");
            jsonObject.addProperty("material", queued.materialId());
            jsonObject.addProperty("blockData", queued.weStateString());
            jsonObject.addProperty("materialPretty", WELoggingExtent.stripNamespace(queued.materialId()));
            String string = queued.place() ? "BLOCK_PLACE" : "BLOCK_BREAK";
            String string2 = WELoggingExtent.stripNamespace(queued.materialId());
            String string3 = (queued.place() ? "placed " : "broken ") + string2 + " (by worldedit)";
            this.db.logActionAsyncWithEventTime(Objects.requireNonNullElse(this.actorUuid, ProtectIds.SYSTEM_ACTOR), Objects.requireNonNullElse(this.actorName, "WORLD_EDIT"), string, string3, jsonObject.toString(), location, queued.when());
            ++n;
        }
        this.queuedCount.set(0);
        this.draining.set(false);
        LIVE.remove((Object)this);
        if (Debug.on(this.plugin, "we.drain")) {
            Debug.log(this.plugin, "we.drain", "end direct drain id=" + this.extentId + " processed=" + n, new Object[0]);
        }
    }

    public void flushAll(ProtectCore protectCore) {
        this.requestDrain(protectCore);
    }

    private static boolean isAirLike(BlockStateHolder<?> blockStateHolder) {
        BlockType blockType = blockStateHolder.getBlockType();
        return blockType == BlockTypes.AIR || blockType == BlockTypes.CAVE_AIR || blockType == BlockTypes.VOID_AIR;
    }

    private static String normalizeWeMaterialId(BlockStateHolder<?> blockStateHolder) {
        return WELoggingExtent.isAirLike(blockStateHolder) ? "minecraft:air" : blockStateHolder.getBlockType().id();
    }

    private static String normalizeWeStateString(BlockStateHolder<?> blockStateHolder) {
        return WELoggingExtent.isAirLike(blockStateHolder) ? "minecraft:air" : blockStateHolder.toString();
    }

    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 blockVector3, B b) {
        int n;
        int n2;
        BlockState blockState = this.getBlock(blockVector3).toImmutableState();
        boolean bl2 = super.setBlock(blockVector3, b);
        if (!bl2) {
            return false;
        }
        boolean bl3 = WELoggingExtent.isAirLike(blockState);
        boolean bl4 = WELoggingExtent.isAirLike(b);
        if (bl3 && bl4) {
            return true;
        }
        long l = System.currentTimeMillis();
        BiConsumer<Boolean, BlockStateHolder> biConsumer = (bl, blockStateHolder) -> {
            String string = WELoggingExtent.normalizeWeStateString(blockStateHolder);
            String string2 = WELoggingExtent.normalizeWeMaterialId(blockStateHolder);
            if (Debug.on(this.plugin, "we.capture")) {
                Debug.log(this.plugin, "we.capture", "[" + this.extentId + "] " + (bl != false ? "place " : "break ") + string2 + " @ " + blockVector3.x() + "," + blockVector3.y() + "," + blockVector3.z() + " data=" + string, new Object[0]);
            }
            this.queue.add(new Queued(blockVector3.x(), blockVector3.y(), blockVector3.z(), (boolean)bl, string2, string, l));
        };
        if (!bl3 && !bl4) {
            biConsumer.accept(false, (BlockStateHolder)blockState);
            biConsumer.accept(true, b);
            n2 = 2;
        } else {
            int n3 = n = !bl4 ? 1 : 0;
            if (n != 0) {
                biConsumer.accept(true, b);
            } else {
                biConsumer.accept(false, (BlockStateHolder)blockState);
            }
            n2 = 1;
        }
        n = this.queuedCount.getAndAdd(n2);
        if (n == 0 || n + n2 >= 4096) {
            this.requestDrain(this.plugin);
        }
        return true;
    }

    private static String stripNamespace(String string) {
        if (string == null) {
            return "unknown";
        }
        int n = string.indexOf(58);
        return n >= 0 ? string.substring(n + 1) : string;
    }

    public static CompletableFuture<Void> drainAllAsync(ProtectCore protectCore) {
        if (LIVE.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        ArrayList<CompletableFuture<Void>> arrayList = new ArrayList<CompletableFuture<Void>>();
        for (WELoggingExtent wELoggingExtent : LIVE) {
            arrayList.add(wELoggingExtent.requestDrainAsync(protectCore));
        }
        return CompletableFuture.allOf((CompletableFuture[])arrayList.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> requestDrainAsync(ProtectCore protectCore) {
        if (this.queue.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!this.draining.compareAndSet(false, true)) {
            return this.drainFuture;
        }
        CompletableFuture<Void> completableFuture = new CompletableFuture<Void>();
        this.drainFuture = completableFuture;
        this.startDrainTask(completableFuture, protectCore);
        return completableFuture;
    }

    public void requestDrain(ProtectCore protectCore) {
        if (this.queue.isEmpty()) {
            return;
        }
        if (!this.draining.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture<Void> completableFuture = new CompletableFuture<Void>();
        this.drainFuture = completableFuture;
        this.startDrainTask(completableFuture, protectCore);
    }

    private void startDrainTask(final CompletableFuture<Void> completableFuture, final ProtectCore protectCore) {
        Bukkit.getScheduler().runTask((Plugin)protectCore, () -> {
            final World world = Bukkit.getWorld((String)this.worldName);
            if (world == null) {
                protectCore.getLogger().warning("[WE-Bridge] Unknown world '" + this.worldName + "'. Dropping " + this.queuedCount.get() + " queued logs.");
                this.queue.clear();
                this.queuedCount.set(0);
                this.draining.set(false);
                completableFuture.complete(null);
                return;
            }
            if (Debug.on(protectCore, "we.drain")) {
                Debug.log(protectCore, "we.drain", "begin drain id=" + this.extentId + " world=" + this.worldName + " qDepth=" + this.queuedCount.get(), new Object[0]);
            }
            final int n = Math.max(1024, protectCore.getConfig().getInt("we.flushBudgetPerTick", 8000));
            new BukkitRunnable(){

                public void run() {
                    Queued queued;
                    int n2;
                    for (n2 = 0; n2 < n && (queued = WELoggingExtent.this.queue.poll()) != null; ++n2) {
                        WELoggingExtent.this.queuedCount.decrementAndGet();
                        Location location = new Location(world, (double)queued.x(), (double)queued.y(), (double)queued.z());
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("origin", "worldedit");
                        jsonObject.addProperty("material", queued.materialId());
                        jsonObject.addProperty("blockData", queued.weStateString());
                        jsonObject.addProperty("materialPretty", WELoggingExtent.stripNamespace(queued.materialId()));
                        String string = queued.place() ? "BLOCK_PLACE" : "BLOCK_BREAK";
                        String string2 = WELoggingExtent.stripNamespace(queued.materialId());
                        String string3 = (queued.place() ? "placed " : "broken ") + string2 + " (by worldedit)";
                        WELoggingExtent.this.db.logActionAsyncWithEventTime(Objects.requireNonNullElse(WELoggingExtent.this.actorUuid, ProtectIds.SYSTEM_ACTOR), Objects.requireNonNullElse(WELoggingExtent.this.actorName, "WORLD_EDIT"), string, string3, jsonObject.toString(), location, queued.when());
                    }
                    if (Debug.on(protectCore, "we.drain")) {
                        Debug.log(protectCore, "we.drain", "tick drain budget=" + n + " processed=" + n2 + " remaining=" + WELoggingExtent.this.queue.size(), new Object[0]);
                    }
                    if (WELoggingExtent.this.queue.isEmpty()) {
                        WELoggingExtent.this.draining.set(false);
                        LIVE.remove((Object)WELoggingExtent.this);
                        if (Debug.on(protectCore, "we.drain")) {
                            Debug.log(protectCore, "we.drain", "end drain; removed from LIVE set", new Object[0]);
                        }
                        this.cancel();
                        completableFuture.complete(null);
                    }
                }
            }.runTaskTimer((Plugin)protectCore, 0L, 1L);
        });
    }

    private record Queued(int x, int y, int z, boolean place, String materialId, String weStateString, long when) {
    }
}

